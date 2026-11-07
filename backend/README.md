# Zayron AI backend

This folder contains the independent Python backend foundation for the existing Zayron AI Android project.

## Current status

Implemented:
- FastAPI application package
- Process-level `GET /health` endpoint
- Database-aware `GET /ready` endpoint that returns ready only after a real PostgreSQL ping
- Safe `GET /config/status` endpoint that reports integration configuration presence without exposing credentials
- Environment-based settings with secret-typed credential fields
- Async PostgreSQL connection pool with bounded pool sizing and startup/shutdown lifecycle
- Supabase asymmetric JWT verification boundary using the project's published JWKS, strict issuer/audience checks, and an explicit signing-algorithm allowlist
- FastAPI HTTP Bearer authentication dependency wired to the Supabase verifier
- Protected `GET /v1/me` endpoint returning only the verified user ID and optional role
- Authentication tests for missing, unconfigured, invalid, expired, and valid tokens
- Python package metadata and development dependencies
- Container definition for a future Cloud Run deployment
- Separate backend CI workflow

Not implemented or connected yet:
- A live Supabase project or hosted PostgreSQL database in this repository environment
- Android login/session screens and token refresh integration
- Legacy Supabase HS256 shared-secret verification (the verifier intentionally accepts only asymmetric ES256/RS256 signing keys)
- Private file storage
- AI provider integrations
- Cloud Tasks or background workers
- Credits, billing, or entitlement enforcement
- Production deployment

The token verifier obtains public signing keys from `{SUPABASE_URL}/auth/v1/.well-known/jwks.json`. It validates the token signature, expected issuer, `authenticated` audience, and required `exp`, `iat`, and `sub` claims. It fails closed when Supabase is not configured and does not accept an unverified token. The service-role key is not used for user-token verification. The FastAPI dependency performs verification in a worker thread so a remote JWKS lookup does not block the async event loop. Failed or missing authentication returns HTTP 401 with `WWW-Authenticate: Bearer`.

`GET /v1/me` requires a valid Supabase access token in the Authorization Bearer header. It returns only the verified subject as `id` and the optional role claim. It never returns the raw token or secret material.

`/health` confirms only that the API process responds. `/ready` returns success only after a real `SELECT 1` database round-trip; without configured and reachable PostgreSQL it returns HTTP 503. `/config/status` reports whether integration settings are present without exposing credentials.

## Configuration

Settings are read from environment variables with the `ZAYRON_` prefix. See `.env.example` for the variable names. Copy it to `.env` for local development and fill in only credentials you actually have. `.env` is ignored by Git. Never commit real secrets, service-account files, database passwords, or provider API keys. Production secrets must come from the deployment environment or a managed secret store.

## Run locally

Requires Python 3.12 or newer.

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
python -m pip install -e ".[dev]"
uvicorn app.main:app --reload
```

Inspect `http://127.0.0.1:8000/health`, `http://127.0.0.1:8000/ready`, `http://127.0.0.1:8000/config/status`, and `http://127.0.0.1:8000/docs`. Without a configured database, `/ready` correctly returns HTTP 503. Without Supabase configuration, protected routes correctly return HTTP 401.

## Run tests

```bash
cd backend
python -m pip install -e ".[dev]"
pytest
```

## Architecture direction

The approved target architecture is FastAPI on Google Cloud Run, Supabase Auth, Supabase PostgreSQL with pgvector, private Supabase Storage, Google Cloud Tasks, and Google Secret Manager. This repository currently contains only the API and verification foundations; no paid cloud resources have been provisioned.
