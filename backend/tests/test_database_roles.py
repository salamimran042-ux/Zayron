import pytest

from app.db.pool import user_connection, worker_connection


class _AsyncContext:
    def __init__(self, connection=None, label=None):
        self.connection = connection
        self.label = label

    async def __aenter__(self):
        if self.label:
            self.connection.events.append(f"{self.label}:begin")
        return self.connection

    async def __aexit__(self, exc_type, exc, tb):
        if self.label:
            self.connection.events.append(f"{self.label}:end")
        return False


class _Connection:
    def __init__(self):
        self.events = []

    def transaction(self):
        return _AsyncContext(self, "transaction")

    async def execute(self, query, *args):
        self.events.append((query, args))
        return "OK"


class _Pool:
    def __init__(self):
        self.connection = _Connection()

    def acquire(self):
        return _AsyncContext(self.connection)


@pytest.mark.asyncio
async def test_user_connection_sets_verified_subject_inside_transaction():
    pool = _Pool()
    user_id = "11111111-1111-4111-8111-111111111111"

    async with user_connection(pool, user_id) as connection:
        assert connection is pool.connection

    assert pool.connection.events == [
        "transaction:begin",
        ("SET LOCAL ROLE zayron_api", ()),
        ("SELECT set_config('request.jwt.claim.sub', $1, true)", (user_id,)),
        "transaction:end",
    ]


@pytest.mark.asyncio
async def test_worker_connection_uses_separate_restricted_role():
    pool = _Pool()

    async with worker_connection(pool) as connection:
        assert connection is pool.connection

    assert pool.connection.events == [
        "transaction:begin",
        ("SET LOCAL ROLE zayron_worker", ()),
        "transaction:end",
    ]
