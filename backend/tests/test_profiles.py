from __future__ import annotations

from medtrack_gateway.auth import encode_dev_token
from medtrack_gateway.profiles import PROFILES


def test_bootstrap_is_idempotent(client, auth):
    first = client.post("/v1/me/bootstrap", headers=auth(), json={"displayName": "Dr. Ankita"})
    second = client.post("/v1/me/bootstrap", headers=auth(), json={"displayName": "Should Not Replace"})
    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json()["profile"]["profileId"] == second.json()["profile"]["profileId"]
    assert second.json()["created"] is False
    assert second.json()["profile"]["displayName"] == "Synthetic Ankita"
    assert second.json()["access"]["entitlement"]["label"] == "Pilot access"


def test_unprovisioned_subject_gets_profile_without_inference(client):
    token = encode_dev_token("medtrack-test", "stranger-profile")
    headers = {"Authorization": f"Bearer {token}"}
    boot = client.post("/v1/me/bootstrap", headers=headers, json={"displayName": "Dr Stranger"})
    assert boot.status_code == 200
    body = boot.json()
    assert body["provisioningState"] == "ACCESS_PENDING"
    assert body["access"]["entitlement"]["accessState"] != "AUTHORIZED"
    job = client.post(
        "/v1/inference-jobs",
        headers=headers,
        json={
            "requestId": "req-denied",
            "deviceId": "dev-x",
            "clientVersion": "1.0",
            "commandSchemaVersion": "care-commands-1",
            "purpose": "SINGLE_PATIENT_CAPTURE",
            "contextDigest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "inputDigest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "contextManifest": {"snapshotAt": "2026-09-20T08:00:00+05:30", "selectionScope": "UNRESOLVED_IDENTITY", "records": [], "missing": []},
            "sourceRefs": [{"kind": "TEXT", "ref": "x"}],
            "text": "note",
        },
    )
    assert job.status_code == 403
    other = client.get("/v1/me/profile", headers={"Authorization": f"Bearer {encode_dev_token('medtrack-test', 'synthetic-ankita')}"})
    assert other.json()["accountId"] != body["profile"]["accountId"]


def test_profile_patch_rejects_subscription_fields(client, auth):
    profile = client.get("/v1/me/profile", headers=auth()).json()
    denied = client.patch(
        "/v1/me/profile",
        headers=auth(),
        json={"expectedVersion": profile["version"], "operationId": "op-sub", "isSubscriber": True},
    )
    assert denied.status_code == 400
    assert denied.json()["error"]["code"] == "FORBIDDEN_FIELD"


def test_profile_specialties_and_affiliations(client, auth):
    profile = client.get("/v1/me/profile", headers=auth()).json()
    saved = client.patch(
        "/v1/me/profile",
        headers=auth(),
        json={
            "expectedVersion": profile["version"],
            "operationId": "op-prof-1",
            "displayName": "Dr. Ankita Patel",
            "professionCode": "physician",
            "specialties": [
                {"specialtyId": "spec-nephrology", "isPrimary": True},
                {"reportedSpecialtyText": "Transplant", "isPrimary": False},
            ],
            "affiliations": [
                {
                    "hospitalId": "hosp-city",
                    "departmentName": "Nephrology",
                    "jobTitle": "Consultant",
                    "isPrimary": True,
                },
                {
                    "reportedHospitalName": "Home clinic",
                    "reportedCity": "Mumbai",
                    "jobTitle": "Visiting",
                    "isPrimary": False,
                },
            ],
        },
    )
    assert saved.status_code == 200, saved.text
    body = saved.json()
    assert body["displayName"] == "Dr. Ankita Patel"
    assert sum(1 for item in body["specialties"] if item["isPrimary"]) == 1
    replay = client.patch(
        "/v1/me/profile",
        headers=auth(),
        json={"expectedVersion": profile["version"], "operationId": "op-prof-1", "displayName": "ignored"},
    )
    assert replay.json()["displayName"] == "Dr. Ankita Patel"


def test_cannot_read_another_account_profile(client):
    a = {"Authorization": f"Bearer {encode_dev_token('medtrack-test', 'synthetic-ankita')}"}
    b = {"Authorization": f"Bearer {encode_dev_token('medtrack-test', 'synthetic-second')}"}
    ankita = client.get("/v1/me/profile", headers=a).json()
    other = client.get("/v1/me/profile", headers=b).json()
    assert ankita["accountId"] != other["accountId"]
    assert other["userId"] != ankita["userId"]


def test_catalogues_do_not_include_affiliations(client, auth):
    hospitals = client.get("/v1/catalogues/hospitals", headers=auth()).json()["items"]
    assert all("affiliations" not in row for row in hospitals)
    assert any(row["name"] == "City Hospital" for row in hospitals)


def test_cached_grant_revocation_blocks_inference(client, auth):
    PROFILES.revoke_grants("00000000-0000-4000-8000-000000000001")
    access = client.get("/v1/me/access", headers=auth()).json()
    assert access["entitlement"]["accessState"] != "AUTHORIZED"
    job = client.post(
        "/v1/inference-jobs",
        headers=auth(),
        json={
            "requestId": "req-revoked",
            "deviceId": "dev-ankita-001",
            "clientVersion": "1.0",
            "commandSchemaVersion": "care-commands-1",
            "purpose": "SINGLE_PATIENT_CAPTURE",
            "contextDigest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "inputDigest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "contextManifest": {"snapshotAt": "2026-09-20T08:00:00+05:30", "selectionScope": "UNRESOLVED_IDENTITY", "records": [], "missing": []},
            "sourceRefs": [{"kind": "TEXT", "ref": "x"}],
            "text": "note",
        },
    )
    assert job.status_code == 403


def test_two_primary_specialties_rejected(client, auth):
    profile = client.get("/v1/me/profile", headers=auth()).json()
    denied = client.patch(
        "/v1/me/profile",
        headers=auth(),
        json={
            "expectedVersion": profile["version"],
            "operationId": "op-two-primary",
            "specialties": [
                {"specialtyId": "spec-nephrology", "isPrimary": True},
                {"specialtyId": "spec-cardiology", "isPrimary": True},
            ],
        },
    )
    assert denied.status_code == 400
    assert denied.json()["error"]["code"] == "VALIDATION"
