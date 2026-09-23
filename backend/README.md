# MedTrack cloud gateway

Deployable Cloud Run service. It is not part of the Android APK and it does not include the workbench UI.

## What runs here

`medtrack_gateway` authenticates Firebase tokens, accepts inference jobs, and runs the production orchestrator:

`medtrack_gateway/orchestration/`

That package owns preflight, Jev requests, routing policy, task planning, clarification, the model registry, proposal assembly, and validation. The Android app talks to it with versioned JSON under `contracts/`. It does not import this Python code.

The AI Testing workbench imports this same package. It is a local test shell, not a second orchestrator.

## Container

Build from the repository root so shared contracts stay available and the Android app stays out of the image:

```bash
docker build -f backend/Dockerfile -t medtrack-gateway .
```

Cloud Run listens on port 8080. Set `OPENROUTER_API_KEY` on the service, not in the APK. One Cloud Run service is enough until document jobs need a separate worker.
