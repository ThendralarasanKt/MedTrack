# PM-008: Medication, procedure, investigation objects

Status: Done | Priority: P1 | Depends on: PM-007  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

MedicationDefinition/Order/Administration, Procedure*, Investigation*, Specimen,
DiagnosticReport, Document links (data contract §6–§7).

## Acceptance

- [x] ChangeMedicationOrder with legal transitions + order events
- [x] RecordAdministration; OMITTED/REFUSED require reason or unknown
- [x] AttachAndMatchReport + AMENDED predecessor rule; ReportReview
- [x] TEXT_ONLY Regimen is not silently normalized

## Delivery notes

Schema 10 + `MIGRATION_9_10`. `CareTherapyCommandService` / `CareTherapyDao`.
Verified by `CareTherapyCommandTest`.
