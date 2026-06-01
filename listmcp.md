# MedTrack MCP Plan Card

## Current MCP Status

The app currently has a working local MCP HTTP server and in-app MCP client.

Available tools:

1. `search_patient`
   - Searches patients by name, patient id, contact number, or room number.
   - Returns matching patient summaries with latest known room.

2. `update_patient_room`
   - Moves a patient from one room to another by updating the latest matched visit.
   - Verified with Ramesh room changes.

3. `get_patient_current_process`
   - Loads the latest matched visit, patient details, medicines, tasks, reports, and follow-up details.
   - Verified with Ramesh in room `101A`.

Current architecture rule:

```text
LLM decides the tool.
MCP executes the tool.
Repository changes or reads app data.
UI shows the result.
```

## Top Useful MCP Tools To Add Next

### 1. `search_patient`

Status:

Implemented and verified through live MCP.

Purpose:

Find patients by name, phone number, room number, or partial text.

Why this is important:

The AI should not need exact patient spelling every time. Before updating or reading patient data, it can search and confirm the correct patient.

Example request:

```text
Find Ramesh
```

Expected arguments:

```json
{
  "query": "Ramesh"
}
```

Expected result:

```json
{
  "success": true,
  "patients": [
    {
      "patientId": 1,
      "patientName": "Ramesh",
      "age": 21,
      "contact": "9876543210",
      "latestRoomNumber": "101A"
    }
  ]
}
```

### 2. `create_patient_visit`

Purpose:

Create a new patient visit with symptoms, diagnosis, room number, and progress notes.

Status:

Implemented as the advertised MCP tool name `create_patient_visit`. The older `create_visit` name is still accepted as a backward-compatible alias.

Why this is important:

This lets the assistant add real clinical workflow data instead of only reading or moving existing visits.

Example request:

```text
Create a visit for Ramesh in room 205B with fever and cough
```

Expected arguments:

```json
{
  "patientName": "Ramesh",
  "roomNumber": "205B",
  "symptoms": "fever and cough",
  "diagnosis": "",
  "progressNotes": ""
}
```

### 3. `create_patient`

Purpose:

Create a new patient record.

Status:

Implemented. Kotlin compile passed.

Why this is important:

The assistant should be able to register a new patient before creating visits, prescriptions, reports, or follow-ups.

Example request:

```text
Add new patient Kumar age 35 male phone 9876500000 with diabetes history
```

Expected arguments:

```json
{
  "patientName": "Kumar",
  "age": 35,
  "sex": "Male",
  "contact": "9876500000",
  "address": "",
  "medicalHistory": "diabetes"
}
```

Possibility:

Feasible. The app already has `PatientEntity`, `PatientDao.insertPatient`, and `PatientRepository.insertPatient`.

Safety rule:

If age, sex, or contact is missing, the assistant should ask a clarification instead of creating a weak patient record.

### 4. `get_patient_medicines`

Purpose:

Return medicines from the all  visit and display it like day 1 (asprin5mg ) to current date (asprin10mg ) in decendeing order recent visit first followed by it  prescription for a patient in a room.

Status:

Implemented and verified through live MCP.

Why this is important:

The user can ask natural questions like:

```text
What medicine is Ramesh in room 201A taking?
```

Expected behavior:

Resolve `Ramesh + 201A` to the latest matched visit, then return prescription medicines for that visit.

Expected arguments:

```json
{
  "patientName": "Ramesh",
  "roomNumber": "201A"
}
```

Expected result:

```json
{
  "success": true,
  "patientName": "Ramesh",
  "roomNumber": "201A",
  "visitId": 3,
  "medicines": [
    {
      "medicineName": "Paracetamol",
      "dosage": "500 mg",
      "frequency": "twice daily",
      "duration": "3 days"
    }
     {
        "medicineName": "Paracetamol",
        "dosage": "50 mg",
        "frequency": "twice daily",
        "duration": "1 days"
     }
     {
        "medicineName": "Paracetamol",
        "dosage": "10 mg",
        "frequency": "twice daily",
        "duration": "3 days"
     }
  ]
}
```

