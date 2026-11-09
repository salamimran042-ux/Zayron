from __future__ import annotations

from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.auth.dependencies import get_current_user
from app.auth.token_verifier import AuthenticatedUser

router = APIRouter(prefix="/api/v1", tags=["chat"])


class ChatMessageInput(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=100_000)


class ChatStreamInput(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(alias="requestId", min_length=1, max_length=128)
    conversation_id: UUID | None = Field(default=None, alias="conversationId")
    messages: list[ChatMessageInput] = Field(min_length=1, max_length=100)

    @model_validator(mode="after")
    def require_user_turn_last(self) -> "ChatStreamInput":
        if self.messages[-1].role != "user":
            raise ValueError("The final chat message must be from the user.")
        return self


@router.post("/chat/stream")
async def stream_chat(
    body: ChatStreamInput,
    user: AuthenticatedUser = Depends(get_current_user),
):
    """Authenticated chat boundary; deliberately fails closed until an AI provider is configured.

    The request is validated but not persisted or sent to a third party here.
    This prevents the Android client from mistaking a placeholder for a real AI reply.
    """
    del body, user
    return JSONResponse(
        status_code=503,
        content={
            "code": "SERVICE_UNAVAILABLE",
            "message": "The Zayron chat model provider is not configured.",
        },
    )
