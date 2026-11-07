from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.auth.routes import router as auth_router
from app.auth.token_verifier import SupabaseTokenVerifier
from app.core.config import get_settings
from app.db.pool import DatabaseRuntime

settings = get_settings()


@asynccontextmanager
async def lifespan(application: FastAPI) -> AsyncIterator[None]:
    database = DatabaseRuntime()
    await database.open(settings)
    application.state.database = database
    application.state.token_verifier = SupabaseTokenVerifier(settings)
    try:
        yield
    finally:
        await database.close()


app = FastAPI(
    title="Zayron AI API",
    description="The server-side foundation for Zayron AI.",
    version="0.1.0",
    lifespan=lifespan,
)
app.include_router(auth_router)


@app.get("/health", tags=["operations"])
async def health() -> dict[str, str]:
    """Report that the API process is running, independent of integrations."""
    return {
        "status": "ok",
        "service": "zayron-api",
        "version": app.version,
    }


@app.get("/ready", tags=["operations"])
async def readiness(request: Request) -> JSONResponse:
    """Report readiness only after a real database ping succeeds."""
    database: DatabaseRuntime = request.app.state.database
    try:
        ready = await database.ping()
    except Exception:
        ready = False
    if not ready:
        return JSONResponse(
            status_code=503,
            content={"status": "not_ready", "database": "unavailable"},
        )
    return JSONResponse(
        status_code=200,
        content={"status": "ready", "database": "reachable"},
    )


@app.get("/config/status", tags=["operations"])
async def configuration_status() -> dict[str, object]:
    """Report which integrations have configuration, never their secret values."""
    return {
        "environment": settings.environment,
        "integrations": settings.integration_status(),
    }
