from types import SimpleNamespace

from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser
from app.main import app
from app.platform import cloud_routes


def authenticated_user():
    return AuthenticatedUser(
        subject="00000000-0000-0000-0000-000000000001",
        claims={"role": "authenticated"},
    )


def test_cloud_conversations_requires_bearer_auth():
    with TestClient(app) as client:
        response = client.get("/api/v1/cloud/conversations")
    assert response.status_code == 401


def test_cloud_conversations_proxy_forwards_user_token_and_publishable_key(monkeypatch):
    class FakeUpstreamResponse:
        status_code = 200
        headers = {"content-type": "application/json; charset=utf-8"}
        content = b'{"conversations":[]}'

    class FakeAsyncClient:
        last_request = None

        def __init__(self, **kwargs):
            self.timeout = kwargs.get("timeout")

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            return False

        async def request(self, method, url, **kwargs):
            FakeAsyncClient.last_request = (method, url, kwargs)
            return FakeUpstreamResponse()

    monkeypatch.setattr(cloud_routes.httpx, "AsyncClient", FakeAsyncClient)
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            app.state.settings = SimpleNamespace(
                supabase_url="https://zayron-example.supabase.co",
                effective_supabase_publishable_key=SecretStr("sb_publishable_test"),
            )
            response = client.get(
                "/api/v1/cloud/conversations?include_archived=true",
                headers={"Authorization": "Bearer user-access-token"},
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)

    assert response.status_code == 200
    assert response.json() == {"conversations": []}
    assert response.headers["cache-control"] == "no-store"
    method, url, kwargs = FakeAsyncClient.last_request
    assert method == "GET"
    assert url == "https://zayron-example.supabase.co/functions/v1/conversations"
    assert kwargs["params"] == {"include_archived": "true"}
    assert kwargs["headers"]["Authorization"] == "Bearer user-access-token"
    assert kwargs["headers"]["apikey"] == "sb_publishable_test"


def test_cloud_message_proxy_forwards_private_attachment_references(monkeypatch):
    class FakeUpstreamResponse:
        status_code = 201
        headers = {"content-type": "application/json"}
        content = b'{"message":{"id":"message-1"}}'

    class FakeAsyncClient:
        last_request = None

        def __init__(self, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            return False

        async def request(self, method, url, **kwargs):
            FakeAsyncClient.last_request = (method, url, kwargs)
            return FakeUpstreamResponse()

    monkeypatch.setattr(cloud_routes.httpx, "AsyncClient", FakeAsyncClient)
    app.dependency_overrides[get_current_user] = authenticated_user
    attachment = {
        "id": "00000000-0000-0000-0000-000000000010",
        "display_name": "notes.pdf",
        "mime_type": "application/pdf",
        "size_bytes": 2048,
        "storage_path": "00000000-0000-0000-0000-000000000001/0123456789abcdef0123456789abcdef.pdf",
    }
    try:
        with TestClient(app) as client:
            app.state.settings = SimpleNamespace(
                supabase_url="https://zayron-example.supabase.co",
                effective_supabase_publishable_key=SecretStr("sb_publishable_test"),
            )
            response = client.post(
                "/api/v1/cloud/messages",
                headers={"Authorization": "Bearer user-access-token"},
                json={
                    "conversation_id": "00000000-0000-0000-0000-000000000123",
                    "content": "Please read this",
                    "attachments": [attachment],
                },
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)

    assert response.status_code == 201
    method, url, kwargs = FakeAsyncClient.last_request
    assert method == "POST"
    assert url == "https://zayron-example.supabase.co/functions/v1/messages"
    assert kwargs["json"]["attachments"] == [attachment]
    assert kwargs["headers"]["Authorization"] == "Bearer user-access-token"


def test_cloud_message_rejects_empty_content_without_attachments():
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/cloud/messages",
                headers={"Authorization": "Bearer user-access-token"},
                json={
                    "conversation_id": "00000000-0000-0000-0000-000000000123",
                    "content": "",
                    "attachments": [],
                },
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)
    assert response.status_code == 422


def test_cloud_message_rejects_attachment_from_another_owner():
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/cloud/messages",
                headers={"Authorization": "Bearer user-access-token"},
                json={
                    "conversation_id": "00000000-0000-0000-0000-000000000123",
                    "content": "Please read this",
                    "attachments": [{
                        "id": "00000000-0000-0000-0000-000000000010",
                        "display_name": "notes.pdf",
                        "mime_type": "application/pdf",
                        "size_bytes": 2048,
                        "storage_path": "00000000-0000-0000-0000-000000000099/0123456789abcdef0123456789abcdef.pdf",
                    }],
                },
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)
    assert response.status_code == 422
    assert response.json()["detail"]["code"] == "INVALID_ATTACHMENT_REFERENCE"
