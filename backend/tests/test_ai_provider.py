from types import SimpleNamespace

from pydantic import SecretStr

from app.platform import chat_routes


def test_provider_configuration_is_disabled_until_all_values_exist(monkeypatch):
    monkeypatch.setattr(
        chat_routes,
        "get_settings",
        lambda: SimpleNamespace(
            ai_provider_api_key=None,
            ai_provider_base_url=None,
            ai_provider_model=None,
            environment="development",
        ),
    )
    assert chat_routes._provider_configuration() is None


def test_provider_configuration_returns_server_side_secret(monkeypatch):
    monkeypatch.setattr(
        chat_routes,
        "get_settings",
        lambda: SimpleNamespace(
            ai_provider_api_key=SecretStr("test-secret"),
            ai_provider_base_url="https://example.invalid/v1/",
            ai_provider_model="test-model",
            environment="development",
        ),
    )
    settings, key, base_url, model = chat_routes._provider_configuration()
    assert key == "test-secret"
    assert base_url == "https://example.invalid/v1"
    assert model == "test-model"
    assert settings.environment == "development"


def test_production_rejects_plaintext_provider_url(monkeypatch):
    monkeypatch.setattr(
        chat_routes,
        "get_settings",
        lambda: SimpleNamespace(
            ai_provider_api_key=SecretStr("test-secret"),
            ai_provider_base_url="http://example.invalid/v1",
            ai_provider_model="test-model",
            environment="production",
        ),
    )
    assert chat_routes._provider_configuration() is None


def test_sse_payload_uses_client_contract():
    assert chat_routes._sse("message.delta", {"delta": "Hello"}) == (
        'event: message.delta\ndata: {"delta":"Hello"}\n\n'
    )
