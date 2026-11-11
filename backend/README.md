# Backend runtime configuration

The API supports an optional OpenAI-compatible chat provider bridge.

Set these environment variables on the backend only:
- `ZAYRON_AI_PROVIDER_BASE_URL`: provider API root, including `/v1` where applicable.
- `ZAYRON_AI_PROVIDER_API_KEY`: secret API key; never put this in Android or source control.
- `ZAYRON_AI_PROVIDER_MODEL`: exact model identifier supported by the selected provider.
- `ZAYRON_AI_PROVIDER_TIMEOUT_SECONDS`: request timeout (10–180 seconds, default 90).

The provider must implement `POST {base_url}/chat/completions` with OpenAI-compatible
streaming responses. The chat endpoint fails closed with HTTP 503 until all three
provider settings are present. Production requires an HTTPS base URL. Provider failures
are returned as generic SSE error events; upstream response bodies and credentials are
never forwarded to clients.

This bridge is provider-neutral. It does not select, purchase, or configure a provider,
and it does not deploy or modify production infrastructure.
