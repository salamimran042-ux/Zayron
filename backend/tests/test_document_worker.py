from uuid import UUID

import httpx
import pytest

from app.core.config import Settings
from app.workers.document_worker import (
    DocumentJobError,
    create_worker_instance_id,
    download_private_attachment,
    validate_document_job,
)

OWNER = "2e4f8a10-6a1b-4c2d-9e3f-123456789abc"
ATTACHMENT = "d4f1b2c3-4a5e-6789-abcd-ef0123456789"
PATH = f"{OWNER}/0123456789abcdef0123456789abcdef.txt"


def valid_job(**overrides):
    job = {
        "id": "f4f1b2c3-4a5e-6789-abcd-ef0123456789",
        "user_id": OWNER,
        "job_type": "document_ingest",
        "input_payload": {
            "operation": "extract_text",
            "attachment": {
                "id": ATTACHMENT,
                "mime_type": "text/plain",
                "size_bytes": 5,
                "storage_path": PATH,
            },
        },
    }
    job.update(overrides)
    return job


def test_worker_process_instances_get_unique_lease_ids():
    first = create_worker_instance_id("document-worker")
    second = create_worker_instance_id("document-worker")
    assert first.startswith("document-worker-")
    assert second.startswith("document-worker-")
    assert first != second


def test_worker_validates_document_job_owner_and_exact_object_path():
    result = validate_document_job(valid_job())
    assert result["owner_id"] == str(UUID(OWNER))
    assert result["attachment_id"] == str(UUID(ATTACHMENT))
    assert result["storage_path"] == PATH


@pytest.mark.parametrize(
    ("overrides", "code"),
    [
        ({"job_type": "image_generation"}, "UNSUPPORTED_JOB_TYPE"),
        ({"user_id": "not-a-uuid"}, "DOCUMENT_JOB_PAYLOAD_INVALID"),
        ({"input_payload": {}}, "DOCUMENT_JOB_PAYLOAD_INVALID"),
        (
            {"input_payload": {
                "operation": "extract_text",
                "attachment": {
                    "id": ATTACHMENT,
                    "mime_type": "text/plain",
                    "size_bytes": 5,
                    "storage_path": "other-user/0123456789abcdef0123456789abcdef.txt",
                },
            }},
            "ATTACHMENT_NOT_FOUND",
        ),
    ],
)
def test_worker_rejects_untrusted_job_metadata(overrides, code):
    with pytest.raises(DocumentJobError) as error:
        validate_document_job(valid_job(**overrides))
    assert error.value.code == code


def test_worker_rejects_oversized_and_unsupported_files():
    oversized = valid_job()
    oversized["input_payload"]["attachment"]["size_bytes"] = 10 * 1024 * 1024 + 1
    with pytest.raises(DocumentJobError, match="ATTACHMENT_TOO_LARGE"):
        validate_document_job(oversized)

    unsupported = valid_job()
    unsupported["input_payload"]["attachment"]["mime_type"] = "image/png"
    with pytest.raises(DocumentJobError, match="DOCUMENT_TYPE_NOT_SUPPORTED"):
        validate_document_job(unsupported)


@pytest.mark.asyncio
async def test_worker_downloads_only_exact_private_object_with_server_key():
    seen = {}

    async def handler(request):
        seen["url"] = str(request.url)
        seen["apikey"] = request.headers.get("apikey")
        return httpx.Response(
            200,
            headers={"content-type": "text/plain", "content-length": "5"},
            content=b"hello",
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        content = await download_private_attachment(
            client,
            supabase_url="https://project.supabase.co",
            secret_key="sb_secret_test",
            storage_path=PATH,
            expected_size=5,
            mime_type="text/plain",
        )

    assert content == b"hello"
    assert seen["url"].endswith(f"/storage/v1/object/zayron-attachments/{PATH}")
    assert seen["apikey"] == "sb_secret_test"
    assert "authorization" not in seen


@pytest.mark.asyncio
async def test_worker_rejects_storage_content_type_mismatch():
    async def handler(request):
        return httpx.Response(
            200,
            headers={"content-type": "application/pdf"},
            content=b"hello",
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(DocumentJobError, match="ATTACHMENT_METADATA_MISMATCH"):
            await download_private_attachment(
                client,
                supabase_url="https://project.supabase.co",
                secret_key="sb_secret_test",
                storage_path=PATH,
                expected_size=5,
                mime_type="text/plain",
            )


def test_worker_settings_select_new_secret_key_before_legacy_service_role():
    settings = Settings(
        supabase_secret_key="sb_secret_current",
        supabase_service_role_key="legacy_service_role",
        _env_file=None,
    )
    assert settings.effective_supabase_secret_key.get_secret_value() == "sb_secret_current"



@pytest.mark.asyncio
async def test_document_worker_verifies_source_attachment_then_persists_extraction():
    from app.workers.document_worker import process_document_job

    calls = []

    async def api_handler(request):
        calls.append(request.url.path)
        if request.url.path.endswith("/attachment"):
            return httpx.Response(
                200,
                json={
                    "attachment": {
                        "id": ATTACHMENT,
                        "display_name": "notes.txt",
                        "mime_type": "text/plain",
                        "size_bytes": 5,
                        "storage_path": PATH,
                    }
                },
            )
        if request.url.path.endswith("/complete"):
            body = __import__("json").loads(request.content)
            assert body["result_payload"]["extracted_text"] == "hello"
            assert body["result_payload"]["attachment_id"] == ATTACHMENT
            return httpx.Response(200, json={"status": "succeeded"})
        raise AssertionError(f"Unexpected API path: {request.url.path}")

    async def storage_handler(request):
        assert request.headers["apikey"] == "sb_secret_test"
        return httpx.Response(
            200,
            headers={"content-type": "text/plain", "content-length": "5"},
            content=b"hello",
        )

    settings = Settings(
        worker_id="document-worker",
        supabase_url="https://project.supabase.co",
        supabase_secret_key="sb_secret_test",
        _env_file=None,
    )
    job = valid_job()
    async with (
        httpx.AsyncClient(
            base_url="https://api.zayron.test",
            transport=httpx.MockTransport(api_handler),
        ) as api_client,
        httpx.AsyncClient(
            transport=httpx.MockTransport(storage_handler),
        ) as storage_client,
    ):
        await process_document_job(api_client, storage_client, settings, job)

    assert calls == [
        f"/internal/v1/jobs/{job['id']}/attachment",
        f"/internal/v1/jobs/{job['id']}/complete",
    ]
