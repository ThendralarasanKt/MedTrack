# Task set 03: Jev through OpenRouter

Status: Planned; all tasks Todo  
Parent milestones: WF-10, WF-11, WF-13, WF-17 and WF-20 in the [workflow backlog](02-chat-clinical-workflows.md)  
Design: [Jev orchestration](../spec/jev-orchestration.md)

## Scope and decisions

Use one backend OpenRouter credential for Jev decisions and selected cloud generative models. Provide separate decision and chat adapters. Do not require a direct TypeSafe key, a local LLM, or provider credentials in the Android APK.

Jev classifies inputs and assesses proposals. Generative models extract fields and produce explanations. Deterministic application code owns identity, permissions, approved record changes and notification scheduling.

This list decomposes the existing workflow milestones rather than creating duplicate work. Link shared implementation and verification evidence in both backlogs. No application code, account configuration, API calls or deployment is performed by creating this task list.

## Task order

| ID | Deliverable | Dependencies | Status |
| --- | --- | --- | --- |
| JV-01 | Verify OpenRouter's decision API contract | None | Done |
| JV-02 | Define internal decision/chat interfaces and mocks | JV-01 documented contract | Done |
| JV-03 | Configure backend authentication, credentials and policy | JV-02; WF-11 identity contract | Done |
| JV-04 | Implement Jev decision adapter | JV-01 through JV-03 | Done |
| JV-05 | Implement generative adapter and capability registry | JV-02, JV-03 | Done |
| JV-06 | Define Jev questions and labelled evaluation data | JV-02; WF-10 input contract | Todo |
| JV-07 | Implement routing policy and calibrate decisions | JV-04 through JV-06 | Todo |
| JV-08 | Integrate the bounded workflow harness | JV-07; WF-10, WF-13 contracts | Todo |
| JV-09 | Add advisory proposal checks | JV-06, JV-08 | Todo |
| JV-10 | Complete recovery, privacy and observability checks | JV-08, JV-09 | Todo |
| JV-11 | Verify end-to-end behaviour and staged enablement | JV-10; shared domain commands available | Todo |

Start with JV-01. Interfaces, fixtures and UI integration can use mocks before live credentials exist. Live contract verification is a gate before claiming the adapters work against OpenRouter; real patient data is a separate gate governed by the main spec.

## JV-01: Verify the OpenRouter decision API contract

### Deliver

- Record current official evidence for Jev access, supported versioned model ID, endpoint, authentication, request and response structure, error codes and limits.
- Distinguish decision responses from chat completions. Verify the supported representation of state, named Noul/Choice/Score questions, probability distributions, confidence, usage and resolved model version.
- Check whether SDK support is verified or a dedicated HTTP adapter is required. Do not assume the direct TypeSafe SDK works by changing its base URL.
- Verify which provider/data-handling controls are actually supported by the decision endpoint. Record unknown or unsupported controls explicitly.
- With a configured development credential and authorized usage, run small synthetic requests covering all decision types and multiple questions. Save sanitized request/response fixtures.

### Acceptance

- The contract document includes source links, verification date, model/endpoint identifiers and live-test status.
- The same OpenRouter credential successfully authenticates a Jev request and a selected generative request; no direct TypeSafe credential is used.
- A model listing or unauthenticated endpoint response is not counted as successful inference verification.
- If the account or required policy is unsupported, document the blocker rather than silently introducing direct TypeSafe access.

## JV-02: Define interfaces, normalized schemas and mocks

### Deliver

- Separate `DecisionService` and `GenerationService` contracts behind the backend gateway.
- Define versioned `DecisionRequest` and `DecisionEnvelope`: job/stage/input IDs, minimized state, question-set version, model version, typed answers, distributions, optional confidence, usage, latency and error state.
- Define normalized errors for unavailable, rate-limited, invalid input, policy denied, malformed response and cancelled work.
- Add deterministic fixtures for mixed intents, uncertainty, missing fields, extra/unknown labels and provider failures.

