from dataclasses import dataclass
from typing import Any
from urllib.parse import urlparse
import time

import httpx

from app.core.config import Settings


class SupabaseAuthError(Exception):
    """Provider failure without retaining credentials or response bodies."""

    def __init__(self, status_code: int, provider_code: str | None = None) -> None:
        self.status_code = status_code
        self.provider_code = provider_code
        super().__init__("Supabase Auth request failed")


@dataclass(frozen=True)
class AuthTokens:
    access_token: str
    refresh_token: str
    expires_at: int
    user_id: str
    email: str
    display_name: str | None

    def response(self) -> dict[str, Any]:
        return {
            "access_token": self.access_token,
            "refresh_token": self.refresh_token,
            "expires_at": self.expires_at,
            "user": {
                "id": self.user_id,
                "email": self.email,
                "display_name": self.display_name,
            },
        }


class SupabaseAuthClient:
    """Server-side adapter for Supabase Auth password, refresh and logout flows."""

    def __init__(
        self,
        supabase_url: str,
        publishable_key: str,
        *,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        base_url = self._validated_base_url(supabase_url)
        self._client = httpx.AsyncClient(
            base_url=base_url,
            headers={"apikey": publishable_key, "Accept": "application/json"},
            timeout=httpx.Timeout(12.0, connect=5.0),
            transport=transport,
        )

    @staticmethod
    def _validated_base_url(supabase_url: str) -> str:
        """Accept only a clean HTTPS origin (HTTP is limited to local development)."""
        if not isinstance(supabase_url, str) or not supabase_url.strip():
            raise ValueError("Supabase URL must be a clean HTTPS origin")
        parsed = urlparse(supabase_url.strip())
        try:
            parsed.port
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
        return f"{parsed.scheme}://{parsed.netloc}/auth/v1/"

    @classmethod
    def from_settings(cls, settings: Settings) -> "SupabaseAuthClient | None":
        publishable_key = settings.effective_supabase_publishable_key
        if not settings.supabase_url or not publishable_key:
            return None
        return cls(
            settings.supabase_url,
            publishable_key.get_secret_value(),
        )

    async def close(self) -> None:
        await self._client.aclose()

    async def sign_in(self, email: str, password: str) -> AuthTokens:
        payload = await self._post(
            "token",
            params={"grant_type": "password"},
            json={"email": email, "password": password},
        )
        return self._parse_tokens(payload)

    async def sign_up(self, email: str, password: str, display_name: str | None = None) -> dict[str, Any]:
        metadata = {"full_name": display_name.strip()} if display_name and display_name.strip() else {}
        payload = await self._post("signup", json={"email": email, "password": password, "data": metadata})
        user = payload.get("user") if isinstance(payload.get("user"), dict) else payload
        user_id, user_email = user.get("id"), user.get("email")
        if not isinstance(user_id, str) or not user_id or not isinstance(user_email, str):
            raise SupabaseAuthError(502)
        has_session = bool(payload.get("access_token")) and bool(payload.get("refresh_token"))
        session = self._parse_tokens(payload).response() if has_session else None
        return {"confirmation_required": session is None,
                "user": {"id": user_id, "email": user_email}, "session": session}

    async def send_password_reset(self, email: str) -> None:
        await self._post("recover", json={"email": email})

    async def refresh(self, refresh_token: str) -> AuthTokens:
        payload = await self._post(
            "token",
            params={"grant_type": "refresh_token"},
            json={"refresh_token": refresh_token},
        )
        return self._parse_tokens(payload)

    async def sign_out(self, access_token: str) -> None:
        if not access_token or len(access_token) > 16_384:
            raise SupabaseAuthError(401)
        try:
            response = await self._client.post(
                "logout",
                headers={"Authorization": f"Bearer {access_token}"},
            )
        except httpx.HTTPError as exc:
            raise SupabaseAuthError(503) from exc
        if response.status_code >= 400:
            raise SupabaseAuthError(response.status_code)

    async def _post(self, path: str, **kwargs: Any) -> dict[str, Any]:
        try:
            response = await self._client.post(path, **kwargs)
        except httpx.HTTPError as exc:
            raise SupabaseAuthError(503) from exc
        if response.status_code >= 400:
            code = None
            try:
                body = response.json()
                candidate = body.get("code") or body.get("error_code")
                if isinstance(candidate, str):
                    code = candidate[:80]
            except (ValueError, AttributeError):
                pass
            raise SupabaseAuthError(response.status_code, code)
        try:
            payload = response.json()
        except ValueError as exc:
            raise SupabaseAuthError(502) from exc
        if not isinstance(payload, dict):
            raise SupabaseAuthError(502)
        return payload

    @staticmethod
    def _parse_tokens(payload: dict[str, Any]) -> AuthTokens:
        access, refresh, user = (
            payload.get("access_token"),
            payload.get("refresh_token"),
            payload.get("user"),
        )
        if not isinstance(access, str) or not access or len(access) > 16_384:
            raise SupabaseAuthError(502)
        if not isinstance(refresh, str) or not refresh or len(refresh) > 16_384:
            raise SupabaseAuthError(502)
        if not isinstance(user, dict):
            raise SupabaseAuthError(502)
        user_id, email = user.get("id"), user.get("email")
        if not isinstance(user_id, str) or not user_id or len(user_id) > 128:
            raise SupabaseAuthError(502)
        if not isinstance(email, str) or not email or len(email) > 320:
            raise SupabaseAuthError(502)
        expiry, duration = payload.get("expires_at"), payload.get("expires_in")
        if isinstance(expiry, (int, float)) and expiry > 0:
            expires_at = int(expiry)
        elif isinstance(duration, (int, float)) and 0 < duration <= 86_400:
            expires_at = int(time.time()) + int(duration)
        else:
            raise SupabaseAuthError(502)
        metadata = user.get("user_metadata")
        name = (
            metadata.get("full_name") or metadata.get("name")
            if isinstance(metadata, dict)
            else None
        )
        display_name = name.strip()[:120] if isinstance(name, str) and name.strip() else None
        return AuthTokens(access, refresh, expires_at, user_id, email, display_name)
