from medtrack_gateway.adapters.orchestration import BoundedOrchestrator
from medtrack_gateway.orchestration import Coordinator, select_routes
from medtrack_gateway.orchestration.preflight import check_request
from medtrack_gateway.orchestration.clarification import needs_clarification


def test_gateway_and_coordinator_share_one_engine():
    engine = BoundedOrchestrator()
    result = engine.run(
        "job-1",
        {
            "requestId": "req-1",
            "text": "Transfer to loc-icu-04",
            "patientId": "pat-1",
            "admissionId": "adm-1",
            "dataClassification": "SYNTHETIC",
            "purpose": "SINGLE_PATIENT_CAPTURE",
            "contextDigest": "sha256:abc",
            "commandSchemaVersion": "care-commands-1",
        },
    )
    assert result["kind"] == "proposal"
    assert result["proposal"]["operations"]
    assert result["routing"]["primary"]["capability"]


def test_non_synthetic_fails_before_a_model_call():
    checked = check_request({"text": "hello", "dataClassification": "REAL"})
    assert checked["ok"] is False
    assert checked["error"]["code"] == "FORBIDDEN"


def test_multi_patient_clarifies_without_a_write():
    message = needs_clarification(
        {"contains_multiple_patient_contexts": {"noul": 0.95}},
        purpose="SINGLE_PATIENT_CAPTURE",
        identity_resolved=True,
    )
    assert message
    routed = select_routes(
        {
            "version": "routing-v1",
            "rules": [
                {
                    "id": "multi",
                    "route": "CLARIFICATION",
                    "reason": "Multiple patients.",
                    "when": {"noul_gte": ["contains_multiple_patient_contexts", 0.8]},
                }
            ],
            "modelRouteMap": {"CLARIFICATION": None},
        },
        answers={"contains_multiple_patient_contexts": {"noul": 0.95}},
        purpose="SINGLE_PATIENT_CAPTURE",
        identity_resolved=True,
        text="Rao and Sharma",
    )
    assert routed["primary"]["capability"] == "CLARIFICATION"
    result = Coordinator().run(
        "job-2",
        {
            "requestId": "req-2",
            "text": "patient Rao and patient Sharma",
            "dataClassification": "SYNTHETIC",
            "purpose": "SINGLE_PATIENT_CAPTURE",
        },
    )
    assert result["kind"] == "clarification"
    assert result["proposal"]["operations"] == []
