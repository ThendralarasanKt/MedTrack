from __future__ import annotations

import json
from pathlib import Path

from medtrack_gateway.proposals import (
    ProposalValidationError,
    context_digest_from_manifest,
    is_commit_ready,
    payload_digest,
    validate_proposal_bundle,
)

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "contracts" / "v1" / "fixtures"


def _load(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def test_success_proposal_is_commit_ready():
    bundle = _load("success.proposal.json")
    issues = validate_proposal_bundle(bundle)
    assert issues == []
    assert is_commit_ready(bundle)
    assert payload_digest(bundle).startswith("sha256:")


def test_label_only_transfer_rejected():
    bundle = _load("label-only-transfer.proposal.json")
    try:
        validate_proposal_bundle(bundle)
        raise AssertionError("label-only transfer must fail")
    except ProposalValidationError as exc:
        assert "locationId" in exc.message


def test_stat_versus_four_hours_fails_consistency():
    bundle = _load("stat-vs-four-hours.proposal.json")
    try:
        validate_proposal_bundle(bundle)
        raise AssertionError("inconsistent STAT vs delay must fail")
    except ProposalValidationError as exc:
        assert "STAT" in exc.message


def test_relative_time_requires_anchor_and_zone():
    bundle = _load("success.proposal.json")
    bundle["operations"].append(
        {
            "operationId": "op-rel",
            "type": "ASSIGN_TASK",
            "target": {"kind": "ADMISSION", "id": "adm-001"},
            "fields": {"admissionId": "adm-001", "title": "Check labs"},
            "effectiveTime": {"relative": {"amount": 4, "unit": "HOURS"}},
        }
    )
    try:
        validate_proposal_bundle(bundle)
        raise AssertionError("relative time without anchor must fail")
    except ProposalValidationError as exc:
        assert "anchor" in exc.message


def test_partial_input_is_not_commit_ready():
    bundle = _load("partial-input.proposal.json")
    issues = validate_proposal_bundle(bundle)
    assert any("unresolved" in i or "medication" in i for i in issues) or bundle["operations"][0]["unresolvedFields"]
    assert not is_commit_ready(bundle)


def test_context_digest_stable():
    manifest = {
        "snapshotAt": "2026-09-20T08:00:00+05:30",
        "records": [{"type": "Admission", "id": "adm-001", "version": 4}],
        "missing": [],
    }
    assert context_digest_from_manifest(manifest) == context_digest_from_manifest(dict(manifest))
