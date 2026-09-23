# PM-009: Referrals, advice, questions, communication

Status: Done | Priority: P1 | Depends on: PM-005  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

Referral, milestones, ConsultationAdvice, ClinicalQuestion, CommunicationEvent
(data contract §8).

## Acceptance

- [x] CreateReferral requires person/team/specialty
- [x] PATIENT_SEEN requires encounter or explicit reported evidence
- [x] CLOSED/CANCELLED milestones update referral status

## Delivery notes

Schema 11 tables + `CareWorkCommandService` referral APIs.
Covered by `CareWorkAndAcceptanceTest`.
