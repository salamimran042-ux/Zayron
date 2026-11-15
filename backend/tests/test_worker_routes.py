from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.core.config import Settings
from app.main import app
from app.platform.worker_routes import ClaimRequest, CompleteRequest


def test_internal_worker_routes_require_separate_worker_secret():
    with TestClient(app) as client:
        app.state.settings = Settings(worker_token=SecretStr("worker-test-secret"), _env_file=None)
        missing = client.post("/internal/v1/jobs/claim", json={"worker_id": "worker-a"})
        invalid = client.post(
            "/internal/v1/jobs/claim",
            json={"worker_id": "worker-a"},
            headers={"Authorization": "Bearer wrong-secret"},
        )
    assert missing.status_code == 401
    assert invalid.status_code == 401


def test_worker_route_does_not_run_without_database():
    with TestClient(app) as client:
        app.state.settings = Settings(worker_token=SecretStr("worker-test-secret"), _env_file=None)
        response = client.post(
            "/internal/v1/jobs/claim",
            json={"worker_id": "worker-a"},
            headers={"Authorization": "Bearer worker-test-secret"},
        )
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "DATABASE_UNAVAILABLE"



def test_worker_claim_can_be_limited_to_document_jobs():
    request = ClaimRequest(worker_id="document-worker", job_types=["document_ingest"])
    assert request.job_types == ["document_ingest"]


def test_worker_complete_rejects_oversized_extracted_result():
    import pytest
    from pydantic import ValidationError

    with pytest.raises(ValidationError):
        CompleteRequest(
            worker_id="document-worker",
            result_payload={"extracted_text": "x" * 421_000},
        )


def test_worker_complete_accepts_bounded_extracted_result():
    request = CompleteRequest(
        worker_id="document-worker",
        result_payload={"extracted_text": "safe extracted text"},
    )
    assert request.result_payload["extracted_text"] == "safe extracted text"
