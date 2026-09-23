# PM-006: Legacy bridge

Status: Done | Priority: P0 | Depends on: PM-004, PM-005  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

Map legacy Patient/Visit/Medicine/Task/Report/FollowUp per data-contract §13
without inventing admission precision. Legacy UI keeps reading legacy tables
until cutover.

## Acceptance

- [x] Seeded fixtures remain readable after bridge
- [x] Unknown fields stay explicitly unknown

## Delivery notes

`LegacyCareBridge` + `care_legacy_links` (schema 8). Patients project to
Person/Patient + uncertain HospitalEpisode; Visit/Medicine/Task/Report/FollowUp
are DEFERRED with full snapshots until PM-007+. Debug seeder runs the bridge
after insert. Verified by `CareLegacyBridgeTest`.
