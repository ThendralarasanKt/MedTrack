from __future__ import annotations

import time

from medtrack_gateway.store import STORE


def test_artifact_account_scoped(client, auth):
    other = {
        "Authorization": "Bearer "
        + __import__("medtrack_gateway.auth", fromlist=["encode_dev_token"]).encode_dev_token(
            "medtrack-test", "synthetic-second", "dev-b"
        )
    }
    created = client.post(
        "/v1/artifacts",
        headers=auth(),
        json={"contentBase64": "aGVsbG8=", "mimeType": "text/plain", "purpose": "input"},
    )
    assert created.status_code == 200
    art_id = created.json()["artifactId"]
    stolen = client.delete(f"/v1/artifacts/{art_id}", headers=other)
    assert stolen.status_code == 404
    deleted = client.delete(f"/v1/artifacts/{art_id}", headers=auth())
    assert deleted.json()["status"] == "DELETED"


def test_oversized_artifact_rejected(client, auth, monkeypatch):
    from medtrack_gateway import config

    monkeypatch.setattr(config.SETTINGS, "max_artifact_bytes", 4)
    res = client.post(
        "/v1/artifacts",
        headers=auth(),
        json={"contentBase64": "aGVsbG8=", "mimeType": "text/plain"},
    )
    assert res.status_code == 413


def test_expired_artifacts_removed(client, auth):
    created = client.post(
        "/v1/artifacts",
        headers=auth(),
        json={"contentBase64": "YWI=", "mimeType": "text/plain"},
    )
    art_id = created.json()["artifactId"]
    art = STORE.get_artifact("00000000-0000-4000-8000-000000000001", art_id)
    art.expires_at = time.time() - 10
    STORE.cleanup()
    gone = STORE.get_artifact("00000000-0000-4000-8000-000000000001", art_id)
    assert gone is not None
    assert gone.state == "DELETED"
    assert gone.payload == b""
