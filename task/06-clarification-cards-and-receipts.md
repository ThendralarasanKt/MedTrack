# Task set 06: Clarification cards and commit receipts

Status: CL-01–CL-16 Done  
Date: 2026-09-23  
Source: [Clarification orchestration](../spec/clarification-routing-orchestration.md), [Clarification UI](../spec/clarification-ui-system.md)

This set binds typed assistant results to trusted Android renderers and the existing local command path. Clarification answers never write clinical records. Proposal approval revalidates and commits through `ProposalCommitService`. Receipts come from commit and scheduling results, not from model text. External transmission stays a draft/manual preview until a channel worker exists.

## Ordered delivery plan

| ID | Deliverable | Depends on | Status |
| --- | --- | --- | --- |
| CL-01 | Versioned `AssistantTurnEnvelope` and fail-closed decode | HY-02, HY-07 | Done |
| CL-02 | Closed card-kind and clarification response-type registries | CL-01 | Done |
| CL-03 | Operation-to-command binding and narrative mismatch block | CL-01; existing `ProposalContract` | Done |
| CL-04 | Clarification answer store (no clinical write) and loop bound | CL-02 | Done |
| CL-05 | Proposal approval digest freeze and commit dispatch | CL-03; `ProposalCommitService` | Done |
| CL-06 | Receipt card from commit + scheduling outbox state | CL-05; `SchedulingOutboxProcessor` | Done |
| CL-07 | Transmission boundary: draft/manual only, never `Sent` | CL-01 | Done |
| CL-08 | Acceptance tests for the card-to-receipt path | CL-04–CL-07 | Done |
| CL-09 | Conversation shell: header, context chip, scrolling timeline, persistent composer | CL-02 | Done |
| CL-10 | Append-only timeline and “new response” scroll behavior | CL-09 | Done |
| CL-11 | Inline card lifecycle: expanded question, compact answer, proposal, receipt | CL-09, CL-06 | Done |
| CL-12 | Answer revision that stays visible and invalidates dependent cards | CL-11, CL-04 | Done |
| CL-13 | Large selectors in a sheet or full screen, returning to the same timeline point | CL-11 | Done |
| CL-14 | Composer stays a new message unless the doctor chooses Use as answer | CL-09, CL-11 | Done |
| CL-15 | Patient scope: preselect from the patient page; earlier turns keep their context | CL-09 | Done |
| CL-16 | Empty, offline, and resume states for the same thread | CL-10, CL-11 | Done |

## CL-01: Assistant turn envelope

Deliver:

- Discriminated envelope (`assistant-turn-v1`) with result kinds `CLARIFICATION`, `INFORMATION`, `PROPOSAL`, `TRANSMISSION_PROPOSAL`, `RECEIPT`, `MANUAL_REQUIRED`, `ERROR`.
- Reject unknown schema versions, result kinds, card kinds, and actions.

Acceptance:

- [x] Unknown discriminator fails closed.
- [x] Envelope bindings (workflow, request, context digest, plan version) are required.

## CL-02: Renderer registries

Deliver:

- Closed clarification response types matching the UI spec registry.
- Closed card-kind to allowed-action map. Backend-invented actions are dropped or rejected.

Acceptance:

- [x] Unknown response type and unknown card kind fail closed.
- [x] Clarification cards cannot advertise `APPROVE_GROUP` or `APPROVE_SEND`.

## CL-03: Command binding and narrative guard

Deliver:

- One allowlisted local command per proposal operation type.
- Narrative that names an operation absent from the typed bundle blocks approval.

Acceptance:

- [x] Unknown operation type fails closed.
- [x] Narrative/operation mismatch is a blocking contract failure.

## CL-04: Clarification answers

Deliver:

- Persist a bound answer (question id, digest, selected refs) without calling `CareWritePath`.
- Enforce a maximum clarification turn count and return `MANUAL_REQUIRED`.

Acceptance:

- [x] Continue/submit answer does not create a clinical record.
- [x] Sixth turn returns manual-required rather than another clinical command.

## CL-05: Proposal approval

Deliver:

- Freeze proposal id, payload digest, and selected groups before commit.
- Dispatch only allowlisted operations through `ProposalCommitService`.

Acceptance:

- [x] Digest mismatch refuses commit.
- [x] Approval uses the existing write path rather than a parallel DAO writer.

## CL-06: Receipts

Deliver:

- Build a `RECEIPT` card from group commit results and scheduling-outbox state.
- Surface pending versus scheduled reminder status without trusting model text.

Acceptance:

