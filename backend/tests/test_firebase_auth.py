from __future__ import annotations

import time
from pathlib import Path

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient

from medtrack_gateway.auth import DIRECTORY, IdentityDirectory, encode_dev_token
from medtrack_gateway.config import Settings, validate_runtime_settings
from medtrack_gateway.firebase import FirebaseTokenVerifier, set_firebase_verifier
from medtrack_gateway.identity_store import IdentityStore

PROJECT = "medtrack-6497f"
ISSUER = f"https://securetoken.google.com/{PROJECT}"
ACCOUNT = "00000000-0000-4000-8000-000000000001"


def _rsa_pair():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return key, key.public_key()


def _firebase_token(private_key, *, kid="test-kid", sub="firebase-ankita", aud=PROJECT, iss=ISSUER, exp=None, iat=None):
    now = int(time.time())
    payload = {
        "iss": iss,
        "aud": aud,
        "sub": sub,
        "exp": exp if exp is not None else now + 3600,
        "iat": iat if iat is not None else now,
    }
    return jwt.encode(payload, private_key, algorithm="RS256", headers={"kid": kid})


@pytest.fixture
def signed_firebase(client):
    private_key, public_key = _rsa_pair()
    set_firebase_verifier(
        FirebaseTokenVerifier(PROJECT, static_keys={"test-kid": public_key}, fetch_jwks=False)
    )
    original_rows = list(DIRECTORY._rows)
    DIRECTORY._rows.append(
        {
            "authIssuer": ISSUER,
            "authSubject": "firebase-ankita",
            "accountId": ACCOUNT,
            "userId": "user-ankita",
            "displayName": "Firebase Ankita",
            "role": "OWNER",
        }
    )
    try:
        yield private_key
    finally:
        DIRECTORY._rows = original_rows


def test_unsigned_jwt_is_rejected(client):
    token = jwt.encode(
        {"iss": ISSUER, "aud": PROJECT, "sub": "firebase-ankita", "exp": int(time.time()) + 3600, "iat": int(time.time())},
        "secret",
        algorithm="HS256",
    )
    res = client.get("/v1/capabilities", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 401
    assert res.json()["error"]["code"] == "UNAUTHENTICATED"


def test_wrong_audience_is_rejected(signed_firebase, client):
    token = _firebase_token(signed_firebase, aud="other-project")
    res = client.get("/v1/capabilities", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 401


def test_expired_firebase_token_is_rejected(signed_firebase, client):
    token = _firebase_token(signed_firebase, exp=int(time.time()) - 120, iat=int(time.time()) - 180)
    res = client.get("/v1/capabilities", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 401


def test_test_issuer_on_jwt_path_is_rejected(signed_firebase, client):
    token = _firebase_token(signed_firebase, iss="medtrack-test")
    res = client.get("/v1/capabilities", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 401


def test_valid_signed_firebase_token_is_accepted(signed_firebase, client):
    token = _firebase_token(signed_firebase)
    res = client.get("/v1/capabilities", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 200
    body = res.json()
    assert body["accountId"] == ACCOUNT
    assert body["authSubject"] == "firebase-ankita"
    assert body["userId"] == "user-ankita"
    assert body["commitEndpoint"] is None


def test_production_rejects_dev_authentication_setting():
    with pytest.raises(RuntimeError, match="Development authentication"):
        validate_runtime_settings(Settings(env="production", allow_dev_tokens=True))


def test_revocation_survives_directory_reload(tmp_path: Path, client, auth):
    path = tmp_path / "identity.sqlite"
    first = IdentityDirectory(IdentityStore(path))
    first.revoke_device(ACCOUNT, "dev-ankita-001")
    second = IdentityDirectory(IdentityStore(path))
    original = DIRECTORY._store
    DIRECTORY._store = second._store
    try:
        res = client.get("/v1/capabilities", headers=auth())
        assert res.status_code == 403
    finally:
        DIRECTORY._store = original


def test_second_device_requires_explicit_replacement(client, auth):
    first = client.post("/v1/devices", headers=auth(), json={"deviceId": "dev-ankita-001"})
    assert first.status_code == 200
    blocked = client.post("/v1/devices", headers=auth("synthetic-ankita", "dev-other"), json={"deviceId": "dev-other"})
    assert blocked.status_code == 409
    assert blocked.json()["error"]["code"] == "DEVICE_REPLACEMENT_REQUIRED"
    replaced = client.post(
        "/v1/devices",
        headers=auth("synthetic-ankita", "dev-other"),
        json={"deviceId": "dev-other", "replaceExisting": True},
    )
    assert replaced.status_code == 200
    old = client.get(
        "/v1/capabilities",
        headers={"Authorization": f"Bearer {encode_dev_token('medtrack-test', 'synthetic-ankita', 'dev-ankita-001')}"},
    )
    assert old.status_code == 403
