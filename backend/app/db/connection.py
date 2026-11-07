from dataclasses import dataclass
from typing import Protocol

from pydantic import SecretStr

from app.core.config import Settings


@dataclass(frozen=True)
class DatabaseConfig:
    """Database connection settings kept separate from API route code."""

    url: SecretStr | None

    @property
    def configured(self) -> bool:
        return self.url is not None and bool(self.url.get_secret_value().strip())

    def driver_url(self) -> str:
        """Reveal the URL only at the eventual database-driver boundary."""
        if not self.configured or self.url is None:
            raise RuntimeError("Zayron database is not configured")
        return self.url.get_secret_value()


class DatabaseAdapter(Protocol):
    """Contract required of a future PostgreSQL adapter."""

    async def ping(self) -> bool:
        """Return True only after a real database round-trip succeeds."""
        ...


def database_config(settings: Settings) -> DatabaseConfig:
    """Build a safe database configuration without opening a connection."""
    return DatabaseConfig(url=settings.database_url)
