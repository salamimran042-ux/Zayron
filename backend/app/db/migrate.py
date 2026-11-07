import asyncio

from app.core.config import get_settings
from app.db.migrations import apply_migrations
from app.db.pool import DatabaseRuntime


async def main() -> None:
    settings = get_settings()
    if settings.migration_database_url is None:
        raise RuntimeError(
            "ZAYRON_MIGRATION_DATABASE_URL must be configured for privileged migrations"
        )
    migration_settings = settings.model_copy(
        update={"database_url": settings.migration_database_url}
    )
    database = DatabaseRuntime()
    await database.open(migration_settings)
    if database.pool is None:
        raise RuntimeError("ZAYRON_MIGRATION_DATABASE_URL must be configured before running migrations")
    try:
        applied = await apply_migrations(database.pool)
        if applied:
            print("Applied migrations: " + ", ".join(applied))
        else:
            print("Database schema is already up to date.")
    finally:
        await database.close()


if __name__ == "__main__":
    asyncio.run(main())
