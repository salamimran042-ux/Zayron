from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from pathlib import Path

from asyncpg import Pool

_MIGRATION_NAME = re.compile(r"^[0-9]{4}_[a-z0-9_]+\.sql$")
_LOCK_ID = 7_260_311_042


@dataclass(frozen=True)
class Migration:
    version: str
    sql: str
    checksum: str


def load_migrations(directory: Path) -> list[Migration]:
    migrations: list[Migration] = []
    for path in sorted(directory.glob("*.sql")):
        if not _MIGRATION_NAME.fullmatch(path.name):
            raise ValueError(f"Invalid migration filename: {path.name}")
        sql = path.read_text(encoding="utf-8")
        if not sql.strip():
            raise ValueError(f"Migration is empty: {path.name}")
        migrations.append(
            Migration(
                version=path.name,
                sql=sql,
                checksum=hashlib.sha256(sql.encode("utf-8")).hexdigest(),
            )
        )
    versions = [migration.version for migration in migrations]
    if len(versions) != len(set(versions)):
        raise ValueError("Duplicate migration versions")
    return migrations


async def apply_migrations(pool: Pool, directory: Path | None = None) -> list[str]:
    migration_dir = directory or Path(__file__).resolve().parents[2] / "migrations"
    migrations = load_migrations(migration_dir)
    applied_now: list[str] = []

    async with pool.acquire() as connection:
        await connection.execute("CREATE SCHEMA IF NOT EXISTS zayron_private")
        await connection.execute(
            "REVOKE ALL ON SCHEMA zayron_private FROM PUBLIC, anon, authenticated, service_role"
        )
        await connection.execute(
            """
            CREATE TABLE IF NOT EXISTS zayron_private.schema_migrations (
                version text PRIMARY KEY,
                checksum text NOT NULL,
                applied_at timestamptz NOT NULL DEFAULT now()
            )
            """
        )

        for migration in migrations:
            async with connection.transaction():
                await connection.execute("SELECT pg_advisory_xact_lock($1)", _LOCK_ID)
                existing = await connection.fetchrow(
                    "SELECT checksum FROM zayron_private.schema_migrations WHERE version = $1",
                    migration.version,
                )
                if existing is not None:
                    if existing["checksum"] != migration.checksum:
                        raise RuntimeError(
                            f"Applied migration was modified: {migration.version}"
                        )
                    continue

                await connection.execute(migration.sql)
                await connection.execute(
                    """
                    INSERT INTO zayron_private.schema_migrations (version, checksum)
                    VALUES ($1, $2)
                    """,
                    migration.version,
                    migration.checksum,
                )
                applied_now.append(migration.version)

    return applied_now
