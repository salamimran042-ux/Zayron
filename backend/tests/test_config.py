import asyncio

from app.auth.supabase_auth import SupabaseAuthClient
from app.core.config import Settings


def test_settings_have_safe_development_defaults() -> None:
    settings = Settings(_env_file=None)

    assert settings.environment == "development"
    assert settings.log_level == "INFO"
    assert settings.database_url is None
    assert settings.migration_database_url is None
    assert settings.supabase_url is None
    assert settings.integration_status() == {
        "database": "not_configured",
        "migration_database": "not_configured",
        "supabase_url": "not_configured",
        "supabase_publishable_key": "not_configured",
        "supabase_secret_key": "not_configured",
        "supabase_anon_key_legacy": "not_configured",
        "supabase_service_role_key_legacy": "not_configured",
        "supabase_jwt_verification": "not_configured",
        "worker": "not_configured",
        "ai_provider": "not_configured",
    }


def test_settings_read_environment_without_exposing_secrets(monkeypatch) -> None:
    monkeypatch.setenv("ZAYRON_ENVIRONMENT", "test")
    monkeypatch.setenv("ZAYRON_DATABASE_URL", "postgresql://example.invalid/db")
    monkeypatch.setenv("ZAYRON_MIGRATION_DATABASE_URL", "postgresql://admin.invalid/db")
    monkeypatch.setenv("ZAYRON_SUPABASE_SECRET_KEY", "test-secret-value")
    monkeypatch.setenv("ZAYRON_SUPABASE_PUBLISHABLE_KEY", "sb_publishable_test")

    settings = Settings(_env_file=None)

    assert settings.environment == "test"
    assert settings.database_url is not None
    assert settings.database_url.get_secret_value() == "postgresql://example.invalid/db"
    assert settings.migration_database_url is not None
    assert settings.migration_database_url.get_secret_value() == "postgresql://admin.invalid/db"
    assert settings.supabase_secret_key is not None
    assert settings.supabase_publishable_key is not None
    assert settings.integration_status()["database"] == "configured"
    assert settings.integration_status()["migration_database"] == "configured"
    assert settings.integration_status()["supabase_secret_key"] == "configured"
    assert settings.integration_status()["supabase_publishable_key"] == "configured"
    assert "test-secret-value" not in str(settings)
    assert "test-secret-value" not in str(settings.integration_status())


def test_legacy_supabase_keys_remain_backward_compatible() -> None:
    settings = Settings(
        supabase_anon_key="legacy-public-key",
        supabase_service_role_key="legacy-secret-key",
        _env_file=None,
    )

    assert settings.effective_supabase_publishable_key is not None
    assert settings.effective_supabase_publishable_key.get_secret_value() == "legacy-public-key"
    assert settings.integration_status()["supabase_publishable_key"] == "configured"
    assert settings.integration_status()["supabase_secret_key"] == "configured"
    assert settings.integration_status()["supabase_anon_key_legacy"] == "configured"


def test_current_publishable_key_takes_precedence_over_legacy_anon_key() -> None:
    settings = Settings(
        supabase_publishable_key="sb_publishable_current",
        supabase_anon_key="legacy_anon",
        _env_file=None,
    )

    assert settings.effective_supabase_publishable_key is not None
    assert settings.effective_supabase_publishable_key.get_secret_value() == "sb_publishable_current"


def test_auth_client_uses_current_publishable_key() -> None:
    settings = Settings(
        supabase_url="https://project.supabase.co",
        supabase_publishable_key="sb_publishable_current",
        supabase_secret_key="sb_secret_server_only",
        _env_file=None,
    )
    client = SupabaseAuthClient.from_settings(settings)

    assert client is not None
    assert client._client.headers["apikey"] == "sb_publishable_current"
    asyncio.run(client.close())


def test_auth_client_falls_back_to_legacy_anon_key() -> None:
    settings = Settings(
        supabase_url="https://project.supabase.co",
        supabase_anon_key="legacy_anon_key",
        _env_file=None,
    )
    client = SupabaseAuthClient.from_settings(settings)

    assert client is not None
    assert client._client.headers["apikey"] == "legacy_anon_key"
    asyncio.run(client.close())
