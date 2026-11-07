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
    supabase_anon_key: SecretStr | None = None
    supabase_service_role_key: SecretStr | None = None

    def integration_status(self) -> dict[str, str]:
        """Expose configuration presence without returning credentials."""
        return {
            "database": "configured" if self.database_url else "not_configured",
            "supabase_url": "configured" if self.supabase_url else "not_configured",
            "supabase_anon_key": "configured" if self.supabase_anon_key else "not_configured",
            "supabase_service_role_key": (
                "configured" if self.supabase_service_role_key else "not_configured"
            ),
        }


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return the process-wide settings instance."""
    return Settings()
