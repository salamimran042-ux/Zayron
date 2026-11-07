import json
import time

import httpx
import pytest

from app.auth.supabase_auth import SupabaseAuthClient, SupabaseAuthError
from app.core.config import Settings


def payload():
    return {
        "access_token": "access-token",
        "refresh_token": "rotated-refresh-token",
        "expires_in": 3600,
        "user": {
            "id": "user-123",
            "email": "user@example.com",
            "user_metadata": {"full_name": "Zayron User"},
        },
    }


@pytest.mark.asyncio
async def test_password_sign_in_uses_server_key_and_returns_minimal_profile():
    requests = []

    async def handler(request):
        requests.append(request)
        return httpx.Response(200, json=payload())

    client = SupabaseAuthClient(
        "https://project.supabase.co",
        "server-anon-key",
        transport=httpx.MockTransport(handler),
    )
    try:
        result = await client.sign_in("user@example.com", "correct horse battery")
        request = requests[0]
        assert request.url.path == "/auth/v1/token"
        assert request.url.params["grant_type"] == "password"
        assert request.headers["apikey"] == "server-anon-key"
        assert json.loads(request.content)["password"] == "correct horse battery"
        response = result.response()
        assert response["refresh_token"] == "rotated-refresh-token"
        assert response["expires_at"] >= int(time.time()) + 3500
        assert response["user"] == {
            "id": "user-123",
            "email": "user@example.com",
            "display_name": "Zayron User",
        }
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_refresh_uses_refresh_grant_and_returns_rotated_token():
    requests = []

    async def handler(request):
        requests.append(request)
        return httpx.Response(200, json=payload())

    client = SupabaseAuthClient(
        "https://project.supabase.co",
        "anon",
        transport=httpx.MockTransport(handler),
    )
    try:
        result = await client.refresh("old-refresh-token")
        assert requests[0].url.params["grant_type"] == "refresh_token"
        assert json.loads(requests[0].content)["refresh_token"] == "old-refresh-token"
        assert result.refresh_token == "rotated-refresh-token"
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_provider_errors_do_not_leak_response_body_or_credentials():
    async def handler(request):
        return httpx.Response(
            400,
            json={"msg": "private provider diagnostic", "code": "invalid_credentials"},
        )

    client = SupabaseAuthClient(
        "https://project.supabase.co",
        "anon",
        transport=httpx.MockTransport(handler),
    )
    try:
        with pytest.raises(SupabaseAuthError) as error:
            await client.sign_in("user@example.com", "wrong-password")
        assert error.value.status_code == 400
        assert "private provider diagnostic" not in str(error.value)
        assert "wrong-password" not in str(error.value)
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_malformed_provider_token_response_is_rejected():
    async def handler(request):
        return httpx.Response(
            200,
            json={"access_token": "access", "refresh_token": "refresh"},
        )

    client = SupabaseAuthClient(
        "https://project.supabase.co",
        "anon",
        transport=httpx.MockTransport(handler),
    )
    try:
        with pytest.raises(SupabaseAuthError) as error:
            await client.sign_in("user@example.com", "password")
        assert error.value.status_code == 502
    finally:
        await client.close()


def test_auth_client_requires_both_supabase_settings():
    assert SupabaseAuthClient.from_settings(Settings(_env_file=None)) is None
    assert SupabaseAuthClient.from_settings(
        Settings(supabase_url="https://project.supabase.co", _env_file=None)
    ) is None


@pytest.mark.parametrize(
    "url",
    [
        "http://project.supabase.co",
        "https://user:password@project.supabase.co",
        "https://project.supabase.co/attacker-path",
        "https://project.supabase.co?redirect=attacker",
        "https://project.supabase.co#fragment",
        "https://project.supabase.co:invalid",
        "ftp://project.supabase.co",
    ],
)
def test_auth_client_rejects_unsafe_supabase_origins(url):
    with pytest.raises(ValueError):
        SupabaseAuthClient(url, "anon-key")


def test_auth_client_allows_local_http_for_development():
    client = SupabaseAuthClient("http://localhost:54321", "anon-key")
    assert str(client._client.base_url) == "http://localhost:54321/auth/v1/"
    import asyncio

    asyncio.run(client.close())
