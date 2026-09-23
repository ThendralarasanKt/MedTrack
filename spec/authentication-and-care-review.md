# Authentication, AI review and patient hub: implementation review and completion specification

Date: 2026-09-20  
Status: Source-reviewed; completion requirements below are not implemented by this document  
Scope: Verify the supplied Firebase/sign-in claims and the four proposed next actions. Documentation only; no application code changed.

Parent: [Hybrid architecture](hybrid-architecture.md). Related delivery: [HY implementation tasks](../task/05-hybrid-architecture-implementation.md), [record contract](patient-management-data-contract.md).

## 1. Verified state

| Claim | Finding |
| --- | --- |
| Correct Firebase Android registration | Present in app/google-services.json for com.medtrack.app under medtrack-6497f. Android OAuth type 1 and web OAuth type 3 exist. |
| Debug signing fingerprint | Configuration contains the supplied a8a3d4b76d493676c84bb2744ef83793ac593d33 fingerprint. This review did not independently compare it with an installed APK certificate. |
| Web client ID generated | Verified generated debug BuildConfig contains 98303527639-etvkaf5a777s149d44jn1ohgnc91a481.apps.googleusercontent.com. Build script takes a local override or extracts the value from JSON with a regex. |
| Sign-in screen | MainActivity gates the workspace on AuthViewModel.unlocked and renders SignInScreen otherwise. |
| Google sign-in implementation | GoogleSignInHelper uses Credential Manager, exchanges the Google credential through FirebaseAuth, and passes a Firebase token/UID to AuthCoordinator. Runtime success not device-tested here. |
| Synthetic debug entry | Present, defaults enabled for debug, forced false in release BuildConfig. Debug builds can still hold persistent account bindings; isolation from real records requires further work. |
| AI staging gate absent | Outdated claim. Orchestrator persists uncommitted typed proposals and calls the local commit service only on confirmation. Completion semantics still have defects below. |
| Missing-Origin bypass | Original omission no longer bypasses bearer checking. LocalMcpHttpServer invokes McpHttpAuth, which requires a token even without Origin. Origin matching itself needs stricter parsing. |
| Patient Hub missing clinical cards | Outdated claim. Medications, investigation/report items, vitals/observations and allergies are queried and rendered. Episode scoping and clinical detail still need refinement. |

Verification run: `gradlew.bat testDebugUnitTest assembleDebug --offline --console=plain` succeeded. All 52 Gradle tasks were UP-TO-DATE; this was a build/cache verification, not a fresh re-execution of every test. Existing debug XML reports contain 48 tests, zero failures/errors/skips, across 15 suites. No instrumented test, emulator/phone sign-in, backend deployment, release build or live provider call was performed.

## 2. Findings requiring correction

### R-01 — Critical: backend does not authenticate Firebase token signatures

Evidence: [auth.py](../backend/medtrack_gateway/auth.py), `_decode_firebase_like`, line 107, calls `jwt.decode` with `verify_signature: False`. The path checks an issuer string but also accepts medtrack-test. Development tokens are enabled by default in Settings. IdentityDirectory device registrations and revocations are process-local dictionaries/sets.

Impact: a token carrying a provisioned subject can be accepted without proof Firebase issued it. Disabling only the mt-dev token prefix does not repair the unsigned JWT path. Process restarts or separate workers do not share device revocation state. Do not deploy this verifier as production authentication.

### R-02 — High: new Google identity can acquire the existing local owner

Evidence: [AuthCoordinator.kt](../apps/android/src/main/java/com/medtrack/app/hybrid/account/AuthCoordinator.kt), `bindGoogle`, lines 47–61: capabilities and registration failures are swallowed, owner is copied from the current binding or CareLocalSession, and onlineAuthorized becomes true. AccountBindingStore checks ownerAccountId equality, but the coordinator has already reused that owner for the new subject.

Impact: backend rejection/unprovisioned identity does not reliably prevent local binding. A different Google subject can inherit the existing local workspace identity; the owner-only mismatch check does not protect against that path. A Firebase login proves identity, not authorization to Ankita's records.

### R-03 — High: sign-out and restart do not establish a locked state

Evidence: [AuthViewModel.kt](../apps/android/src/main/java/com/medtrack/app/ui/auth/AuthViewModel.kt) initializes unlocked from any persisted binding. `signOut` repeats that check after AccountBindingStore.clearOnlineSession only changes onlineAuthorized to false. AccountSession similarly considers any binding unlocked.

Impact: sign-out clears credentials but leaves the workspace logically unlocked; restart treats an offline binding as sufficient access. The offline-unlock button is not a substitute for a real local verification policy. Synthetic and Google bindings also use the same fallback care workspace.

### R-04 — High: proposal status can claim commitment after failure

