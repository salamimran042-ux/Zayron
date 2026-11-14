from __future__ import annotations

from io import BytesIO
import re
from typing import Literal
from uuid import uuid4, UUID
from urllib.parse import quote

import httpx
from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from pypdf import PdfReader

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser

router = APIRouter(prefix="/api/v1/cloud", tags=["cloud attachments"])

BUCKET = "zayron-attachments"
MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
ALLOWED_TYPES = {
    "application/pdf": "pdf",
    "text/plain": "txt",
    "text/markdown": "md",
    "text/csv": "csv",
    "application/json": "json",
    "image/png": "png",
    "image/jpeg": "jpg",
    "image/webp": "webp",
}
UPLOAD_TIMEOUT = httpx.Timeout(30.0, connect=5.0)


@router.post("/attachments", status_code=201)
async def upload_attachment(
    request: Request,
    file: UploadFile = File(...),
    user: AuthenticatedUser = Depends(get_current_user),
) -> JSONResponse:
    try:
        owner_id = str(UUID(user.subject))
    except (ValueError, TypeError, AttributeError) as exc:
        raise HTTPException(401, detail={"code": "AUTH_REQUIRED"}) from exc

    media_type = (file.content_type or "").split(";", 1)[0].strip().lower()
    extension = ALLOWED_TYPES.get(media_type)
    if extension is None:
        raise HTTPException(415, detail={"code": "ATTACHMENT_TYPE_NOT_ALLOWED"})

    content = await file.read(MAX_ATTACHMENT_BYTES + 1)
    if not content:
        raise HTTPException(400, detail={"code": "ATTACHMENT_EMPTY"})
    if len(content) > MAX_ATTACHMENT_BYTES:
        raise HTTPException(413, detail={"code": "ATTACHMENT_TOO_LARGE"})

    settings = getattr(request.app.state, "settings", None)
    supabase_url = getattr(settings, "supabase_url", None)
    key = getattr(settings, "effective_supabase_publishable_key", None)
    authorization = request.headers.get("authorization", "")
    if not isinstance(supabase_url, str) or not supabase_url.startswith("https://") or key is None:
        raise HTTPException(503, detail={"code": "CLOUD_DATA_UNAVAILABLE"})
    publishable_key = key.get_secret_value()
    if not publishable_key or not authorization.lower().startswith("bearer "):
        raise HTTPException(401, detail={"code": "AUTH_REQUIRED"})

    object_path = f"{owner_id}/{uuid4().hex}.{extension}"
    url = (
        f"{supabase_url.rstrip('/')}/storage/v1/object/"
        f"{quote(BUCKET, safe='')}/{quote(object_path, safe='/')}"
    )
    headers = {
        "apikey": publishable_key,
        "Authorization": authorization,
        "Content-Type": media_type,
        "x-upsert": "false",
    }
    try:
        async with httpx.AsyncClient(timeout=UPLOAD_TIMEOUT) as client:
            upstream = await client.post(url, content=content, headers=headers)
    except httpx.HTTPError as exc:
        raise HTTPException(503, detail={"code": "CLOUD_DATA_UNAVAILABLE"}) from exc

    if upstream.status_code not in (200, 201):
        response_type = upstream.headers.get("content-type", "").split(";", 1)[0]
        if response_type != "application/json":
            raise HTTPException(502, detail={"code": "CLOUD_DATA_BAD_RESPONSE"})
        return JSONResponse(
            content={"code": "ATTACHMENT_UPLOAD_FAILED"},
            status_code=upstream.status_code,
            headers={"Cache-Control": "no-store"},
        )
    return JSONResponse(
        content={"path": object_path, "bucket": BUCKET},
        status_code=201,
        headers={"Cache-Control": "no-store"},
    )


MAX_EXTRACTED_CHARS = 100_000
MAX_PDF_PAGES = 100
TEXT_TYPES = {
    "application/pdf", "text/plain", "text/markdown", "text/csv", "application/json",
}


class AttachmentExtractRequest(BaseModel):
    storage_path: str = Field(min_length=1, max_length=160)
    mime_type: Literal[
        "application/pdf", "text/plain", "text/markdown", "text/csv",
        "application/json", "image/png", "image/jpeg", "image/webp",
    ]
    size_bytes: int = Field(ge=1, le=MAX_ATTACHMENT_BYTES)


def _validate_owned_storage_path(storage_path: str, owner_id: str, mime_type: str) -> bool:
    extension = ALLOWED_TYPES[mime_type]
    pattern = rf"{re.escape(owner_id)}/[0-9a-f]{{32}}\.{extension}"
    return re.fullmatch(pattern, storage_path, flags=re.IGNORECASE) is not None


