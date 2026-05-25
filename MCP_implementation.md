# MCP Implementation Plan

## Goal

Create only 2 MCP tools for this app:

1. `update_patient_room`
2. `get_patient_current_process`

These tools should work against the existing DoctorsCRM app data model and should reuse the current Room database structure and repository flow as much as possible.

This document is intentionally broken into very small tasks. We will complete them one by one. After each task, we should review:

- how to create it
- why it is needed
- what it should do
- why it belongs in that layer/file

## Tool Scope

### Tool 1: `update_patient_room`

Inputs:

- `patient_name`
- `current_room_number`
- `new_room_number`

Expected behavior:

- Find the correct patient using patient name
- Validate the patient currently matches the provided room number
- Update the room number in the latest active visit context
- Return a clear success or failure response

### Tool 2: `get_patient_current_process`

Inputs:

- `patient_name`
- `room_number`

Expected behavior:

- Find the correct patient and relevant visit
- Load the latest visit details
- Include current medicines/prescriptions
- Include tasks / current process items
- Include uploaded reports
- Include follow-up details if present
- Return a structured summary of the patient’s current treatment process

## Important Architectural Observation

In the current codebase, `roomNo` is stored in `visits`, not in `patients`.

That means:

- a patient does not have one global room number in the database
- room number belongs to a consultation/visit record
- "update room number of patient" must decide which visit row to update

Before implementation, we should define the rule clearly:

- most likely update the latest visit for the matched patient if its current `roomNo` matches the provided room number

This is important because tool behavior must match the current schema.

## High-Level Build Strategy

We should build the MCP support in layers:

1. Define exact MCP tool contract
2. Add database queries needed by the tools
3. Add repository methods
4. Add MCP service/tool handler layer
5. Add structured response models
6. Add validation and error handling
7. Test with real app data

## Tiny Task Breakdown

### Phase 1: Understand and Lock Requirements

#### Task 1.1

Write the exact functional contract for `update_patient_room`.

Output:

- final input fields
- matching rules
- update rule
- success response
- failure responses

Why:

- prevents ambiguous behavior later

#### Task 1.2

Write the exact functional contract for `get_patient_current_process`.

Output:

- final input fields
- patient matching rules
- which visit is considered "current"
- exact fields returned in response

Why:

- this tool spans multiple tables, so the result shape must be locked early

#### Task 1.3

Decide the patient matching strategy.

Options to evaluate:

- exact name match only
- exact name + room match
- case-insensitive name match
- partial name fallback

Why:

- MCP tools need deterministic behavior
- patient names may not be unique

#### Task 1.4

Decide what “current process” means in this app.

Likely meaning:

- latest visit
- medicines attached to that visit
- tasks attached to that visit
- reports attached to that visit
- latest follow-up attached to that visit

Why:

- the term is business-facing, but implementation must map to exact tables

### Phase 2: Data Mapping and Query Design

#### Task 2.1

Document all existing tables involved in the two tools.

Tables:

- `patients`
- `visits`
- `tasks`
- `medicines`
- `reports`
- `follow_ups`

Why:

- confirms all data sources before writing queries

#### Task 2.2

Document which fields from each table are needed for MCP output.

Why:

- avoids over-fetching and unclear payloads

#### Task 2.3

Design the query needed to find a patient by name and room.

Why:

- tool 1 and tool 2 both depend on reliable patient selection

#### Task 2.4

Design the query needed to fetch the latest visit for a matched patient.

Why:

- both tools depend on identifying the correct visit row

#### Task 2.5

Design the query needed to update the room number for the selected visit.

Why:

- room number lives in `visits`, so update must target the correct visit record

#### Task 2.6

Design the read queries for medicines, tasks, reports, and follow-up tied to the latest visit.

Why:

- tool 2 needs a complete patient-process snapshot

#### Task 2.7

Decide whether tool 2 should use one large join query or multiple small DAO queries.

Why:

- affects maintainability, complexity, and response assembly

### Phase 3: Room DAO Preparation

#### Task 3.1

