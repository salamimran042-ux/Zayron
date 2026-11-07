import { createClient } from "npm:@supabase/supabase-js@2.116.0";

const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, apikey, content-type",
  "access-control-allow-methods": "GET, POST, PATCH, DELETE, OPTIONS",
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
    global: { headers: { Authorization: `Bearer ${match[1]}` } },
  });
  const { data, error } = await client.auth.getUser(match[1]);
  if (error || !data.user?.id) return { error: "unauthorized" as const };
  return { client, user: data.user };
}
Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const auth = await authenticatedClient(request);
  if ("error" in auth) return json({ error: auth.error }, auth.error === "unauthorized" ? 401 : 503);
  const { client, user } = auth;

  if (request.method === "GET") {
    const includeArchived = new URL(request.url).searchParams.get("include_archived") === "true";
    let query = client.from("conversations")
      .select("id,title,metadata,created_at,updated_at,archived_at")
      .eq("user_id", user.id)
      .order("updated_at", { ascending: false })
      .limit(100);
    if (!includeArchived) query = query.is("archived_at", null);
    const { data, error } = await query;
    if (error) return json({ error: "conversations_unavailable" }, 500);
    return json({ conversations: data ?? [] });
  }

  if (request.method === "POST") {
    let body: Record<string, unknown>;
    try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
    const title = typeof body.title === "string" ? body.title.trim() : "New conversation";
    if (title.length > 160) return json({ error: "title_too_long" }, 400);
    const { data, error } = await client.from("conversations").insert({
      id: crypto.randomUUID(), user_id: user.id, title: title || "New conversation", metadata: {},
    }).select("id,title,metadata,created_at,updated_at,archived_at").single();
    if (error || !data) return json({ error: "conversation_create_failed" }, 500);
    return json({ conversation: data }, 201);
  }

  if (request.method === "PATCH") {
    let body: Record<string, unknown>;
    try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
    const id = typeof body.id === "string" ? body.id : "";
    if (!/^[0-9a-f-]{36}$/i.test(id)) return json({ error: "invalid_conversation_id" }, 400);
    const changes: Record<string, unknown> = { updated_at: new Date().toISOString() };
    if (typeof body.title === "string") {
      const title = body.title.trim();
      if (!title || title.length > 160) return json({ error: "invalid_title" }, 400);
      changes.title = title;
    }
    if (body.archived === true) changes.archived_at = new Date().toISOString();
    if (body.archived === false) changes.archived_at = null;
    const { data, error } = await client.from("conversations").update(changes)
      .eq("id", id).eq("user_id", user.id)
      .select("id,title,metadata,created_at,updated_at,archived_at").maybeSingle();
    if (error) return json({ error: "conversation_update_failed" }, 500);
    if (!data) return json({ error: "conversation_not_found" }, 404);
    return json({ conversation: data });
  }

  if (request.method === "DELETE") {
    const id = new URL(request.url).searchParams.get("id") ?? "";
    if (!/^[0-9a-f-]{36}$/i.test(id)) return json({ error: "invalid_conversation_id" }, 400);
    const { data, error } = await client.from("conversations").delete()
      .eq("id", id).eq("user_id", user.id).select("id").maybeSingle();
    if (error) return json({ error: "conversation_delete_failed" }, 500);
    if (!data) return json({ error: "conversation_not_found" }, 404);
    return json({ deleted: true, id });
  }

  return json({ error: "method_not_allowed" }, 405);
});