Possibility:

Feasible. The app already has `MedicineEntity`, `MedicineDao.getMedicinesForVisitNow`, and the existing `get_patient_current_process` already returns medicines.

Note:

This tool is a focused read-only tool. It is better than using `get_patient_current_process` when the user only asks about medicines.

### 5. `get_patient_reports`

Purpose:

Return all reports across every visit for the resolved patient, recent report first. The room number is used to safely resolve the patient, and each report includes its visit date, time, and room.

Status:

Implemented and verified through live MCP.

Why this is important:

The user can ask:

```text
Reports of Ramesh in room no 101A
```

Expected behavior:

Resolve `patientName + roomNumber` to the latest matched visit for safety, then fetch reports from all visits for that patient. Sort recent first by report upload timestamp, then report id.

Expected arguments:

```json
{
  "patientName": "Ramesh",
  "roomNumber": "101A"
}
```

Expected result:

```json
{
  "success": true,
  "patientName": "Ramesh",
  "roomNumber": "101A",
  "reports": [
    {
      "reportId": 10,
      "reportName": "CBC Report",
      "filePath": "...",
      "createdAt": "..."
    }
  ]
}
```

Possibility:

Feasible. The app already has `ReportEntity`, `ReportDao.getReportsForVisitNow`, and report file storage.

Open question:

Need to confirm whether `ReportEntity` has a timestamp field. If not, we can sort by report id descending as a practical recent-first fallback.

### 6. `get_patient_blood_reports`

Purpose:

Return only blood-related reports for the latest matched patient visit, recent first.

Status:

Implemented and verified through live MCP.

Why this is important:

The user can ask:

```text
Show the blood report of Ramesh in room no 101A
```

Expected behavior:

Resolve `patientName + roomNumber` to the latest matched visit, then filter reports where report name/type contains blood-related terms such as `blood`, `CBC`, `RBC`, `WBC`, `hemoglobin`, `platelet`, or similar.

Expected arguments:

```json
{
  "patientName": "Ramesh",
  "roomNumber": "101A"
}
```

Expected result:

```json
{
  "success": true,
  "patientName": "Ramesh",
  "roomNumber": "101A",
  "bloodReports": [
    {
      "reportId": 10,
      "reportName": "CBC Blood Report",
      "filePath": "...",
      "createdAt": "..."
    }
  ]
}
```

Possibility:

Partially feasible now. The app has reports, but report category/type may not be structured. If reports only have display names, first version can filter by report name keywords. Better version should add a `reportType` field later.

### 7. `add_patient_task`

Purpose:

Add a task for a patient visit, such as checking vitals, taking blood test, or reviewing report.

Status:

Implemented and verified through live MCP.

Why this is important:

Tasks are action items. This makes the assistant useful for day-to-day hospital workflow.

Example request:

```text
Add task for Ramesh in 101A to check temperature at 6 PM
```

Expected arguments:

```json
{
  "patientName": "Ramesh",
  "roomNumber": "101A",
  "taskTitle": "Check temperature",
  "taskTime": "18:00"
}
```

### 8. `add_or_update_medicine`

Purpose:

Add medicine instructions to the latest matched patient visit, or update an existing medicine entry.

Status:

Implemented and verified through live MCP.

Why this is important:

Medication is one of the most common doctor-assistant actions. It must be handled through strict schema validation.

Example request:

```text
Add paracetamol 500 mg twice daily for Ramesh in 101A
```

Expected arguments:

```json
{
  "patientName": "Ramesh",
  "roomNumber": "101A",
  "medicineName": "Paracetamol",
  "dosage": "500 mg",
  "frequency": "twice daily",
  "duration": ""
}
```

Safety rule:

This tool should return a clear success or failure message and should avoid guessing unclear medicine names or dosages.

### 9. `schedule_follow_up`

Purpose:

Create or update a follow-up reminder for a patient visit.

Status:

Implemented and verified through live MCP.

Why this is important:

