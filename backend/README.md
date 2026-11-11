# Zayron AI backend

FastAPI service foundation for the Zayron AI Android application.

## Runtime configuration

Supply values through protected environment variables or a secret manager. Never commit real credentials.

- `ZAYRON_ENVIRONMENT`: environment label.
- `ZAYRON_DATABASE_URL`: PostgreSQL connection string for the restricted `zayron_runtime` login. This is the only database URL the running API needs.
- `ZAYRON_MIGRATION_DATABASE_URL`: privileged PostgreSQL connection string used only for the one-off migration command. Do not put it in the API service's runtime environment.
- `ZAYRON_WORKER_TOKEN`: long random secret used only by trusted job workers; keep it in a server-side secret manager.
- `ZAYRON_WORKER_API_URL`: HTTPS origin of the deployed Zayron API, configured only on the background worker.
- `ZAYRON_WORKER_ID`: stable worker identifier (defaults to `zayron-document-worker`).
- `ZAYRON_WORKER_POLL_SECONDS`: idle queue polling interval (defaults to 5 seconds).
- `ZAYRON_WORKER_LEASE_SECONDS`: job lease duration from 30 to 900 seconds (defaults to 600).
- `ZAYRON_SUPABASE_URL`: clean HTTPS Supabase project origin.
- `ZAYRON_SUPABASE_PUBLISHABLE_KEY`: current Supabase publishable API key used by the server-side Auth adapter.
- `ZAYRON_SUPABASE_SECRET_KEY`: server-only Supabase secret key used by the isolated document worker to retrieve one already-authorized private object. It bypasses RLS, so the worker verifies the job lease, original user message, owner UUID path, MIME type and size before making the exact-object request. It must never be sent to Android.
- `ZAYRON_SUPABASE_ANON_KEY` and `ZAYRON_SUPABASE_SERVICE_ROLE_KEY`: legacy compatibility variables only. Prefer the current publishable/secret key names for new projects.

The Auth adapter sends the publishable key in the Supabase `apikey` header. It does not use the secret key for login, registration, refresh, or logout. Configuration status reveals presence only, never secret values. Do not put any Supabase secret key, database password, or worker token in the Android app or public repository.

## Database security and connection model

The Supabase database uses dedicated, non-bypass roles:

- `zayron_runtime` is the backend login role. It is deliberately `NOINHERIT` and can switch only into the two restricted group roles.
- `zayron_api` is used for authenticated user requests. Each request starts a transaction, sets this role locally, and sets `request.jwt.claim.sub` from the subject of the already-verified Supabase access token. PostgreSQL RLS then checks `auth.uid() = user_id`.
- `zayron_worker` is used only after the separate internal worker bearer token has been verified. It can claim and update jobs and insert job events, but has no access to unrelated application tables.
- None of these roles has `BYPASSRLS`. The API must not connect as `postgres` or `service_role`.

All seven application tables have RLS enabled and forced. Anonymous, authenticated Data API, and service-role table grants are revoked; the backend uses its restricted direct PostgreSQL connection. The migration also removes broad default table/sequence grants for objects created by the PostgreSQL migration owner. Do not add broad grants to clear an advisor warning.

The `zayron_runtime` role is created without login/password by the security migration. Before deployment, enable login and set a strong password using the Supabase SQL editor or approved database administration workflow, then store the resulting connection string only in the hosting provider's secret manager. Never paste the password into chat, commit it, or place it in Android.

Choose the Supabase connection mode based on the deployment host's network: direct connections are suitable for persistent IPv6-capable backends; use the shared pooler's **session mode** for an IPv4-only persistent host. Copy the exact host and username from Supabase Dashboard → Connect; do not construct a pooler hostname. The driver requires TLS.

The API does not run migrations at startup. Run migrations as a separate deployment step with `ZAYRON_MIGRATION_DATABASE_URL`, using a privileged database connection that is never configured on the live API process:

```bash
cd backend
python -m pip install -e ".[dev]"
python -m app.db.migrate
pytest
```

The migration ledger is stored in the unexposed `zayron_private.schema_migrations` schema. The runner takes a PostgreSQL transaction-scoped advisory lock, applies each migration atomically, records a SHA-256 checksum, and refuses to continue if an already-applied migration has been edited. Add a new numbered SQL file for every future schema change; do not rewrite an applied migration.

## Database platform foundation

The versioned migrations create the platform's core persistence tables:

- `user_profiles` for user preferences and profile metadata.
- `conversations` and `messages` for account-owned chat history. A composite foreign key ensures a message's `user_id` matches its conversation owner.
- `user_entitlements` for plan and access state; this is infrastructure only, not a billing integration.
- `usage_events` for auditable feature usage, token counts, optional estimated cost and idempotency.
- `ai_jobs` for queued work, retries, scheduling and terminal outcomes.
- `ai_job_events` for job lifecycle events.

## Authenticated platform API

The API exposes account-scoped profile, conversation, message, job, entitlement and usage-read endpoints. Job creation is idempotent when an `Idempotency-Key` is supplied. A client can cancel a job only while it remains queued. The server never trusts a client-supplied account ID; ownership comes from the verified JWT subject. PostgreSQL RLS independently enforces the same ownership boundary for every query.

The job API includes a worker control plane: workers authenticate with a separate server-side token, claim jobs using PostgreSQL `FOR UPDATE SKIP LOCKED`, renew leases, and report success or failure. Failures can be rescheduled within the attempt limit. Worker endpoints never accept a user ID from the client. The document worker implementation is described below; it is not live until separately deployed and configured. No AI provider is connected. Usage events and entitlement grants are server-owned and cannot be written through client routes.