- [x] Pending outbox yields “scheduling pending”, not “scheduled”.
- [x] Applied outbox can report “Reminder scheduled”.

## CL-07: Transmission boundary

Deliver:

- Transmission proposals may be saved as local drafts.
- No delivery receipt and no `Sent` status.

Acceptance:

- [x] Approving a clinical proposal does not create a transmission outbox entry.
- [x] Transmission approval cannot claim sent.

## CL-08: Acceptance

Deliver:

- Unit tests covering decode, registries, clarification non-write, approval commit, receipt states, and transmission refusal.

Acceptance:

- [x] `AssistantTurnPipelineTest` passes.

## CL-09: Conversation shell

Spec: [clarification UI §3.1](../spec/clarification-ui-system.md).

Deliver a messaging-style screen with four stable regions: conversation header, patient/admission context chip, chronological timeline, and a bottom composer for text, voice, and attachments. This replaces a single box whose cards appear and disappear.

Acceptance:

- [x] Header, context chip, timeline, and composer are all visible together.
- [x] The composer stays available while a clarification card is open.

Delivery: `ConversationScreen` on the Capture route.

## CL-10: Append-only timeline

Deliver:

- New messages, cards, and receipts append. They do not replace earlier turns.
- If the doctor is near the bottom, scroll to the new item. If they are reading older content, show a `New response` affordance instead of jumping.

Acceptance:

- [x] Answering a card leaves the prior doctor message and assistant text in place.
- [x] Scrolling up during a new result does not force the viewport to the bottom.

## CL-11: Inline card lifecycle

Deliver timeline states:

- The active clarification card is expanded and interactive.
- After Continue, it collapses to a compact answer summary and stays in history.
- Proposal cards stay expanded until approved or discarded.
- Approval replaces the proposal with a receipt for what was saved and whether effects were scheduled.
- A long-running report job is its own card and does not block the composer.

Acceptance:

- [x] Answered clarification cards remain visible as summaries.
- [x] A receipt shows saved versus scheduling-pending from actual commit state.
- [x] A document job card does not disable sending another message.

## CL-12: Visible answer revisions

Deliver:

- `Change` on an unfinalized answer appends a revision. It does not rewrite history in place.
- Dependent later answers and proposals are marked invalid until the workflow replans.

Acceptance:

- [x] The original answer and the revision are both visible.
- [x] A later card that depended on the changed answer cannot be submitted until it is refreshed.

## CL-13: Large selectors

Deliver bottom-sheet or full-screen pickers, launched from the inline card, for patient search, ward/bed navigation, long medication lists, people search, and document inspection. Closing the sheet returns to the same timeline position with the selection shown on the card.

Acceptance:

- [x] A long list does not replace the conversation with a permanent new screen.
- [x] After selection, the doctor is back at the card that opened the sheet.

## CL-14: Composer versus card answer

Deliver:

- Typing or tapping inside a card answers that card.
- Send from the main composer starts a new chat turn.
- If the text looks like an answer to the open question, offer `Use as answer` and `Send as new message`. Do not choose silently.
- Show which workflow is still waiting when a new message starts another one.

Acceptance:

- [x] An unrelated note sent from the composer does not submit the active clarification.
- [x] `Use as answer` is an explicit action.

## CL-15: Patient scope

Deliver:

- Opening chat from a patient page preselects that patient and admission on the context chip.
- Global chat remains available, and the active context is always visible when one is selected.
- Changing context applies to the next request only. Earlier messages keep the patient they were about.
- Cross-patient results use separate patient headers. There is no single approval covering several patients.

Acceptance:

- [x] A context switch does not relabel earlier bubbles.
- [x] Two patients in one result cannot be approved from one merged card.

## CL-16: Empty, offline, and resume

Deliver:

- Empty chat starters: add a patient, record an update, review pending work, attach a report. They enter normal workflows.
- Offline sends show `Saved on device` and, when interpretation needs the network, `Waiting for connection`.
- After process death, reopen the same thread and the active unanswered card. Do not present an empty chat while a clinical workflow is still waiting.

Acceptance:

- [x] Restart restores the unanswered card in the existing thread.
- [x] Offline text is visible immediately with a saved-on-device state.

Delivery notes: `ConversationTimeline` plus `ConversationScreen` on Capture and patient-hub AI capture. `ConversationTimelineTest` covers append, scroll cue, card collapse, revision, sheet selection, composer choice, scope, separate cross-patient proposals, offline, and JSON resume. The scripted antibiotic / report / two-patient flows are local so the timeline can be used without a live model. Approving a proposal in this screen records the receipt states; it does not yet call `ProposalCommitService`.
