from __future__ import annotations

from medtrack_gateway.auth import encode_dev_token


def _job_body(request_id: str, digest: str, text: str = "hello"):
    return {
        "requestId": request_id,
        "deviceId": "dev-ankita-001",
        "clientVersion": "1.0",
        "commandSchemaVersion": "care-commands-1",
        "purpose": "SINGLE_PATIENT_CAPTURE",
        "patientId": "pat-001",
        "admissionId": "adm-001",
        "contextDigest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "inputDigest": digest,
        "contextManifest": {
            "snapshotAt": "2026-09-20T08:00:00+05:30",
            "selectionScope": "SINGLE_PATIENT",
            "records": [{"type": "Admission", "id": "adm-001", "version": 1}],
            "missing": [],
        },
        "sourceRefs": [{"kind": "TEXT", "ref": "draft-1"}],
        "text": text,
    }


def test_idempotent_same_digest(client, auth):
    body = _job_body("req-dup-1", "sha256:same")
    first = client.post("/v1/inference-jobs", headers=auth(), json=body)
    second = client.post("/v1/inference-jobs", headers=auth(), json=body)
    assert first.status_code == 201
    assert second.status_code == 200
    assert first.json()["jobId"] == second.json()["jobId"]


def test_conflict_different_digest(client, auth):
    first = client.post("/v1/inference-jobs", headers=auth(), json=_job_body("req-dup-2", "sha256:one"))
    assert first.status_code == 201
    second = client.post("/v1/inference-jobs", headers=auth(), json=_job_body("req-dup-2", "sha256:two"))
    assert second.status_code == 409
    assert second.json()["error"]["code"] == "CONFLICT"


def test_cancel_suppresses_result(client, auth):
    created = client.post(
        "/v1/inference-jobs",
        headers=auth(),
        json=_job_body("req-cancel-1", "sha256:c1"),
    )
    job_id = created.json()["jobId"]
    cancelled = client.post(f"/v1/inference-jobs/{job_id}/cancel", headers=auth())
    assert cancelled.json()["status"] == "CANCELLED"
    assert "result" not in cancelled.json()


def test_fixture_timeout(client, auth):
    headers = {**auth(), "X-MedTrack-Fixture": "timeout"}
    created = client.post("/v1/inference-jobs", headers=headers, json=_job_body("req-to-1", "sha256:t1"))
    assert created.json()["status"] == "FAILED"
    assert created.json()["errorCode"] == "TRANSIENT_PROVIDER"


def test_unsupported_schema(client, auth):
    body = _job_body("req-ver-1", "sha256:v1")
    body["commandSchemaVersion"] = "care-commands-0"
    res = client.post("/v1/inference-jobs", headers=auth(), json=body)
    assert res.status_code == 400
    assert res.json()["error"]["code"] == "UNSUPPORTED_SCHEMA"


def test_success_fixture_returns_proposal(client, auth):
    headers = {**auth(), "X-MedTrack-Fixture": "success"}
    created = client.post("/v1/inference-jobs", headers=headers, json=_job_body("req-ok-1", "sha256:ok"))
    result = created.json()["result"]
    assert result["kind"] == "proposal"
    assert result["proposal"]["operations"][0]["target"]["id"] == "loc-icu-04"
