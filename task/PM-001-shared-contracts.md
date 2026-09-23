# PM-001: Shared contracts, schema export, migration baseline

Status: Done | Priority: P0 | Depends on: MT-001  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

Room/table map scaffolding, `ClinicalTime`, ownership fields, enums,
`exportSchema = true`, non-destructive Migration 5→6 adding care tables only.

## Acceptance

- [x] Schema export enabled
- [x] ClinicalTime precision rules tested
- [x] Legacy tables untouched by migration

## Delivery notes

Implemented under `app/src/main/java/com/medtrack/app/data/care/` and
`data/db/CareMigrations.kt`.
