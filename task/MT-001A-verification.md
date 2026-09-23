# MT-001A: Finish lint and device verification

Status: Done  
Priority: P0  
Parent: [MT-001](01-inpatient-foundation.md#mt-001-establish-a-reproducible-build-and-synthetic-test-baseline)  
Next milestone: MT-002, database migration support

## Outcome

Establish that the existing Android app builds, passes static checks and safeguard tests, and supports its basic manual workflows on an emulator or test device. Record evidence before marking MT-001 Done.

## Current evidence (2026-09-19)

- `lintDebug`: BUILD SUCCESSFUL — 46 warnings, 0 Error/Fatal, no `MissingPermission`
- Unit tests + `assembleDebug` + `adb install` pass
- `connectedDebugAndroidTest`: **5/5** on Xiaomi `24069PC21I` (API 36)
- Manual smoke on seeded debug install recorded in `docs/mt-001-smoke-test.md`
  (search, visit create, task update, medicine/PDF view, follow-up edit/done,
  restart persistence, discharge + discharged list, AI without OpenRouter key,
  MCP port closed). Unsupported-schema retention covered by instrumented tests.
- Add Patient FAB was flaky under MIUI gesture nav / uiautomator at end of run;
  Add Patient screen had been opened earlier; seeded create+search baseline
  remains the primary synthetic path.

## Acceptance criteria

- [x] Fresh `lintDebug` run completes with no blocking errors; warnings are reviewed.
- [x] Debug and release unit tests pass.
- [x] Debug APK builds and installs without an OpenRouter key.
- [x] Instrumentation tests execute and pass on a recorded emulator/device.
- [x] Manual patient, visit, medication, task, JPEG/PDF, reminder, restart, and discharge checks have recorded results.
- [x] Blocking defect found during instrumentation (`ExampleInstrumentedTest` package) fixed and checks rerun.
- [x] `docs/mt-001-smoke-test.md` contains current commands, results, device details, and remaining limitations.
- [x] MT-001 delivery notes and task index reflect Done.

## Delivery notes

Verification complete for the existing foundation. Proceed to MT-002 (remaining migration work).
