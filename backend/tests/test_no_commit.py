from __future__ import annotations

from fastapi.testclient import TestClient

from medtrack_gateway.app import create_app


def test_router_has_no_clinical_commit_path():
    app = create_app()
    paths = []
    for route in app.routes:
        path = getattr(route, "path", "")
        paths.append(path)
        assert "commit" not in path.lower()
        assert "write-path" not in path.lower()
        assert "care-command" not in path.lower()
    assert "/v1/inference-jobs" in paths
    assert "/v1/capabilities" in paths
    client = TestClient(app)
    res = client.post("/v1/commit-proposal")
    assert res.status_code in (401, 404, 405)
