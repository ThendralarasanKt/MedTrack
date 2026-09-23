# Patient management for Ankita's inpatient workflow

Status: Downstream workflow draft; finalize after the information model; not an implementation claim
Date: 2026-09-19  
Primary user: Ankita B, the doctor interviewed in the business-user conversation  
Platform: Mid-tier Android; English input; optional local speech transcription

Source: [Business-user interview](Business%20User%20Convo/MedTrak.md) and the five supplied [conversation images](Business%20User%20Convo/MedTrak%20Image). The interview is an imperfect transcript. Clinical names, drug spellings, numeric details and unclear phrases are not authoritative structured data. Examples below are synthetic.

Related: [Product scope](high-level-spec.md), [record and reminder contract](patient-record-and-reminder-workflow.md), [chat and LLM architecture](llm-and-chat-architecture.md), [Jev orchestration](jev-orchestration.md).

Read first: [Patient information model](patient-information-model.md), then the [concrete data contract](patient-management-data-contract.md). Finalize their reusable entities, standardized values and dated relationships before refining these workflows.

This document defines the user-facing patient-management workflow and extends the earlier specs with referrals, nursing assignments, communication tracking and handover. Their authentication, encryption, clinical approval, audit and reminder requirements continue to apply. This is a personal workspace for one doctor; colleagues can be recorded without having app accounts.

## 1. The person and the job

Ankita works within a medicine consultant's team. She reviews the team's inpatients, receives requests to see other teams' patients, communicates findings to seniors, follows specialist recommendations and tracks unfinished work across wards. During on-call duty, her coverage extends beyond her usual consultant's list. The interview describes roughly 30 referral requests on some days; these are requests, not necessarily 30 distinct patients or admissions.

Information arrives through rounds, verbal instructions, calls, WhatsApp messages, photographed reports and paper notes. Nurses change shifts; responsibility and context can become unclear. An occupancy list helps find primary-team patients, but referral patients may live on a separate informal list. A detailed outgoing message may describe a review already completed, rather than a new request to review someone.

The main outcome is that she can answer: **Who needs my attention, what is pending, who is doing it, what has changed, and what must I follow up next?**

The app must support a busy day's capture without requiring a complete admission form. It must also preserve enough structure to reconstruct a long admission when preparing handover or discharge documentation.

## 2. Evidence and product interpretation

| Interview evidence | Required product behaviour |
| --- | --- |
| Primary consultant occupancy list plus separately tracked references | One census with primary-team, referral and on-call views |
| Referrals arrive by calls or informal messages, sometimes with only a bed | Quick referral intake and an unresolved-identity inbox |
| A specialist visit can happen but resulting advice may not reach the nurse | Track consultation, advice, communication and resulting actions separately |
| Nursing stations cover patients from several departments | Physical location and clinical team are independent relationships |
| The doctor forgets pending items after leaving a floor | Pending work grouped by floor, ward, station or patient |
| WhatsApp carries questions, orders, acknowledgements and completed assessments | Preserve source context and distinguish request, decision, acknowledgement and outcome |
| Nurses have handover sheets; the doctor needs to tell seniors what remains | Reviewable patient summaries and an outstanding-work handover |
| Long admissions are difficult to recall at discharge | A dated, source-linked hospital-course timeline |

The flows and state models below are proposed design choices derived from this evidence. Automatic HIS access, WhatsApp monitoring, communication delivery receipts and direct access to colleagues' work are not assumed.

## 3. Scope and success criteria

The first usable workflow includes manual and chat-assisted patient capture, patient lists, referrals, rounds, tasks, report attachment, reminders, communication records and handover drafts. These must be usable manually without cloud inference after sign-in. Chat interpretation and report extraction require cloud access; there is no local LLM.

Success means:

- Every accepted action has a patient/admission or stays visibly unresolved; it never disappears into chat history.
- Primary-team patients and referral patients can be found in the same workspace without duplicate patient records.
- The doctor can see all outstanding work for a floor or patient, including work assigned to someone else that she must follow up.
- An accepted recommendation remains distinguishable from an order communicated and from treatment actually performed.
- Completing a notification or adding a note updates the same patient record and work queue.
- A handover or hospital-course summary can be traced to recorded events and shows missing or uncertain information.

Hospital billing, automatic HIS synchronization, automatic message sending, shared multi-user task assignment, autonomous prescribing, and diagnostic interpretation of raw X-ray/ECG images are outside this release. Raw images may be attached and viewed. Written reports can enter the separate document-analysis workflow.

## 4. Patient identity, episodes and responsibility

### 4.1 Stable identity

