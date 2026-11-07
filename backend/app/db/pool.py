from dataclasses import dataclass

import asyncpg

from app.core.config import Settings
from app.db.connection import database_config


@dataclass
class DatabaseRuntime:
    """Owns the optional PostgreSQL pool for the lifetime of the API process."""

    pool: asyncpg.Pool | None = None

    async def open(self, settings: Settings) -> None:
        config = database_config(settings)
        if not config.configured:
            self.pool = None
            return
        self.pool = await asyncpg.create_pool(
            dsn=config.driver_url(),
            min_size=1,
            max_size=5,
            command_timeout=10,
        )

    async def ping(self) -> bool:
        if self.pool is None:
            return False
        async with self.pool.acquire() as connection:
            return await connection.fetchval("SELECT 1") == 1

    async def close(self) -> None:
        if self.pool is not None:
            await self.pool.close()
            self.pool = None
