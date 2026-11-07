from fastapi.testclient import TestClient
from app.main import app


def test_platform_endpoints_require_bearer_auth():
    paths = [
        ("/api/v1/profile", "GET"),
        ("/api/v1/conversations", "GET"),
        ("/api/v1/jobs", "GET"),
        ("/api/v1/entitlements", "GET"),
        ("/api/v1/usage", "GET"),
    ]
    with TestClient(app) as client:
        for path, method in paths:
            response = client.request(method, path)
            assert response.status_code == 401, (path, response.status_code)


def test_platform_endpoints_reject_unconfigured_verification():
    with TestClient(app) as client:
        response = client.get(
            "/api/v1/conversations",
            headers={"Authorization": "Bearer not.a.valid.token"},
        )
    assert response.status_code == 401
