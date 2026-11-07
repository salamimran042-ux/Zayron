import asyncio

from fastapi import Depends, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.auth.token_verifier import AuthenticationError, AuthenticatedUser, SupabaseTokenVerifier

bearer_scheme = HTTPBearer(auto_error=False)


async def get_current_user(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> AuthenticatedUser:
    """Authenticate a bearer token, failing closed when verification is unavailable."""
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=401,
            detail="Bearer authentication is required",
            headers={"WWW-Authenticate": "Bearer"},
        )

    verifier: SupabaseTokenVerifier | None = getattr(
        request.app.state, "token_verifier", None
    )
    if verifier is None:
        raise HTTPException(
            status_code=401,
            detail="Authentication is unavailable",
            headers={"WWW-Authenticate": "Bearer"},
        )

    try:
        return await asyncio.to_thread(verifier.verify, credentials.credentials)
    except AuthenticationError as exc:
        raise HTTPException(
            status_code=401,
            detail="Invalid authentication credentials",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc
