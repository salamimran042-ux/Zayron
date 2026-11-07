import pytest

from app.core.config import Settings
from app.db.connection import database_config


def test_database_is_explicitly_unconfigured_by_default() -> None:
    config = database_config(Settings(_env_file=None))

    assert config.configured is False
    with pytest.raises(RuntimeError, match="not configured"):
        config.driver_url()


def test_database_url_is_available_only_at_driver_boundary(monkeypatch) -> None:
    monkeypatch.setenv("ZAYRON_DATABASE_URL", "postgresql://user:pass@example.invalid/zayron")
    config = database_config(Settings(_env_file=None))

    assert config.configured is True
    assert config.driver_url() == "postgresql://user:pass@example.invalid/zayron"
    assert "user:pass" not in repr(config)