Evidence: [ProposalCommitService.kt](../apps/android/src/main/java/com/medtrack/app/hybrid/proposal/ProposalCommitService.kt), lines 95–99, collects per-group errors and then sets the stored proposal COMMITTED regardless of results. [AssistantViewModel.kt](../apps/android/src/main/java/com/medtrack/app/ui/assistant/AssistantViewModel.kt) clears proposals and navigates on a successful returned summary, including a partial-failure summary. Discard only clears UI state; it does not invoke the commit service's persistent discard method.

Impact: pending/failed groups can disappear from review and durable proposal state becomes inaccurate. Group transactions are a useful existing foundation; they do not make the whole lifecycle complete.

### R-05 — High: proposal review and dispatch do not preserve the full approved meaning

Evidence: [AiAssistantOrchestrator.kt](../apps/android/src/main/java/com/medtrack/app/ai/AiAssistantOrchestrator.kt) summarizes each operation as type/display hint/ID, filters individual selected operations and recomputes the approval digest at commit. It does not enforce atomic-group selection or general dependency completion. ProposalContract only uses dependsOn in a narrow STAT/delay check. ProposalCommitService makes expected versions optional in several dispatch paths and substitutes the current time for missing/unparseable effective times. Medication start forwards name and dose text, but not a complete regimen/route/effective-time/attribution payload.

Impact: a doctor may not see the actual clinical values being approved; deselection can split an intended atomic group; approved details can be dropped or unknown times replaced by invented care times. Digest equality calculated at execution is not sufficient evidence of a persisted approval of the same reviewed payload.

### R-06 — Medium: captured sign-in token has no request-time refresh path

Evidence: GoogleSignInHelper fetches a Firebase ID token during sign-in; AuthTokenStore persists that string. InferenceRequestRepository later retrieves that same stored token. No other getIdToken call was found in the inspected source.

Impact: expiry can leave cloud operations failing until another login. A refresh failure must not destroy local drafts or become synthetic-token fallback.

### R-07 — Medium: assistant screen does not supply patient context

Evidence: AssistantViewModel calls `parseUserMessage(message, conversation)` without admissionId. Orchestrator defaults to ContextSelector.unresolved; its conversation parameter is not used to construct the request.

Impact: patient-targeted chat context is not established by the shown screen path. This is a functional completion gap, not proof that unresolved identity bypasses all command validation.

### R-08 — Medium: patient hub observations span admissions without explicit separation

Evidence: [CareCensusQuery.kt](../app/src/main/java/com/medtrack/app/data/care/query/CareCensusQuery.kt), line 129, uses observationsForPatient for the current admission hub. HubVital exposes name, value and time but not originating admission. Other sections retrieve admission-specific orders/reports.

Impact: historical observations can be mixed into the current admission's vitals section. Patient-level allergy/history is appropriate when labelled; episode-specific observations need explicit scope and provenance.

### R-09 — Medium: Origin matching accepts prefix lookalikes

Evidence: [McpHttpAuth.kt](../app/src/main/java/com/medtrack/app/mcp/transport/McpHttpAuth.kt), `isAllowedOrigin`, uses startsWith for localhost and 127.0.0.1. This admits lookalike hostnames to the Origin allowlist. Bearer authentication is still independently required, so this is not the original missing-token bypass.

### R-10 — Low: OAuth configuration extraction depends on JSON formatting

Evidence: app/build.gradle.kts uses a regex requiring a particular client_id/client_type field sequence and newline, with no matching Android-client selection.

Impact: valid reordered/minified JSON or multiple registrations may yield a missing/wrong web client. Current generated debug value is correct; the parsing contract remains fragile.

## 3. Required authentication and session specification

Define separate states: LOCKED, AUTHENTICATING, AUTHENTICATED_UNPROVISIONED, AUTHORIZED_ONLINE and AUTHORIZED_OFFLINE. Persisted database ownership is not a session-unlock flag.

1. Verify Firebase credentials with a supported cryptographic verifier: signature, algorithm/key, issuer, audience, required subject and time claims. Reject test issuers and development tokens in production on every parsing path. Production startup must fail with development authentication enabled.
2. Return verified backend identity/account/device binding through an explicit bootstrap response. Do not derive cloud account ownership from local synthetic constants or silently reuse another subject's local owner.
3. First binding requires successful provisioning and device authorization. Network failure permits offline access only for the previously verified owner under local unlock policy; authorization denial cannot be translated into first-time access.
4. Validate account compatibility before saving a replacement token or changing active binding. Failure leaves the prior owner intact and does not expose its records to the attempted identity.
5. Keep database ownership across sign-out, but clear online session and set local UI access LOCKED. On restart enforce the chosen local unlock policy. Specify device credential/biometric verification rather than treating an offline button as authentication.
6. Separate synthetic storage/binding from real accounts. Release cannot unlock a lingering synthetic workspace because a debug build previously created it.
7. Obtain valid Firebase tokens for online requests through a refresh-capable provider. On authentication failure allow one bounded refresh/retry where appropriate; distinguish 401, provisioning denial and network failure. Preserve local work.
8. Persist membership/device revocation and enforce it consistently across backend processes/restarts. Device replacement is explicit; registration must not silently revoke another active device as a side effect of ordinary login.
9. Use correct dispatcher boundaries: synchronous HttpURLConnection gateway calls must not block the main/UI coroutine during Google binding.
10. Select OAuth configuration structurally for com.medtrack.app and the intended Firebase project; report missing/ambiguous web clients explicitly. Derive issuer configuration consistently rather than maintaining unrelated hard-coded copies.

