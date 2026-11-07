from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient

from app.auth.token_verifier import SupabaseTokenVerifier
from app.core.config import Settings
from app.main import app


class StaticKeyResolver:
    def __init__(self, key):
        self.key = key

    def get_signing_key_from_jwt(self, token: str):
        return SimpleNamespace(key=self.key)


def build_token(private_key, *, expired: bool = False) -> str:
    now = datetime.now(timezone.utc)
    expiry = now - timedelta(minutes=1) if expired else now + timedelta(minutes=10)
    claims = {
        "sub": "user-123",
        "aud": "authenticated",
        "iss": "https://zayron-test.supabase.co/auth/v1",
        "iat": now - timedelta(minutes=2),
        "exp": expiry,
        "role": "authenticated",
    }
    return jwt.encode(
        claims,
        private_key,
        algorithm="RS256",
        headers={"kid": "test-signing-key"},
    )


def configured_verifier(public_key) -> SupabaseTokenVerifier:
    return SupabaseTokenVerifier(
        Settings(supabase_url="https://zayron-test.supabase.co", _env_file=None),
        key_resolver=StaticKeyResolver(public_key),
    )


def test_me_rejects_missing_bearer_token() -> None:
    with TestClient(app) as client:
        response = client.get("/api/v1/me")

    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"


def test_me_rejects_token_when_verifier_is_not_configured() -> None:
    with TestClient(app) as client:
        response = client.get("/api/v1/me", headers={"Authorization": "Bearer not.a.valid.token"})

    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"


def test_me_rejects_invalid_token() -> None:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    other_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    with TestClient(app) as client:
        app.state.token_verifier = configured_verifier(other_key.public_key())
        response = client.get(
            "/api/v1/me",
            headers={"Authorization": f"Bearer {build_token(private_key)}"},
        )

    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid authentication credentials"


def test_me_rejects_expired_token() -> None:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    with TestClient(app) as client:
        app.state.token_verifier = configured_verifier(private_key.public_key())
        response = client.get(
            "/api/v1/me",
            headers={"Authorization": f"Bearer {build_token(private_key, expired=True)}"},
        )

    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid authentication credentials"


def test_me_returns_only_verified_identity_fields() -> None:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    with TestClient(app) as client:
        app.state.token_verifier = configured_verifier(private_key.public_key())
        response = client.get(
            "/api/v1/me",
            headers={"Authorization": f"Bearer {build_token(private_key)}"},
        )

    assert response.status_code == 200
    assert response.json() == {"id": "user-123", "role": "authenticated"}


def test_me_is_not_exposed_on_unversioned_legacy_path() -> None:
    with TestClient(app) as client:
        response = client.get("/v1/me")

    assert response.status_code == 404


def test_me_rejects_basic_auth_scheme() -> None:
    with TestClient(app) as client:
        response = client.get(
            "/api/v1/me",
            headers={"Authorization": "Basic dXNlcjpwYXNz"},
        )

    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"
