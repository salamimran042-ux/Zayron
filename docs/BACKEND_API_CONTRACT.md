# Zayron AI — backend integration contract

This document defines the secure integration boundary for the Android client. Source implementation does not mean an endpoint is deployed or live.

## Security rules

1. Android talks only to a Zayron-controlled HTTPS backend.
2. AI provider keys stay on the backend in a secret manager or protected environment variables.
3. Every operation enforces user identity, ownership, credits, entitlements and rate limits server-side.
4. Uploads use short-lived user-scoped authorization and validate file type, size and ownership.
5. Long-running operations return job identifiers and authenticated status.
6. Logs must not store passwords, access tokens, private documents or provider secrets.
7. Retries must not cause duplicate jobs or charges.

## Authentication and session lifecycle

| Route | Behavior | Source status |
|---|---|---|
| `POST /api/v1/auth/login` | Exchange email/password with Supabase Auth; return access token, refresh token, absolute expiry and minimal profile | Implemented; mocked tests |
| `POST /api/v1/auth/refresh` | Exchange refresh token and return provider-rotated token pair | Implemented; mocked tests |
| `POST /api/v1/auth/logout` | Revoke current provider session using Bearer access token | Implemented; mocked tests |
| `GET /api/v1/me` | Verify Supabase JWT and return verified subject | Implemented; tests |

Supabase Auth owns password validation, refresh-token rotation and session revocation. The backend does not persist passwords or refresh tokens. The Android app must keep token material only in encrypted Android Keystore storage. Supabase project rate limits and abuse controls must be reviewed before production.

Still outstanding: configure a real Supabase project, review email-confirmation/password policies, deploy the API over HTTPS, implement Android login/refresh/logout and concurrent refresh coordination, then test the full device-to-provider lifecycle.

## Implemented platform endpoints (source; database required)

| Route | Purpose | Access |
|---|---|---|
| `GET/PATCH /api/v1/profile` | Read/update the signed-in user's profile preferences | Authenticated |
| `GET/POST /api/v1/conversations` | List and create owned conversations | Authenticated |
| `GET/PATCH/DELETE /api/v1/conversations/{id}` | Read, rename/archive, or delete an owned conversation | Authenticated |
| `GET/POST /api/v1/conversations/{id}/messages` | Read messages or append a user message | Authenticated |
| `GET/POST /api/v1/jobs` | List jobs or enqueue a supported tool job | Authenticated |
| `GET /api/v1/jobs/{id}` | Read an owned job | Authenticated |
| `POST /api/v1/jobs/{id}/cancel` | Cancel a queued job | Authenticated |
| `GET /api/v1/entitlements` | Read plan/access state (defaults to free) | Authenticated |
| `GET /api/v1/usage` | Read recent usage totals and events | Authenticated |

Every database query is scoped by the verified account UUID. Missing or foreign-owned records return the same not-found response. Job creation accepts an optional `Idempotency-Key`; retries with the same key return the existing job rather than creating duplicates. User-facing APIs cannot write their own usage records or entitlement grants.

These routes require PostgreSQL and the explicit migrations. They are source implementation only; no live database, worker, billing integration, storage service, or AI provider is connected. Queued jobs remain queued until a separately secured worker is implemented.

## Remaining proposed API surface

- `POST /api/v1/auth/register`: account creation and email verification.
- `POST /api/v1/chat/stream`: authenticated AI streaming.
- `POST /api/v1/uploads`: scoped upload authorization.
- `POST /api/v1/video/image-to-video`: queued video generation.
- `POST /api/v1/image/inpaint` and `/remove-object`: image processing jobs.
- `POST /api/v1/documents` and `/documents/{id}/ask`: ingestion and cited document Q&A.
- `POST /api/v1/apps/generate` and `GET /api/v1/apps/jobs/{jobId}`: isolated app generation/build jobs.
- `POST /api/v1/voice/transcribe` and `/voice/speak`: optional speech services.
- `GET /api/v1/usage`: credits and usage history.

## Chat streaming contract (proposed, not deployed)

Authenticated `POST /api/v1/chat/stream` uses JSON, `Accept: text/event-stream`, a Bearer token and an idempotency key matching requestId. Success requires `message.completed`; a disconnect or `[DONE]` before that event is failure, not success.