Create or extend DAO method to find patient visits by patient name and room number.

Why:

- DAO is the correct database access layer

#### Task 3.2

Create DAO method to fetch the latest visit for a patient.

Why:

- needed as a reusable primitive across MCP logic

#### Task 3.3

Create DAO method to update `roomNo` for a visit by visit id.

Why:

- direct, minimal, and aligned with current schema

#### Task 3.4

Create DAO method to fetch medicines for a visit.

Why:

- tool 2 must show current prescriptions

#### Task 3.5

Create DAO method to fetch tasks for a visit.

Why:

- tasks represent active process/work items

#### Task 3.6

Create DAO method to fetch reports for a visit.

Why:

- reports are part of the patient’s current clinical state

#### Task 3.7

Create DAO method to fetch the latest follow-up for a visit.

Why:

- gives next-step continuity in the process view

### Phase 4: Repository Layer

#### Task 4.1

Add repository method to resolve a patient + visit context from `patient_name` and `room_number`.

Why:

- repository should provide domain-friendly operations over raw DAO calls

#### Task 4.2

Add repository method to update a visit room number safely.

Why:

- centralizes validation and update flow

#### Task 4.3

Add repository method to assemble current process data for a patient.

Why:

- MCP layer should not directly orchestrate many DAO calls if repository can provide a cleaner domain method

### Phase 5: MCP Tool Contract Design

#### Task 5.1

Choose where MCP code should live in this project.

Possible examples:

- `app/src/main/java/com/medtrack/app/mcp/`
- `app/src/main/java/com/medtrack/app/integration/mcp/`

Why:

- a stable folder structure prevents scattered implementation

#### Task 5.2

Define request model for `update_patient_room`.

Example fields:

- `patientName`
- `currentRoomNumber`
- `newRoomNumber`

Why:

- MCP handlers should accept strongly typed input

#### Task 5.3

Define response model for `update_patient_room`.

Example fields:

- `success`
- `message`
- `patientId`
- `visitId`
- `oldRoomNumber`
- `newRoomNumber`

Why:

- consistent structured output is easier for agents and clients

#### Task 5.4

Define request model for `get_patient_current_process`.

Example fields:

- `patientName`
- `roomNumber`

Why:

- locks input schema before implementation

#### Task 5.5

Define response model for `get_patient_current_process`.

Example sections:

- patient info
- visit info
- medicines
- tasks
- reports
- follow-up

Why:

- this is the main payload contract for downstream MCP consumers

#### Task 5.6

Define standard MCP error response shape.

Example cases:

- patient not found
- room mismatch
- multiple matches
- no visit found
- no current process found

Why:

- predictable failures are as important as success responses

### Phase 6: MCP Handler Implementation

#### Task 6.1

Create MCP package/folder structure.

Why:

- establishes the integration boundary

#### Task 6.2

Create tool handler class for `update_patient_room`.

Why:

- isolates one tool’s orchestration logic

#### Task 6.3

Implement validation for `update_patient_room` inputs.

Why:

- avoids invalid DB calls and ambiguous updates

#### Task 6.4

Implement patient/visit resolution for `update_patient_room`.

Why:

- ensures update targets the correct visit row

#### Task 6.5

Implement room update action and response assembly.

Why:

- this is the core behavior of tool 1

#### Task 6.6

Create tool handler class for `get_patient_current_process`.

Why:

- separates read-only process retrieval from update logic

#### Task 6.7

Implement validation for `get_patient_current_process` inputs.

Why:

- prevents invalid or broad lookups

#### Task 6.8

Implement patient/visit resolution for `get_patient_current_process`.

Why:

- correctness depends on selecting the right visit

#### Task 6.9

Implement full process aggregation for the latest visit.

Why:

- combines medicines, tasks, reports, and follow-up into one response

#### Task 6.10

Return final structured MCP payload for `get_patient_current_process`.

Why:

- makes the tool usable by agents and external clients

### Phase 7: Registration / Exposure

#### Task 7.1

