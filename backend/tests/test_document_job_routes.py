from uuid import UUID

from app.platform.document_job_routes import (
    StoredAttachment,
    _attachment_path_is_owned,
    _find_attachment,
)


OWNER = UUID("2e4f8a10-6a1b-4c2d-9e3f-123456789abc")
ATTACHMENT_ID = UUID("d4f1b2c3-4a5e-6789-abcd-ef0123456789")


def _reference(**overrides):
    value = {
        "id": str(ATTACHMENT_ID),
        "display_name": "research.pdf",
        "mime_type": "application/pdf",
        "size_bytes": 4096,
        "storage_path": f"{OWNER}/0123456789abcdef0123456789abcdef.pdf",
    }
    value.update(overrides)
    return value


def test_document_job_finds_only_requested_attachment_in_message_metadata():
    found = _find_attachment({"attachments": [_reference()]}, ATTACHMENT_ID)
    assert found is not None
    assert found.display_name == "research.pdf"
    assert found.mime_type == "application/pdf"


def test_document_job_does_not_find_attachment_not_in_message():
    assert _find_attachment({"attachments": [_reference()]}, UUID(int=7)) is None


def test_document_job_rejects_malformed_or_oversized_attachment_metadata():
    assert _find_attachment({"attachments": "not-a-list"}, ATTACHMENT_ID) is None
    assert _find_attachment(
        {"attachments": [_reference(size_bytes=20 * 1024 * 1024)]},
        ATTACHMENT_ID,
    ) is None


def test_document_job_requires_owner_scoped_server_generated_path():
    owned = StoredAttachment.model_validate(_reference())
    other_user = StoredAttachment.model_validate(
        _reference(storage_path=f"{UUID(int=9)}/0123456789abcdef0123456789abcdef.pdf")
    )
    malformed = StoredAttachment.model_validate(_reference(storage_path="../private.pdf"))
    assert _attachment_path_is_owned(owned, OWNER)
    assert not _attachment_path_is_owned(other_user, OWNER)
    assert not _attachment_path_is_owned(malformed, OWNER)


def test_document_job_rejects_extension_mismatch():
    wrong_extension = StoredAttachment.model_validate(
        _reference(storage_path=f"{OWNER}/0123456789abcdef0123456789abcdef.txt")
    )
    assert not _attachment_path_is_owned(wrong_extension, OWNER)
