from __future__ import annotations

import json
from datetime import datetime
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, Response
from pydantic import BaseModel, Field

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser
from app.db.pool import user_connection

router = APIRouter(prefix="/api/v1", tags=["platform"])


class ProfileUpdate(BaseModel):
    display_name: str | None = Field(default=None, max_length=80)
    locale: str | None = Field(default=None, min_length=2, max_length=20)
    timezone: str | None = Field(default=None, min_length=1, max_length=64)


class ConversationCreate(BaseModel):
    title: str = Field(default="", max_length=200)


class ConversationUpdate(BaseModel):
    title: str | None = Field(default=None, max_length=200)
    archived: bool | None = None


class MessageCreate(BaseModel):
    content: str = Field(min_length=1, max_length=100_000)
    metadata: dict[str, Any] = Field(default_factory=dict)


class JobCreate(BaseModel):
    job_type: str = Field(pattern="^(image_to_video|image_inpaint|image_erase|document_ingest|app_build|voice_transcribe)$")
    input_payload: dict[str, Any] = Field(default_factory=dict)


def _identity(user: AuthenticatedUser) -> UUID:
    try:
        return UUID(user.subject)
    except (ValueError, TypeError, AttributeError) as exc:
        raise HTTPException(status_code=401, detail="Authenticated subject is not a valid account ID") from exc


def _pool(request: Request):
    database = getattr(request.app.state, "database", None)
    pool = getattr(database, "pool", None)
    if pool is None:
        raise HTTPException(status_code=503, detail={"code": "DATABASE_UNAVAILABLE"})
    return pool


def _decode_json(value: Any) -> Any:
    if isinstance(value, str):
        try:
            return json.loads(value)
        except json.JSONDecodeError:
            return {}
    return value


def _row(row: Any) -> dict[str, Any]:
    result = dict(row)
    for key in ("metadata", "limits", "input_payload", "result_payload"):
        if key in result and result[key] is not None:
            result[key] = _decode_json(result[key])
    return result


@router.get("/profile")
async def get_profile(request: Request, user: AuthenticatedUser = Depends(get_current_user)):
    user_id = _identity(user)
    async with user_connection(_pool(request), user_id) as connection:
        row = await connection.fetchrow(
            """SELECT user_id, display_name, locale, timezone, created_at, updated_at
               FROM public.user_profiles WHERE user_id = $1""", user_id
        )
    if row is None:
        return {"user_id": str(user_id), "display_name": None, "locale": "en", "timezone": "UTC"}
    result = _row(row)
    result["user_id"] = str(result["user_id"])
    return result


@router.patch("/profile")
async def update_profile(body: ProfileUpdate, request: Request,
                         user: AuthenticatedUser = Depends(get_current_user)):
    user_id = _identity(user)
    changes = body.model_dump(exclude_unset=True)
    if not changes:
        raise HTTPException(400, detail={"code": "NO_PROFILE_CHANGES"})
    allowed = {"display_name", "locale", "timezone"}
    if set(changes) - allowed:
        raise HTTPException(400, detail={"code": "INVALID_PROFILE_FIELDS"})
    async with user_connection(_pool(request), user_id) as connection:
        row = await connection.fetchrow(
            """INSERT INTO public.user_profiles (user_id, display_name, locale, timezone)
               VALUES ($1, $2, $3, $4)
               ON CONFLICT (user_id) DO UPDATE SET
                 display_name = COALESCE(EXCLUDED.display_name, user_profiles.display_name),
                 locale = COALESCE(EXCLUDED.locale, user_profiles.locale),
                 timezone = COALESCE(EXCLUDED.timezone, user_profiles.timezone),
                 updated_at = now()
               RETURNING user_id, display_name, locale, timezone, created_at, updated_at""",
            user_id, changes.get("display_name"), changes.get("locale"), changes.get("timezone")
        )
    result = _row(row)
    result["user_id"] = str(result["user_id"])
    return result


