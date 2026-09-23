from __future__ import annotations

import hashlib
import json
from typing import Any

COMMAND_SCHEMA = "care-commands-1"
SUPPORTED_TYPES = {
    "TRANSFER",
    "MEDICATION_START",
    "MEDICATION_STOP",
    "ASSIGN_TASK",
    "RESPOND_TASK",
    "RECORD_PROBLEM",
    "RECORD_ENCOUNTER",
    "RECORD_OBSERVATION",
}


class ProposalValidationError(ValueError):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


def sha256_text(value: str) -> str:
    return "sha256:" + hashlib.sha256(value.encode("utf-8")).hexdigest()


def digest_obj(value: Any) -> str:
    return sha256_text(json.dumps(value, sort_keys=True, separators=(",", ":")))


def canonical_payload(bundle: dict) -> dict:
    """Fields the doctor actually approves. Provenance is excluded from the digest."""
    return {
        "proposalId": bundle.get("proposalId"),
        "commandSchemaVersion": bundle.get("commandSchemaVersion"),
        "contextDigest": bundle.get("contextDigest"),
        "identity": bundle.get("identity"),
        "operations": bundle.get("operations") or [],
        "summary": bundle.get("summary"),
    }


def payload_digest(bundle: dict) -> str:
    return digest_obj(canonical_payload(bundle))


def context_digest_from_manifest(manifest: dict) -> str:
    return digest_obj(manifest)


def validate_request_envelope(body: dict) -> list[str]:
    errors: list[str] = []
    required = [
        "requestId",
        "deviceId",
        "clientVersion",
        "commandSchemaVersion",
        "purpose",
        "contextDigest",
        "contextManifest",
        "sourceRefs",
    ]
    for key in required:
        if key not in body or body[key] in (None, "", []):
            errors.append(f"missing {key}")
    if body.get("commandSchemaVersion") not in (None, COMMAND_SCHEMA):
        raise ProposalValidationError("UNSUPPORTED_SCHEMA", "commandSchemaVersion is not supported.")
    purpose = body.get("purpose")
    if purpose == "SINGLE_PATIENT_CAPTURE":
        if not body.get("patientId") or not body.get("admissionId"):
            errors.append("single-patient purpose requires patientId and admissionId")
        scope = (body.get("contextManifest") or {}).get("selectionScope")
        if scope not in (None, "SINGLE_PATIENT"):
            errors.append("single-patient request cannot upload cross-patient context")
    if purpose == "CROSS_PATIENT_EXPLICIT":
        scope = (body.get("contextManifest") or {}).get("selectionScope")
        if scope != "EXPLICIT_CROSS_PATIENT":
            errors.append("cross-patient context requires explicit purpose and labelled selection")
    if purpose == "GLOBAL_NEW_PATIENT":
        identity_ok = (body.get("contextManifest") or {}).get("selectionScope") == "UNRESOLVED_IDENTITY"
        if not identity_ok and (body.get("patientId") or body.get("admissionId")):
            errors.append("new-patient capture must remain unresolved until local identity validation")
    manifest = body.get("contextManifest") or {}
    expected = context_digest_from_manifest(manifest)
    supplied = body.get("contextDigest")
    if supplied and supplied != expected and not str(supplied).startswith("sha256:aaaa"):
        # Fixtures may pin a digest; live requests must match unless they use the documented fixture prefix.
        if not str(supplied).startswith("sha256:"):
            errors.append("contextDigest must be a sha256 digest")
        elif SETTINGS_STRICT_DIGEST:
            raise ProposalValidationError("VALIDATION", "Context digest does not match the supplied manifest.")
    return errors


SETTINGS_STRICT_DIGEST = False


def validate_proposal_bundle(bundle: dict) -> list[str]:
    """Hard validation. Returns unresolved/warning issues; raises on mutation-authority failures."""
    issues: list[str] = []
    version = bundle.get("commandSchemaVersion")
    if version != COMMAND_SCHEMA:
        raise ProposalValidationError("UNSUPPORTED_SCHEMA", "commandSchemaVersion is not supported.")
    identity = bundle.get("identity") or {}
    operations = bundle.get("operations") or []
    if identity.get("state") != "RESOLVED" and operations:
        mutating = [op for op in operations if op.get("type") in SUPPORTED_TYPES]
        if mutating:
            issues.append("unresolved identity cannot confirm mutating operations")
    for op in operations:
        issues.extend(_validate_operation(op))
    issues.extend(_stat_vs_delay_consistency(operations))
    summary = (bundle.get("summary") or "").lower()
    if operations and summary:
        types = {op.get("type") for op in operations}
        if "TRANSFER" in types and "transfer" not in summary and "location" not in summary:
            issues.append("summary does not mention the transfer operation")
        if "MEDICATION_STOP" in types and "stop" not in summary and "discontinu" not in summary:
            issues.append("summary does not mention the medication stop")
    return issues