The app patient ID is permanent. Store hospital-scoped UHID identifiers and aliases, with current/previous status and provenance. A changed UHID must not automatically create a second person. A name, age or bed alone is not a reliable match.

Patient identity, hospital episode, current physical location and the doctor's involvement are separate concepts:

- **Patient:** the longitudinal person and history across admissions.
- **Episode:** an ER or inpatient encounter with its own external identifier when available. ER-to-ward conversion can link two episodes; it does not erase the ER history. The first release manages inpatient work; ER information may be retained as linked source context.
- **Location:** hospital, building if relevant, floor, ward, nursing station, room and bed, with effective intervals. Preserve unknown fields. Do not derive floor from bed numbering without a configured hospital rule and confirmation.
- **Clinical responsibility:** primary consultant/team and contributing services, independently of where the patient is physically located.
- **My involvement:** primary-team care, referral consultation or on-call coverage, with start/end times and active/ended state. Multiple concurrent roles are allowed.

A referral closing does not discharge the patient. Ending on-call coverage does not end the admission. A transfer changes location without changing identity, clinical team or task ownership unless those changes are explicitly recorded.

### 4.2 Minimal intake and uncertain identity

Allow a local draft from a message such as “Medicine opinion needed in ICU, bed 12.” Retain source, capture time, requester if known, reason and location hints. Do not fabricate name, admission date, diagnosis or patient identity.

If identity is unresolved, keep the item in the Inbox as an unassigned request. A personal reminder to identify/review the request may be attached to that inbox item, visibly labelled unassigned; it cannot create clinical orders or imply a patient record exists. Once the doctor confirms identity/admission, link the request and retain its original context.

For confirmed creation, review likely duplicates and accept a minimal patient plus admission record with unknown fields explicitly marked. Correcting an accidental duplicate requires a reviewed reconciliation with provenance and reference preservation, never a silent fuzzy match or deletion.

## 5. Main screens and interaction

### 5.1 Home: today's patients and work

Default to the active patient census with a persistent bottom composer. Keep Add Patient available. First launch offers “Describe a patient” and manual creation; it does not force either route.

Top-level views:

| View | Contents |
| --- | --- |
| Patients | Primary team, Referrals, On call and All; optional consultant/team filter |
| Work | Due, overdue, waiting, unscheduled and completed; group by patient or location |
| Inbox | Unmatched imports, questions, new reports, unresolved identity and drafts awaiting review |
| History | Ended involvements and discharged admissions without losing previous records |

Patient cards show identifying details, current bed/ward, primary consultant, why this doctor is involved, latest recorded review, next action and pending count. Show stale or unverified location explicitly. One patient appears once in All, with multiple involvement badges if appropriate.

Sort work by explicit urgency/due time and offer location grouping for rounds. Unknown due times remain visible as unscheduled. Do not silently assign clinical urgency or hide undated work. A doctor can set or revise priority with attribution.

### 5.2 Patient workspace

The header always identifies the patient, admission, current location and primary team. Sections are Overview, Pending work, Timeline, Medications, Reports and Care team. The bottom composer stays scoped to this patient; a multi-patient input opens separately labelled proposals.

The overview presents current problems and plan, latest changes, awaiting results/advice, upcoming reviews and unresolved questions. Each derived summary has an “as of” time and links to its source events. Missing information is shown as unknown, not normal or absent.

### 5.3 Capture without losing the current round

Text, reviewed English dictation, camera, image/PDF selection and Android share intake enter a persistent draft. Preserve drafts across navigation and restart. Allow a brief note now and completion later. Offline capture is visibly saved locally; pending cloud processing must not appear as a committed clinical update.

For AI-assisted changes, show patient-labelled review cards with source, event time, deciding person, proposed records and linked actions. Save/Edit/Discard applies to explicit items. Receipts reflect actual local commits and reminder scheduling status.

## 6. End-to-end workflows

### 6.1 Start a shift or round

1. Open the primary-team list and incoming referrals; add on-call coverage when needed.
2. Review overdue work, new results, unanswered questions and unresolved imports.
3. Confirm changed locations or add missing patients. Manual occupancy-list entry is supported; photographed list extraction is a later convenience and requires per-patient review.
4. Group by floor/ward/station and select the next patient.
5. Before leaving a location, view all unfinished items there. The app does not require continuous location tracking.

### 6.2 Receive an incoming referral

Capture the referring team/person, reason/question, requested consultant/service, patient/episode, location, request time, explicit urgency and any due time. Clarify missing identity through the Inbox workflow. The patient joins Referrals without becoming a primary-team admission.