@router.post("/conversations", status_code=201)
async def create_conversation(body: ConversationCreate, request: Request,
                              user: AuthenticatedUser = Depends(get_current_user)):
    user_id, conversation_id = _identity(user), uuid4()
    async with user_connection(_pool(request), user_id) as connection:
        row = await connection.fetchrow(
            """INSERT INTO public.conversations (id, user_id, title)
               VALUES ($1, $2, $3)
               RETURNING id, title, metadata, created_at, updated_at, archived_at""",
            conversation_id, user_id, body.title.strip()
        )
    result = _row(row)
    result["id"] = str(result["id"])
    return result


@router.get("/conversations")
async def list_conversations(request: Request, limit: int = Query(30, ge=1, le=100),
                             offset: int = Query(0, ge=0),
                             user: AuthenticatedUser = Depends(get_current_user)):
    user_id = _identity(user)
    async with user_connection(_pool(request), user_id) as connection:
        rows = await connection.fetch(
            """SELECT id, title, metadata, created_at, updated_at, archived_at
               FROM public.conversations WHERE user_id = $1
               ORDER BY updated_at DESC, id DESC LIMIT $2 OFFSET $3""",
            user_id, limit, offset
        )
    result = []
    for row in rows:
        item = _row(row)
        item["id"] = str(item["id"])
        result.append(item)
    return {"items": result, "limit": limit, "offset": offset}


@router.get("/conversations/{conversation_id}")
async def get_conversation(conversation_id: UUID, request: Request,
                           user: AuthenticatedUser = Depends(get_current_user)):
    async with user_connection(_pool(request), _identity(user)) as connection:
        row = await connection.fetchrow(
            """SELECT id, title, metadata, created_at, updated_at, archived_at
               FROM public.conversations WHERE id = $1 AND user_id = $2""",
            conversation_id, _identity(user)
        )
    if row is None:
        raise HTTPException(404, detail={"code": "CONVERSATION_NOT_FOUND"})
    result = _row(row)
    result["id"] = str(result["id"])
    return result


@router.patch("/conversations/{conversation_id}")
async def update_conversation(conversation_id: UUID, body: ConversationUpdate,
                              request: Request,
                              user: AuthenticatedUser = Depends(get_current_user)):
    user_id = _identity(user)
    changes = body.model_dump(exclude_unset=True)
    if not changes:
        raise HTTPException(400, detail={"code": "NO_CONVERSATION_CHANGES"})
    title = changes.get("title")
    archived = changes.get("archived")
    async with user_connection(_pool(request), user_id) as connection:
        row = await connection.fetchrow(
            """UPDATE public.conversations SET
                 title = COALESCE($3, title),
                 archived_at = CASE WHEN $4::boolean IS NULL THEN archived_at
                                    WHEN $4 THEN COALESCE(archived_at, now())
                                    ELSE NULL END,
                 updated_at = now()
               WHERE id = $1 AND user_id = $2
               RETURNING id, title, metadata, created_at, updated_at, archived_at""",
            conversation_id, user_id, title.strip() if title is not None else None, archived
        )
    if row is None:
        raise HTTPException(404, detail={"code": "CONVERSATION_NOT_FOUND"})
    result = _row(row)
    result["id"] = str(result["id"])
    return result


@router.delete("/conversations/{conversation_id}", status_code=204)
async def delete_conversation(conversation_id: UUID, request: Request,
                              user: AuthenticatedUser = Depends(get_current_user)):
    async with user_connection(_pool(request), _identity(user)) as connection:
        result = await connection.execute(
            "DELETE FROM public.conversations WHERE id = $1 AND user_id = $2",
            conversation_id, _identity(user)
        )
    if result == "DELETE 0":
        raise HTTPException(404, detail={"code": "CONVERSATION_NOT_FOUND"})
    return Response(status_code=204)


