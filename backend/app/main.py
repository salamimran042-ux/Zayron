from fastapi import FastAPI

app = FastAPI(
    title="Zayron AI API",
    description="The server-side foundation for Zayron AI.",
    version="0.1.0",
)


@app.get("/health", tags=["operations"])
async def health() -> dict[str, str]:
    """Report that the API process is running.

    This is a process-level health check only. It does not claim that
    databases, storage, AI providers, or background workers are connected.
    """
    return {
        "status": "ok",
        "service": "zayron-api",
        "version": app.version,
    }
