# Zayron AI backend

FastAPI service foundation for the Zayron AI Android application.

## Runtime configuration

Supply values through protected environment variables or a secret manager. Never commit real credentials.

- `ZAYRON_ENVIRONMENT`: environment label.
- `ZAYRON_DATABASE_URL`: PostgreSQL connection string.
- `ZAYRON_SUPABASE_URL`: clean HTTPS Supabase project origin.
- `ZAYRON_SUPABASE_ANON_KEY`: Supabase public anon key used by the server-side Auth client.
- `ZAYRON_SUPABASE_SERVICE_ROLE_KEY`: reserved for narrowly scoped server administration; never send it to Android.

Configuration status reveals presence only, never secret values.

## Database platform foundation

The versioned migrations create the platform's core persistence tables:

- `user_profiles` for user preferences and profile metadata.
- `conversations` and `messages` for account-owned chat history. A composite foreign key ensures a message's `user_id` matches its conversation owner.
- `user_entitlements` for plan and access state; this is infrastructure only, not a billing integration.
- `usage_events` for auditable feature usage, token counts, optional estimated cost and idempotency.
- `ai_jobs` for queued work, retries, scheduling and terminal outcomes.

Apply migrations explicitly after configuring PostgreSQL:

```bash
cd backend
python -m pip install -e ".[dev]"
python -m app.db.migrate
pytest
```

Migrations are not run automatically at API startup. The runner takes a PostgreSQL transaction-scoped advisory lock, applies each migration atomically, records a SHA-256 checksum, and refuses to continue if an already-applied migration has been edited. Add a new numbered SQL file for every future schema change; do not rewrite an applied migration.

The API connects with a server-side database role. Every future data-access query must scope reads and writes by the authenticated `user_id`; the schema's composite ownership constraint additionally prevents cross-account message/conversation links. Do not expose the database connection or privileged credentials to Android. Production row-level security and database-role policy must be designed and verified before multi-tenant production launch.

## Authenticated platform API

The API exposes account-scoped profile, conversation, message, job, entitlement and usage-read endpoints. Job creation is idempotent when an `Idempotency-Key` is supplied. A client can cancel a job only while it remains queued. The server never trusts a client-supplied account ID; ownership comes from the verified JWT subject.

The job API currently creates and tracks queued work only. A worker, queue-claim lease, provider execution, file storage and actual AI generation are not connected. Usage events and entitlement grants are server-owned and cannot be written through client routes.

## Authentication foundation

- `POST /api/v1/auth/login` exchanges email/password with Supabase Auth over HTTPS.
- `POST /api/v1/auth/refresh` exchanges a refresh token and returns the provider-rotated token pair.
- `POST /api/v1/auth/logout` revokes the current provider session using a Bearer access token.
- `GET /api/v1/me` verifies JWT signature, issuer, audience and required claims before returning the subject.

Supabase Auth owns credential validation, refresh rotation and session revocation. The backend does not persist passwords or refresh tokens. Android must store token material only in its Android Keystore-backed encrypted store. Provider response bodies and credentials are not exposed in API errors. Supabase project-side rate limits must be reviewed before production.

These routes are implemented in source and tested with mocked provider responses. They are not live until a real Supabase project is configured and the backend is deployed.
