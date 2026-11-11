from __future__ import annotations

import json
from typing import Any, Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser
from app.db.pool import user_connection
from app.platform.routes import _identity, _pool

router = APIRouter(prefix="/api/v1/documents", tags=["document jobs"])

TEXT_MIME_TYPES = {
    "application/pdf", "text/plain", "text/markdown", "text/csv", "application/json",
}


class DocumentJobRequest(BaseModel):
    conversation_id: UUID
    message_id: UUID
    attachment_id: UUID


class StoredAttachment(BaseModel):
    id: UUID
    display_name: str = Field(min_length=1, max_length=160)
    mime_type: Literal[
        "application/pdf", "text/plain", "text/markdown", "text/csv",
        "application/json", "image/png", "image/jpeg", "image/webp",
    ]
    size_bytes: int = Field(ge=1, le=10 * 1024 * 1024)
    storage_path: str = Field(min_length=1, max_length=160)


def _decode_metadata(value: Any) -> dict[str, Any]:
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except json.JSONDecodeError:
            return {}
    return value if isinstance(value, dict) else {}


def _find_attachment(metadata: Any, attachment_id: UUID) -> StoredAttachment | None:
    attachments = _decode_metadata(metadata).get("attachments", [])
    if not isinstance(attachments, list) or len(attachments) > 5:
        return None
    for item in attachments:
        try:
            attachment = StoredAttachment.model_validate(item)
        except Exception:
            continue
        if attachment.id == attachment_id:
            return attachment
    return None


@router.post("/jobs", status_code=202)
async def create_document_job(
    body: DocumentJobRequest,
    request: Request,
    user: AuthenticatedUser = Depends(get_current_user),
):
    """Queue bounded document preparation for an attachment on the user's own message.

    This endpoint does not run OCR or an AI model. A worker handler/provider adapter
    must be configured before a queued job can produce an answer.
    """
    user_id = _identity(user)
    async with user_connection(_pool(request), user_id) as connection:
        message = await connection.fetchrow(
            """SELECT m.id, m.conversation_id, m.role, m.metadata
               FROM public.messages AS m
               JOIN public.conversations AS c
                 ON c.id = m.conversation_id AND c.user_id = m.user_id
               WHERE m.id = $1 AND m.conversation_id = $2
                 AND m.user_id = $3 AND c.user_id = $3""",
            body.message_id, body.conversation_id, user_id,
        )
        if message is None or message["role"] != "user":
            raise HTTPException(404, detail={"code": "MESSAGE_NOT_FOUND"})

        attachment = _find_attachment(message["metadata"], body.attachment_id)
        if attachment is None:
            raise HTTPException(404, detail={"code": "ATTACHMENT_NOT_FOUND"})
        if attachment.mime_type not in TEXT_MIME_TYPES:
            raise HTTPException(409, detail={"code": "DOCUMENT_TYPE_NOT_SUPPORTED"})

        # The message record is the source of truth: clients cannot submit an
        # arbitrary storage path or substitute another user's attachment.
        payload = {
            "conversation_id": str(body.conversation_id),
            "message_id": str(body.message_id),
            "attachment": attachment.model_dump(mode="json"),
            "operation": "extract_text",
            "limits": {"max_bytes": 10 * 1024 * 1024, "max_pdf_pages": 100,
                       "max_extracted_chars": 100_000},
        }
        idempotency_key = f"document:{body.message_id}:{body.attachment_id}"
        row = await connection.fetchrow(
            """INSERT INTO public.ai_jobs
                 (id, user_id, job_type, input_payload, idempotency_key)
               VALUES (gen_random_uuid(), $1, 'document_ingest', $2::jsonb, $3)
               ON CONFLICT (user_id, idempotency_key)
                 WHERE idempotency_key IS NOT NULL DO NOTHING
               RETURNING id, job_type, status, attempts, max_attempts,
                         available_at, created_at""",
            user_id, json.dumps(payload), idempotency_key,
        )
        if row is None:
            row = await connection.fetchrow(
                """SELECT id, job_type, status, attempts, max_attempts,
                          available_at, created_at
                   FROM public.ai_jobs WHERE user_id = $1 AND idempotency_key = $2""",
                user_id, idempotency_key,
            )
    if row is None:
        raise HTTPException(503, detail={"code": "DOCUMENT_JOB_CREATE_FAILED"})
    return {
        "id": str(row["id"]),
        "job_type": row["job_type"],
        "status": row["status"],
        "attempts": row["attempts"],
        "max_attempts": row["max_attempts"],
        "available_at": row["available_at"],
        "created_at": row["created_at"],
        "processing": "queued_for_worker",
    }
