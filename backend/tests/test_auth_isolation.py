from __future__ import annotations

from medtrack_gateway.auth import DIRECTORY, encode_dev_token
from medtrack_gateway.store import STORE


def _job_body(request_id: str, digest: str = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"):
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
        "text": "Transfer to loc-icu-04.",
    }


def test_unauthenticated(client):
    res = client.get("/v1/capabilities")
    assert res.status_code == 401
    assert res.json()["error"]["code"] == "UNAUTHENTICATED"


def test_expired_token(client):
    token = encode_dev_token("medtrack-test", "synthetic-ankita", exp=1)
    res = client.get("/v1/capabilities", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 401


def test_unprovisioned(client):
    token = encode_dev_token("medtrack-test", "stranger")
    res = client.get("/v1/capabilities", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 403
    assert res.json()["error"]["code"] == "UNPROVISIONED"


def test_disabled_membership(client, auth):
    DIRECTORY.disable_membership("00000000-0000-4000-8000-000000000001", "user-ankita")
    res = client.get("/v1/capabilities", headers=auth())
    assert res.status_code == 403


def test_revoked_device(client, auth):
    DIRECTORY.revoke_device("00000000-0000-4000-8000-000000000001", "dev-ankita-001")
    res = client.get("/v1/capabilities", headers=auth())
    assert res.status_code == 403


def test_two_accounts_cannot_read_each_others_jobs(client):
    a = {"Authorization": f"Bearer {encode_dev_token('medtrack-test', 'synthetic-ankita', 'dev-a')}"}
    b = {"Authorization": f"Bearer {encode_dev_token('medtrack-test', 'synthetic-second', 'dev-b')}"}
    created = client.post("/v1/inference-jobs", headers=a, json=_job_body("req-iso-1"))
    assert created.status_code == 201
    job_id = created.json()["jobId"]
    stolen = client.get(f"/v1/inference-jobs/{job_id}", headers=b)
    assert stolen.status_code == 404
    forged = client.post(
        "/v1/inference-jobs",
        headers=b,
        json={**_job_body("req-iso-2"), "accountId": "00000000-0000-4000-8000-000000000001"},
    )
    assert forged.status_code == 201
    other_job = forged.json()["jobId"]
    assert STORE.get_job("00000000-0000-4000-8000-000000000001", other_job) is None
    assert STORE.get_job("00000000-0000-4000-8000-000000000901", other_job) is not None


def test_similar_patient_ids_do_not_leak_cache(client):
    a = {"Authorization": f"Bearer {encode_dev_token('medtrack-test', 'synthetic-ankita', 'dev-a')}"}
    b = {"Authorization": f"Bearer {encode_dev_token('medtrack-test', 'synthetic-second', 'dev-b')}"}
    STORE.cache_put("00000000-0000-4000-8000-000000000001", "pat-001", "ankita-secret")
    assert STORE.cache_get("00000000-0000-4000-8000-000000000901", "pat-001") is None
    art = client.post(
        "/v1/artifacts",
        headers=a,
        json={"contentBase64": "YWI=", "digest": None, "mimeType": "text/plain", "purpose": "input"},
    )
    art_id = art.json()["artifactId"]
    other = client.get  # noqa: F841
    stolen = client.delete(f"/v1/artifacts/{art_id}", headers=b)
    assert stolen.status_code == 404


def test_provisioning_is_data_not_name_branch():
    subjects = {row["authSubject"] for row in DIRECTORY._rows}
    assert "synthetic-ankita" in subjects
    assert "synthetic-second" in subjects