Decide how MCP tools will be exposed from this app.

Examples:

- internal service interface
- local MCP server wrapper
- plugin bridge

Why:

- implementation depends on the actual MCP hosting model

#### Task 7.2

Register `update_patient_room` in the MCP tool registry.

Why:

- tool is not usable until registered

#### Task 7.3

Register `get_patient_current_process` in the MCP tool registry.

Why:

- tool is not usable until registered

### Phase 8: Validation and Testing

#### Task 8.1

Write test cases for patient lookup resolution.

Why:

- matching logic is the highest-risk part

#### Task 8.2

Write test cases for room update success and room mismatch failure.

Why:

- protects tool 1 from unsafe updates

#### Task 8.3

Write test cases for process summary retrieval.

Why:

- ensures tool 2 returns complete data

#### Task 8.4

Write test cases for empty-data scenarios.

Examples:

- patient exists but no visit
- visit exists but no medicines
- visit exists but no reports
- follow-up missing

Why:

- MCP tools should degrade gracefully

#### Task 8.5

Run end-to-end manual verification with sample patient data.

Why:

- confirms the tool works with real database behavior, not only unit assumptions

## Recommended Execution Order

We should complete tasks in this order:

1. Task 1.1
2. Task 1.2
3. Task 1.3
4. Task 1.4
5. Task 2.1
6. Task 2.2
7. Task 2.3
8. Task 2.4
9. Task 2.5
10. Task 2.6
11. Task 2.7
12. Task 3.1 to 3.7
13. Task 4.1 to 4.3
14. Task 5.1 to 5.6
15. Task 6.1 to 6.10
16. Task 7.1 to 7.3
17. Task 8.1 to 8.5

## What We Should Do Next

Start with **Task 1.1** only:

- define the exact contract for `update_patient_room`
- decide matching behavior
- decide what exact row gets updated
- decide exact success and failure responses

We should not start coding before that is locked, because the current schema stores room number in `visits`, not `patients`.

---

## Task 1.1 Completion

### Task Name

Define the exact functional contract for `update_patient_room`.

### Why This Task Must Come First

This tool sounds simple at the user level, but the current schema makes it slightly more specific:

- room number is not stored on the `patients` table
- room number is stored on the `visits` table
- one patient can have multiple visits

Because of that, we cannot say “update the patient room” unless we first define:

- how the patient is matched
- which visit row is eligible for update
- what happens if more than one row matches

If we skip this step, the actual implementation can become unsafe and may update the wrong visit.

### What This Tool Should Do

The tool should locate a single patient visit context using:

- patient name
- current room number

Then it should update that matched visit’s `roomNo` field to a new room number.

This is not a patient-table update. It is a visit-table update.

### Final Tool Name

`update_patient_room`

### Final Input Contract

Required fields:

- `patient_name: String`
- `current_room_number: String`
- `new_room_number: String`

### Input Rules

#### Rule 1

`patient_name` is required and must not be blank.

#### Rule 2

`current_room_number` is required and must not be blank.

#### Rule 3

`new_room_number` is required and must not be blank.

#### Rule 4

`new_room_number` must be different from `current_room_number`.

#### Rule 5

All three input strings should be trimmed before matching or updating.

#### Rule 6

Name matching should be case-insensitive.

Reason:

- user-entered names may vary in letter case

#### Rule 7

Room number matching should be case-insensitive after trim.

Reason:

- room values like `icu3` and `ICU3` should resolve to the same logical room

### Matching Contract

The tool will identify the target row using this rule:

- search visits joined with patients
- filter where patient name matches `patient_name`
- filter where visit `roomNo` matches `current_room_number`
- order results by latest visit first

### Exact Selection Rule

If exactly one matching visit is found:

- select that visit

If multiple matching visits are found:

- select the latest visit using:
  - highest `visitDate`
  - then highest `visitTime`
  - then highest `visitId` as final tie-breaker

If no matching visit is found:

- return a not-found failure response

### Why We Use Latest Visit As Tie-Breaker

This app stores room number on each visit. A patient may have:

