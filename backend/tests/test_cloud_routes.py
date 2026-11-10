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
