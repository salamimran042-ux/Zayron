from fastapi.testclient import TestClient

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser
from app.main import app


def authenticated_user():
    return AuthenticatedUser(subject="00000000-0000-0000-0000-000000000001", claims={})


def test_chat_stream_requires_bearer_auth():
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/chat/stream",
            json={
                "requestId": "request-1",
                "conversationId": None,
                "messages": [{"role": "user", "content": "Hello"}],
            },
        )
    assert response.status_code == 401


def test_chat_stream_validates_request_and_reports_provider_not_configured():
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/chat/stream",
                json={
                    "requestId": "request-1",
                    "conversationId": None,
                    "messages": [{"role": "user", "content": "Hello"}],
                },
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)

    assert response.status_code == 503
    assert response.json() == {
        "code": "SERVICE_UNAVAILABLE",
        "message": "The Zayron chat model provider is not configured.",
    }


def test_chat_stream_rejects_assistant_as_final_message():
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/chat/stream",
                json={
                    "requestId": "request-2",
                    "conversationId": None,
                    "messages": [{"role": "assistant", "content": "A previous answer"}],
                },
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)

    assert response.status_code == 422