Follow-up scheduling already exists in the app data. MCP should expose it so the assistant can manage reminders.

Example request:

```text
Schedule follow up for Ramesh in 101A tomorrow at 10 AM for report review
```

Expected arguments:

```json
{
  "patientName": "Ramesh",
  "roomNumber": "101A",
  "scheduledDate": "YYYY-MM-DD",
  "scheduledTime": "10:00",
  "reason": "report review"
}
```

## Recommended Build Order

1. `search_patient`
2. `create_patient`
3. `create_patient_visit`
4. `get_patient_medicines`
5. `get_patient_reports`
6. `get_patient_blood_reports`
7. `add_patient_task`
8. `schedule_follow_up`
9. `add_or_update_medicine`

Reason:

`search_patient` should come first because many later tools can use it for suggestions, validation, and safer patient resolution.

`create_patient` should come before `create_patient_visit` because a visit must belong to a patient.

Read-only tools like `get_patient_medicines`, `get_patient_reports`, and `get_patient_blood_reports` are safer to build before write-heavy tools like medicine updates.

## Additional Useful MCP Tools To Consider

These are not part of the original planned list, but they are useful for real daily workflow.

### A1. `update_patient_task_status`

Purpose:

Mark a task as `DONE` or `PENDING`.

Status:

Implemented and verified through live MCP.

Why this is important:

Doctors often ask natural questions like "is MRI done?" and then say "mark MRI done". The app already stores task status, so this tool should update only the matched task instead of creating a duplicate task.

Example request:

```text
Mark Kamal's MRI scan in room 21 as done
```

Expected arguments:

```json
{
  "patientName": "Kamal",
  "roomNumber": "21",
  "taskName": "MRI scan",
  "status": "DONE"
}
```

Safety rule:

If multiple tasks match the same name, return matches and ask for clarification instead of updating all.

### A2. `get_patient_tasks`

Purpose:

Return all tasks for the resolved patient visit, optionally filtered by status.

Status:

Implemented and verified through live MCP.

Why this is important:

The broad `get_patient_current_process` returns tasks, but a focused task tool is easier for an LLM to choose when the user asks "what work is pending?" or "what tasks are done?"

Example request:

```text
What tasks are pending for Kamal in room 21?
```

Expected arguments:

```json
{
  "patientName": "Kamal",
  "roomNumber": "21",
  "status": "PENDING"
}
```

### A3. `get_due_follow_ups`

Purpose:

Return follow-ups due today, overdue, or upcoming.

Status:

Implemented and verified through live MCP.

Why this is important:

This helps the assistant answer operational questions like "who needs follow-up today?" without opening each patient manually.

Example request:

```text
Show today's follow-ups
```

Expected arguments:

```json
{
  "date": "2026-05-27",
  "status": "PENDING"
}
```

### A4. `mark_follow_up_done`

Purpose:

Mark a follow-up as completed.

Status:

Implemented and verified through live MCP.

Why this is important:

Follow-ups already have a `DONE` state. MCP should expose it so the assistant can complete reminders after the doctor reviews the patient.

Example request:

```text
Mark Kamal's MRI review follow-up as done
```

Expected arguments:

```json
{
  "followUpId": 5
}
```

Alternative arguments:

```json
{
  "patientName": "Kamal",
  "roomNumber": "21",
  "reason": "MRI review"
}
```

### A5. `reschedule_follow_up`

Purpose:

Change the date/time/reason of an existing follow-up.

Status:

Implemented and verified through live MCP.

Why this is important:

Doctors often reschedule rather than create a new reminder. This avoids duplicate pending follow-ups for the same visit.

Example request:

```text
Move Kamal's MRI review to tomorrow at 11 AM
```

Expected arguments:

```json
{
  "followUpId": 5,
  "scheduledDate": "2026-05-28",
  "scheduledTime": "11:00",
  "reason": "MRI review"
}
```

### A6. `upload_patient_report_metadata`

Purpose:

Attach report metadata to a visit when a file is already saved or selected by the app.

Why this is important:

