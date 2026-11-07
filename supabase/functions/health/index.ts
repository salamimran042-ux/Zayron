// Public, non-sensitive liveness endpoint. It does not access the database,
// authenticate users, or expose configuration/secrets.
Deno.serve((request: Request) => {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response(
      JSON.stringify({ error: "method_not_allowed" }),
      {
        status: 405,
        headers: {
          "content-type": "application/json; charset=utf-8",
          "cache-control": "no-store",
          "allow": "GET, HEAD",
        },
      },
    );
  }

  const body = JSON.stringify({
    status: "ok",
    service: "zayron-ai-edge",
    endpoint: "health",
    database: "not_checked",
  });

  return new Response(request.method === "HEAD" ? null : body, {
    status: 200,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
});