### Acceptance

- Noul probability cannot be confused with Choice/Score confidence; an absent answer is not interpreted as false.
- Decision and chat parsers cannot accidentally accept each other's response types.
- Mock workflows run without network access or a configured key, and expose proposals rather than claiming saved records.

## JV-03: Configure the authenticated backend and shared credential

### Deliver

- Server-side OpenRouter secret configuration, startup validation and credential rotation procedure.
- Verify the app's Google/Firebase identity and enforce per-account authorization, request limits and usage budgets.
- Add explicit allowed decision/generative model configurations and route-specific provider/data policy.
- Remove provider credentials from the production Android build path as part of WF-11; keep authentication to MedTrack separate from authentication to OpenRouter.
- Document temporary request/result retention, secure transport and redacted operational logging.

### Acceptance

- No provider secret appears in an APK, repository, client response, exception trace or telemetry.
- Missing/invalid app identity cannot consume provider inference through the gateway.
- A shared credential does not imply identical retention or routing policy across models; unsupported required controls block the affected route.
- Budget enforcement prevents unbounded inference loops and retries.

## JV-04: Implement the Jev decision adapter

### Deliver

- Map internal state/questions to the verified OpenRouter decision contract and normalize the result.
- Validate named questions, response types, allowed labels, finite probabilities and required fields.
- Add request size/context limits, cancellation, timeouts, bounded transient retries and circuit breaking.
- Record model and question-set versions without recording clinical text in telemetry.

### Acceptance

- Synthetic contract cases match the approved fixtures; unsupported schemas fail explicitly.
- Missing, contradictory or malformed decision data cannot produce an executable clinical command.
- Retry policy does not retry permanent validation/authentication errors or exceed the job budget.
- Adapter tests require no direct TypeSafe SDK/account/key.

## JV-05: Implement generative adapter and capability registry

### Deliver

- A separate OpenRouter generative adapter for structured extraction and reasoning.
- Versioned role mappings for routine extraction, document processing and clinical reasoning, with explicit capabilities and permitted fallback models.
- Validate JSON/schema output, evidence references and tool names before proposal generation.
- Reuse backend credential storage and usage controls while preserving route-specific policy.

### Acceptance

- A clinical/document job cannot fall back to an incapable model merely because it is available.
- Generative output remains a proposed change or answer, not a saved record.
- Binary reports are sent only to capable approved processing routes, never as Jev text state.
- Provider errors and schema failures are visible in normalized job results.

## JV-06: Define question sets and evaluation data

### Deliver

- Versioned independent intent questions for lookup, patient creation, transfer, tasks/reminders, clinical events, medication changes, reasoning and multiple patients.
- Segment-level statement classification for observation, completed care, advice, question, mixed or unclear content.
- Proposal checks for source support and unresolved ambiguity; include Score only where its ordered levels have a defined use.
- Labelled synthetic English cases with regional accents represented as reviewed transcript text, negation, misspellings, duplicate names, quoted instructions and mixed patient updates.
- Separate development/calibration and held-out evaluation sets; clinician review of clinically consequential labels.

### Acceptance

- Multiple intents can be true without being forced into one category.
- Questions within a batch do not depend on answers from that same batch.
- “Nurse asks whether to stop” and “doctor stopped” receive different intended interpretations.
- The question set does not treat classification of urgency as an autonomous clinical triage service.

## JV-07: Implement policy and calibrate Jev decisions

### Deliver

- Per-question thresholds, abstention handling and explicit conservative routes for uncertain clinical content.
- Deterministic policy that combines model outputs with capability, identity and authorization requirements.
- Evaluation of per-intent precision/recall, critical false negatives, probability calibration, abstention, latency and end-to-end cost.
- A versioned policy report comparing the Jev route with deterministic and generative-classifier baselines.

### Acceptance

