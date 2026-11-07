# Zayron AI backend

This folder contains the independent Python backend foundation for the existing Zayron AI Android project.

## Current status

Implemented:
- FastAPI application package
- Process-level `GET /health` endpoint
- Database-aware `GET /ready` endpoint that returns ready only after a real PostgreSQL ping
- Safe `GET /config/status` endpoint that reports integration configuration presence without exposing credentials
- Environment-based settings with secret-typed credential fields
- Async PostgreSQL connection pool with bounded pool sizing, startup/shutdown lifecycle, and a typed adapter contract
- Automated health and configuration tests
- Python package metadata and development dependencies
- Container definition for a future Cloud Run deployment
- Separate backend CI workflow

Not implemented or connected yet:
- Supabase authentication and a live PostgreSQL connection in this repository environment (the async PostgreSQL adapter is implemented, but no database credentials or hosted database are connected)
- Private file storage
- AI provider integrations
- Cloud Tasks or background workers
- Credits, billing, or entitlement enforcement
- Production deployment

`/health` confirms only that the API process responds. `/ready` returns success only after a real `SELECT 1` database round-trip; without configured and reachable PostgreSQL it returns HTTP 503. `/config/status` reports whether integration settings are present without exposing credentials. The API opens a bounded async PostgreSQL pool only when a database URL is supplied and closes it during shutdown.

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

Inspect `http://127.0.0.1:8000/health`, `http://127.0.0.1:8000/ready`, and `http://127.0.0.1:8000/config/status`. Without a configured database, `/ready` correctly returns HTTP 503.

## Run tests

```bash
cd backend
python -m pip install -e ".[dev]"
pytest
```

## Architecture direction

The approved target architecture is FastAPI on Google Cloud Run, Supabase Auth, Supabase PostgreSQL with pgvector, private Supabase Storage, Google Cloud Tasks, and Google Secret Manager. This repository currently contains only the API process and configuration foundation; no paid cloud resources have been provisioned.
