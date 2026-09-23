# MedTrack AI Testing Workbench

Local browser app for evaluating Jev and generative proposal flows **before** enabling them in the Android app.

Status: **WB-02** (versioned config + routing inspector) + **WB-03 scaffold** (LIVE_SYNTHETIC gated on `OPENROUTER_API_KEY`). Routing and orchestration come from `backend/medtrack_gateway/orchestration`. Default remains MOCK. Never writes Android clinical records.

## Start (mock mode)

From the MedTrack repo root:

```bash
cd "AI Testing workbench"
python -m pip install -e ".[dev]"
python scripts/run_workbench.py
```

Open [http://127.0.0.1:8765/workbench](http://127.0.0.1:8765/workbench).

Banner must read: **MOCK — NO PROVIDER CALLS** (or “LIVE PROVIDER AVAILABLE — default still MOCK” if a key is already in the environment).

## Live synthetic (optional)

When you are ready for real OpenRouter calls on **synthetic** cases only:

1. Set the key in your shell (do not commit it; the browser never sees it):

```powershell
$env:OPENROUTER_API_KEY = "sk-or-..."
```

2. Restart the workbench.
3. Confirm `/workbench/api/status` shows `"openRouterKeyConfigured": true` and `"providerAvailable": true`.
4. In the UI, switch Mode to **LIVE_SYNTHETIC** and run a case.

Without the key, LIVE_SYNTHETIC is disabled and returns `KEY_REQUIRED`.

Generation routes use pinned **free** OpenRouter models (verified live). If a primary `:free` slug is taken offline, the run tries the approved free fallbacks listed in the experiment config.

## What this does

- Versioned question sets, routing policy, model registry, and prompts under `config/` and `prompts/`
- Mock pipeline with routing reasons in the trace and UI inspector
- Optional live synthetic path via existing OpenRouter adapters (key required)
- Stage trace and simulate-approval review (evaluation only)

## What this does not do

- Batch evaluation dashboards (WB-04+)
- Android `CareWritePath` or Room writes
- Real patient data

## Tests

```bash
cd "AI Testing workbench"
pytest -q
```

Local state lives under `.workbench/` (gitignored).