The UI can upload files, but MCP can help name and classify reports. This is useful for commands like "attach this as MRI report for Kamal".

Expected arguments:

```json
{
  "patientName": "Kamal",
  "roomNumber": "21",
  "fileName": "MRI report.pdf",
  "filePath": "/data/user/0/com.medtrack.app/files/reports/visit_4/mri_report.pdf",
  "fileType": "PDF",
  "reportType": "MRI"
}
```

Open question:

This should probably wait until reports have a structured `reportType` field. First version can store only the existing `fileName`, `filePath`, and `fileType`.

### A7. `get_patient_visit_history`

Purpose:

Return a compact timeline of all visits for a patient.

Why this is important:

The assistant often needs context before choosing a specific visit, especially when the same patient has many rooms over time.

Example request:

```text
Show Ramesh visit history
```

Expected arguments:

```json
{
  "patientName": "Ramesh"
}
```

### A8. `update_patient_basic_info`

Purpose:

Update patient name, age, sex, contact, address, or medical history.

Why this is important:

Patient information changes and typos happen. Without this tool, the assistant can create records but cannot safely correct them.

Example request:

```text
Update Kamal phone number to 9992229338
```

Expected arguments:

```json
{
  "patientId": 2,
  "contact": "9992229338"
}
```

Safety rule:

Prefer `patientId` for updates. If only name is provided and multiple matches exist, ask for clarification.

### A9. `discharge_patient`

Purpose:

Mark the latest visit as discharged or closed.

Why this is important:

Current visits and old visits need a clear boundary. Right now the app infers current state from latest visit and room. A discharge tool would make "active patient" logic cleaner.

Open question:

The current `VisitEntity` does not have a status/discharged field. This tool needs a small schema change before implementation.

### A10. `handover_summary`

Purpose:

Return a concise handover summary for one patient: current visit, diagnosis, pending tasks, active medicines, latest reports, and pending follow-up.

Why this is important:

This is one of the most useful LLM-facing tools for doctor workflow. It gives a clean shift handover without making the LLM call many separate tools.

Example request:

```text
Give handover for Kamal in room 21
```

Expected arguments:

```json
{
  "patientName": "Kamal",
  "roomNumber": "21"
}
```

Recommended build order for this new section:

1. `update_patient_task_status`
2. `get_patient_tasks`
3. `get_due_follow_ups`
4. `mark_follow_up_done`
5. `reschedule_follow_up`
6. `get_patient_visit_history`
7. `update_patient_basic_info`
8. `handover_summary`
9. `upload_patient_report_metadata`
10. `discharge_patient`

Reason:

Task and follow-up tools give immediate value using existing schema. Visit history and patient edits are also feasible with existing tables. Report metadata and discharge need more schema/design care.

## Update Rule

After every MCP task completion, update this file with:

1. What tool was added or changed.
2. What files were changed.
3. What test was run.
4. Whether the tool was verified through MCP.
5. What the next MCP task is.

## Completion Log

### 2026-05-25

Completed:

- Verified existing `update_patient_room` MCP tool.
- Verified existing `get_patient_current_process` MCP tool.
- Added this MCP plan card.
- Added requested future tools to the plan: `create_patient`, `get_patient_medicines`, `get_patient_reports`, and `get_patient_blood_reports`.
- Implemented `search_patient` MCP tool across models, catalog, service, router, JSON conversion, and patient DAO search.
- Verified compile with `./gradlew :app:compileDebugKotlin`.
- Install/MCP verification blocked because `adb devices` returned no connected device.

Next:

- Verify `search_patient` through MCP.
- Then implement `create_patient`.

### 2026-05-26

Completed:

- Implemented `create_patient` MCP tool across models, catalog, service, router, and JSON conversion.
- Added validation for patient name, age, sex, and contact before inserting.
- Reused existing `PatientEntity`, `PatientRepository.insertPatient`, and `PatientDao.insertPatient`.
- Verified compile with `./gradlew :app:compileDebugKotlin`.

Files changed:

