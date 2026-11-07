from pathlib import Path

import pytest

from app.db.migrations import apply_migrations, load_migrations


def test_load_migrations_orders_files_and_calculates_sha256(tmp_path: Path) -> None:
    (tmp_path / "0002_second.sql").write_text("SELECT 2;", encoding="utf-8")
    (tmp_path / "0001_first.sql").write_text("SELECT 1;", encoding="utf-8")

    migrations = load_migrations(tmp_path)

    assert [item.version for item in migrations] == [
        "0001_first.sql",
        "0002_second.sql",
    ]
    assert len(migrations[0].checksum) == 64


def test_load_migrations_rejects_unversioned_sql(tmp_path: Path) -> None:
    (tmp_path / "schema.sql").write_text("SELECT 1;", encoding="utf-8")

    with pytest.raises(ValueError, match="Invalid migration filename"):
        load_migrations(tmp_path)


def test_load_migrations_rejects_empty_migration(tmp_path: Path) -> None:
    (tmp_path / "0001_empty.sql").write_text("  ", encoding="utf-8")

    with pytest.raises(ValueError, match="Migration is empty"):
        load_migrations(tmp_path)


class _Transaction:
    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False


class _Connection:
    def __init__(self) -> None:
        self.executed: list[tuple[str, tuple[object, ...]]] = []
        self.applied: dict[str, str] = {}

    async def execute(self, query: str, *args: object) -> str:
        self.executed.append((query, args))
        if query.lstrip().startswith("INSERT INTO zayron_private.schema_migrations"):
            self.applied[str(args[0])] = str(args[1])
        return "OK"

    async def fetchrow(self, query: str, *args: object):
        return {"checksum": self.applied[str(args[0])]} if str(args[0]) in self.applied else None

    def transaction(self):
        return _Transaction()


class _Acquire:
    def __init__(self, connection: _Connection) -> None:
        self.connection = connection

    async def __aenter__(self):
        return self.connection

    async def __aexit__(self, exc_type, exc, tb):
        return False


class _Pool:
    def __init__(self) -> None:
        self.connection = _Connection()

    def acquire(self):
        return _Acquire(self.connection)


@pytest.mark.asyncio
async def test_apply_migrations_is_idempotent(tmp_path: Path) -> None:
    (tmp_path / "0001_core.sql").write_text("CREATE TABLE example (id integer);", encoding="utf-8")
    pool = _Pool()

    first = await apply_migrations(pool, tmp_path)
    second = await apply_migrations(pool, tmp_path)

    assert first == ["0001_core.sql"]
    assert second == []
    assert pool.connection.applied["0001_core.sql"]
    assert any("CREATE SCHEMA IF NOT EXISTS zayron_private" in query for query, _ in pool.connection.executed)