- old visits in an old room
- multiple consultations over time

So the safest business rule is:

- if there are multiple patient-name + room matches, update only the latest visit row

This keeps the tool deterministic and avoids bulk updating history.

### Update Contract

The tool updates only one row:

- the selected visit row

Field updated:

- `visits.roomNo = new_room_number`

The tool must not:

- update the `patients` table
- update multiple visits at once
- create a new visit
- modify tasks, medicines, reports, or follow-ups

### Success Response Contract

The tool should return a structured success payload with:

- `success: true`
- `message`
- `patient_name`
- `visit_id`
- `patient_id`
- `old_room_number`
- `new_room_number`
- `visit_date`
- `visit_time`

### Example Success Meaning

Example message:

- `Room updated successfully for the latest matched visit.`

### Failure Response Contract

The tool should return `success: false` with a clear machine-usable and human-readable message.

#### Failure Case 1

Missing patient name

Message:

- `patient_name is required`

#### Failure Case 2

Missing current room number

Message:

- `current_room_number is required`

#### Failure Case 3

Missing new room number

Message:

- `new_room_number is required`

#### Failure Case 4

Same current and new room

Message:

- `new_room_number must be different from current_room_number`

#### Failure Case 5

No patient visit matches the provided patient name and current room number

Message:

- `No matching patient visit found for the given patient name and current room number`

#### Failure Case 6

Database update fails unexpectedly

Message:

- `Failed to update patient room`

### What This Tool Should Not Try To Solve

This v1 contract does not try to solve:

- fuzzy patient matching
- partial name matching
- ambiguity prompts
- moving all visits of a patient to a new room
- validating whether the new room exists in a master room table

Those can be future enhancements, but they should not be mixed into v1.

### Why This Contract Belongs At The Requirement Layer

This definition must be written before DAO or repository code because it controls:

- query shape
- ordering logic
- update scope
- error handling
- response schema

If this contract changes later, the downstream implementation changes too.

### Final Decision Summary For Task 1.1

- tool name: `update_patient_room`
- inputs: `patient_name`, `current_room_number`, `new_room_number`
- match target: patient + current room from joined `patients` and `visits`
- update target: one latest matching visit row only
- updated field: `visits.roomNo`
- no bulk updates
- case-insensitive name and room matching
- structured success and failure responses

---

## Task 1.2 Completion

### Task Name

Define the exact functional contract for `get_patient_current_process`.

### Why This Task Comes Next

This tool is broader than the room update tool. It is not a single-table lookup.

It has to combine data from multiple parts of the app:

- patient identity
- latest visit
- medicines
- tasks
- reports
- follow-up

If we do not lock the exact meaning of “current process” now, later implementation will become inconsistent. One developer might return only visit notes, another might include medicines, and another might use all visits instead of the latest visit.

### What This Tool Should Do

The tool should locate the correct patient context using:

- patient name
- room number

Then it should identify the latest matching visit and return a structured summary of the patient’s current clinical process for that visit.

In this app, “current process” should mean:

- the latest matched visit
- the process artifacts attached to that visit

This makes the tool aligned with the current schema, because medicines, tasks, reports, and follow-ups are all visit-linked.

### Final Tool Name

`get_patient_current_process`

### Final Input Contract

Required fields:

- `patient_name: String`
- `room_number: String`

### Input Rules

#### Rule 1

`patient_name` is required and must not be blank.

#### Rule 2

`room_number` is required and must not be blank.

#### Rule 3

Both fields should be trimmed before matching.

#### Rule 4

Patient name matching should be case-insensitive.

#### Rule 5

Room number matching should be case-insensitive after trim.

### Matching Contract

The tool will resolve the patient context by:

- joining `patients` and `visits`
- matching `patients.name` with `patient_name`
- matching `visits.roomNo` with `room_number`
- sorting matched visits by newest first

### Exact Visit Selection Rule

If exactly one matching visit is found:

- use that visit

If multiple matching visits are found:

