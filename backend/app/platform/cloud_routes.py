from __future__ import annotations

from typing import Any

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import Response
from pydantic import BaseModel, Field

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser

router = APIRouter(prefix="/api/v1/cloud", tags=["cloud data"])
UPSTREAM_TIMEOUT = httpx.Timeout(15.0, connect=5.0)


class ConversationCreate(BaseModel):
    title: str = Field(default="New conversation", max_length=160)


class MessageCreate(BaseModel):
    conversation_id: str = Field(min_length=36, max_length=36)
    content: str = Field(min_length=1, max_length=100_000)


def _upstream_config(request: Request) -> tuple[str, str, str]:
    settings = getattr(request.app.state, "settings", None)
    supabase_url = getattr(settings, "supabase_url", None)
    key = getattr(settings, "effective_supabase_publishable_key", None)
    authorization = request.headers.get("authorization", "")
    if not isinstance(supabase_url, str) or not supabase_url.startswith("https://"):
        raise HTTPException(503, detail={"code": "CLOUD_DATA_UNAVAILABLE"})
    if key is None:
        raise HTTPException(503, detail={"code": "CLOUD_DATA_UNAVAILABLE"})
    publishable_key = key.get_secret_value()
    if not publishable_key:
        raise HTTPException(503, detail={"code": "CLOUD_DATA_UNAVAILABLE"})
    if not authorization.lower().startswith("bearer "):
        raise HTTPException(401, detail={"code": "AUTH_REQUIRED"})
    return supabase_url.rstrip("/"), publishable_key, authorization


async def _forward(
    request: Request,
    user: AuthenticatedUser,
    function_name: str,
    *,
    method: str,
    params: dict[str, str] | None = None,
    body: dict[str, Any] | None = None,
) -> Response:
    del user  # Authentication dependency validates the JWT; forward it for Supabase RLS.
    base_url, publishable_key, authorization = _upstream_config(request)
    url = f"{base_url}/functions/v1/{function_name}"
    headers = {
        "apikey": publishable_key,
        "Authorization": authorization,
        "Accept": "application/json",
    }
    try:
        async with httpx.AsyncClient(timeout=UPSTREAM_TIMEOUT) as client:
            upstream = await client.request(
                method,
                url,
                params=params,
                json=body,
                headers=headers,
            )
    except httpx.HTTPError as exc:
        raise HTTPException(503, detail={"code": "CLOUD_DATA_UNAVAILABLE"}) from exc

    # Supabase functions return deliberately small JSON payloads. Do not expose
    # upstream response headers, cookies, or credential-bearing diagnostics.
    media_type = upstream.headers.get("content-type", "application/json").split(";")[0]
    if media_type != "application/json":
        raise HTTPException(502, detail={"code": "CLOUD_DATA_BAD_RESPONSE"})
    return Response(
        content=upstream.content,
        status_code=upstream.status_code,
        media_type="application/json",
        headers={"Cache-Control": "no-store"},
    )


@router.get("/conversations")
async def list_cloud_conversations(
    request: Request,
    include_archived: bool = Query(default=False),
    user: AuthenticatedUser = Depends(get_current_user),
) -> Response:
    return await _forward(
        request,
        user,
        "conversations",
        method="GET",
        params={"include_archived": str(include_archived).lower()},
    )


@router.post("/conversations", status_code=201)
async def create_cloud_conversation(
    body: ConversationCreate,
    request: Request,
    user: AuthenticatedUser = Depends(get_current_user),
) -> Response:
    return await _forward(
        request,
        user,
        "conversations",
        method="POST",
        body={"title": body.title.strip() or "New conversation"},
    )


@router.get("/messages")
async def list_cloud_messages(
    request: Request,
    conversation_id: str = Query(min_length=36, max_length=36),
    user: AuthenticatedUser = Depends(get_current_user),
) -> Response:
    return await _forward(
        request,
        user,
        "messages",
        method="GET",
        params={"conversation_id": conversation_id},
    )


@router.post("/messages", status_code=201)
async def create_cloud_message(
    body: MessageCreate,
    request: Request,
    user: AuthenticatedUser = Depends(get_current_user),
) -> Response:
    return await _forward(
        request,
        user,
        "messages",
        method="POST",
        body={
            "conversation_id": body.conversation_id,
            "content": body.content,
        },
    )
