import { createClient } from "npm:@supabase/supabase-js@2.116.0";

const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, apikey, content-type",
  "access-control-allow-methods": "GET, OPTIONS",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function getPublishableKey(): string | null {
  const namedKeys = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
  if (namedKeys) {
    try {
      const parsed = JSON.parse(namedKeys) as Record<string, string>;
      if (typeof parsed.default === "string" && parsed.default.length > 0) {
        return parsed.default;
      }
    } catch {
      console.error("Publishable key environment is not valid JSON");
      return null;
    }
  }

  return Deno.env.get("SUPABASE_PUBLISHABLE_KEY")
    ?? Deno.env.get("SUPABASE_ANON_KEY")
    ?? null;
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method !== "GET") {
    return jsonResponse({ error: "method_not_allowed" }, 405);
  }

  const authorization = request.headers.get("authorization") ?? "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const apiKey = getPublishableKey();

  if (!supabaseUrl || !apiKey) {
    return jsonResponse({ error: "service_not_configured" }, 503);
  }

  const supabase = createClient(supabaseUrl, apiKey, {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
      detectSessionInUrl: false,
    },
    global: {
      headers: { Authorization: `Bearer ${match[1]}` },
    },
  });

  const { data: authData, error: authError } =
    await supabase.auth.getUser(match[1]);

  if (authError || !authData.user?.id) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }

  const { data, error } = await supabase.rpc("zayron_get_my_profile");

  if (error) {
    console.error("Profile RPC failed", error.code ?? "unknown");
    return jsonResponse({ error: "profile_unavailable" }, 500);
  }

  const profile = Array.isArray(data) ? data[0] : null;
  if (!profile || profile.user_id !== authData.user.id) {
    return jsonResponse({ error: "profile_unavailable" }, 500);
  }

  return jsonResponse({ profile });
});
