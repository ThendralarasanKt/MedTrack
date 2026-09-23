# Task set 05: Local clinical records and cloud AI

Status: HY-01–HY-13 implemented; HY-14 blocked on authorized Ankita onboarding  
Date: 2026-09-20  
Source: [Hybrid architecture specification](../spec/hybrid-architecture.md)

First release serves Ankita on one active Android device. The backend must support isolated personal accounts from its first implementation, tested with multiple synthetic users. Clinical records, approved writes and reminders remain local. Cloud clinical synchronization, shared patient access, a nurse dashboard and automated WhatsApp ingestion are outside this task set.

Creating this backlog performs no implementation, deployment, account provisioning or live inference. Use synthetic records for development. Real clinical use remains subject to the existing authentication, encryption, recovery and release criteria.

## Relationship to existing work

- The [user profile and subscription specification](../spec/user-profile-and-subscription.md) adds UP-01 through UP-06 delivery slices under HY-02/HY-03/HY-04/HY-06/HY-12/HY-13. Cloud profile bootstrap, My Profile, encrypted cache/outbox, local clinician mapping and Ankita PILOT grants are implemented. Payment-provider integration remains deferred.

- PM-001 through PM-013 are recorded Done. Reuse the patient model, CareWritePath, query layer, provenance and reminders; do not rebuild or reset those milestones.
- HY-01 verifies the current implementation and identifies concrete gaps. Old reviews are not proof a defect still exists.
- MT-012 remains the authentication/encryption milestone. HY-03 and HY-04 refine its account and device integration; link shared evidence rather than implementing twice.
- WF-09 through WF-17 remain the chat/workflow milestones. This backlog supplies their deployment, identity, versioned gateway and local commit integration.
- JV-01 through JV-11 remain the detailed Jev/OpenRouter adapter and evaluation work. HY-08 aggregates that work rather than defining a competing model stack.
- The hybrid specification is authoritative for local commits and deferred sync. Any older task wording suggesting a cloud clinical commit endpoint must be reconciled before implementation.

## Ordered delivery plan

| ID | Deliverable | Depends on | Status |
| --- | --- | --- | --- |
| HY-01 | Audit current boundaries and record implementation gaps | Existing PM delivery | Done |
| HY-02 | Freeze API, proposal and context contracts with mocks | HY-01 | Done |
| HY-03 | Backend identity, personal accounts and isolation | HY-02 | Done |
| HY-04 | Android sign-in, owner mapping and device binding | HY-02, HY-03; MT-012 | Done |
| HY-05 | Authenticated jobs and private artifact lifecycle | HY-03 | Done |
| HY-06 | Android context selection and persistent request queue | HY-02, HY-04 | Done |
| HY-07 | Local proposal review and atomic commit integration | HY-02, HY-04; existing CareWritePath | Done |
| HY-08 | Jev/generative adapters and bounded orchestration | HY-02, HY-05; JV sub-tasks | Done |
| HY-09 | Connect Android to gateway and remove direct provider access | HY-05 through HY-08 | Done |
| HY-10 | Offline, stale-action and reminder reconciliation | HY-06, HY-07, HY-09 | Done |
| HY-11 | Encrypted backup, restore and device replacement | HY-04; existing storage/migrations | Done |
| HY-12 | Operating controls and multi-account security verification | HY-03, HY-05, HY-08, HY-09 | Done |
| HY-13 | End-to-end verification and pilot release candidate | HY-10 through HY-12; MT-012 complete | Done |
| HY-14 | Controlled Ankita pilot and measured follow-up | HY-13; authorized deployment and onboarding | Blocked |

Start with HY-01, then HY-02. Implementation evidence: [HY-02-14-delivery.md](HY-02-14-delivery.md). UI/mock integration needs no model key. Live inference requires a backend OpenRouter credential. Provisioning a second synthetic account for tests does not enable a second production user.

## HY-01: Establish the actual implementation baseline

Deliver:

- Inventory current schema, account ownership, UI/manual command callers, AI staging/commit path, network transports, provider credentials, reminder scheduling and backup behaviour.
- Verify which known fixes already exist, including staged writes and MCP authentication. Record file references and relevant existing test evidence.
- Identify any remaining direct DAO writes bypassing domain validation, unstable target IDs, non-atomic grouped operations or hard-coded ownership assumptions.
- Create a gap table mapping each hybrid requirement to existing implementation, required extension and HY/WF/JV task owner.

Acceptance:

