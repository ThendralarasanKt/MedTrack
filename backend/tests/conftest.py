from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from medtrack_gateway.app import create_app
from medtrack_gateway.auth import DIRECTORY, encode_dev_token
from medtrack_gateway.firebase import reset_firebase_verifier
from medtrack_gateway.store import STORE


@pytest.fixture
def app():
    STORE.reset()
    DIRECTORY.reset_runtime()
    reset_firebase_verifier()
    return create_app()


@pytest.fixture
def client(app):
    return TestClient(app)


def token(subject: str = "synthetic-ankita", device: str = "dev-ankita-001") -> str:
    return encode_dev_token("medtrack-test", subject, device)


@pytest.fixture
def auth():
    def _auth(subject: str = "synthetic-ankita", device: str = "dev-ankita-001") -> dict[str, str]:
        return {"Authorization": f"Bearer {token(subject, device)}"}

    return _auth