- `app/src/main/java/com/medtrack/app/mcp/McpModels.kt`
- `app/src/main/java/com/medtrack/app/mcp/MedTrackMcpCatalog.kt`
- `app/src/main/java/com/medtrack/app/mcp/MedTrackMcpService.kt`
- `app/src/main/java/com/medtrack/app/mcp/transport/McpRequestRouter.kt`
- `app/src/main/java/com/medtrack/app/mcp/transport/McpJson.kt`
- `learning_inchbyInch.md`
- `listmcp.md`

Next:

- Verify `create_patient` through MCP.
- Then implement `create_patient_visit`.

### 2026-05-27

Completed:

- Verified `create_patient` through MCP by creating patient `Kamal`.
- Implemented and verified visit creation through MCP.
- Aligned the planned visit tool name by exposing `create_patient_visit`; kept `create_visit` accepted as a compatibility alias.
- Added grouped MCP tool discovery with `toolGroups`, `category`, and `useWhen` metadata.
- Implemented `get_patient_medicines` across models, catalog, service, router, and JSON conversion.

Files changed:

- `app/src/main/java/com/medtrack/app/mcp/McpModels.kt`
- `app/src/main/java/com/medtrack/app/mcp/MedTrackMcpCatalog.kt`
- `app/src/main/java/com/medtrack/app/mcp/MedTrackMcpService.kt`
- `app/src/main/java/com/medtrack/app/mcp/transport/McpRequestRouter.kt`
- `app/src/main/java/com/medtrack/app/mcp/transport/McpJson.kt`
- `listmcp.md`

Test:

- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat :app:installDebug`
- Verified `get_patient_medicines` through MCP using `Kamal` in room `21`.

Next:

- Planned MCP tool list is implemented. Next work should be refinement: add structured report types, medicine frequency column, and update/delete tools as needed.

Additional completed on 2026-05-27:

- Implemented and verified `get_patient_reports`.
- Updated `get_patient_reports` to return reports across all visits for the resolved patient, sorted by upload time newest first, while still using `patientName + roomNumber` as the safety anchor.
- Implemented and verified `get_patient_blood_reports`.
- Implemented and verified `add_patient_task`.
- Implemented and verified `schedule_follow_up`.
- Implemented and verified `add_or_update_medicine`.
- Changed `TaskDao.insertTask` and `MedicineDao.insertMedicine` to return inserted row IDs so MCP responses can identify created rows.
- Added `MedicineDao.updateMedicine` access through `ClinicalRepository` for medicine updates.

Additional files changed:

- `app/src/main/java/com/medtrack/app/data/db/dao/TaskDao.kt`
- `app/src/main/java/com/medtrack/app/data/db/dao/MedicineDao.kt`
- `app/src/main/java/com/medtrack/app/data/repository/ClinicalRepository.kt`

Final verification:

- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat :app:installDebug`
- Verified the new tools through the local MCP server at `http://127.0.0.1:8765/mcp`.

Additional completed after first additional-tool pass:

- Implemented and verified `update_patient_task_status`.
- Implemented and verified `get_patient_tasks`.
- Implemented and verified `get_due_follow_ups`.
- Implemented and verified `mark_follow_up_done`.
- Implemented and verified `reschedule_follow_up`.
- Verification side effects:
  - Marked Kamal's `Check vitals` task as `DONE`.
  - Rescheduled follow-up `5` to `2026-05-28 09:30` with reason `MRI review rescheduled`.
  - Marked follow-up `5` as `DONE`.

Additional files changed:

- `app/src/main/java/com/medtrack/app/mcp/McpModels.kt`
- `app/src/main/java/com/medtrack/app/mcp/MedTrackMcpCatalog.kt`
- `app/src/main/java/com/medtrack/app/mcp/MedTrackMcpService.kt`
- `app/src/main/java/com/medtrack/app/mcp/transport/McpRequestRouter.kt`
- `app/src/main/java/com/medtrack/app/mcp/transport/McpJson.kt`
- `app/src/main/java/com/medtrack/app/data/repository/ClinicalRepository.kt`
