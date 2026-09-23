# PM-012: Single write-path for forms, MCP, AI

Status: Done | Priority: P0 | Depends on: PM-005 through PM-010  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

All input methods call the same command/validation layer; no weaker chat schema.

## Acceptance

- [x] `CareWritePath` facade exposes admissions / clinical / therapy / work services
- [x] Commands share provenance + idempotent operationId rules

## Delivery notes

`CareWritePath` injects `CareCommandService`, `CareClinicalAssessmentService`,
`CareTherapyCommandService`, `CareWorkCommandService`. MCP/AI should call this
facade only.
