from fastapi.testclient import TestClient

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser
from app.main import app


OWNER = "00000000-0000-0000-0000-000000000001"


def authenticated_user():
    return AuthenticatedUser(subject=OWNER, claims={"role": "authenticated"})


def test_document_job_endpoint_requires_authentication():
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/documents/jobs",
            json={
                "conversation_id": "10000000-0000-0000-0000-000000000001",
                "message_id": "20000000-0000-0000-0000-000000000001",
                "attachment_id": "30000000-0000-0000-0000-000000000001",
            },
        )
    assert response.status_code == 401


def test_document_job_endpoint_accepts_only_message_and_attachment_ids():
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/documents/jobs",
                headers={"Authorization": "Bearer user-access-token"},
                json={
                    "conversation_id": "10000000-0000-0000-0000-000000000001",
                    "message_id": "20000000-0000-0000-0000-000000000001",
                    "attachment_id": "30000000-0000-0000-0000-000000000001",
                    "storage_path": f"{OWNER}/0123456789abcdef0123456789abcdef.pdf",
                },
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)
    assert response.status_code == 422


def test_document_job_endpoint_fails_closed_when_database_is_not_configured():
    app.dependency_overrides[get_current_user] = authenticated_user
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/documents/jobs",
                headers={"Authorization": "Bearer user-access-token"},
                json={
                    "conversation_id": "10000000-0000-0000-0000-000000000001",
                    "message_id": "20000000-0000-0000-0000-000000000001",
                    "attachment_id": "30000000-0000-0000-0000-000000000001",
                },
            )
    finally:
        app.dependency_overrides.pop(get_current_user, None)
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "DATABASE_UNAVAILABLE"
