from __future__ import annotations

import json
from collections.abc import AsyncIterator
from typing import Any

import httpx
from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser
from app.core.config import get_settings

router = APIRouter(prefix="/api/v1", tags=["chat"])


class ChatMessageInput(BaseModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(min_length=1, max_length=100_000)


class ChatStreamInput(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(alias="requestId", min_length=1, max_length=128)
    conversation_id: str | None = Field(default=None, alias="conversationId")
    messages: list[ChatMessageInput] = Field(min_length=1, max_length=100)

    @model_validator(mode="after")
    def require_user_turn_last(self) -> "ChatStreamInput":
        if self.messages[-1].role != "user":
            raise ValueError("The final chat message must be from the user.")
        return self


def _provider_configuration():
    settings = get_settings()
    key = settings.ai_provider_api_key
    base_url = (settings.ai_provider_base_url or "").rstrip("/")
    model = settings.ai_provider_model
    if not key or not base_url or not model:
        return None
    # Do not permit credentials to be sent over plaintext HTTP in production.
    if settings.environment.lower() in {"production", "prod"} and not base_url.startswith("https://"):
        return None
    if not base_url.startswith(("https://", "http://")):
        return None
    return settings, key.get_secret_value(), base_url, model


def _sse(event: str, payload: dict[str, Any]) -> str:
    return f"event: {event}\ndata: {json.dumps(payload, separators=(',', ':'))}\n\n"


async def _provider_events(
    messages: list[ChatMessageInput],
    configuration,
) -> AsyncIterator[str]:
    settings, api_key, base_url, model = configuration
    timeout = httpx.Timeout(settings.ai_provider_timeout_seconds)
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    body = {
        "model": model,
        "messages": [message.model_dump() for message in messages],
        "stream": True,
    }
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            async with client.stream(
                "POST", f"{base_url}/chat/completions", headers=headers, json=body
            ) as response:
                if response.status_code < 200 or response.status_code >= 300:
                    yield _sse("error", {"code": "AI_PROVIDER_UNAVAILABLE"})
                    return
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    raw = line[5:].strip()
                    if not raw:
                        continue
                    if raw == "[DONE]":
                        yield _sse("message.completed", {"messageId": None})
                        return
                    try:
                        chunk = json.loads(raw)
                        delta = chunk["choices"][0].get("delta", {}).get("content")
                    except (ValueError, KeyError, IndexError, TypeError):
                        continue
                    if isinstance(delta, str) and delta:
                        yield _sse("message.delta", {"delta": delta})
        yield _sse("error", {"code": "AI_PROVIDER_STREAM_INCOMPLETE"})
    except (httpx.TimeoutException, httpx.TransportError):
        yield _sse("error", {"code": "AI_PROVIDER_UNAVAILABLE"})
    except Exception:
        # Provider errors are deliberately generic; never leak upstream details or secrets.
        yield _sse("error", {"code": "AI_PROVIDER_ERROR"})


@router.post("/chat/stream")
async def stream_chat(
    body: ChatStreamInput,
    user: AuthenticatedUser = Depends(get_current_user),
):
    """Authenticated OpenAI-compatible streaming gateway; credentials stay server-side."""
    del user
    configuration = _provider_configuration()
    if configuration is None:
        return JSONResponse(
            status_code=503,
            content={
                "code": "SERVICE_UNAVAILABLE",
                "message": "The Zayron chat model provider is not configured.",
            },
        )
    return StreamingResponse(
        _provider_events(body.messages, configuration),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"},
    )
