# PM-005: Provenance core

Status: Done | Priority: P0 | Depends on: PM-003  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

ClinicalEvent, EventParticipant, RecordRevision, AuditEntry, AppliedOperation;
commands write event/audit/operation in one transaction; idempotent operation IDs.

## Acceptance

- [x] Same operationId retry returns original receipt
- [x] Different payload under same ID rejected
- [x] Partial failure leaves no orphan clinical rows

## Delivery notes

Schema 7 + `MIGRATION_6_7` (index names aligned to Room schema export).
Writer: `CareProvenanceWriter`. Wired into `CreatePatientAndAdmission` and
`RecordTransfer` (episode version bumped on transfer). Verified by
`CareProvenanceTest` (Robolectric).