- use the latest visit using:
  - highest `visitDate`
  - then highest `visitTime`
  - then highest `visitId`

If no matching visit is found:

- return a not-found failure response

### Why The Latest Matched Visit Is The Correct Rule

The app stores the room number inside each visit. A patient may appear in:

- older historical visits
- multiple consultations
- repeated room entries across time

So the safest and most deterministic rule is:

- find all visits for the matching patient name and room number
- pick the newest matching visit only

This avoids merging old clinical history into a current-process response.

### What “Current Process” Includes

The response should include data for the selected visit only.

Included sections:

#### Section 1

Patient summary

Fields:

- `patient_id`
- `patient_name`
- `age`
- `sex`
- `contact`
- `address`
- `medical_history`

#### Section 2

Visit summary

Fields:

- `visit_id`
- `visit_date`
- `visit_time`
- `room_number`
- `symptoms`
- `diagnosis`
- `progress_notes`

#### Section 3

Current prescriptions / medicines

For each medicine:

- `medicine_id`
- `name`
- `dosage`
- `duration`

#### Section 4

Current tasks / care process items

For each task:

- `task_id`
- `task_name`
- `assigned_to`
- `role`
- `instructions`
- `status`

#### Section 5

Reports / documents

For each report:

- `report_id`
- `file_name`
- `file_path`
- `file_type`

#### Section 6

Latest follow-up for the selected visit

Fields:

- `follow_up_id`
- `scheduled_date`
- `scheduled_time`
- `reason`
- `status`
- `is_notified`
- `notified_at`
- `completed_at`

### What This Tool Should Not Include In v1

This tool should not:

- merge data from multiple visits
- build a whole patient lifetime summary
- infer “currently taking” from all historical prescriptions
- summarize old reports from unrelated visits
- include unrelated follow-ups from other visits

This is important because the request says “last visit details” and “current process”. In the current schema, that maps best to one selected latest visit.

### Special Interpretation Of “Prescription Medicine Current Taking”

The app schema stores medicines per visit. It does not track a separate live medication adherence state.

So in v1, the tool should interpret:

- “prescription medicine current taking”

as:

- medicines attached to the selected latest visit

This is the most correct interpretation based on available data.

### Success Response Contract

The tool should return a structured success payload with:

- `success: true`
- `message`
- `patient`
- `visit`
- `medicines`
- `tasks`
- `reports`
- `follow_up`

### Example Success Meaning

Example message:

- `Current patient process loaded successfully from the latest matched visit.`

### Failure Response Contract

The tool should return `success: false` with a clear message.

#### Failure Case 1

Missing patient name

Message:

- `patient_name is required`

#### Failure Case 2

Missing room number

Message:

- `room_number is required`

#### Failure Case 3

No matching patient visit found for the given patient name and room number

Message:

- `No matching patient visit found for the given patient name and room number`

#### Failure Case 4

Matched patient exists but visit-linked details cannot be loaded

Message:

- `Failed to load patient current process`

### Empty Section Behavior

Some sections may legitimately be empty.

Allowed empty sections:

- `medicines = []`
- `tasks = []`
- `reports = []`
- `follow_up = null`

This should still be a success response if the patient and selected visit are found.

Reason:

- absence of medicines or reports is not an error
- the core requirement is to load the patient’s latest matched visit context

### Why This Contract Belongs At The Requirement Layer

This tool spans several tables and response sections. Before writing DAO methods, we need a fixed answer to:

- which visit is selected
- what “current process” means
- which sections are mandatory
- which sections can be empty
- what the response shape looks like

Without this contract, downstream DAO and repository design will drift.

### Final Decision Summary For Task 1.2

- tool name: `get_patient_current_process`
- inputs: `patient_name`, `room_number`
- match target: joined `patients` + `visits`
- selected row: latest matched visit only
- scope: one visit plus all visit-linked process data
- includes: patient, visit, medicines, tasks, reports, latest visit follow-up
- empty medicines/tasks/reports/follow-up are allowed
- no cross-visit aggregation in v1
- structured success and failure responses
