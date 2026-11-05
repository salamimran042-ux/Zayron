# Zayron AI — backend integration contract (planning document)

This document defines a safe integration boundary for the Android client. It is a contract proposal, not evidence that these endpoints exist or are deployed.

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
| Profile | `GET /me` | User profile and current entitlement summary |
| Chat | `POST /chat/stream` | Authenticated streaming response; server enforces usage limits |
| Upload | `POST /uploads` | Create a scoped upload and return a short-lived upload target |
| Video | `POST /video/image-to-video` | Validate uploaded image and prompt; return a job ID |
| Image edit | `POST /image/inpaint`, `POST /image/remove-object` | Validate source image and mask/instruction; return a job ID |
| Documents | `POST /documents`, `POST /documents/{id}/ask` | Ingest permitted document and answer with source references |
| App Builder | `POST /apps/generate`, `GET /apps/jobs/{jobId}` | Generate project files in an isolated worker and report build status |
| Voice | `POST /voice/transcribe`, `POST /voice/speak` | Optional transcription and speech synthesis, subject to user consent |
| Jobs | `GET /jobs/{jobId}`, `POST /jobs/{jobId}/cancel` | User-scoped job status and cancellation |
| Usage | `GET /usage` | Current balance and usage history |

## Common response conventions

- JSON errors use stable machine-readable codes such as `AUTH_REQUIRED`, `INSUFFICIENT_CREDITS`, `FILE_TOO_LARGE`, `UNSUPPORTED_MEDIA`, `RATE_LIMITED` and `SERVICE_UNAVAILABLE`.
- Job creation returns `jobId`, `status` and an optional estimated wait message. Do not invent completion percentages.
- Every operation has an idempotency key to prevent accidental duplicate jobs or charges.
- The backend returns only resources belonging to the authenticated user.
- Client-visible error messages are safe and do not expose provider or infrastructure details.

## Required acceptance tests before enabling a feature

- Unauthenticated request is rejected.
- User A cannot read, cancel or download User B's job or file.
- Expired and revoked tokens are rejected.
- Credit and entitlement checks happen server-side.
- Oversized, malformed and unsupported files are rejected.
- Provider outage produces a recoverable error and does not falsely report success.
- Retrying an idempotent request does not create a second charge or job.
- Secrets do not appear in APK resources, source, logs or network responses.
