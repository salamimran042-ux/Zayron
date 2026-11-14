import { createClient } from "npm:@supabase/supabase-js@2.116.0";

const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, apikey, content-type",
  "access-control-allow-methods": "GET, POST, PATCH, DELETE, OPTIONS",
};
const ATTACHMENT_BUCKET = "zayron-attachments";
const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;
const MIME_EXTENSIONS: Record<string, string> = {
  "application/pdf": "pdf", "text/plain": "txt", "text/markdown": "md",
  "text/csv": "csv", "application/json": "json", "image/png": "png",
  "image/jpeg": "jpg", "image/webp": "webp",
};
function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status, headers: { ...corsHeaders, "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}
function getPublishableKey(): string | null {
  const namedKeys = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
  if (namedKeys) {
    try {
      const parsed = JSON.parse(namedKeys) as Record<string, string>;
      if (typeof parsed.default === "string" && parsed.default.length > 0) return parsed.default;
    } catch {
      console.error("Publishable key environment is not valid JSON");
      return null;
    }
  }
  return Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? Deno.env.get("SUPABASE_ANON_KEY") ?? null;
}

async function authenticatedClient(request: Request) {
  const match = (request.headers.get("authorization") ?? "").match(/^Bearer\s+(.+)$/i);
  if (!match) return { error: "unauthorized" as const };
  const url = Deno.env.get("SUPABASE_URL");
  const key = getPublishableKey();
  if (!url || !key) return { error: "service_not_configured" as const };
  const client = createClient(url, key, {
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
    global: { headers: { Authorization: \`Bearer \${match[1]}\` } },
  });
  const { data, error } = await client.auth.getUser(match[1]);
  if (error || !data.user?.id) return { error: "unauthorized" as const };
  return { client, user: data.user };
}

type AttachmentReference = {
  id: string;
  display_name: string;
  mime_type: string;
  size_bytes: number;
  storage_path: string;
};

async function validateAttachments(
  client: ReturnType<typeof createClient>,
  ownerId: string,
  input: unknown,
): Promise<AttachmentReference[] | null> {
  if (input === undefined) return [];
  if (!Array.isArray(input) || input.length > 5) return null;
  const result: AttachmentReference[] = [];
  for (const value of input) {
    if (!value || typeof value !== "object") return null;
    const item = value as Record<string, unknown>;
    const id = typeof item.id === "string" ? item.id : "";
    const displayName = typeof item.display_name === "string" ? item.display_name.trim() : "";
    const mimeType = typeof item.mime_type === "string" ? item.mime_type.toLowerCase() : "";
    const sizeBytes = item.size_bytes;
    const storagePath = typeof item.storage_path === "string" ? item.storage_path : "";
    const extension = MIME_EXTENSIONS[mimeType];
    if (!/^[0-9a-f-]{36}$/i.test(id) ||
        !displayName || displayName.length > 160 ||
        !extension ||
        typeof sizeBytes !== "number" || !Number.isSafeInteger(sizeBytes) ||
        sizeBytes < 1 || sizeBytes > MAX_ATTACHMENT_BYTES) return null;

    // Only server-issued UUID filenames inside the authenticated user's folder
    // are accepted. Public URLs, traversal, and another user's path are rejected.
    const match = storagePath.match(/^([0-9a-f-]{36})\/([0-9a-f]{32})\.(pdf|txt|md|csv|json|png|jpg|webp)$/i);
    if (!match || match[1].toLowerCase() !== ownerId.toLowerCase() ||
        match[3].toLowerCase() !== extension) return null;
    const filename = match[2] + "." + match[3];
    const { data: objects, error } = await client.storage
      .from(ATTACHMENT_BUCKET)
      .list(ownerId, { search: filename, limit: 100 });
    const object = objects?.find((entry) => entry.name === filename);
    if (error || !object) return null;
    const actualSize = Number(object.metadata?.size);
    const actualMime = object.metadata?.mimetype;
    if (!Number.isSafeInteger(actualSize) || actualSize !== sizeBytes ||
        typeof actualMime !== "string" || actualMime.toLowerCase() !== mimeType) return null;

    result.push({
      id,
      display_name: displayName,
      mime_type: mimeType,
      size_bytes: sizeBytes,
      storage_path: storagePath,
    });
  }
  return result;
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const auth = await authenticatedClient(request);
  if ("error" in auth) return json({ error: auth.error }, auth.error === "unauthorized" ? 401 : 503);
  const { client, user } = auth;

  if (request.method === "GET") {
    const url = new URL(request.url);
    const conversationId = url.searchParams.get("conversation_id") ?? "";
    if (!/^[0-9a-f-]{36}$/i.test(conversationId)) return json({ error: "conversation_id_required" }, 400);
    const { data, error } = await client.from("messages")
      .select("id,conversation_id,role,content,status,metadata,created_at")
      .eq("conversation_id", conversationId).eq("user_id", user.id)
      .order("created_at", { ascending: true }).limit(500);
    if (error) return json({ error: "messages_unavailable" }, 500);
    return json({ messages: data ?? [] });
  }

  if (request.method === "POST") {
    let body: Record<string, unknown>;
    try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
    const conversationId = typeof body.conversation_id === "string" ? body.conversation_id : "";
    const content = typeof body.content === "string" ? body.content : "";
    if (!/^[0-9a-f-]{36}$/i.test(conversationId)) return json({ error: "invalid_conversation_id" }, 400);
    if (content.length > 100000) return json({ error: "invalid_message_content" }, 400);
    const attachments = await validateAttachments(client, user.id, body.attachments);
    if (attachments === null) return json({ error: "invalid_or_unowned_attachment" }, 400);
    if (!content.trim() && attachments.length === 0) return json({ error: "invalid_message_content" }, 400);

    const { data: conversation, error: conversationError } = await client.from("conversations")
      .select("id").eq("id", conversationId).eq("user_id", user.id).maybeSingle();
    if (conversationError) return json({ error: "conversation_unavailable" }, 500);
    if (!conversation) return json({ error: "conversation_not_found" }, 404);

    const { data, error } = await client.from("messages").insert({
      id: crypto.randomUUID(), conversation_id: conversationId, user_id: user.id,
      role: "user", content, status: "complete", metadata: { attachments },
    }).select("id,conversation_id,role,content,status,metadata,created_at").single();
    if (error || !data) return json({ error: "message_create_failed" }, 500);

    const { error: updateError } = await client.from("conversations")
      .update({ updated_at: new Date().toISOString() })
      .eq("id", conversationId).eq("user_id", user.id);
    if (updateError) console.error("Conversation timestamp update failed");
    return json({ message: data }, 201);
  }

  return json({ error: "method_not_allowed" }, 405);
});