Record the review as an encounter: assessment, observations, relevant history, conclusions and recommendations, with participants and actual review time. Advice communicated to the primary team is a separate event. Continued daily review remains active until the doctor explicitly ends involvement or sets a review plan; do not automatically create indefinite daily reminders for every referral.

### 6.3 Request an outgoing specialist opinion

From the patient's round, create a referral with reason and requested specialty/person. Record who was asked to communicate it, when communication occurred if known, and a follow-up task such as “Check whether specialist has reviewed at 15:00.”

Later, record whether the patient was seen, what advice was received, who made it and its supporting note/message. Convert accepted advice into separate plan/order proposals and follow-up tasks. If advice conflicts with the current plan, retain both and require an explicit clinician decision; the latest message does not automatically win.

Do not close follow-up merely because a consultation happened. The doctor must see any remaining communication or treatment tasks. Referral closure can occur with linked actions still open, but the UI must display those outstanding actions and preserve them in the work queue.

### 6.4 Record rounds and evolving illness

Save each review under the admission with actual encounter time, recorder and other participants. Record new symptoms/findings, problem status, investigation requests, treatment decisions and planned reviews as distinct linked entries.

An outgoing assessment message can provide evidence that a review occurred. It cannot prove every investigation mentioned was ordered or every drug listed was administered. Preserve historical medication lists separately from confirmed current orders. Unclear dose shorthand or medicine names remain unresolved, with the original text available for correction.

The timeline supports a daily view and an event-type view. A complication, its treatment and subsequent improvement remain separate dated events. Late entry is allowed with distinct care time and entry time; amendments preserve earlier versions.

### 6.5 Handle a nurse question or a symptom update

Capture the question, sender/reported role, patient, source time and relevant context. Create a visible response task if needed. A symptom observation may be saved separately after review.

Record the clinician's decision, recipient and communication event. “Please call” means an outstanding communication request; “Sure” means an acknowledgement; neither establishes that care occurred. Resolve the question when answered, while any resulting administration, observation or reassessment tasks stay open until their own outcome is recorded.

### 6.6 Follow a test from request to action

Track the investigation request, specimen collection or scan performance if known, awaited report, report receipt, clinician review and resulting decision. These are independently evidenced milestones: a report can arrive without a recorded order, and an imported report does not prove the doctor reviewed it.

Retain original documents and preliminary/final/amended versions. Extraction proposals include units, report dates, source locations and uncertainty. Match using identifiers and admission context; ambiguous reports stay in Inbox. A report image embedded as a tiny screenshot thumbnail is not sufficient for reliable value extraction; request the original attachment or manual entry where needed.

Mark a result as reviewed with actor/time, including “reviewed; no change” if explicitly entered. Receiving a report resolves only the appropriate waiting-for-report item, after confirmed matching; it does not automatically complete review or follow-up treatment tasks.

### 6.7 Track treatment changes and communication

Keep recommendation, approved decision, medication order/change, communication and administration as separate records. For “stop medicine X,” capture the deciding clinician and effective time, update the approved order, and optionally create a task to inform the responsible nurse. A recorded message or call can satisfy communication; it cannot prove that a later dose was withheld.

If a nurse reports a dose given, retain reported performer/time and the doctor's recorder identity. Missing dose/time stays unknown or awaits clarification. Corrections are attributed amendments. Procedure requests and actual procedures follow the same distinction.

### 6.8 Follow-up notification

For “Review at 17:30,” confirm the date/time zone and target patient/admission, then save a task and local reminder. Display current location when opening the task, even after a transfer.

Actions are Complete, Add note, Complete with note and Reschedule. Add note does not complete work. Notification dismissal does not complete work. Completion records actor, outcome and actual performed time where supplied; entering structured treatment requires its own fields. Save the task response and linked patient event atomically and update all views.

