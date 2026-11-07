import { createClient } from "npm:@supabase/supabase-js@2.116.0";

const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, apikey, content-type",
  "access-control-allow-methods": "GET, POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

function publishableKey(): string | null {
  const named = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
  if (named) {
    try {
      const parsed = JSON.parse(named) as Record<string, string>;
      if (typeof parsed.default === "string" && parsed.default.length > 0) return parsed.default;
    } catch {
      console.error("Publishable key environment is not valid JSON");
      return null;
    }
  }
  return Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? Deno.env.get("SUPABASE_ANON_KEY") ?? null;
}

function bearerToken(request: Request): string | null {
  const authorization = request.headers.get("authorization") ?? "";
  if (!authorization.toLowerCase().startsWith("bearer ")) return null;
  const token = authorization.slice(7).trim();
  return token.length > 0 ? token : null;
}

function sessionResponse(
  session: { access_token: string; refresh_token: string; expires_at?: number; expires_in: number },
  user: { id: string; email?: string; user_metadata?: Record<string, unknown> | null },
) {
  const metadata = user.user_metadata ?? {};
  const displayName = [metadata.display_name, metadata.full_name, metadata.name]
    .find((value) => typeof value === "string" && value.trim().length > 0);
  return {
    access_token: session.access_token,
    refresh_token: session.refresh_token,
    expires_at: session.expires_at ?? Math.floor(Date.now() / 1000) + session.expires_in,
    user: {
      id: user.id,
      email: user.email ?? "",
      display_name: typeof displayName === "string" ? displayName : null,
    },
  };
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const url = Deno.env.get("SUPABASE_URL");
  const key = publishableKey();
  if (!url || !key) return json({ error: "service_not_configured" }, 503);

  const path = new URL(request.url).pathname;
  const client = createClient(url, key, {
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
  });

  if (path.endsWith("/api/v1/auth/login") && request.method === "POST") {
    let body: Record<string, unknown>;
    try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
    const email = typeof body.email === "string" ? body.email.trim() : "";
    const password = typeof body.password === "string" ? body.password : "";
    if (!email || !password || password.length > 1024) return json({ error: "invalid_credentials" }, 400);

    const { data, error } = await client.auth.signInWithPassword({ email, password });
    if (error || !data.session || !data.user) return json({ error: "invalid_credentials" }, 401);
    return json(sessionResponse(data.session, data.user));
  }

  if (path.endsWith("/api/v1/auth/refresh") && request.method === "POST") {
    let body: Record<string, unknown>;
    try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
    const refreshToken = typeof body.refresh_token === "string" ? body.refresh_token : "";
    if (!refreshToken) return json({ error: "invalid_refresh_token" }, 400);

    const { data, error } = await client.auth.refreshSession({ refresh_token: refreshToken });
    if (error || !data.session || !data.user) return json({ error: "session_expired" }, 401);
    return json(sessionResponse(data.session, data.user));
  }

  if (path.endsWith("/api/v1/auth/logout") && request.method === "POST") {
    const token = bearerToken(request);
    if (!token) return json({ error: "unauthorized" }, 401);
    const { data, error } = await client.auth.getUser(token);
    if (error || !data.user?.id) return json({ error: "unauthorized" }, 401);

    const response = await fetch(`${url}/auth/v1/logout`, {
      method: "POST",
      headers: { apikey: key, authorization: `Bearer ${token}` },
    });
    if (!response.ok) return json({ error: "logout_unavailable" }, 502);
    return json({ ok: true });
  }

  if (path.endsWith("/api/v1/me") && request.method === "GET") {
    const token = bearerToken(request);
    if (!token) return json({ error: "unauthorized" }, 401);
    const { data: authData, error: authError } = await client.auth.getUser(token);
    if (authError || !authData.user?.id) return json({ error: "unauthorized" }, 401);

    const userClient = createClient(url, key, {
      auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
      global: { headers: { Authorization: `Bearer ${token}` } },
    });
    const { data, error } = await userClient.rpc("zayron_get_my_profile");
    if (error) {
      console.error("Profile RPC failed", error.code ?? "unknown");
      return json({ error: "profile_unavailable" }, 500);
    }
    const profile = Array.isArray(data) ? data[0] : null;
    if (!profile || profile.user_id !== authData.user.id) return json({ error: "profile_unavailable" }, 500);
    return json({
      id: authData.user.id,
      email: authData.user.email ?? "",
      display_name: profile.display_name ?? null,
    });
  }

  return json({ error: "not_found" }, 404);
});
