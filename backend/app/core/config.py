from functools import lru_cache

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration loaded from environment variables only."""

    model_config = SettingsConfigDict(
        env_prefix="ZAYRON_",
        case_sensitive=False,
        extra="ignore",
    )

    environment: str = "development"
    log_level: str = "INFO"
    database_url: SecretStr | None = None
    migration_database_url: SecretStr | None = None
    supabase_url: str | None = None

    # Current Supabase key format. The publishable key is used for Auth API
    # requests; the secret key is server-only and is not needed for sign-in.
    supabase_publishable_key: SecretStr | None = None
    supabase_secret_key: SecretStr | None = None

    # Backward-compatible aliases for projects still using legacy key names.
    supabase_anon_key: SecretStr | None = None
    supabase_service_role_key: SecretStr | None = None

    # OpenAI-compatible provider bridge. All credentials remain server-side.
    # Base URL should be the provider's API root, e.g. https://host.example/v1.
    ai_provider_base_url: str | None = None
    ai_provider_api_key: SecretStr | None = None
    ai_provider_model: str | None = None
    ai_provider_timeout_seconds: int = Field(default=90, ge=10, le=180)

    worker_token: SecretStr | None = None
    worker_api_url: str | None = None
    worker_id: str = Field(default="zayron-document-worker", min_length=3, max_length=100, pattern="^[a-zA-Z0-9._-]+$")
    worker_poll_seconds: int = Field(default=5, ge=1, le=60)
    worker_lease_seconds: int = Field(default=600, ge=30, le=900)

    @property
    def effective_supabase_publishable_key(self) -> SecretStr | None:
        return self.supabase_publishable_key or self.supabase_anon_key

    @property
    def effective_supabase_secret_key(self) -> SecretStr | None:
        return self.supabase_secret_key or self.supabase_service_role_key

    def integration_status(self) -> dict[str, str]:
        publishable_configured = self.effective_supabase_publishable_key is not None
        secret_configured = self.effective_supabase_secret_key is not None
        ai_configured = bool(
            self.ai_provider_base_url and self.ai_provider_api_key and self.ai_provider_model
        )
        worker_secret = self.worker_token.get_secret_value() if self.worker_token else ""
        worker_configured = bool(
            worker_secret
            and self.worker_api_url
            and self.worker_api_url.startswith("https://")
            and self.supabase_url
            and self.supabase_url.startswith("https://")
            and secret_configured
            and self.effective_supabase_secret_key.get_secret_value()
        )
        return {
            "database": "configured" if self.database_url else "not_configured",
            "migration_database": "configured" if self.migration_database_url else "not_configured",
            "supabase_url": "configured" if self.supabase_url else "not_configured",
            "supabase_publishable_key": "configured" if publishable_configured else "not_configured",
            "supabase_secret_key": "configured" if secret_configured else "not_configured",
            "supabase_anon_key_legacy": "configured" if self.supabase_anon_key else "not_configured",
            "supabase_service_role_key_legacy": "configured" if self.supabase_service_role_key else "not_configured",
            "supabase_jwt_verification": "configured" if self.supabase_url else "not_configured",
            "worker": "configured" if worker_configured else "not_configured",
            "ai_provider": "configured" if ai_configured else "not_configured",
        }


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return the process-wide settings instance."""
    return Settings()