@router.get("/conversations/{conversation_id}/messages")
async def list_messages(conversation_id: UUID, request: Request,
                        limit: int = Query(50, ge=1, le=100),
                        before: datetime | None = None,
                        user: AuthenticatedUser = Depends(get_current_user)):
    user_id = _identity(user)
    async with user_connection(_pool(request), user_id) as connection:
        owned = await connection.fetchval(
            "SELECT 1 FROM public.conversations WHERE id = $1 AND user_id = $2",
            conversation_id, user_id
        )
        if not owned:
            raise HTTPException(404, detail={"code": "CONVERSATION_NOT_FOUND"})
        rows = await connection.fetch(
            """SELECT id, role, content, status, metadata, created_at
               FROM public.messages WHERE conversation_id = $1 AND user_id = $2
                 AND ($3::timestamptz IS NULL OR created_at < $3)
               ORDER BY created_at DESC, id DESC LIMIT $4""",
            conversation_id, user_id, before, limit
        )
    items = []
    for row in rows:
        item = _row(row)
        item["id"] = str(item["id"])
        items.append(item)
    return {"items": list(reversed(items)), "limit": limit}


@router.post("/conversations/{conversation_id}/messages", status_code=201)
async def add_user_message(conversation_id: UUID, body: MessageCreate, request: Request,
                           user: AuthenticatedUser = Depends(get_current_user)):
    user_id, message_id = _identity(user), uuid4()
    async with user_connection(_pool(request), user_id) as connection:
        owned = await connection.fetchval(
            "SELECT 1 FROM public.conversations WHERE id = $1 AND user_id = $2 FOR UPDATE",
            conversation_id, user_id
        )
        if not owned:
            raise HTTPException(404, detail={"code": "CONVERSATION_NOT_FOUND"})
        row = await connection.fetchrow(
            """INSERT INTO public.messages
               (id, conversation_id, user_id, role, content, status, metadata)
               VALUES ($1, $2, $3, 'user', $4, 'complete', $5::jsonb)
               RETURNING id, role, content, status, metadata, created_at""",
            message_id, conversation_id, user_id, body.content, json.dumps(body.metadata)
        )
        await connection.execute(
            "UPDATE public.conversations SET updated_at = now() WHERE id = $1 AND user_id = $2",
            conversation_id, user_id
        )
    result = _row(row)
    result["id"] = str(result["id"])
    return result


@router.post("/jobs", status_code=202)
async def create_job(body: JobCreate, request: Request,
                     idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
                     user: AuthenticatedUser = Depends(get_current_user)):
    if idempotency_key is not None and (not idempotency_key.strip() or len(idempotency_key) > 128):
        raise HTTPException(400, detail={"code": "INVALID_IDEMPOTENCY_KEY"})
    user_id, job_id = _identity(user), uuid4()
    async with user_connection(_pool(request), user_id) as connection:
        row = await connection.fetchrow(
            """INSERT INTO public.ai_jobs (id, user_id, job_type, input_payload, idempotency_key)
               VALUES ($1, $2, $3, $4::jsonb, $5)
               ON CONFLICT (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL
               DO NOTHING
               RETURNING id, job_type, status, input_payload, result_payload, attempts,
                         max_attempts, available_at, created_at, updated_at""",
            job_id, user_id, body.job_type, json.dumps(body.input_payload), idempotency_key
        )
        if row is None and idempotency_key is not None:
            row = await connection.fetchrow(
                """SELECT id, job_type, status, input_payload, result_payload, attempts,
                          max_attempts, available_at, created_at, updated_at
                   FROM public.ai_jobs WHERE user_id = $1 AND idempotency_key = $2""",
                user_id, idempotency_key
            )
    if row is None:
        raise HTTPException(503, detail={"code": "JOB_CREATE_FAILED"})
    result = _row(row)
    result["id"] = str(result["id"])
    return result


