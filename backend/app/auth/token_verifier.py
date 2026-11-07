from dataclasses import dataclass
from typing import Any, Protocol
from urllib.parse import urlparse

import jwt
from jwt import PyJWKClient

from app.core.config import Settings

SUPABASE_AUTH_AUDIENCE = "authenticated"
ALLOWED_SIGNING_ALGORITHMS = ("ES256", "RS256")


class AuthenticationError(Exception):
    """Raised when a bearer token cannot be safely authenticated."""


class SigningKeyResolver(Protocol):
    def get_signing_key_from_jwt(self, token: str) -> Any:
        """Resolve the trusted public signing key for a token's key ID."""
        ...


@dataclass(frozen=True)
class AuthenticatedUser:
    """Minimal verified identity; claims are retained for authorized use."""

    subject: str
    claims: dict[str, Any]


class SupabaseTokenVerifier:
    """Verify Supabase asymmetric JWTs against the project's published JWKS."""

    def __init__(
        self,
        settings: Settings,
        key_resolver: SigningKeyResolver | None = None,
    ) -> None:
        self._issuer = self._build_issuer(settings.supabase_url)
        self._jwks_client = key_resolver
        if self._jwks_client is None and self._issuer is not None:
            self._jwks_client = PyJWKClient(
                f"{self._issuer}/.well-known/jwks.json",
                timeout=5,
                cache_keys=True,
            )

    @staticmethod
    def _build_issuer(supabase_url: str | None) -> str | None:
        if not supabase_url or not supabase_url.strip():
            return None
        parsed = urlparse(supabase_url.strip())
        try:
            parsed.port  # Validate malformed ports before accepting the configured authority.
        except ValueError as exc:
            raise ValueError("Supabase URL contains an invalid port") from exc
        local_http = parsed.hostname in {"localhost", "127.0.0.1", "::1"}
        if (
            not parsed.hostname
            or parsed.username is not None
            or parsed.password is not None
            or parsed.path not in {"", "/"}
            or parsed.query
            or parsed.fragment
            or (parsed.scheme != "https" and not (local_http and parsed.scheme == "http"))
        ):
            raise ValueError(
                "Supabase URL must be a clean HTTPS origin "
                "(HTTP is allowed only for localhost)"
            )
        return f"{parsed.scheme}://{parsed.netloc}/auth/v1"

    def verify(self, token: str) -> AuthenticatedUser:
        """Return an identity only after signature and standard claims validate."""
        if not token or not token.strip():
            raise AuthenticationError("Bearer token is missing")
        if len(token) > 16_384:
            raise AuthenticationError("Bearer token exceeds the maximum accepted size")
        if self._issuer is None or self._jwks_client is None:
            raise AuthenticationError("Supabase token verification is not configured")

        try:
            signing_key = self._jwks_client.get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=list(ALLOWED_SIGNING_ALGORITHMS),
                audience=SUPABASE_AUTH_AUDIENCE,
                issuer=self._issuer,
                options={"require": ["exp", "iat", "sub"]},
            )
            subject = claims.get("sub")
            if not isinstance(subject, str) or not subject.strip():
                raise AuthenticationError("Token subject is invalid")
            return AuthenticatedUser(subject=subject, claims=claims)
        except AuthenticationError:
            raise
        except Exception as exc:
            # Do not leak token contents, key material, or provider internals.
            raise AuthenticationError("Bearer token is invalid or could not be verified") from exc
