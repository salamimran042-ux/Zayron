from fastapi.testclient import TestClient

from app.main import app


def test_health_endpoint_is_public_and_reports_service_identity():
    with TestClient(app) as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["service"] == "zayron-api"


def test_configuration_status_is_public_and_never_returns_secret_values():
    with TestClient(app) as client:
        response = client.get("/config/status")

    assert response.status_code == 200
    payload = response.json()
    assert set(payload) == {"environment", "integrations"}
    assert "ai_provider" in payload["integrations"]
    assert all(
        value in {"configured", "not_configured"}
        for value in payload["integrations"].values()
    )
    assert "api_key" not in response.text.lower()
    assert "secret" not in response.text.lower()


def test_openapi_documents_the_expected_operational_and_chat_routes():
    with TestClient(app) as client:
        response = client.get("/openapi.json")

    assert response.status_code == 200
    paths = response.json()["paths"]
    assert "/health" in paths
    assert "/config/status" in paths
    assert "/api/v1/chat/stream" in paths

from pydantic import SecretStr

from app.core.config import Settings


def test_ai_provider_is_reported_configured_only_when_all_values_exist():
    complete = Settings(
        _env_file=None,
        ai_provider_base_url="https://api.groq.com/openai/v1",
        ai_provider_api_key=SecretStr("unit-test-only"),
        ai_provider_model="openai/gpt-oss-20b",
    )
    incomplete = Settings(
        _env_file=None,
        ai_provider_base_url="https://api.groq.com/openai/v1",
        ai_provider_model="openai/gpt-oss-20b",
    )

    assert complete.integration_status()["ai_provider"] == "configured"
    assert incomplete.integration_status()["ai_provider"] == "not_configured"