@router.get("/jobs")
async def list_jobs(request: Request, limit: int = Query(30, ge=1, le=100),
                    offset: int = Query(0, ge=0),
                    user: AuthenticatedUser = Depends(get_current_user)):
    user_id = _identity(user)
    async with user_connection(_pool(request), user_id) as connection:
        rows = await connection.fetch(
            """SELECT id, job_type, status, result_payload, attempts, max_attempts,
                      available_at, finished_at, error_code, created_at, updated_at
               FROM public.ai_jobs WHERE user_id = $1
               ORDER BY created_at DESC, id DESC LIMIT $2 OFFSET $3""",
            user_id, limit, offset
        )
    items = []
    for row in rows:
        item = _row(row)
        item["id"] = str(item["id"])
        items.append(item)
    return {"items": items, "limit": limit, "offset": offset}


@router.get("/jobs/{job_id}")
async def get_job(job_id: UUID, request: Request,
                  user: AuthenticatedUser = Depends(get_current_user)):
    async with user_connection(_pool(request), _identity(user)) as connection:
        row = await connection.fetchrow(
            """SELECT id, job_type, status, input_payload, result_payload, attempts,
                      max_attempts, available_at, locked_at, finished_at, error_code,
                      created_at, updated_at
               FROM public.ai_jobs WHERE id = $1 AND user_id = $2""",
            job_id, _identity(user)
        )
    if row is None:
        raise HTTPException(404, detail={"code": "JOB_NOT_FOUND"})
    result = _row(row)
    result["id"] = str(result["id"])
    return result


@router.post("/jobs/{job_id}/cancel")
async def cancel_job(job_id: UUID, request: Request,
                     user: AuthenticatedUser = Depends(get_current_user)):
    user_id = _identity(user)
    async with user_connection(_pool(request), user_id) as connection:
        row = await connection.fetchrow(
            """UPDATE public.ai_jobs SET status = 'cancelled', finished_at = now(),
                      updated_at = now()
               WHERE id = $1 AND user_id = $2 AND status = 'queued'
               RETURNING id, job_type, status, updated_at""",
            job_id, user_id
        )
        if row is None:
            existing = await connection.fetchrow(
                "SELECT status FROM public.ai_jobs WHERE id = $1 AND user_id = $2",
                job_id, user_id
            )
    if row is None:
        if existing is None:
            raise HTTPException(404, detail={"code": "JOB_NOT_FOUND"})
        raise HTTPException(409, detail={"code": "JOB_NOT_CANCELLABLE", "status": existing["status"]})
    result = _row(row)
    result["id"] = str(result["id"])
    return result


@router.get("/entitlements")
async def get_entitlements(request: Request,
                           user: AuthenticatedUser = Depends(get_current_user)):
    user_id = _identity(user)
    async with user_connection(_pool(request), user_id) as connection:
        row = await connection.fetchrow(
            """SELECT plan_code, status, source, limits, period_started_at,
                      period_ends_at, updated_at
               FROM public.user_entitlements WHERE user_id = $1""", user_id
        )
    if row is None:
        return {"plan_code": "free", "status": "active", "source": "default",
                "limits": {}, "period_started_at": None, "period_ends_at": None}
    return _row(row)


@router.get("/usage")
async def get_usage(request: Request, user: AuthenticatedUser = Depends(get_current_user)):
    user_id = _identity(user)
    async with user_connection(_pool(request), user_id) as connection:
        summary = await connection.fetchrow(
            """SELECT count(*) AS event_count, COALESCE(sum(input_tokens), 0) AS input_tokens,
                      COALESCE(sum(output_tokens), 0) AS output_tokens,
                      COALESCE(sum(units), 0) AS units,
                      COALESCE(sum(estimated_cost_usd), 0) AS estimated_cost_usd
               FROM public.usage_events
               WHERE user_id = $1 AND created_at >= now() - interval '30 days'""", user_id
        )
        recent = await connection.fetch(
            """SELECT id, feature_code, provider_code, model_code, input_tokens,
                      output_tokens, units, estimated_cost_usd, created_at
               FROM public.usage_events WHERE user_id = $1
               ORDER BY created_at DESC LIMIT 50""", user_id
        )
    return {"period_days": 30, "summary": _row(summary),
            "recent_events": [_row(item) for item in recent]}