- Thresholds are justified by held-out MedTrack cases rather than copied from a tutorial.
- High confidence never bypasses clinical approval, stable patient resolution, account ownership or expected record versions.
- Missing/uncertain decisions produce clarification, an approved stronger route or queued work rather than guessed values.
- A rollback restores the last evaluated model/question/policy combination.

## JV-08: Integrate the bounded workflow harness

### Deliver

- Compose Jev routing, cloud extraction/reasoning and typed proposals in the Python backend, using LangChain components behind MedTrack interfaces where useful.
- Route known button actions and report uploads deterministically when intent is already explicit; avoid unnecessary classifier calls.
- Re-evaluate only at meaningful state changes, using stage IDs and source/context versions.
- Bound tool/action steps, retries and context size; persist resumable job states and partial outcomes.
- Connect proposals to WF-13's Android review and shared local command layer.

### Acceptance

- A mixed transfer/medication/reminder input produces separate traceable actions, correct clarification and appropriate clinical review.
- Switching patient threads cannot redirect a pending proposal.
- Backend inference cannot write directly into the local clinical record or fabricate a commit receipt.
- The harness does not depend on experimental middleware defaults to provide human approval.

## JV-09: Add advisory proposal checks

### Deliver

- Compare original source spans with extracted actions using the evaluated proposal-stage question set.
- Preserve unsupported/ambiguous findings as review information; offer edit, clarification or rejection without losing the original note.
- Recheck changed proposal content and invalidate approval when its version changes.

### Acceptance

- Jev can flag a problem but cannot independently approve a clinical action.
- Hard policy violations fail even if Jev rates the proposal as supported.
- Speculative advice and quoted third-party instructions cannot become authorized orders.
- Evaluation measures both missed unsupported actions and unnecessary blocked valid proposals.

## JV-10: Verify recovery, privacy and observability

### Deliver

- Test Jev outage, OpenRouter outage, timeout, rate limit, missing answers, unexpected versions, cancellation and interrupted acknowledgement.
- Enforce bounded retry/circuit breaker and approved fallback or queued states.
- Disable raw clinical traces in LangChain/LangSmith and SDK/HTTP instrumentation by default; verify logs with synthetic sensitive-marker tests.
- Record content-free operational metrics and versioned decision provenance needed to investigate routing outcomes.
- Test that cloud failure never prevents local task completion, notification notes or reminder triggering.

### Acceptance

- No failure path silently relaxes approvals, invents decisions or switches to an unapproved provider.
- Same-operation retry cannot duplicate record writes; stale job results cannot overwrite newer work.
- Local/manual workflows remain usable without inference, with queued chat work labelled accurately.
- App and backend logs contain neither provider secrets nor clinical input/output content by default.

## JV-11: Run integration evaluation and stage enablement

### Deliver

- Exercise mock, live synthetic, shadow and routing-enabled modes in that order, each with recorded results.
- Verify chat creation, multiple patients, medication-change review, JPEG/PDF preprocessing, a 17:30 reminder and notification-note write-back.
- Record model/policy versions, effective provider route, latency/cost and corrections during evaluation.
- Provide feature flags for Jev routing and proposal checks, plus rollback and incident procedures.

### Acceptance

- Authenticated OpenRouter decision and generative calls pass the live contract suite.
- Clinical review and patient isolation remain intact across end-to-end scenarios; no wrong-patient or unauthorized clinical write is accepted in the release suite.
- Notifications and their local response records work with all cloud inference disabled.
- Required data-handling and clinical evaluation gates are met before enabling real-patient traffic.
- Mark parent milestone criteria complete only for outcomes actually tested. Remaining device/lint/foundation blockers remain visible.

## Delivery record

For each task, record status, implementation files/commit, test commands and results, model/question/policy versions where applicable, and remaining limitations. All tasks are currently Todo. The first task is contract verification, not asking the user to paste credentials into chat.
