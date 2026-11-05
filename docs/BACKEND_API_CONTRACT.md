# Zayron AI — backend integration contract (planning document)

This document defines a safe integration boundary for the Android client. It is a proposal, not evidence that endpoints exist or are deployed.

## Non-negotiable security rules

1. The Android app talks only to a Zayron-controlled HTTPS backend.
2. AI provider keys stay on the backend, in a secret manager or protected environment variables.
3. The backend authenticates users and checks entitlements, credits, rate limits and permissions for every operation.
4. File uploads use short-lived, user-scoped upload authorization. The backend validates file type, size and ownership.
5. Long-running operations return job identifiers; the client polls or subscribes to authenticated job status.
6. Logs must not store passwords, access tokens, raw private documents or provider secrets.
7. Provide user-visible failure states and allow safe retry without charging twice.

## Proposed API surface

Base path: `/api/v1`

| Capability | Proposed route | Expected behavior |
|---|---|---|
| Account | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` | Account lifecycle and short-lived access tokens |
| Profile | `GET /me` | User profile and entitlement summary |
| Chat | `POST /chat/stream` | Authenticated streaming response; server enforces usage limits |
| Upload | `POST /uploads` | Create scoped upload and return a short-lived target |
| Video | `POST /video/image-to-video` | Validate image and prompt; return job ID |
| Image edit | `POST /image/inpaint`, `POST /image/remove-object` | Validate source image and mask/instruction; return job ID |
| Documents | `POST /documents`, `POST /documents/{id}/ask` | Ingest permitted document and answer with source references |
| App Builder | `POST /apps/generate`, `GET /apps/jobs/{jobId}` | Generate files in an isolated worker and report build status |
| Voice | `POST /voice/transcribe`, `POST /voice/speak` | Optional transcription and speech synthesis, subject to consent |
| Jobs | `GET /jobs/{jobId}`, `POST /jobs/{jobId}/cancel` | User-scoped job status and cancellation |
| Usage | `GET /usage` | Current balance and usage history |

## Chat streaming wire contract (proposed, not deployed)

Proposed authenticated request: `POST /api/v1/chat/stream`, JSON request body, `Accept: text/event-stream`, `Authorization: Bearer <access-token>`, and an `Idempotency-Key` header matching the JSON `requestId`.

```json
{
  "requestId": "client-generated-unique-id",
  "conversationId": null,
  "messages": [{ "role": "user", "content": "Hello" }]
}
```

The response uses Server-Sent Events:

```text
event: message.delta
data: {"delta":"Hello"}

event: message.completed
data: {"messageId":"server-assistant-message-id"}
```

Supported events:
- `message.delta`: JSON `{"delta":"text fragment"}`; append to the in-progress assistant message.
- `message.completed`: JSON `{"messageId":"..."}`; confirms success. Only now may the client mark the response SENT.
- `error`: JSON `{"code":"DOCUMENTED_ERROR_CODE"}`; terminate and map the code to an approved safe message.
- `data: [DONE]`: optional protocol sentinel only. It is not proof of successful completion and does not replace `message.completed`.

Unknown events and malformed frames are ignored. A disconnect or `[DONE]` sentinel before `message.completed` is an incomplete/failed response, never a successful answer. Retry the same logical request with the same idempotency key. Server events must never contain provider secrets, stack traces or private account data.

## Android authentication boundary

The Android source currently contains only an authentication domain contract. It is not connected to an endpoint and has no concrete repository or session-store implementation. Before enabling sign-in, the deployed backend must specify credential transport, access-token expiry, refresh-token rotation, revocation, logout behavior, account status responses and recovery flows. Credentials remain transient, are never logged, and tokens stay out of UI models. Any approved session material must use reviewed secure storage.

## Common response conventions

- JSON errors use stable codes such as `AUTH_REQUIRED`, `INSUFFICIENT_CREDITS`, `FILE_TOO_LARGE`, `UNSUPPORTED_MEDIA`, `RATE_LIMITED` and `SERVICE_UNAVAILABLE`.
- Job creation returns `jobId`, `status` and optional estimated wait text; do not invent completion percentages.
- Every operation has an idempotency key to prevent duplicate jobs or charges.
- The backend returns only resources belonging to the authenticated user.
- Client messages are safe and never expose raw response bodies.

## Required acceptance tests before enabling a feature

- Unauthenticated requests are rejected.
- User A cannot read, cancel or download User B's job or file.
- Expired and revoked tokens are rejected.
- Credit and entitlement checks happen server-side.
- Oversized, malformed and unsupported files are rejected.
- Provider outage produces a recoverable error and never falsely reports success.
- Retrying an idempotent request does not create a second charge or job.
- Secrets do not appear in APK resources, source, logs or network responses.