## Authenticated chat boundary

- `POST /api/v1/chat/stream` accepts the Android chat request shape (`requestId`, optional `conversationId`, and ordered user/assistant messages) and requires a verified Supabase bearer token.
- The endpoint validates that the final message is from the user and bounds message count and content size.
- Until a model provider is deliberately configured, it returns HTTP 503 with the machine-readable code `SERVICE_UNAVAILABLE`. It does not fabricate an answer, persist messages, or forward chat content to an unconfigured third party.

This route is a safe API contract foundation, not a live AI integration. A server-side provider adapter and an approved provider/configuration are still required before streamed assistant responses can be generated.

## Authentication foundation

- `POST /api/v1/auth/register` creates an account through Supabase Auth; email confirmation remains provider-controlled.
- `POST /api/v1/auth/password-reset` requests a reset email with a generic response to avoid account enumeration.
- `POST /api/v1/auth/login` exchanges email/password with Supabase Auth over HTTPS.
- `POST /api/v1/auth/refresh` exchanges a refresh token and returns the provider-rotated token pair.
- `POST /api/v1/auth/logout` revokes the current provider session using a Bearer access token.
- `GET /api/v1/me` verifies JWT signature, issuer, audience and required claims before returning the subject.

Supabase Auth owns credential validation, refresh rotation and session revocation. The backend does not persist passwords or refresh tokens. Android must store token material only in its Android Keystore-backed encrypted store. Provider response bodies and credentials are not exposed in API errors. Supabase project-side rate limits must be reviewed before production.

These routes are implemented in source and tested with mocked provider responses; they are not live until a real Supabase project is configured and the backend is deployed. The current key names are supported while legacy anon/service-role environment names remain temporarily compatible.


## Private attachment retrieval and document extraction

- `POST /api/v1/cloud/attachments/extract` accepts an attachment storage path, declared MIME type and byte count. It requires the user's verified Supabase JWT.
- The backend validates that the path is a server-generated UUID filename inside that same user's folder, then downloads the object from the private bucket using the user's JWT and publishable key. It does not create a public URL or use a service-role key.
- PDF text extraction is bounded to 100 pages and 100,000 characters. Plain text, Markdown, CSV and JSON are decoded as UTF-8. Declared size and MIME type are checked against the retrieved object.
- Images are deliberately rejected with `VISION_PROCESSING_NOT_CONFIGURED` until a secure vision/model adapter exists. Scanned PDFs with no text layer are not OCR'd yet.
- This endpoint prepares document text for a future model request; it does not generate an AI answer, perform RAG, or claim that Zayron understands the attachment.


## Document job orchestration

- `POST /api/v1/documents/jobs` queues document preparation for one attachment already recorded in a user's own chat message.
- The request supplies the conversation, message and attachment IDs only. The API loads the message under the authenticated user's database/RLS context, confirms it is a user message in the owned conversation, selects the matching stored attachment reference, and revalidates its owner-scoped UUID storage path and MIME/extension pairing.
- The endpoint creates an idempotent `document_ingest` job in the existing `ai_jobs` queue. Its payload contains the verified attachment reference and bounded extraction limits, not the file bytes or extracted text.
- Repeating the same request returns the existing job rather than creating duplicate work. Unsupported image attachments are rejected from this text-extraction job path.
- This endpoint is queue orchestration. The secure worker implementation below downloads the exact private object, performs bounded text extraction, and persists the result to the owner-scoped job record. No model call, OCR, RAG, or document answer is claimed.

## Secure document worker

Run the document worker as a separate background process, not inside the Android app or the API web process:

```bash
cd backend
python -m app.workers.document_worker
```

Configure the worker with ZAYRON_WORKER_API_URL, ZAYRON_WORKER_TOKEN, ZAYRON_WORKER_ID, ZAYRON_SUPABASE_URL, and ZAYRON_SUPABASE_SECRET_KEY in the hosting provider's protected environment. Do not add these secrets to GitHub source, Android resources, APK build arguments, or logs. The worker process claims only document_ingest jobs.

Before retrieving a file, the worker asks the API to re-check the active lease and compare the queued attachment reference against the original user message under that user's RLS context. The worker then downloads only that exact path from the private zayron-attachments bucket using the server-only Supabase secret key in the apikey header. It does not list the bucket, create a public URL, or accept a client-supplied path. The downloaded MIME type and byte count must match the stored message metadata.

Extraction supports text-layer PDFs (maximum 100 pages), plain text, Markdown, CSV and JSON, with a 10 MiB input cap and a 100,000-character extraction cap. Encrypted PDFs, malformed documents, unsupported types, mismatched metadata and oversized results fail with bounded machine-readable error codes. Scanned PDF OCR and image understanding remain unsupported until a dedicated OCR/vision adapter is added.

On success, the worker writes a bounded extraction result to the same leased ai_jobs.result_payload record. Completion is conditional on the worker still owning an unexpired lease; a stale worker cannot overwrite a newer attempt. Job lifecycle events record state and safe metadata, never file bytes or extracted text. The authenticated owner can poll GET /api/v1/documents/jobs/{job_id} to read status and the completed result; RLS and the user-scoped API transaction prevent cross-account reads.

This worker is implemented and covered by mocked storage/API tests. It is not live until a separate background worker service is deployed and its protected environment is configured. Do not place the Supabase secret key or worker token in the API's Android-facing configuration.
