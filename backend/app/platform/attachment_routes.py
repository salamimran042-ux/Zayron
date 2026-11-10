from __future__ import annotations

from uuid import uuid4, UUID
from urllib.parse import quote

import httpx
from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse

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
