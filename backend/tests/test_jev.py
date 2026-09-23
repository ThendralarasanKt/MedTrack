from __future__ import annotations

from medtrack_gateway.adapters import MockDecisionService, parse_decision_response, DecisionError, DecisionRequest, QUESTION_SET_V1


def test_noul_not_confused_with_choice_confidence():
    payload = {
        "answers": {
            "requests_location_change": {"noul": 0.91},
            "statement_kind": {"choice": "recommendation", "probabilities": {"recommendation": 0.8}, "confidence": 0.8},
        }
    }
    parsed = parse_decision_response(payload)
    assert "confidence" not in parsed["requests_location_change"]
    assert parsed["statement_kind"]["confidence"] == 0.8
    assert parsed["requests_location_change"]["noul"] == 0.91


def test_chat_payload_rejected():
    try:
        parse_decision_response({"choices": [{"message": {"content": "hi"}}]})
        raise AssertionError("chat payload must not parse as a decision")
    except DecisionError as exc:
        assert exc.code == "MALFORMED_RESPONSE"


def test_absent_noul_is_unavailable_not_false():
    parsed = parse_decision_response({"answers": {"q": {"noul": None}}})
    assert parsed["q"].get("unavailable") is True
    assert parsed["q"]["noul"] is None


def test_mock_decision_runs_offline():
    service = MockDecisionService()
    envelope = service.decide(
        DecisionRequest(
            job_id="job-1",
            stage="intent",
            input_id="in-1",
            state={"text": "Transfer the patient and stop ceftriaxone"},
            question_set_version=QUESTION_SET_V1["version"],
            questions=QUESTION_SET_V1["questions"],
        )
    )
    assert envelope.answers["requests_location_change"]["noul"] > 0.8
    assert envelope.answers["contains_medication_change"]["noul"] > 0.8
    # Confidence is not write authority; this test only checks routing features exist.
    assert envelope.model == "mock-jev"
