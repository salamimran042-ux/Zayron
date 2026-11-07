from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.auth.routes import router as auth_router
from app.auth.supabase_auth import SupabaseAuthClient
from app.auth.token_verifier import SupabaseTokenVerifier
from app.core.config import get_settings
from app.db.pool import DatabaseRuntime
from app.platform.routes import router as platform_router
from app.platform.worker_routes import router as worker_router

settings = get_settings()


@asynccontextmanager
async def lifespan(application: FastAPI) -> AsyncIterator[None]:
    database = DatabaseRuntime()
    await database.open(settings)
    application.state.database = database
    application.state.settings = settings
    application.state.token_verifier = SupabaseTokenVerifier(settings)
    auth_client = SupabaseAuthClient.from_settings(settings)
    application.state.supabase_auth = auth_client
    try:
        yield
    finally:
        if auth_client is not None:
            await auth_client.close()
        await database.close()


app = FastAPI(
    title="Zayron AI API",
    description="The server-side foundation for Zayron AI.",
    version="0.2.0",
    lifespan=lifespan,
)
app.include_router(auth_router)
app.include_router(platform_router)
app.include_router(worker_router)


@app.get("/health", tags=["operations"])
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "zayron-api", "version": app.version}


@app.get("/ready", tags=["operations"])
async def readiness(request: Request) -> JSONResponse:
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
    return {
        "environment": settings.environment,
        "integrations": settings.integration_status(),
    }
