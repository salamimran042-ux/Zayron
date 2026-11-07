from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_reports_api_process_is_running() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "zayron-api",
        "version": "0.1.0",
    }
