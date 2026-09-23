# MedTrack

MedTrack is a local-first inpatient care workspace for doctors, with a cloud
backend for bounded AI interpretation and routing.

## Repository layout

- `apps/android/` — Android application and local encrypted clinical workspace
- `apps/ios/` — planned iOS application
- `backend/` — authenticated Cloud Run gateway, orchestration and workers
- `contracts/` — versioned JSON/API contracts shared across clients and backend
- `AI Testing workbench/` — synthetic routing and model evaluation tools
- `spec/` — product, data, orchestration and deployment specifications
- `task/` — implementation backlog and verification evidence
- `infra/` — staging and production deployment configuration

The root Gradle wrapper remains available, so Android commands continue to run
from the repository root with `gradlew` or `gradlew.bat`.