- [x] Every required architectural boundary has an evidence-backed disposition. No task treats a historic review as a fresh finding without checking source.
- [x] Existing clinical records and migrations are preserved; no schema reset or model rewrite proposed merely to introduce the gateway.
- [x] Implementation plan identifies test/deployment configuration and required secrets without writing secret values into the repo.

### Delivery notes

Status: Done (2026-09-20)

Evidence: [HY-01-baseline.md](HY-01-baseline.md)

Checked in current source (not the September 20 review as gospel):

- Care model Room v12, 71 entities, migrations 5→12, SQLCipher, `CareWritePath` for UI/MCP/AI commits.
- AI staging gate and MCP Bearer auth are present (`AiAssistantOrchestrator.parseUserMessage` / `commitProposals`; `McpHttpAuth`).
- Remaining gaps for HY-02+: hard-coded `CareLocalSession`, device OpenRouter key, label-based MCP targets, in-memory proposals, unused scheduling outbox, no Auth UI, no backend, no encrypted backup product.

No schema reset. Next: HY-02 versioned contracts and mocks.

## HY-02: Define versioned contracts and synthetic fixtures

Deliver:

- Versioned API schemas for capabilities, artifact upload/deletion, inference submission/status/cancellation and their error responses.
- Request envelope: requestId, deviceId, client/command schema versions, purpose, source references, context manifest and digest; optional resolved patient/admission IDs.
- Proposal envelope: stable targets, expected versions, evidence, field values/units, attribution, effective-time precision, unresolved fields, operation dependencies and atomic groups.
- Context selection contract and response-size limits. Specify compatible schema evolution and explicit unsupported-command rejection.
- Mock gateway and fixtures for success, clarification, stale context, partial input, timeout, unsupported version, wrong account and duplicate request.

Acceptance:

- A transfer targets a location ID and a medication stop targets an order ID; display labels alone cannot pass mutation validation.
- Relative reminder times include their anchor and explicit resolved date/time zone.
- The “stat test” versus “four hours later” example fails consistency review rather than silently committing.
- No cloud clinical commit endpoint exists. Contract tests can run without provider credentials.

## HY-03: Build backend authentication and personal-account isolation

Deliver:

- Small backend service with identity/policy, job and model adapter boundaries; environment-separated configuration and secrets.
- Persist UserIdentity, PersonalAccount, AccountMembership, DeviceRegistration and AccountPolicy as specified.
- Validate Firebase identity tokens; derive account access from server records. Provisioning uses verified issuer/subject, not hard-coded names or trusted client account IDs.
- First-release provisioning permits Ankita's owner account; other identities remain unprovisioned. Allow synthetic accounts in test environments.
- Account-scoped query/storage helpers, active membership/device checks and per-account route/usage limits.

Acceptance:

- Invalid/expired tokens, disabled memberships, revoked devices and unprovisioned users receive correct rejections.
- Two synthetic accounts cannot access each other's jobs, artifacts or policy using guessed IDs or forged request fields.
- Provisioning a further isolated owner account needs configuration/data changes, not a new API or conditional code for that person's name.
- No hospital, professional team or clinical Person grants application access by itself.

## HY-04: Bind Android identity, local ownership and one active device

Deliver:

- Complete the relevant MT-012 sign-in/encrypted-access requirements and map the authenticated user to a stable personal account ID.
- Register the active device; bind requests, drafts, proposals and notifications to their local account/database context.
- Define migration of existing local ownership IDs: verify intended owner, preserve stable clinical IDs and audit, and perform any remapping transactionally. Never claim an old database solely because its displayed doctor name matches.
- Handle account switching, sign-out, revoked online access and offline app unlock without opening another user's records or deleting clinical history.

Acceptance:

- Previously authorized manual workflows operate offline under local access rules; new inference still requires backend authorization.
- Sign-out or account change cannot apply an old proposal/notification to the new account.
- Another sign-in does not receive Ankita's database, attachments or restore material.
- One active backend device is enforced; immediate remote revocation of offline local access is not falsely promised.

## HY-05: Implement durable jobs and temporary artifacts

Deliver:

- InferenceJob and TemporaryArtifact persistence, authenticated lifecycle endpoints and bounded worker execution.
- Account/requestId idempotency with input digest conflict detection; safe worker claiming, crash recovery, cancellation, expiry and bounded retries.
- Private uploads with MIME/content, size and digest checks; artifact references remain account-scoped through processing and retrieval.
- Cleanup within 24 hours of terminal status and no later than 48 hours from creation, including failed/cancelled jobs and abandoned uploads. Select metadata retention explicitly before deployment.

Acceptance:

