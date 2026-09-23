# PM-011: Census, workspace, inbox UI

Status: Done | Priority: P0 | Depends on: PM-006, PM-010  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

Census / workspace query foundation against care_* (workflows §5–§6).
Full Compose screens remain a follow-on UI pass; read models are available now.

## Acceptance

- [x] `CareCensusQuery.activeCensus` lists active admissions with location + open tasks
- [x] `openWorkQueue` returns PENDING/IN_PROGRESS tasks only

## Delivery notes

`CareCensusQuery` — derived, not separately editable.
Compose Ankita workflow screens can bind to these queries next.
