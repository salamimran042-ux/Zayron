from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient
from app.auth.supabase_auth import AuthTokens, SupabaseAuthError
from app.auth.token_verifier import SupabaseTokenVerifier
from app.core.config import Settings
from app.main import app

class StaticKeyResolver:
    def __init__(self, key): self.key = key
    def get_signing_key_from_jwt(self, token): return SimpleNamespace(key=self.key)

class FakeSupabaseAuth:
    def __init__(self): self.calls = []
    async def sign_in(self, email, password):
        self.calls.append(("login", email, password)); return self.tokens()
    async def refresh(self, refresh_token):
        self.calls.append(("refresh", refresh_token)); return self.tokens()
    async def sign_out(self, access_token): self.calls.append(("logout", access_token))
    @staticmethod
    def tokens():
        return AuthTokens("access-token", "rotated-refresh-token", 2_000_000_000,
                          "user-123", "user@example.com", "Zayron User")

def build_token(private_key, expired=False):
    now = datetime.now(timezone.utc)
    claims = {"sub": "user-123", "aud": "authenticated",
              "iss": "https://zayron-test.supabase.co/auth/v1",
              "iat": now - timedelta(minutes=2),
              "exp": now - timedelta(minutes=1) if expired else now + timedelta(minutes=10),
              "role": "authenticated"}
    return jwt.encode(claims, private_key, algorithm="RS256",
                      headers={"kid": "test-signing-key"})

def configured_verifier(public_key):
    return SupabaseTokenVerifier(Settings(supabase_url="https://zayron-test.supabase.co",
                                          _env_file=None),
                                 key_resolver=StaticKeyResolver(public_key))

def test_me_rejects_missing_bearer_token():
    with TestClient(app) as client: response = client.get("/api/v1/me")
    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"

def test_me_rejects_token_when_verifier_is_not_configured():
    with TestClient(app) as client:
        response = client.get("/api/v1/me", headers={"Authorization": "Bearer not.a.valid.token"})
    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"

def test_me_rejects_invalid_token():
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    other = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    with TestClient(app) as client:
        app.state.token_verifier = configured_verifier(other.public_key())
        response = client.get("/api/v1/me", headers={"Authorization": f"Bearer {build_token(private)}"})
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid authentication credentials"

def test_me_rejects_expired_token():
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    with TestClient(app) as client:
        app.state.token_verifier = configured_verifier(private.public_key())
        response = client.get("/api/v1/me", headers={"Authorization": f"Bearer {build_token(private, True)}"})
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid authentication credentials"

def test_me_returns_only_verified_identity_fields():
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    with TestClient(app) as client:
        app.state.token_verifier = configured_verifier(private.public_key())
        response = client.get("/api/v1/me", headers={"Authorization": f"Bearer {build_token(private)}"})
    assert response.status_code == 200
    assert response.json() == {"id": "user-123", "role": "authenticated"}

def test_me_is_not_exposed_on_legacy_path():
    with TestClient(app) as client: response = client.get("/v1/me")
    assert response.status_code == 404

def test_me_rejects_basic_auth_scheme():
    with TestClient(app) as client:
        response = client.get("/api/v1/me", headers={"Authorization": "Basic dXNlcjpwYXNz"})
    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"

def test_login_returns_tokens_and_does_not_echo_password():
    fake = FakeSupabaseAuth()
    with TestClient(app) as client:
        app.state.supabase_auth = fake
        response = client.post("/api/v1/auth/login",
            json={"email": "user@example.com", "password": "secret-password"})
    assert response.status_code == 200
    assert response.json()["refresh_token"] == "rotated-refresh-token"
    assert response.json()["user"]["id"] == "user-123"
    assert "secret-password" not in response.text
    assert fake.calls == [("login", "user@example.com", "secret-password")]

def test_refresh_returns_rotated_tokens():
    fake = FakeSupabaseAuth()
    with TestClient(app) as client:
        app.state.supabase_auth = fake
        response = client.post("/api/v1/auth/refresh",
            json={"refresh_token": "old-refresh-token"})
    assert response.status_code == 200
    assert response.json()["refresh_token"] == "rotated-refresh-token"

def test_logout_requires_bearer_and_revokes_provider_session():
    fake = FakeSupabaseAuth()
    with TestClient(app) as client:
        app.state.supabase_auth = fake
        denied = client.post("/api/v1/auth/logout")
        response = client.post("/api/v1/auth/logout",
            headers={"Authorization": "Bearer active-access-token"})
    assert denied.status_code == 401
    assert response.status_code == 204
    assert fake.calls == [("logout", "active-access-token")]

def test_auth_routes_fail_closed_without_supabase_configuration():
    with TestClient(app) as client:
        app.state.supabase_auth = None
        response = client.post("/api/v1/auth/login",
            json={"email": "user@example.com", "password": "password"})
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "AUTH_SERVICE_UNAVAILABLE"

def test_auth_provider_errors_map_to_safe_codes():
    class RejectedAuth(FakeSupabaseAuth):
        async def sign_in(self, email, password):
            raise SupabaseAuthError(400, "invalid_credentials")
        async def refresh(self, refresh_token):
            raise SupabaseAuthError(400, "invalid_refresh_token")
    with TestClient(app) as client:
        app.state.supabase_auth = RejectedAuth()
        login = client.post("/api/v1/auth/login",
            json={"email": "user@example.com", "password": "wrong"})
        refresh = client.post("/api/v1/auth/refresh",
            json={"refresh_token": "bad-token"})
    assert login.status_code == 401
    assert login.json()["detail"]["code"] == "INVALID_CREDENTIALS"
    assert refresh.status_code == 401
    assert refresh.json()["detail"]["code"] == "SESSION_EXPIRED"
