from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from app.auth.token_verifier import AuthenticationError, SupabaseTokenVerifier
from app.core.config import Settings


class StaticKeyResolver:
    def __init__(self, key):
        self.key = key

    def get_signing_key_from_jwt(self, token: str):
        return SimpleNamespace(key=self.key)


@pytest.fixture
def signing_keys():
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    wrong_private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return private_key, private_key.public_key(), wrong_private_key.public_key()


def make_token(private_key, **overrides):
    now = datetime.now(timezone.utc)
    claims = {
        "sub": "user-123",
        "aud": "authenticated",
        "iss": "https://zayron-test.supabase.co/auth/v1",
        "iat": now,
        "exp": now + timedelta(minutes=10),
        "role": "authenticated",
    }
    claims.update(overrides)
    return jwt.encode(
        claims,
        private_key,
        algorithm="RS256",
        headers={"kid": "test-signing-key"},
    )


def test_verifies_valid_supabase_access_token(signing_keys) -> None:
    private_key, public_key, _ = signing_keys
    verifier = SupabaseTokenVerifier(
        Settings(supabase_url="https://zayron-test.supabase.co", _env_file=None),
        key_resolver=StaticKeyResolver(public_key),
    )

    identity = verifier.verify(make_token(private_key))

    assert identity.subject == "user-123"
    assert identity.claims["aud"] == "authenticated"
    assert identity.claims["role"] == "authenticated"


def test_rejects_token_when_supabase_is_not_configured() -> None:
    verifier = SupabaseTokenVerifier(Settings(_env_file=None))

    with pytest.raises(AuthenticationError, match="not configured"):
        verifier.verify("some.token.value")


def test_rejects_expired_token(signing_keys) -> None:
    private_key, public_key, _ = signing_keys
    verifier = SupabaseTokenVerifier(
        Settings(supabase_url="https://zayron-test.supabase.co", _env_file=None),
        key_resolver=StaticKeyResolver(public_key),
    )
    expired = datetime.now(timezone.utc) - timedelta(minutes=5)

    with pytest.raises(AuthenticationError, match="invalid"):
        verifier.verify(make_token(private_key, exp=expired))


def test_rejects_wrong_issuer(signing_keys) -> None:
    private_key, public_key, _ = signing_keys
    verifier = SupabaseTokenVerifier(
        Settings(supabase_url="https://zayron-test.supabase.co", _env_file=None),
        key_resolver=StaticKeyResolver(public_key),
    )

    with pytest.raises(AuthenticationError, match="invalid"):
        verifier.verify(make_token(private_key, iss="https://attacker.example/auth/v1"))


def test_rejects_token_signed_by_untrusted_key(signing_keys) -> None:
    private_key, _, wrong_public_key = signing_keys
    verifier = SupabaseTokenVerifier(
        Settings(supabase_url="https://zayron-test.supabase.co", _env_file=None),
        key_resolver=StaticKeyResolver(wrong_public_key),
    )

    with pytest.raises(AuthenticationError, match="invalid"):
        verifier.verify(make_token(private_key))


def test_rejects_non_https_remote_supabase_url() -> None:
    with pytest.raises(ValueError, match="HTTPS"):
        SupabaseTokenVerifier(
            Settings(supabase_url="http://zayron-test.supabase.co", _env_file=None)
        )


@pytest.mark.parametrize(
    "supabase_url",
    [
        "https://user@zayron-test.supabase.co",
        "https://user:password@zayron-test.supabase.co",
        "https://zayron-test.supabase.co/custom/path",
        "https://zayron-test.supabase.co?redirect=attacker.example",
        "https://zayron-test.supabase.co#fragment",
        "https://zayron-test.supabase.co:invalid",
    ],
)
def test_rejects_supabase_urls_that_are_not_clean_origins(supabase_url: str) -> None:
    with pytest.raises(ValueError):
        SupabaseTokenVerifier(Settings(supabase_url=supabase_url, _env_file=None))


def test_rejects_oversized_bearer_token(signing_keys) -> None:
    _, public_key, _ = signing_keys
    verifier = SupabaseTokenVerifier(
        Settings(supabase_url="https://zayron-test.supabase.co", _env_file=None),
        key_resolver=StaticKeyResolver(public_key),
    )

    with pytest.raises(AuthenticationError, match="maximum accepted size"):
        verifier.verify("a" * 16_385)


def test_rejects_token_with_future_issued_at(signing_keys) -> None:
    private_key, public_key, _ = signing_keys
    verifier = SupabaseTokenVerifier(
        Settings(supabase_url="https://zayron-test.supabase.co", _env_file=None),
        key_resolver=StaticKeyResolver(public_key),
    )

    future_iat = datetime.now(timezone.utc) + timedelta(minutes=5)
    with pytest.raises(AuthenticationError, match="invalid"):
        verifier.verify(make_token(private_key, iat=future_iat))


def test_rejects_wrong_audience(signing_keys) -> None:
    private_key, public_key, _ = signing_keys
    verifier = SupabaseTokenVerifier(
        Settings(supabase_url="https://zayron-test.supabase.co", _env_file=None),
        key_resolver=StaticKeyResolver(public_key),
    )

    with pytest.raises(AuthenticationError, match="invalid"):
        verifier.verify(make_token(private_key, aud="another-service"))