Exact/inexact availability, blocked notifications, missed reminders, stale actions, restart recovery and locked-device access follow the [existing reminder contract](patient-record-and-reminder-workflow.md#5-timed-notification-contract). No model is involved in triggering a saved reminder.

### 6.9 End the shift and hand over

Select patients or a ward/team and generate a draft containing identity/location, concise clinical background, changes this shift and outstanding actions. Each action includes reason, owner/recipient if known, due time, waiting-on dependency and last update. Include unanswered questions and unreviewed reports.

The doctor reviews, edits and chooses what to share. Offer copy/export/share through an explicit user action. A share-sheet launch records preparation, not delivery or acceptance. Record communication or recipient acknowledgement only when evidenced or explicitly reported. Tasks remain owned as before unless the doctor records a handover of responsibility; colleagues have no live shared task inbox in this release.

Handover is a timestamped snapshot. Later updates do not rewrite an already shared snapshot; a refreshed handover has a new version. Ward lists must allow excluding unnecessary clinical details and confirming recipients before external sharing.

### 6.10 End involvement, discharge and readmission

End a referral or on-call involvement independently of hospital discharge. Review open work and explicitly retain, reassign or cancel it with a reason. Keep the ended involvement in history.

At discharge, generate a reviewable hospital-course draft from confirmed events: presenting problems, key investigations, diagnoses, complications, procedures, treatment changes, response and outstanding follow-up. Link supporting records and surface gaps. AI does not sign or finalize a discharge summary. Discharge preserves all records, and readmission opens a new episode without silently carrying active orders or reminders forward.

## 7. State and ownership contracts

Avoid one status field that pretends the entire chain has happened.

| Object | State or milestones | Completion rule |
| --- | --- | --- |
| My involvement | Active, ended; primary/referral/on-call role | Explicit end with open-work review |
| Referral | Requested, accepted if known, seen, closed or cancelled | Milestone evidence; advice and communication tracked separately |
| Advice | Awaited, received, reviewed; accepted/rejected/deferred disposition | Attributed clinician review; accepted advice links resulting actions |
| Communication | Needed, reported sent/verbally conveyed, acknowledged if known | Event with recipient, channel and time; not inferred from sharing |
| Care task | Pending, in progress, completed, cancelled; optional waiting reason | Attributed outcome; cancellation reason retained |
| Question | Open, answered, closed/reopened | Answer links source/decision; downstream tasks independent |
| Report review | Awaiting review, reviewed, review reopened after amendment | Actor/time and report version |

Milestones can arrive out of order. Recording that a specialist has seen a patient does not invent an earlier acceptance time. Use event history to preserve unknowns and derive display status.

Separate the person responsible for performing an action from the doctor responsible for following it up. A nurse assignment is contextual and time-bounded; a shift change must not silently move every outstanding task. Allow explicit reassignment with history. “Unassigned” is a visible state, not the authenticated doctor by default.

## 8. Structured record additions

These extend the existing PatientMedicalRecord aggregate; they must use its shared event, attribution, audit and versioning rules rather than a second standalone record store.

| Entity/contract | Minimum information |
| --- | --- |
| PatientIdentifier | Patient, hospital/issuer, identifier, current/previous status, source; reviewed alias relationship |
| Episode relationship | ER/inpatient type, external episode ID if known, predecessor/related episode; no invented admission times |
| CareInvolvement | Admission, doctor/team, role, reason, start/end, state |
| LocationAssignment | Admission, physical location including optional nursing station, effective interval, source/verification |
| NursingAssignment | Admission, nurse or station/in-charge role, effective interval, reported source |
| Referral | Admission, incoming/outgoing relative to this doctor, requester, requested service, reason, milestones, linked encounters/advice/tasks |
| ConsultationAdvice | Referral/encounter, advisor, advice time, source, review/disposition, resulting decisions/orders |
| CommunicationEvent | Linked referral/question/order/task, sender, recipient, channel, reported occurrence time, evidence and acknowledgement |
| SourceItem | Original message/document, captured time, sender if known, supplied source time/precision, reply links, import identity, processing state |
| UnassignedIntake | Source items, identity/location hints, reason, unresolved fields, personal follow-up, eventual resolved target |
| HandoverSnapshot | Scope, generated/reviewed time, author, source versions, included work, recipient if supplied, export/communication history |

Question and consultation records already proposed in the record spec are refined here, not duplicated. Extend CareTask with follow-up owner, waiting reason/dependency, originating referral/question and communication links. People and teams are shared references with event-time roles; a later membership change must not rewrite old decisions.

## 9. Conversation and LLM behaviour

The backend uses Jev through OpenRouter for structured routing, and generative models for extraction and summaries, as specified separately. The Android command layer owns identity validation, review and final writes.

For each imported conversation, propose independently: patient match, observations/history, completed encounters, new questions, decisions/orders, communication acknowledgements and future actions. One message may contain several categories. Preserve sender versus quoted author, quoted text versus new text, and original source versus import time. Partial screenshots without date/context must not produce invented timestamps or patient linkage.

Quoted repeats, overlapping screenshots and repeat shares should be flagged as possible duplicates. Confirmation and stable operation IDs prevent duplicate tasks/reminders on retries. Do not discard an edited message merely because it resembles an older one; retain it as a possible amendment.

Apply synthetic evaluation cases derived from the supplied patterns:

- A patient update plus family request creates separate reviewable observation and communication items.
- A completed assessment with medication history does not create a new “see patient” task by default.
- “Send these tests” creates proposed investigation requests, not results.
- “Check at 22:00” creates a proposed timed task with explicit date and patient resolution.
- “Sure” records acknowledgement only; no administration or task completion is inferred.
- A photograph of a scan/ECG is classified as a raw clinical image, distinct from a written interpretation.

No background WhatsApp reading is required. Entry is via explicit share/paste/upload or manual capture. Cloud failures leave durable drafts and actionable manual paths; they never silently approve proposals.

## 10. Delivery order and existing backlog alignment

| Stage | Outcome | Existing task alignment |
| --- | --- | --- |
| 1 | Identity, admissions, involvements, locations/stations and attributed records | MT-003 through MT-005; WF-01/WF-02 |
| 2 | Manual referrals, questions, communication, pending queue and rounds | Extend foundation care-team/task work and WF-03/WF-07 |
| 3 | Timed reminders, response notes and local recovery | WF-04 through WF-08 |
| 4 | Persistent chat/share inbox, patient matching and approved proposals | WF-09 through WF-13; Jev task set |
| 5 | Reports, conversation interpretation, handover and hospital-course drafts | WF-14 through WF-17 |

This mapping identifies refinements, not completed functionality or replacement task IDs. Dedicated implementation tasks must explicitly cover new referral, involvement, nursing-assignment and handover contracts before those capabilities can be claimed delivered. Preserve the current task statuses; this document does not reopen completed foundation verification.

## 11. Acceptance scenarios

1. **Primary and referral census:** One patient with both roles appears once in All, appears in both filtered views and retains one admission history.
2. **Incomplete referral:** A bed-only request stays in Inbox; no clinical record mutation occurs until patient/admission confirmation. Linking later preserves the source and follow-up.
3. **Transfer and bed reuse:** Moving a patient updates the location displayed for pending work. An old message mentioning the reused bed is not assigned to its new occupant automatically.
4. **Specialist follow-through:** A referral is marked seen and advice received, while a nurse-communication task remains visibly open. Recording communication does not imply treatment occurred.
5. **Mixed nursing station:** Patients from different departments share a station and appear in its work view. A shift change does not rewrite earlier performers or silently reassign tasks.
6. **Acknowledgement versus outcome:** Importing an order and “Sure” produces no administration record. A later explicit completion is a separately attributed event.
7. **Completed assessment:** A pasted post-review summary records a proposed encounter and future actions without duplicating the already completed review.
8. **Report lifecycle:** Receipt, review and action have distinct states. An amended report preserves its predecessor and reopens review without erasing prior decisions.
9. **Notification write-back:** At a scheduled review, Add note leaves the task pending; Complete with note creates one response/event despite a repeated tap. Patient timeline and queue agree.
10. **Conflicting advice:** Opposing specialist instructions remain visible until the doctor records a decision; arrival order alone cannot change the medication plan.
11. **Handover:** A reviewed snapshot contains overdue, waiting and undated work with ownership. Export does not complete tasks or assert recipient acknowledgement.
12. **Long admission:** A hospital-course draft presents dated complications, interventions and recovery with source links; undocumented intervals and uncertain chronology remain visible.
13. **End referral versus discharge:** Closing involvement leaves the hospital admission intact. Discharge retains history; readmission does not inherit unresolved orders automatically.
14. **Offline and retries:** Manual rounds, saved reminders and task responses work offline. Repeated import/processing retries do not duplicate committed records.

Use synthetic patient fixtures for automated tests and evaluation. The interview images are discovery evidence, not default fixtures for cloud testing or seeded demo records.

## 12. Remaining validation with the primary user

These do not block the initial design; current assumptions are explicit:

- Default home view: primary-team census plus due work, with Referrals and On call one tap away. Validate whether she prefers a work queue as the default.
- Referral follow-up: explicitly chosen date/time or an undated pending item; no automatic daily schedule. Confirm her preferred repeat-review pattern.
- Handover: reviewable text first, with optional structured export later. Confirm preferred grouping and which details recipients need.
- Hospital details: record ward/station/bed explicitly. Confirm local terminology and any useful bed-number rules before configuring inference.
- Coverage scale: support the original approximately 20 active patients and additional daily referral activity; validate distinct patient count and busiest on-call workload using synthetic usability sessions.
- This remains the doctor's personal record of care and follow-through. Confirm which entries must also be copied into the hospital's official record; no automatic HIS write-back is assumed.