- Retrying the same request/digest returns the same job; a changed digest conflicts. Retries never execute clinical commands.
- Cancellation races do not expose a cancelled result as an approved action. Expired artifacts are inaccessible and removed by verified cleanup.
- Job or worker failure leaves recoverable status, not indefinitely running records.
- Routine logs omit clinical content, access tokens and provider secrets.

## HY-06: Build local context selection and request persistence

Deliver:

- Persist draft, request intent, source references, account/device binding and processing state before sending.
- Build minimal patient/admission context with record versions, source times, snapshot time, missing-data markers and a digest.
- Support global/new-patient inputs without pretending unresolved identity is confirmed.
- Send selected text/documents after explicit submit; keep device dictation separate and do not automatically upload raw audio.
- Queue/retry within account and capability rules; show offline, queued, failed and awaiting-review states accurately.

Acceptance:

- Single-patient requests do not upload the whole census. Cross-patient context requires an explicit purpose and labelled selection.
- Process restart preserves drafts and request identity; repeated submission does not duplicate jobs.
- Local changes after snapshot creation remain detectable at proposal review/commit.
- Unsupported local speech falls back to typing or an explicitly enabled cloud option; no silent remote audio fallback.

## HY-07: Integrate proposals with the local clinical command layer

Deliver:

- Durable proposal storage and editable patient-labelled review cards, reusing WF-13 and existing staging functionality.
- Resolve target IDs, attribution, clinical times and missing required fields. Validate summary/operation consistency and operation dependencies.
- Approval bound to the exact edited payload digest; expected-version validation immediately before commit.
- Atomic clinical command groups with record/event/revision/audit/operation receipt writes and scheduling-outbox entries in the same transaction.
- Explicit partial selection only where dependencies permit it; per-group receipts for independent groups.

Acceptance:

- Before approval, extraction, tool calls and discarded proposals cause no clinical mutations.
- A downloaded valid proposal can commit offline. Stale target versions require a revised diff and renewed review.
- Failure inside an atomic group leaves no partial clinical updates; repeated operation IDs return the original result without duplicate care events.
- Manual actions, notifications and approved proposals enforce the same clinical invariants.
- “Saved” and “reminder scheduled” receipts come from actual local outcomes, not generated narrative.

## HY-08: Implement cloud routing and bounded model execution

Deliver through the existing JV backlog:

- JV-01/JV-02: verify current OpenRouter Jev contract, define decision/generative interfaces and fixtures.
- JV-03 through JV-07: backend-held credentials, separate adapters, capability registry, versioned question sets and evaluated routing thresholds; reuse HY-03 authentication.
- JV-08/JV-09: bounded harness and advisory proposal checks against HY-02 schemas; read-only context tools and proposal-producing write tools.
- JV-10/JV-11: model-path failure handling, privacy, observability and end-to-end adapter evidence.

Acceptance:

- Jev and generative roles are distinct, with no local LLM or separate TypeSafe-key requirement.
- Live adapters are supported by verified synthetic contract tests, not just model listings or mocked success.
- Model confidence cannot authorize a write. No model process has a clinical database write credential.
- Provider failure, invalid output and exhausted budgets produce explicit recoverable results; there is no silent clinical fallback action.

## HY-09: Cut Android over to the authenticated gateway

Deliver:

- Replace direct device-to-OpenRouter calls with HY-02/HY-05 APIs, validated capability negotiation and authenticated job retrieval.
- Remove provider keys from the APK/client configuration path. Keep provider secrets exclusively backend-side.
- Connect returned proposals to HY-07 local review/commit; preserve durable captures and manual operation during failures.
- Inventory local MCP HTTP callers before removal/disablement. Retain local domain commands even if the optional socket transport is removed.
- Define feature flags and rollback to manual/mock-safe operation; rollback must not restore client-embedded provider credentials or obsolete write behaviour.

Acceptance:

- Application-package/config inspection finds no provider secret; network tests show no direct client provider request.
- Both model paths reach the gateway with account isolation, then commit only through local commands.
- Gateway outage leaves census, local clinical actions and existing reminders usable.
- No unsupported cloud-saved or synchronized status appears in the UI.

## HY-10: Verify offline work and reminder reconciliation

Deliver:

- Integration of scheduling-outbox processing with local command commits, including failed OS scheduling, reschedule/cancel and restart reconciliation.
- Notification Complete, Add note, Complete with note and Reschedule routed through account-bound/version-checked commands.
- Recovery for downloaded proposals, queued inference and interrupted app processes.

Acceptance:

