from types import SimpleNamespace

from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser
from app.main import app
from app.platform import attachment_routes


OWNER = "00000000-0000-0000-0000-000000000001"


def authenticated_user():
    return AuthenticatedUser(subject=OWNER, claims={"role": "authenticated"})


def setup_settings():
    app.state.settings = SimpleNamespace(
        supabase_url="https://zayron-example.supabase.co",
        effective_supabase_publishable_key=SecretStr("sb_publishable_test"),
    )


def test_attachment_upload_requires_authentication():
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/cloud/attachments",
            files={"file": ("note.txt", b"hello", "text/plain")},
        )
    assert response.status_code == 401


def test_attachment_upload_rejects_unsupported_type_before_upstream(monkeypatch):
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            setup_settings()
            response = client.post(
                "/api/v1/cloud/attachments",
                headers={"Authorization": "Bearer user-access-token"},
                files={"file": ("payload.exe", b"no", "application/octet-stream")},
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)
    assert response.status_code == 415
    assert response.json()["detail"]["code"] == "ATTACHMENT_TYPE_NOT_ALLOWED"


def test_attachment_upload_forwards_user_jwt_and_owner_scoped_random_path(monkeypatch):
    class FakeUpstreamResponse:
        status_code = 200
        headers = {"content-type": "application/json"}
        content = b'{"Key":"zayron-attachments/uploaded"}'

    class FakeAsyncClient:
        request_data = None

        def __init__(self, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            return False

        async def post(self, url, **kwargs):
            FakeAsyncClient.request_data = (url, kwargs)
            return FakeUpstreamResponse()

    monkeypatch.setattr(attachment_routes.httpx, "AsyncClient", FakeAsyncClient)
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            setup_settings()
            response = client.post(
                "/api/v1/cloud/attachments",
                headers={"Authorization": "Bearer user-access-token"},
                files={"file": ("../../private.txt", b"safe text", "text/plain")},
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)

    assert response.status_code == 201
    assert response.headers["cache-control"] == "no-store"
    uploaded_path = response.json()["path"]
    assert uploaded_path.startswith(f"{OWNER}/")
    assert response.json()["bucket"] == "zayron-attachments"
    url, kwargs = FakeAsyncClient.request_data
    assert url.startswith(
        "https://zayron-example.supabase.co/storage/v1/object/zayron-attachments/"
        f"{OWNER}/"
    )
    assert "../" not in url
    assert url.endswith(".txt")
    assert kwargs["content"] == b"safe text"
    assert kwargs["headers"]["Authorization"] == "Bearer user-access-token"
    assert kwargs["headers"]["apikey"] == "sb_publishable_test"
    assert kwargs["headers"]["x-upsert"] == "false"


def test_attachment_upload_rejects_oversized_content(monkeypatch):
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            setup_settings()
            response = client.post(
                "/api/v1/cloud/attachments",
                headers={"Authorization": "Bearer user-access-token"},
                files={"file": ("large.txt", b"x" * (attachment_routes.MAX_ATTACHMENT_BYTES + 1), "text/plain")},
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)
    assert response.status_code == 413
    assert response.json()["detail"]["code"] == "ATTACHMENT_TOO_LARGE"



def test_attachment_extract_requires_authentication():
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/cloud/attachments/extract",
            json={
                "storage_path": f"{OWNER}/0123456789abcdef0123456789abcdef.txt",
                "mime_type": "text/plain",
                "size_bytes": 5,
            },
        )
    assert response.status_code == 401


def test_attachment_extract_rejects_another_users_path_before_storage(monkeypatch):
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            setup_settings()
            response = client.post(
                "/api/v1/cloud/attachments/extract",
                headers={"Authorization": "Bearer user-access-token"},
                json={
                    "storage_path": "00000000-0000-0000-0000-000000000099/0123456789abcdef0123456789abcdef.txt",
                    "mime_type": "text/plain",
                    "size_bytes": 5,
                },
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)
    assert response.status_code == 404


def test_attachment_extract_downloads_with_user_jwt_and_returns_text(monkeypatch):
    class FakeUpstreamResponse:
        status_code = 200
        headers = {"content-type": "text/plain; charset=utf-8"}
        content = b"Hello Zayron"

    class FakeAsyncClient:
        request_data = None

        def __init__(self, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            return False

        async def get(self, url, **kwargs):
            FakeAsyncClient.request_data = (url, kwargs)
            return FakeUpstreamResponse()

    monkeypatch.setattr(attachment_routes.httpx, "AsyncClient", FakeAsyncClient)
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            setup_settings()
            response = client.post(
                "/api/v1/cloud/attachments/extract",
                headers={"Authorization": "Bearer user-access-token"},
                json={
                    "storage_path": f"{OWNER}/0123456789abcdef0123456789abcdef.txt",
                    "mime_type": "text/plain",
                    "size_bytes": len(FakeUpstreamResponse.content),
                },
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)
    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    assert response.json()["extracted_text"] == "Hello Zayron"
    url, kwargs = FakeAsyncClient.request_data
    assert url.endswith(f"/{OWNER}/0123456789abcdef0123456789abcdef.txt")
    assert kwargs["headers"]["Authorization"] == "Bearer user-access-token"
    assert kwargs["headers"]["apikey"] == "sb_publishable_test"


def test_attachment_extract_rejects_images_until_vision_pipeline_exists():
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            setup_settings()
            response = client.post(
                "/api/v1/cloud/attachments/extract",
                headers={"Authorization": "Bearer user-access-token"},
                json={
                    "storage_path": f"{OWNER}/0123456789abcdef0123456789abcdef.png",
                    "mime_type": "image/png",
                    "size_bytes": 100,
                },
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "VISION_PROCESSING_NOT_CONFIGURED"
