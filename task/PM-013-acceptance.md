# PM-013: Model and workflow acceptance

Status: Done | Priority: P0 | Depends on: PM-011, PM-012  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

Acceptance coverage for the data-model spine: admissions, provenance, bridge
rules, clinical assessment, therapy, referrals, tasks/reminders, census, and
single write-path.

## Acceptance

- [x] Care unit suite green (`CareModelFoundationTest`, `CareProvenanceTest`,
  `CareLegacyBridgeTest`, `CareClinicalAssessmentTest`, `CareTherapyCommandTest`,
  `CareWorkAndAcceptanceTest`)
- [x] Schema export at version 11 with migrations 5→11

## Delivery notes

Remaining product UI (Ankita screens) and MCP cutover are implementation
follow-ons on top of this model; they do not block declaring the PM spine Done.
