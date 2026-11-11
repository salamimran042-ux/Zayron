from __future__ import annotations

import asyncio
import json
import re
from typing import Any
from urllib.parse import quote
from uuid import UUID

import httpx

from app.core.config import Settings, get_settings
from app.platform.attachment_routes import (
    BUCKET,
    MAX_ATTACHMENT_BYTES,
    _extract_document_text,
)

MIME_EXTENSIONS = {
    "application/pdf": "pdf",
    "text/plain": "txt",
    "text/markdown": "md",
    "text/csv": "csv",
    "application/json": "json",
}
MAX_RESULT_BYTES = 120_000
STORAGE_TIMEOUT = httpx.Timeout(60.0, connect=5.0)


class DocumentJobError(Exception):
    """A permanent, safe-to-record document job error."""

    def __init__(self, code: str) -> None:
        super().__init__(code)
        self.code = code


class StorageUnavailable(Exception):
    """A transient storage failure that may be retried."""


def validate_document_job(job: dict[str, Any]) -> dict[str, Any]:
    """Validate claimed job metadata before any privileged Storage request."""
    if job.get("job_type") != "document_ingest":
        raise DocumentJobError("UNSUPPORTED_JOB_TYPE")
    try:
        owner_id = str(UUID(str(job["user_id"])))
        payload = job["input_payload"]
        attachment = payload["attachment"]
        attachment_id = str(UUID(str(attachment["id"])))
        mime_type = attachment["mime_type"]
        storage_path = attachment["storage_path"]
        size_bytes = attachment["size_bytes"]
    except (KeyError, TypeError, ValueError, AttributeError):
        raise DocumentJobError("DOCUMENT_JOB_PAYLOAD_INVALID") from None

    extension = MIME_EXTENSIONS.get(mime_type)
    if extension is None:
        raise DocumentJobError("DOCUMENT_TYPE_NOT_SUPPORTED")
    if payload.get("operation") != "extract_text":
        raise DocumentJobError("DOCUMENT_OPERATION_NOT_SUPPORTED")
    if not isinstance(size_bytes, int) or isinstance(size_bytes, bool):
        raise DocumentJobError("ATTACHMENT_METADATA_INVALID")
    if size_bytes < 1 or size_bytes > MAX_ATTACHMENT_BYTES:
        raise DocumentJobError("ATTACHMENT_TOO_LARGE")
    expected = rf"{re.escape(owner_id)}/[0-9a-f]{{32}}\.{extension}"
    if not isinstance(storage_path, str) or not re.fullmatch(
        expected, storage_path, flags=re.IGNORECASE
    ):
        raise DocumentJobError("ATTACHMENT_NOT_FOUND")

    return {
        "owner_id": owner_id,
        "attachment_id": attachment_id,
        "mime_type": mime_type,
        "storage_path": storage_path,
        "size_bytes": size_bytes,
    }


async def download_private_attachment(
    client: httpx.AsyncClient,
    *,
    supabase_url: str,
    secret_key: str,
    storage_path: str,
    expected_size: int,
    mime_type: str,
) -> bytes:
    """Download one validated object; never list or enumerate the private bucket."""
    url = (
        f"{supabase_url.rstrip('/')}/storage/v1/object/"
        f"{quote(BUCKET, safe='')}/{quote(storage_path, safe='/')}"
    )
    headers = {"apikey": secret_key}
    try:
        async with client.stream("GET", url, headers=headers) as response:
            if response.status_code in (404, 403):
                raise DocumentJobError("ATTACHMENT_NOT_FOUND")
            if response.status_code == 429 or response.status_code >= 500:
                raise StorageUnavailable("storage_temporarily_unavailable")
            if response.status_code != 200:
                raise DocumentJobError("ATTACHMENT_DOWNLOAD_REJECTED")
            actual_type = response.headers.get("content-type", "").split(";", 1)[0].lower()
            if actual_type != mime_type:
                raise DocumentJobError("ATTACHMENT_METADATA_MISMATCH")
            content_length = response.headers.get("content-length")
            if content_length:
                try:
                    if int(content_length) != expected_size:
                        raise DocumentJobError("ATTACHMENT_METADATA_MISMATCH")
                except ValueError:
                    raise DocumentJobError("ATTACHMENT_METADATA_MISMATCH") from None
            chunks: list[bytes] = []
            total = 0
            async for chunk in response.aiter_bytes():
                total += len(chunk)
                if total > MAX_ATTACHMENT_BYTES or total > expected_size:
                    raise DocumentJobError("ATTACHMENT_METADATA_MISMATCH")
                chunks.append(chunk)
    except (httpx.TimeoutException, httpx.NetworkError) as exc:
        raise StorageUnavailable("storage_temporarily_unavailable") from exc
    content = b"".join(chunks)
    if not content or len(content) != expected_size:
        raise DocumentJobError("ATTACHMENT_METADATA_MISMATCH")
    return content


