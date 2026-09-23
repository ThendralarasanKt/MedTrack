from __future__ import annotations

from medtrack_gateway import config
from medtrack_gateway.store import STORE


def test_quota_is_per_account(client, auth, monkeypatch):
    monkeypatch.setattr(config.SETTINGS, "max_jobs_per_account_hour", 2)
    body = {
        "requestId": "req-q-1",
        "deviceId": "dev-ankita-001",
        "clientVersion": "1.0",
        "commandSchemaVersion": "care-commands-1",
        "purpose": "SINGLE_PATIENT_CAPTURE",
        "patientId": "pat-001",
        "admissionId": "adm-001",
        "contextDigest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "inputDigest": "sha256:q1",
        "contextManifest": {
            "snapshotAt": "2026-09-20T08:00:00+05:30",
            "selectionScope": "SINGLE_PATIENT",
            "records": [{"type": "Admission", "id": "adm-001", "version": 1}],
            "missing": [],
        },
        "sourceRefs": [{"kind": "TEXT", "ref": "d"}],
        "text": "note",
    }
    assert client.post("/v1/inference-jobs", headers=auth(), json=body).status_code == 201
    body2 = dict(body)
    body2["requestId"] = "req-q-2"
    body2["inputDigest"] = "sha256:q2"
    assert client.post("/v1/inference-jobs", headers=auth(), json=body2).status_code == 201
    body3 = dict(body)
    body3["requestId"] = "req-q-3"
    body3["inputDigest"] = "sha256:q3"
    third = client.post("/v1/inference-jobs", headers=auth(), json=body3)
    assert third.status_code == 429
    other = {
        "Authorization": "Bearer "
        + __import__("medtrack_gateway.auth", fromlist=["encode_dev_token"]).encode_dev_token(
            "medtrack-test", "synthetic-second", "dev-b"
        )
    }
    body4 = dict(body)
    body4["requestId"] = "req-q-other"
    body4["deviceId"] = "dev-b"
    body4["inputDigest"] = "sha256:qo"
    other_res = client.post("/v1/inference-jobs", headers=other, json=body4)
    assert other_res.status_code == 201


def test_route_disable(client, auth):
    client.post("/v1/ops/disable-route", headers=auth(), json={"route": "capture"})
    caps = client.get("/v1/capabilities", headers=auth())
    assert "capture" not in caps.json()["enabledRoutes"]
