from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException, Request, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field, SecretStr
from app.auth.dependencies import get_current_user
from app.auth.supabase_auth import SupabaseAuthClient, SupabaseAuthError
from app.auth.token_verifier import AuthenticatedUser

router = APIRouter(prefix="/api/v1", tags=["authentication"])
bearer_scheme = HTTPBearer(auto_error=False)

class LoginRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)
    password: SecretStr = Field(min_length=1, max_length=1024)

class RefreshRequest(BaseModel):
    refresh_token: SecretStr = Field(min_length=1, max_length=16_384)

class RegistrationRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)
    password: SecretStr = Field(min_length=8, max_length=1024)
    display_name: str | None = Field(default=None, max_length=80)

class PasswordResetRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)

def _auth_client(request: Request) -> SupabaseAuthClient:
    client = getattr(request.app.state, "supabase_auth", None)
    if client is None:
        raise HTTPException(503, detail={"code": "AUTH_SERVICE_UNAVAILABLE"})
    return client

def _provider_error(exc: SupabaseAuthError, *, refreshing: bool = False) -> HTTPException:
    if exc.status_code in {400, 401}:
        code = "SESSION_EXPIRED" if refreshing else "INVALID_CREDENTIALS"
        return HTTPException(401, detail={"code": code})
    if exc.status_code == 429:
        return HTTPException(429, detail={"code": "RATE_LIMITED"})
    return HTTPException(503, detail={"code": "AUTH_SERVICE_UNAVAILABLE"})


@router.post("/auth/register", status_code=202)
async def register(body: RegistrationRequest, request: Request) -> dict:
    """Create an account; Supabase controls email verification and session issuance."""
    email = body.email.strip()
    if "@" not in email or any(character.isspace() for character in email):
        raise HTTPException(422, detail={"code": "INVALID_EMAIL"})
    try:
        return await _auth_client(request).sign_up(
            email, body.password.get_secret_value(), body.display_name
        )
    except SupabaseAuthError as exc:
        raise _provider_error(exc) from exc


@router.post("/auth/password-reset", status_code=202)
async def password_reset(body: PasswordResetRequest, request: Request) -> dict:
    """Request a reset email without revealing whether the account exists."""
    email = body.email.strip()
    if "@" not in email or any(character.isspace() for character in email):
        raise HTTPException(422, detail={"code": "INVALID_EMAIL"})
    try:
        await _auth_client(request).send_password_reset(email)
    except SupabaseAuthError as exc:
        raise _provider_error(exc) from exc
    return {"status": "accepted"}


@router.post("/auth/login")
async def login(body: LoginRequest, request: Request) -> dict:
    """Exchange credentials with Supabase; never persist or log the password."""
    try:
        result = await _auth_client(request).sign_in(body.email.strip(), body.password.get_secret_value())
        return result.response()
    except SupabaseAuthError as exc:
        raise _provider_error(exc) from exc

@router.post("/auth/refresh")
async def refresh(body: RefreshRequest, request: Request) -> dict:
    """Exchange a refresh token and return the provider-rotated token pair."""
    try:
        result = await _auth_client(request).refresh(body.refresh_token.get_secret_value())
        return result.response()
    except SupabaseAuthError as exc:
        raise _provider_error(exc, refreshing=True) from exc

@router.post("/auth/logout", status_code=204)
async def logout(request: Request,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)]) -> Response:
    """Revoke the current Supabase session using its access token."""
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(401, detail={"code": "AUTH_REQUIRED"},
                            headers={"WWW-Authenticate": "Bearer"})
    try:
        await _auth_client(request).sign_out(credentials.credentials)
    except SupabaseAuthError as exc:
        raise _provider_error(exc, refreshing=True) from exc
    return Response(status_code=204)

@router.get("/me")
async def get_me(user: AuthenticatedUser = Depends(get_current_user)) -> dict[str, str]:
    role = user.claims.get("role")
    response = {"id": user.subject}
    if isinstance(role, str):
        response["role"] = role
    return response
