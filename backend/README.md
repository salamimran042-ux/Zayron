# Zayron AI backend

This folder contains the independent Python backend foundation for the existing Zayron AI Android project.

## Current status

Implemented:
- FastAPI application package
- Process-level `GET /health` endpoint
- Automated health endpoint test
- Python package metadata and development dependencies
- Container definition for a future Cloud Run deployment
- Separate backend CI workflow

Not implemented or connected yet:
- Supabase authentication or database
- Private file storage
- AI provider integrations
- Cloud Tasks or background workers
- Credits, billing, or entitlement enforcement
- Production deployment

The health endpoint only confirms that the API process responds. It does not imply that external services are configured.

## Run locally

Requires Python 3.12 or newer.

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
python -m pip install -e ".[dev]"
uvicorn app.main:app --reload
```

Open `http://127.0.0.1:8000/health` to inspect the process health response.

## Run tests

```bash
cd backend
python -m pip install -e ".[dev]"
pytest
```

## Configuration and secrets

No credentials or provider keys are required for this foundation. Do not commit real secrets, service-account files, database passwords, or provider API keys. Future integrations must read secrets from the deployment environment or a managed secret store.

## Architecture direction

The approved target architecture is FastAPI on Google Cloud Run, Supabase Auth, Supabase PostgreSQL with pgvector, private Supabase Storage, Google Cloud Tasks, and Google Secret Manager. This repository currently contains only the API process foundation; no paid cloud resources have been provisioned.
