from app.core.config import Settings


def test_settings_have_safe_development_defaults() -> None:
    settings = Settings(_env_file=None)

    assert settings.environment == "development"
    assert settings.log_level == "INFO"
    assert settings.database_url is None
    assert settings.supabase_url is None
    assert settings.integration_status() == {
        "database": "not_configured",
        "supabase_url": "not_configured",
        "supabase_anon_key": "not_configured",
        "supabase_service_role_key": "not_configured",
    }


def test_settings_read_environment_without_exposing_secrets(monkeypatch) -> None:
    monkeypatch.setenv("ZAYRON_ENVIRONMENT", "test")
    monkeypatch.setenv("ZAYRON_DATABASE_URL", "postgresql://example.invalid/db")
    monkeypatch.setenv("ZAYRON_SUPABASE_SERVICE_ROLE_KEY", "test-secret-value")

    settings = Settings(_env_file=None)

    assert settings.environment == "test"
    assert settings.database_url is not None
    assert settings.database_url.get_secret_value() == "postgresql://example.invalid/db"
    assert settings.supabase_service_role_key is not None
    assert settings.integration_status()["database"] == "configured"
    assert settings.integration_status()["supabase_service_role_key"] == "configured"
    assert "test-secret-value" not in str(settings)
    assert "test-secret-value" not in str(settings.integration_status())