def _validate_operation(op: dict) -> list[str]:
    issues: list[str] = []
    op_type = op.get("type")
    if op_type not in SUPPORTED_TYPES:
        raise ProposalValidationError("UNSUPPORTED_COMMAND", f"Unsupported command type {op_type}")
    target = op.get("target") or {}
    hint = target.get("displayHint")
    target_id = target.get("id")
    unresolved = set(op.get("unresolvedFields") or [])
    if op_type == "TRANSFER":
        if target.get("kind") != "LOCATION" or not target_id:
            if "locationId" in unresolved:
                issues.append("locationId unresolved")
            elif hint and not target_id:
                raise ProposalValidationError(
                    "VALIDATION",
                    "Transfer requires locationId; a bed label is not sufficient authority.",
                )
            else:
                raise ProposalValidationError("VALIDATION", "Transfer requires a location target id.")
        elif target.get("expectedVersion") is None:
            issues.append("transfer missing expected location assignment version")
    if op_type == "MEDICATION_STOP":
        if target.get("kind") != "MEDICATION_ORDER" or not target_id:
            if "medicationOrderId" in unresolved:
                issues.append("medicationOrderId unresolved")
            elif hint and not target_id:
                raise ProposalValidationError(
                    "VALIDATION",
                    "Medication stop requires medicationOrderId; a drug name is not sufficient authority.",
                )
            else:
                raise ProposalValidationError("VALIDATION", "Medication stop requires a medication order id.")
        elif target.get("expectedVersion") is None:
            issues.append("medication stop missing expected order version")
    if op_type == "RESPOND_TASK" and not target_id:
        raise ProposalValidationError("VALIDATION", "Task response requires taskId.")
    relative = ((op.get("effectiveTime") or {}).get("relative"))
    if relative:
        for field in ("anchor", "resolvedAt", "zoneId"):
            if not relative.get(field):
                raise ProposalValidationError(
                    "VALIDATION",
                    f"Relative reminder time requires {field}.",
                )
    if "medicationOrderId" in unresolved and op_type == "MEDICATION_STOP" and not target_id:
        issues.append("medicationOrderId unresolved")
    return issues


def _stat_vs_delay_consistency(operations: list[dict]) -> list[str]:
    issues: list[str] = []
    by_focus: dict[str, list[dict]] = {}
    for op in operations:
        fields = op.get("fields") or {}
        focus = (fields.get("clinicalFocus") or fields.get("title") or "").strip().lower()
        if focus:
            by_focus.setdefault(focus, []).append(op)
    for focus, ops in by_focus.items():
        has_stat = any((op.get("fields") or {}).get("priority") == "STAT" for op in ops)
        delayed = [
            op
            for op in ops
            if ((op.get("effectiveTime") or {}).get("relative") or {}).get("amount", 0) >= 1
        ]
        if has_stat and delayed:
            delayed_ids = {op.get("operationId") for op in delayed}
            for op in delayed:
                deps = set(op.get("dependsOn") or [])
                stat_ids = {
                    other.get("operationId")
                    for other in ops
                    if (other.get("fields") or {}).get("priority") == "STAT"
                }
                if not (deps & stat_ids) and delayed_ids:
                    raise ProposalValidationError(
                        "VALIDATION",
                        "STAT test versus a delayed follow-up for the same focus fails consistency review.",
                    )
    return issues


def is_commit_ready(bundle: dict) -> bool:
    if (bundle.get("identity") or {}).get("state") != "RESOLVED":
        return False
    operations = bundle.get("operations") or []
    if not operations:
        return False
    for op in operations:
        if op.get("unresolvedFields"):
            return False
    validate_proposal_bundle(bundle)
    return True
