from __future__ import annotations

import hmac
import json
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field, SecretStr, field_validator

from app.core.config import get_settings
from app.db.pool import user_connection, worker_connection
from app.platform.routes import _decode_json, _pool, _row

router = APIRouter(prefix="/internal/v1/jobs", tags=["worker-internal"])
bearer = HTTPBearer(auto_error=False)


class ClaimRequest(BaseModel):
    worker_id: str = Field(min_length=3, max_length=100, pattern="^[a-zA-Z0-9._-]+$")
    lease_seconds: int = Field(default=180, ge=30, le=900)
    job_types: list[str] | None = Field(default=None, max_length=10)


class CompleteRequest(BaseModel):
    worker_id: str = Field(min_length=3, max_length=100, pattern="^[a-zA-Z0-9._-]+$")
    result_payload: dict[str, Any] = Field(default_factory=dict)

    @field_validator("result_payload")
    @classmethod
    def bound_result_payload(cls, value: dict[str, Any]) -> dict[str, Any]:
        if len(json.dumps(value, ensure_ascii=False).encode("utf-8")) > 420_000:
            raise ValueError("result_payload exceeds the 420 KB worker result limit")
        return value


class FailRequest(BaseModel):
    worker_id: str = Field(min_length=3, max_length=100, pattern="^[a-zA-Z0-9._-]+$")
    error_code: str = Field(min_length=1, max_length=80, pattern="^[A-Z0-9_]+$")
    retry_after_seconds: int = Field(default=0, ge=0, le=86_400)


