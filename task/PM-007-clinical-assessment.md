# PM-007: Clinical assessment objects

Status: Done | Priority: P1 | Depends on: PM-005  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

Encounter, ClinicalNote, Problem, Allergy, AllergyAssessment, Observation,
ClinicalDecision, CarePlanRevision (data contract §5).

## Acceptance

- [x] RecordEncounter writes encounter (+ optional note) with provenance
- [x] Problem code requires codeSystem; status/certainty changes create revisions
- [x] Empty allergies ≠ NONE_KNOWN without AllergyAssessment
- [x] Observation requires exactly one typed value
- [x] CommitClinicalDecision links problems; decision-maker not defaulted to recorder

## Delivery notes

Schema 9 + `MIGRATION_8_9`. Commands in `CareClinicalAssessmentService`.
Verified by `CareClinicalAssessmentTest`.
