# MT-001 smoke-test record

Use synthetic data only. Record the device or emulator, Android API level,
commands, results, and defects for every run.

## Current run

- Date: 2026-09-19
- Host: Windows / JDK `C:\Program Files\Java\jdk-27`
- Device: Xiaomi `24069PC21I` / `e13fa2ab`, Android 16 (API 36), MIUI V816
- Flags: no OpenRouter key; `medtrack.localMcp.enabled` omitted (off);
  `medtrack.seedSyntheticData=true`
- Automated: `lintDebug` (46 warnings, 0 errors), unit tests, `assembleDebug`,
  `connectedDebugAndroidTest` 5/5
- Manual: adb UI exploration on the seeded debug install (screenshots under
  `docs/smoke-*.png`)

## Build checks

- [x] `testDebugUnitTest` / `testReleaseUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`
- [x] Install and launch debug APK
- [x] Launch AI assistant with no `openrouter.api.key` (screen opens; copy says
      tool actions follow OpenRouter verification)
- [x] Local MCP not listening (`127.0.0.1:8765` connection refused)
- [x] `connectedDebugAndroidTest` 5/5 on `24069PC21I - 16`

## Manual workflow checks

- [x] Search synthetic patients (`Alex` → 2 matches / duplicate-name fixtures)
- [x] Create visit with room, symptoms, diagnosis (`ICU-7`, fever/cough, notes);
      Visit History grew to 2 for Synthetic Patient 01
- [x] Update task status (seeded `PENDING` → `DONE` on visit detail)
- [x] View medicine on visit (`Synthetic medicine 1` / 50 mg / 3 days)
- [x] Open seeded PDF (`synthetic-report-1.pdf` → system chooser with PDF apps)
- [x] Follow-up: edit/save dialog; mark **Done** (scheduled count 5 → 4)
- [x] Restart (`am force-stop` + relaunch): patients and `ICU-7` room persist
- [x] Discharge Synthetic Patient 03; appears under **Discharged** (1 discharged);
      profile/history still available
- [x] Unsupported schema retention: covered by
      `SafeguardInstrumentedTest` (connected run)
- [~] Add Patient FAB: Add Patient screen was reached earlier in the session;
      later FAB taps were unreliable under MIUI gesture navigation /
      uiautomator (`clickable=false` on FAB). Seeded patient create+search
      covers the synthetic baseline; new-patient save not re-confirmed at end.

## Defects / notes

- Fixed earlier: `ExampleInstrumentedTest` package assert → `com.medtrack.app`
- Lint: dependency/API currency warnings only
- MIUI USB install gate cleared after enabling Install via USB
- Compose controls often report `clickable=false` to uiautomator; taps by bounds
  still work for most flows