Acceptance: forged/wrong-audience/expired/test tokens rejected; failed provisioning does not bind; a second Google subject cannot open the first subject's data; sign-out stays locked after restart; valid offline owner can explicitly unlock; expired tokens refresh without losing drafts; revocation survives worker restart; real and synthetic data remain separate. Device sign-in test includes cancel, no credential, first login, return login and backend outage. The fallback credential request's cancellation must also be handled consistently.

## 4. Required AI review and commit specification

- Review cards show patient/admission, actual changed fields and old/new values, dose/route/regimen where applicable, time/precision, attribution, source evidence and unresolved fields. Narrative model text is supplementary.
- Persist the exact reviewed payload and approval digest. An edit, selection change or stale-record reconciliation invalidates that approval and presents the updated review.
- Select atomic groups as units; reject dangling/cyclic dependencies and do not run a dependent group after its prerequisite fails. Allow independent partial results with durable per-group state.
- Require appropriate expected versions for existing-record changes. Verify patient/admission/target consistency and account/device binding before dispatch.
- Dispatch every supported clinical field without silent loss. Unsupported fields/types require clarification or explicit rejection. Missing/invalid care times remain unknown or unresolved; they never default to now unless the doctor explicitly supplied “now” with an anchored capture time.
- Store PENDING_REVIEW, PARTIALLY_COMMITTED, COMMITTED, DISCARDED and FAILED/NEEDS_REVIEW consistently with per-operation receipts. Failed groups remain actionable after navigation/restart. Discard persists state and cannot be replayed as an unreviewed commit.
- Repeated confirmation returns existing receipts. Only successfully committed clinical actions appear as saved; reminder scheduling has its own status.
- Wire patient context into the assistant route, visibly label it, and resolve global/new-patient inputs explicitly. Source conversation references must be durable if used in interpretation.

Acceptance: no clinical writes on parse/discard; interrupted atomic group leaves no partial writes; partial failure retains pending groups; deselecting a prerequisite is blocked; edits/staleness require renewed review; unknown timestamps stay unknown; medication details survive round-trip; exact reviewed patient is the actual commit target; process restart preserves proposal state.

## 5. Required MCP and patient hub completion specification

MCP: retain bearer authentication regardless of Origin. Parse Origin as a URI and compare exact allowed scheme/host/port, reject malformed/null/lookalike origins according to documented policy, and keep loopback binding and release-disabled configuration. Existing missing-Origin authentication tests remain relevant; add adversarial hostname cases. Local MCP does not replace cloud identity or per-record authorization.

Patient hub: retain implemented clinical cards. Default current observations and inpatient medication/orders to the selected admission. Provide explicitly labelled prior-admission/longitudinal history, source links, care times and units. Separate active treatment from stopped/historical orders; distinguish an investigation request from its report and review state. No allergy records must remain “not recorded/unknown,” not “no known allergies.” Ensure actions route through the same attributed/versioned command layer used elsewhere.

Acceptance: a readmitted patient's previous vitals do not appear as current without a historical label; every report/order card distinguishes its type and state; treatment history remains accessible; missing data is not represented as a clinical negative.

## 6. Completion order and delivery mapping

| Order | Work | Existing task mapping |
| --- | --- | --- |
| 1 | R-01 token verification and persistent authorization | HY-03, HY-12, MT-012 |
| 2 | R-02/R-03 owner binding, locking and synthetic separation; R-06 token refresh | HY-04, MT-012 |
| 3 | R-04/R-05 proposal state, full review semantics and faithful command dispatch | HY-07, WF-13 |
| 4 | R-07 context and R-08 clinical hub scoping | HY-06/HY-09; PM/WF query refinements |
| 5 | R-09 Origin hardening and R-10 configuration parsing | HY-01/HY-09 and HY-04 |
| 6 | Fresh automated checks and device authentication/workflow smoke | HY-13 |

These are refinements to existing tasks, not evidence that the previously completed PM model must be rebuilt. Existing sign-in implementation may remain available for synthetic development while sign-in deployment is deferred. Before a real-user backend pilot, the authentication and local ownership blockers must be resolved.

The suggested next actions “implement staging” and “add patient hub cards” should therefore be replaced by completion of the specific gaps above. A device smoke test is still useful but cannot certify signature verification, owner isolation or atomic proposal behaviour by itself.