async def process_document_job(
    api_client: httpx.AsyncClient,
    storage_client: httpx.AsyncClient,
    settings: Settings,
    job: dict[str, Any],
) -> None:
    """Retrieve, extract, and persist a bounded result for the currently leased job."""
    job_id = str(job["id"])
    worker_id = settings.worker_id
    try:
        attachment = validate_document_job(job)
        secret = settings.effective_supabase_secret_key
        if secret is None or not secret.get_secret_value():
            raise DocumentJobError("DOCUMENT_STORAGE_NOT_CONFIGURED")
        if not settings.supabase_url or not settings.supabase_url.startswith("https://"):
            raise DocumentJobError("DOCUMENT_STORAGE_NOT_CONFIGURED")

        content = await download_private_attachment(
            storage_client,
            supabase_url=settings.supabase_url,
            secret_key=secret.get_secret_value(),
            storage_path=attachment["storage_path"],
            expected_size=attachment["size_bytes"],
            mime_type=attachment["mime_type"],
        )
        try:
            extracted_text = _extract_document_text(content, attachment["mime_type"])
        except OverflowError as exc:
            code = str(exc)
            raise DocumentJobError(
                code if code in {"pdf_page_limit", "extracted_text_limit"} else "DOCUMENT_LIMIT_EXCEEDED"
            ) from None
        except UnicodeDecodeError:
            raise DocumentJobError("ATTACHMENT_TEXT_ENCODING_UNSUPPORTED") from None
        except ValueError as exc:
            code = str(exc)
            raise DocumentJobError(
                code if code in {"encrypted_pdf", "no_extractable_text"} else "INVALID_DOCUMENT"
            ) from None
        except Exception:
            raise DocumentJobError("INVALID_DOCUMENT") from None

        result_payload = {
            "attachment_id": attachment["attachment_id"],
            "mime_type": attachment["mime_type"],
            "size_bytes": len(content),
            "extraction": "text",
            "extracted_text": extracted_text,
        }
        if len(json.dumps(result_payload, ensure_ascii=False).encode("utf-8")) > MAX_RESULT_BYTES:
            raise DocumentJobError("EXTRACTED_RESULT_TOO_LARGE")
        response = await api_client.post(
            f"/internal/v1/jobs/{job_id}/complete",
            json={"worker_id": worker_id, "result_payload": result_payload},
        )
        if response.status_code == 409:
            # Lease loss/cancellation is authoritative; never overwrite another worker.
            return
        response.raise_for_status()
    except StorageUnavailable:
        await _fail_job(api_client, job_id, worker_id, "DOCUMENT_STORAGE_TEMPORARY_FAILURE", 30)
    except DocumentJobError as exc:
        await _fail_job(api_client, job_id, worker_id, exc.code, 0)
    except httpx.HTTPError:
        await _fail_job(api_client, job_id, worker_id, "DOCUMENT_WORKER_API_FAILURE", 30)


async def _fail_job(
    api_client: httpx.AsyncClient,
    job_id: str,
    worker_id: str,
    error_code: str,
    retry_after_seconds: int,
) -> None:
    response = await api_client.post(
        f"/internal/v1/jobs/{job_id}/fail",
        json={
            "worker_id": worker_id,
            "error_code": error_code,
            "retry_after_seconds": retry_after_seconds,
        },
    )
    if response.status_code != 409:
        response.raise_for_status()


async def run_once(
    api_client: httpx.AsyncClient,
    storage_client: httpx.AsyncClient,
    settings: Settings,
) -> bool:
    response = await api_client.post(
        "/internal/v1/jobs/claim",
        json={
            "worker_id": settings.worker_id,
            "lease_seconds": settings.worker_lease_seconds,
            "job_types": ["document_ingest"],
        },
    )
    response.raise_for_status()
    job = response.json().get("job")
    if not job:
        return False
    await process_document_job(api_client, storage_client, settings, job)
    return True


async def run_forever(settings: Settings) -> None:
    if not settings.worker_api_url or not settings.worker_api_url.startswith("https://"):
        raise SystemExit("ZAYRON_WORKER_API_URL must be configured with an HTTPS API URL.")
    if not settings.worker_token or not settings.worker_token.get_secret_value():
        raise SystemExit("ZAYRON_WORKER_TOKEN is not configured.")
    if not settings.effective_supabase_secret_key:
        raise SystemExit("A server-only Supabase secret key is required for document retrieval.")
    if not settings.supabase_url:
        raise SystemExit("ZAYRON_SUPABASE_URL is not configured.")

    api_headers = {"Authorization": f"Bearer {settings.worker_token.get_secret_value()}"}
    async with (
        httpx.AsyncClient(
            base_url=settings.worker_api_url.rstrip("/"),
            headers=api_headers,
            timeout=STORAGE_TIMEOUT,
        ) as api_client,
        httpx.AsyncClient(timeout=STORAGE_TIMEOUT, follow_redirects=False) as storage_client,
    ):
        while True:
            found_job = await run_once(api_client, storage_client, settings)
            if not found_job:
                await asyncio.sleep(settings.worker_poll_seconds)


def main() -> None:
    asyncio.run(run_forever(get_settings()))


if __name__ == "__main__":
    main()
