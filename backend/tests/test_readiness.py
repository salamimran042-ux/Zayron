from fastapi.testclient import TestClient

from app.main import app


def test_health_is_live_without_database_configuration() -> None:
    with TestClient(app) as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_readiness_does_not_claim_database_is_available_without_configuration() -> None:
    with TestClient(app) as client:
        response = client.get("/ready")

    assert response.status_code == 503
    assert response.json() == {
        "status": "not_ready",
        "database": "unavailable",
    }