async def require_worker(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> str:
    settings = getattr(request.app.state, "settings", None) or get_settings()
    expected: SecretStr | None = settings.worker_token
    supplied = credentials.credentials if credentials and credentials.scheme.lower() == "bearer" else ""
    if expected is None or not expected.get_secret_value() or not hmac.compare_digest(
        supplied, expected.get_secret_value()
    ):
        raise HTTPException(401, detail={"code": "WORKER_AUTH_REQUIRED"},
                            headers={"WWW-Authenticate": "Bearer"})
    return "worker"


async def _append_event(connection, job_id: UUID, user_id: UUID,
                        event_type: str, detail: dict[str, Any] | None = None) -> None:
    await connection.execute(
        """INSERT INTO public.ai_job_events (id, job_id, user_id, event_type, detail)
           VALUES ($1, $2, $3, $4, $5::jsonb)""",
        uuid4(), job_id, user_id, event_type, json.dumps(detail or {})
    )


@router.post("/claim")
async def claim_job(body: ClaimRequest, request: Request,
                    _: str = Depends(require_worker)):
    async with worker_connection(_pool(request)) as connection:
        expired = await connection.fetch(
            """UPDATE public.ai_jobs SET status = 'failed',
                 error_code = 'WORKER_LEASE_EXHAUSTED', finished_at = now(),
                 lease_owner = NULL, lease_expires_at = NULL, locked_at = NULL,
                 updated_at = now()
               WHERE status = 'running' AND lease_expires_at <= now()
                 AND attempts >= max_attempts
               RETURNING id, user_id"""
        )
        for expired_job in expired:
            await _append_event(
                connection, expired_job["id"], expired_job["user_id"], "failed",
                {"error_code": "WORKER_LEASE_EXHAUSTED"}
            )
        row = await connection.fetchrow(
            """WITH candidate AS (
                 SELECT id FROM public.ai_jobs
                 WHERE (
                   (status = 'queued' AND available_at <= now())
                   OR (status = 'running' AND lease_expires_at <= now()
                       AND attempts < max_attempts)
                 )
                 AND ($3::text[] IS NULL OR job_type = ANY($3::text[]))
                 ORDER BY available_at, created_at
                 FOR UPDATE SKIP LOCKED LIMIT 1
               )
               UPDATE public.ai_jobs AS job SET
                 status = 'running', attempts = job.attempts + 1,
                 locked_at = now(),
                 lease_expires_at = now() + ($2 * interval '1 second'),
                 lease_owner = $1, cancel_requested = false, updated_at = now()
               FROM candidate WHERE job.id = candidate.id
               RETURNING job.id, job.user_id, job.job_type, job.input_payload,
                         job.attempts, job.max_attempts, job.lease_expires_at""",
            body.worker_id, body.lease_seconds, body.job_types
        )
        if row is None:
            return {"job": None}
        await _append_event(connection, row["id"], row["user_id"], "started",
                            {"worker_id": body.worker_id, "attempt": row["attempts"]})
    result = _row(row)
    result["id"] = str(result["id"])
    result["user_id"] = str(result["user_id"])
    return {"job": result}




@router.post("/{job_id}/attachment")
async def get_leased_document_attachment(
    job_id: UUID,
    body: ClaimRequest,
    request: Request,
    _: str = Depends(require_worker),
):
    """Return attachment metadata only after re-checking the source user message."""
    async with worker_connection(_pool(request)) as connection:
        job = await connection.fetchrow(
            """SELECT user_id, input_payload FROM public.ai_jobs
               WHERE id = $1 AND job_type = 'document_ingest'
                 AND status = 'running' AND lease_owner = $2
                 AND cancel_requested = false AND lease_expires_at > now()""",
            job_id, body.worker_id,
        )
    if job is None:
        raise HTTPException(409, detail={"code": "JOB_LEASE_LOST_OR_CANCELLED"})

    payload = _decode_json(job["input_payload"])
    if not isinstance(payload, dict):
        raise HTTPException(422, detail={"code": "DOCUMENT_JOB_PAYLOAD_INVALID"})
    try:
        owner_id = UUID(str(job["user_id"]))
        conversation_id = UUID(str(payload["conversation_id"]))
        message_id = UUID(str(payload["message_id"]))
        expected_attachment_id = UUID(str(payload["attachment"]["id"]))
    except (KeyError, TypeError, ValueError, AttributeError):
        raise HTTPException(422, detail={"code": "DOCUMENT_JOB_PAYLOAD_INVALID"}) from None

    async with user_connection(_pool(request), owner_id) as connection:
        message = await connection.fetchrow(
            """SELECT m.metadata, m.role
               FROM public.messages AS m
               JOIN public.conversations AS c
                 ON c.id = m.conversation_id AND c.user_id = m.user_id
               WHERE m.id = $1 AND m.conversation_id = $2
                 AND m.user_id = $3 AND c.user_id = $3""",
            message_id, conversation_id, owner_id,
        )
    if message is None or message["role"] != "user":
        raise HTTPException(404, detail={"code": "ATTACHMENT_NOT_FOUND"})

    from app.platform.document_job_routes import _find_attachment

    stored = _find_attachment(message["metadata"], expected_attachment_id)
    if stored is None:
        raise HTTPException(404, detail={"code": "ATTACHMENT_NOT_FOUND"})
    try:
        queued = payload["attachment"]
        if (
            UUID(str(queued["id"])) != stored.id
            or queued["storage_path"] != stored.storage_path
            or queued["mime_type"] != stored.mime_type
            or queued["size_bytes"] != stored.size_bytes
        ):
            raise ValueError("attachment metadata changed")
    except (KeyError, TypeError, ValueError):
        raise HTTPException(409, detail={"code": "ATTACHMENT_METADATA_MISMATCH"}) from None
    return {"attachment": stored.model_dump(mode="json")}


@router.post("/{job_id}/heartbeat")
async def heartbeat(job_id: UUID, body: ClaimRequest, request: Request,
                    _: str = Depends(require_worker)):
    async with worker_connection(_pool(request)) as connection:
        row = await connection.fetchrow(
            """UPDATE public.ai_jobs SET
                 lease_expires_at = now() + ($3 * interval '1 second'), updated_at = now()
               WHERE id = $1 AND status = 'running' AND lease_owner = $2
                 AND cancel_requested = false AND lease_expires_at > now()
               RETURNING id, lease_expires_at""",
            job_id, body.worker_id, body.lease_seconds
        )
    if row is None:
        raise HTTPException(409, detail={"code": "JOB_LEASE_LOST_OR_CANCELLED"})
    result = _row(row)
    result["id"] = str(result["id"])
    return result


@router.post("/{job_id}/complete")
async def complete_job(job_id: UUID, body: CompleteRequest, request: Request,
                       _: str = Depends(require_worker)):
    async with worker_connection(_pool(request)) as connection:
        row = await connection.fetchrow(
            """UPDATE public.ai_jobs SET status = 'succeeded',
                 result_payload = $3::jsonb, finished_at = now(), updated_at = now(),
                 lease_owner = NULL, lease_expires_at = NULL, locked_at = NULL
               WHERE id = $1 AND status = 'running' AND lease_owner = $2
                 AND cancel_requested = false AND lease_expires_at > now()
               RETURNING id, user_id, status, finished_at""",
            job_id, body.worker_id, json.dumps(body.result_payload)
        )
        if row is None:
            raise HTTPException(409, detail={"code": "JOB_LEASE_LOST_OR_CANCELLED"})
        await _append_event(connection, row["id"], row["user_id"], "succeeded")
    result = _row(row)
    result["id"] = str(result["id"])
    result["user_id"] = str(result["user_id"])
    return result


@router.post("/{job_id}/fail")
async def fail_job(job_id: UUID, body: FailRequest, request: Request,
                   _: str = Depends(require_worker)):
    async with worker_connection(_pool(request)) as connection:
        current = await connection.fetchrow(
            """SELECT id, user_id, attempts, max_attempts FROM public.ai_jobs
               WHERE id = $1 AND status = 'running' AND lease_owner = $2
                 AND lease_expires_at > now() AND cancel_requested = false
               FOR UPDATE""", job_id, body.worker_id
        )
        if current is None:
            raise HTTPException(409, detail={"code": "JOB_LEASE_LOST_OR_CANCELLED"})
        retry = body.retry_after_seconds > 0 and current["attempts"] < current["max_attempts"]
        status = "queued" if retry else "failed"
        await connection.execute(
            """UPDATE public.ai_jobs SET status = $2, error_code = $3,
                 available_at = CASE WHEN $2 = 'queued'
                   THEN now() + ($4 * interval '1 second') ELSE available_at END,
                 finished_at = CASE WHEN $2 = 'failed' THEN now() ELSE NULL END,
                 lease_owner = NULL, lease_expires_at = NULL, locked_at = NULL,
                 updated_at = now()
               WHERE id = $1""",
            job_id, status, body.error_code, body.retry_after_seconds
        )
        await _append_event(
            connection, current["id"], current["user_id"],
            "retry_scheduled" if retry else "failed",
            {"error_code": body.error_code, "retry_after_seconds": body.retry_after_seconds if retry else 0}
        )
    return {"id": str(job_id), "status": status, "error_code": body.error_code}
