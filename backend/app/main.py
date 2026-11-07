from fastapi import FastAPI

from app.core.config import get_settings

settings = get_settings()

app = FastAPI(
    title="Zayron AI API",
    description="The server-side foundation for Zayron AI.",
    version="0.1.0",
)


@app.get("/health", tags=["operations"])
async def health() -> dict[str, str]:
    """Report that the API process is running, independent of integrations."""
    return {
        "status": "ok",
        "service": "zayron-api",
        "version": app.version,
    }


@app.get("/config/status", tags=["operations"])
async def configuration_status() -> dict[str, object]:
    """Report which integrations have configuration, never their secret values."""
    return {
        "environment": settings.environment,
        "integrations": settings.integration_status(),
    }
