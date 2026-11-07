from functools import lru_cache

from pydantic import SecretStr
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
    supabase_url: str | None = None

    # Current Supabase key format. The publishable key is used for Auth API
    # requests; the secret key is server-only and is not needed for sign-in.
    supabase_publishable_key: SecretStr | None = None
    supabase_secret_key: SecretStr | None = None

    # Backward-compatible aliases for projects still using legacy key names.
    # Prefer ZAYRON_SUPABASE_PUBLISHABLE_KEY / ZAYRON_SUPABASE_SECRET_KEY.
    supabase_anon_key: SecretStr | None = None
    supabase_service_role_key: SecretStr | None = None

    worker_token: SecretStr | None = None

    @property
    def effective_supabase_publishable_key(self) -> SecretStr | None:
        """Prefer the current publishable key, falling back to legacy anon key."""
        return self.supabase_publishable_key or self.supabase_anon_key

    def integration_status(self) -> dict[str, str]:
        """Expose configuration presence without returning credentials."""
        publishable_configured = self.effective_supabase_publishable_key is not None
        secret_configured = (
            self.supabase_secret_key is not None
            or self.supabase_service_role_key is not None
        )
        return {
            "database": "configured" if self.database_url else "not_configured",
            "supabase_url": "configured" if self.supabase_url else "not_configured",
            "supabase_publishable_key": "configured" if publishable_configured else "not_configured",
            "supabase_secret_key": "configured" if secret_configured else "not_configured",
            "supabase_anon_key_legacy": (
                "configured" if self.supabase_anon_key else "not_configured"
            ),
            "supabase_service_role_key_legacy": (
                "configured" if self.supabase_service_role_key else "not_configured"
            ),
            "supabase_jwt_verification": (
                "configured" if self.supabase_url else "not_configured"
            ),
            "worker": "configured" if self.worker_token else "not_configured",
        }


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return the process-wide settings instance."""
    return Settings()