def _extract_document_text(content: bytes, mime_type: str) -> str:
    if mime_type == "application/pdf":
        reader = PdfReader(BytesIO(content), strict=True)
        if reader.is_encrypted:
            raise ValueError("encrypted_pdf")
        if len(reader.pages) > MAX_PDF_PAGES:
            raise OverflowError("pdf_page_limit")
        chunks: list[str] = []
        total = 0
        for page in reader.pages:
            page_text = page.extract_text() or ""
            total += len(page_text)
            if total > MAX_EXTRACTED_CHARS:
                raise OverflowError("extracted_text_limit")
            chunks.append(page_text)
        text = "\n\n".join(chunks)
    else:
        text = content.decode("utf-8-sig", errors="strict")
    if not text.strip():
        raise ValueError("no_extractable_text")
    if len(text) > MAX_EXTRACTED_CHARS:
        raise OverflowError("extracted_text_limit")
    return text


@router.post("/attachments/extract")
async def extract_attachment_text(
    body: AttachmentExtractRequest,
    request: Request,
    user: AuthenticatedUser = Depends(get_current_user),
) -> JSONResponse:
    try:
        owner_id = str(UUID(user.subject))
    except (ValueError, TypeError, AttributeError) as exc:
        raise HTTPException(401, detail={"code": "AUTH_REQUIRED"}) from exc

    if body.mime_type not in TEXT_TYPES:
        raise HTTPException(409, detail={"code": "VISION_PROCESSING_NOT_CONFIGURED"})
    if not _validate_owned_storage_path(body.storage_path, owner_id, body.mime_type):
        raise HTTPException(404, detail={"code": "ATTACHMENT_NOT_FOUND"})

    settings = getattr(request.app.state, "settings", None)
    supabase_url = getattr(settings, "supabase_url", None)
    key = getattr(settings, "effective_supabase_publishable_key", None)
    authorization = request.headers.get("authorization", "")
    if not isinstance(supabase_url, str) or not supabase_url.startswith("https://") or key is None:
        raise HTTPException(503, detail={"code": "CLOUD_DATA_UNAVAILABLE"})
    publishable_key = key.get_secret_value()
    if not publishable_key or not authorization.lower().startswith("bearer "):
        raise HTTPException(401, detail={"code": "AUTH_REQUIRED"})

    url = (
        f"{supabase_url.rstrip('/')}/storage/v1/object/"
        f"{quote(BUCKET, safe='')}/{quote(body.storage_path, safe='/')}"
    )
    headers = {"apikey": publishable_key, "Authorization": authorization}
    try:
        async with httpx.AsyncClient(timeout=UPLOAD_TIMEOUT) as client:
            upstream = await client.get(url, headers=headers)
    except httpx.HTTPError as exc:
        raise HTTPException(503, detail={"code": "CLOUD_DATA_UNAVAILABLE"}) from exc

    if upstream.status_code == 404 or upstream.status_code == 403:
        raise HTTPException(404, detail={"code": "ATTACHMENT_NOT_FOUND"})
    if upstream.status_code != 200:
        raise HTTPException(503, detail={"code": "CLOUD_DATA_UNAVAILABLE"})
    content = upstream.content
    if not content or len(content) != body.size_bytes:
        raise HTTPException(422, detail={"code": "ATTACHMENT_METADATA_MISMATCH"})
    if len(content) > MAX_ATTACHMENT_BYTES:
        raise HTTPException(413, detail={"code": "ATTACHMENT_TOO_LARGE"})
    actual_type = upstream.headers.get("content-type", "").split(";", 1)[0].lower()
    if actual_type != body.mime_type:
        raise HTTPException(422, detail={"code": "ATTACHMENT_METADATA_MISMATCH"})

    try:
        extracted_text = _extract_document_text(content, body.mime_type)
    except OverflowError as exc:
        code = str(exc)
        raise HTTPException(413, detail={"code": code}) from exc
    except UnicodeDecodeError as exc:
        raise HTTPException(422, detail={"code": "ATTACHMENT_TEXT_ENCODING_UNSUPPORTED"}) from exc
    except Exception as exc:
        code = str(exc) if str(exc) in {"encrypted_pdf", "no_extractable_text"} else "invalid_pdf"
        raise HTTPException(422, detail={"code": code}) from exc

    return JSONResponse(
        content={
            "storage_path": body.storage_path,
            "mime_type": body.mime_type,
            "size_bytes": len(content),
            "extracted_text": extracted_text,
            "extraction": "text",
        },
        headers={"Cache-Control": "no-store"},
    )