- Airplane-mode tests cover patient review, a transfer, a task note/completion and a downloaded proposal commit.
- A failed scheduling attempt preserves the saved task with accurate unavailable/inexact status.
- Repeated taps, stale alarms and old proposals do not undo completion or duplicate events.
- Notification dismissal and Add note do not complete a task. Changed bed assignment is reflected when opening pending work.
- Device tests cover the existing reminder contract, including permission loss and restart; results state measured limits rather than unconditional delivery guarantees.

## HY-11: Implement encrypted recovery and device replacement

Deliver:

- Encrypted backup/export format covering database, original attachments, provenance and schema/version manifest.
- Explicit key custody/recovery design, integrity validation, account binding and supported restore-version rules.
- Restore into a checked staging location before activating the recovered store; preserve the existing store if validation fails.
- Device replacement flow with explicit backend revocation/registration and reminder reconciliation on the new device.

Acceptance:

- Synthetic backup restores identity, clinical history, attachments and audit consistently; wrong key, wrong account, tampering and unsupported schema fail without data loss.
- Failed restore does not overwrite the active database.
- Restore does not blindly duplicate pending notifications or replay old clinical commands.
- User-facing behaviour states that sign-in alone does not recover records and that an old offline device's alarms cannot be remotely guaranteed to stop.
- Cloud AI job storage is never offered as patient-record backup.

## HY-12: Complete operating controls and isolation verification

Deliver:

- Per-account/provider usage limits, request limits, rate controls, timeouts, bounded queues and route-disable controls.
- Redacted operational metrics, account-scoped caches, secret rotation configuration, artifact-expiry monitoring and failure alerts.
- Automated negative authorization coverage across endpoints, workers, artifact URLs, caches and cancellation/retry paths.
- Controlled synthetic release environment and deployment/runbook configuration; no automatic production launch as part of backlog creation.

Acceptance:

- Cross-account tests include concurrent jobs with similar patient IDs and identical document hashes; neither results nor deduplication leaks content.
- Queue overload and one account exhausting quota do not bypass another account's authorization or limits.
- Logs and failure reports contain no clinical payloads or secrets by default; cleanup failures are observable and recoverable.
- Account/device disablement blocks new backend work and retrieval according to policy; offline limitations remain documented.

## HY-13: Produce a verified pilot release candidate

Deliver:

- Synthetic end-to-end scenarios linking capture → cloud proposal → local review → clinical commit → timed follow-up → completion/note → patient timeline.
- Scenarios for wrong patient, bed reuse, changed medication order, amended report, repeated import, interrupted network, account switch and app restart.
- Mid-tier Android measurements for local interaction/save latency, cloud response latency, capture correction rate and reminder behaviour; evaluate separately.
- Backup/restore evidence, current release checklist, rollback procedure and documented unresolved limitations.

Acceptance:

- Required Android tests/build/lint and backend contract/isolation tests pass; device smoke and reminder results are recorded.
- MT-012 and recovery prerequisites pass before any real patient pilot.
- UI promises match delivered scope: no shared charts, automatic WhatsApp reading or clinical synchronization.
- Each failed scenario is resolved or explicitly blocks its affected capability. Task status includes evidence, not only “works locally”.

## HY-14: Run the controlled Ankita pilot

Deliver:

- Deploy the verified backend and configure Ankita's account/device when deployment/onboarding is authorized.
- Start with synthetic rehearsal; enable real clinical use only after the release prerequisites are met.
- Validate her primary/referral census, context capture, review effort, pending-work visibility and reminder response with measured feedback.
- Record follow-up issues and prioritize them without silently enabling shared access or synchronization.

Acceptance:

- Ankita completes the agreed representative workflow, understands local-save/cloud-processing states and can recover from tested offline/error conditions.
- Provider cost, turnaround time, extraction corrections and notification behaviour have recorded observations.
- Further isolated users can be provisioned through the established administrative path; tests demonstrate readiness without expanding the actual pilot audience.

## Explicitly deferred

Do not include cloud clinical database replication, last-write-wins reconciliation, organization/team sharing, multiple active devices, nurse web dashboards, automated WhatsApp ingestion or raw scan/ECG diagnosis in these tasks. When required, create a separate sync/shared-access specification with semantic conflicts, ownership migration, attachment reconciliation and reminder ownership before implementation.

## Completion evidence

For each HY task record: changed files/commit, relevant existing work reused, verification commands/results, device/environment when applicable, remaining limitations and linked WF/JV/MT evidence. Mark Done only when its acceptance criteria pass. Maintain existing PM and other task statuses; creating this file does not certify or reopen their implementation.
