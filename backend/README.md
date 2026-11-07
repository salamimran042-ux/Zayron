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

## Authentication foundation

- `POST /api/v1/auth/login` exchanges email/password with Supabase Auth over HTTPS.
- `POST /api/v1/auth/refresh` exchanges a refresh token and returns the provider-rotated token pair.
- `POST /api/v1/auth/logout` revokes the current provider session using a Bearer access token.
- `GET /api/v1/me` verifies JWT signature, issuer, audience and required claims before returning the subject.

Supabase Auth owns credential validation, refresh rotation and session revocation. The backend does not persist passwords or refresh tokens. Android must store token material only in its Android Keystore-backed encrypted store. Provider response bodies and credentials are not exposed in API errors. Supabase project-side rate limits must be reviewed before production.

These routes are implemented in source and tested with mocked provider responses. They are not live until a real Supabase project is configured and the backend is deployed.

## Local tests

```bash
python -m pip install -e ".[dev]"
pytest
```
