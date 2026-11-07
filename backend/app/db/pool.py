from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import AsyncIterator
from uuid import UUID

import asyncpg

from app.core.config import Settings
from app.db.connection import database_config


@dataclass
class DatabaseRuntime:
    """Owns the least-privilege PostgreSQL pool for the API process."""

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
            ssl="require",
            server_settings={"application_name": "zayron-api"},
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


@asynccontextmanager
async def user_connection(
    pool: asyncpg.Pool, user_id: UUID | str
) -> AsyncIterator[asyncpg.Connection]:
    """Run one user request as the RLS-enforced API role and verified JWT subject."""
    async with pool.acquire() as connection:
        async with connection.transaction():
            await connection.execute("SET LOCAL ROLE zayron_api")
            await connection.execute(
                "SELECT set_config('request.jwt.claim.sub', $1, true)",
                str(user_id),
            )
            yield connection


@asynccontextmanager
async def worker_connection(
    pool: asyncpg.Pool,
) -> AsyncIterator[asyncpg.Connection]:
    """Run one internal worker operation under its narrowly scoped RLS role."""
    async with pool.acquire() as connection:
        async with connection.transaction():
            await connection.execute("SET LOCAL ROLE zayron_worker")
            yield connection
