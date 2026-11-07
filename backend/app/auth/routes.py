from fastapi import APIRouter, Depends

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser

router = APIRouter(prefix="/v1", tags=["authentication"])


@router.get("/me")
async def get_me(user: AuthenticatedUser = Depends(get_current_user)) -> dict[str, str]:
    """Return a minimal identity derived only from verified JWT claims."""
    role = user.claims.get("role")
    response = {"id": user.subject}
    if isinstance(role, str):
        response["role"] = role
    return response
