package com.medtrack.app.mcp

object MedTrackMcpCatalog {
    const val UPDATE_PATIENT_ROOM = "update_patient_room"
    const val GET_PATIENT_CURRENT_PROCESS = "get_patient_current_process"
    const val GET_PATIENT_MEDICINES = "get_patient_medicines"
    const val GET_PATIENT_REPORTS = "get_patient_reports"
    const val GET_PATIENT_BLOOD_REPORTS = "get_patient_blood_reports"
    const val ADD_PATIENT_TASK = "add_patient_task"
    const val UPDATE_PATIENT_TASK_STATUS = "update_patient_task_status"
    const val GET_PATIENT_TASKS = "get_patient_tasks"
    const val SCHEDULE_FOLLOW_UP = "schedule_follow_up"
    const val GET_DUE_FOLLOW_UPS = "get_due_follow_ups"
    const val MARK_FOLLOW_UP_DONE = "mark_follow_up_done"
    const val RESCHEDULE_FOLLOW_UP = "reschedule_follow_up"
    const val ADD_OR_UPDATE_MEDICINE = "add_or_update_medicine"
    const val REMOVE_PATIENT_MEDICINE = "remove_patient_medicine"
    const val SEARCH_PATIENT = "search_patient"
    const val CREATE_PATIENT = "create_patient"
    const val CREATE_PATIENT_VISIT = "create_patient_visit"
    const val CREATE_VISIT_ALIAS = "create_visit"
    const val DISCHARGE_PATIENT = "discharge_patient"

    private const val PATIENT_RECORDS = "Patient Records"
    private const val CLINICAL_VISITS = "Clinical Visits"
    private const val CARE_PROCESS = "Care Process"
    private const val MEDICINES = "Medicines"
    private const val REPORTS = "Reports"
    private const val TASKS = "Tasks"
    private const val FOLLOW_UPS = "Follow Ups"

    val tools: List<McpToolDescriptor> = listOf(
        McpToolDescriptor(
            name = SEARCH_PATIENT,
            description = "Search patients by name, patient id, contact number, or room number. Returns matching patient summaries with latest known room.",
            inputFields = listOf(
                McpToolField("query", "string", true, "Search text such as patient name, id, phone number, or room number."),
                McpToolField("limit", "integer", false, "Maximum number of matches to return. Defaults to 10.")
            ),
            category = PATIENT_RECORDS,
            useWhen = "Use before creating visits when the patient id is unknown, or whenever the user asks to find an existing patient."
        ),
        McpToolDescriptor(
            name = CREATE_PATIENT,
            description = "Create a new patient record with required age, sex, and contact details.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Patient full name."),
                McpToolField("age", "integer", true, "Patient age in years. Must be greater than zero."),
                McpToolField("sex", "string", true, "Patient sex such as Male, Female, or Other."),
                McpToolField("contact", "string", true, "Patient phone/contact number."),
                McpToolField("address", "string", false, "Patient address, if available."),
                McpToolField("medicalHistory", "string", false, "Known medical history, if available.")
            ),
            category = PATIENT_RECORDS,
            useWhen = "Use when the user asks to add, register, or create a new patient."
        ),
        McpToolDescriptor(
            name = CREATE_PATIENT_VISIT,
            description = "Create a patient visit with room, symptoms, diagnosis, progress notes, one optional task, and one optional medicine.",
            inputFields = listOf(
                McpToolField("patientId", "integer", false, "Patient id. Preferred when known."),
                McpToolField("patientName", "string", false, "Patient name to resolve when patientId is not supplied."),
                McpToolField("roomNumber", "string", true, "Room or bed number for this visit."),
                McpToolField("symptoms", "string", false, "Patient symptoms."),
                McpToolField("diagnosis", "string", false, "Diagnosis or clinical impression."),
                McpToolField("progressNotes", "string", false, "Visit notes or plan."),
                McpToolField("taskName", "string", false, "Clinical task name, such as X-ray."),
                McpToolField("taskAssignedTo", "string", false, "Task assignee, if known."),
                McpToolField("taskRole", "string", false, "Task role, such as Nurse or Radiology."),
                McpToolField("taskInstructions", "string", false, "Task instructions."),
                McpToolField("taskStatus", "string", false, "Task status. Defaults to PENDING."),
                McpToolField("medicineName", "string", false, "Medicine name."),
                McpToolField("medicineDosage", "string", false, "Medicine dosage."),
                McpToolField("medicineDuration", "string", false, "Medicine duration."),
                McpToolField("medicineNotes", "string", false, "Medicine notes.")
            ),
            category = CLINICAL_VISITS,
            useWhen = "Use when the user asks to add a consultation, visit, room admission, symptoms, diagnosis, task, or prescription for a patient."
        ),
        McpToolDescriptor(
            name = DISCHARGE_PATIENT,
            description = "Mark a patient as discharged using the latest matched visit context.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number used to resolve the latest matched visit.")
            ),
            category = CLINICAL_VISITS,
            useWhen = "Use when the user asks to discharge, close, or complete the current patient case."
        ),
        McpToolDescriptor(
            name = GET_PATIENT_MEDICINES,
            description = "Return a patient's medicine history across visits, resolved from the patient name and current room.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Current room number used to resolve the patient safely.")
            ),
            category = MEDICINES,
            useWhen = "Use when the user asks what medicines a patient is taking, medicine history, prescription history, or dose changes."
        ),
        McpToolDescriptor(
            name = ADD_OR_UPDATE_MEDICINE,
            description = "Add a medicine to the latest matched patient visit, or update an existing medicine with the same name.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number used to resolve the latest matched visit."),
                McpToolField("medicineName", "string", true, "Medicine name."),
                McpToolField("dosage", "string", true, "Medicine dosage, such as 500 mg."),
                McpToolField("frequency", "string", false, "Frequency instruction, such as twice daily."),
                McpToolField("duration", "string", false, "Medicine duration, such as 3 days."),
                McpToolField("notes", "string", false, "Additional medicine notes.")
            ),
            category = MEDICINES,
            useWhen = "Use when the user asks to add, prescribe, change, or update a medicine for a patient in a room."
        ),
        McpToolDescriptor(
            name = REMOVE_PATIENT_MEDICINE,
            description = "Remove a medicine from the latest matched patient visit.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number used to resolve the latest matched visit."),
                McpToolField("medicineName", "string", true, "Medicine name to remove from the visit.")
            ),
            category = MEDICINES,
            useWhen = "Use when the user asks to stop, remove, delete, or discontinue a medicine for a patient in a room."
        ),
        McpToolDescriptor(
            name = GET_PATIENT_REPORTS,
            description = "Return all reports across every visit for the resolved patient, recent upload first.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number used to resolve the latest matched visit.")
            ),
            category = REPORTS,
            useWhen = "Use when the user asks for reports, documents, scans, PDFs, X-rays, or uploaded files for a patient. The room resolves the patient safely; results include reports from all visits."
        ),
        McpToolDescriptor(
            name = GET_PATIENT_BLOOD_REPORTS,
            description = "Return blood-related reports for the latest matched patient visit, recent upload first.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number used to resolve the latest matched visit.")
            ),
            category = REPORTS,
            useWhen = "Use when the user asks specifically for blood reports, CBC, RBC, WBC, hemoglobin, platelet, or lab blood documents."
        ),
        McpToolDescriptor(
            name = ADD_PATIENT_TASK,
            description = "Add a clinical task to the latest matched patient visit.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number used to resolve the latest matched visit."),
                McpToolField("taskTitle", "string", true, "Task title, such as Check temperature or Take X-ray."),
                McpToolField("assignedTo", "string", false, "Task assignee, if known."),
                McpToolField("role", "string", false, "Role responsible for the task, such as Nurse, Lab, or Radiology."),
                McpToolField("instructions", "string", false, "Extra task instructions."),
                McpToolField("status", "string", false, "Task status. Defaults to PENDING. Use DONE only if explicitly completed.")
            ),
            category = TASKS,
            useWhen = "Use when the user asks to add an action item, task, test, nursing instruction, lab work, scan, or clinical todo for a patient."
        ),
        McpToolDescriptor(
            name = UPDATE_PATIENT_TASK_STATUS,
            description = "Mark a task on the latest matched patient visit as DONE or PENDING.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number used to resolve the latest matched visit."),
                McpToolField("taskName", "string", true, "Task name to update."),
                McpToolField("status", "string", true, "New task status: DONE or PENDING.")
            ),
            category = TASKS,
            useWhen = "Use when the user asks to mark a task done, pending, completed, or not completed."
        ),
        McpToolDescriptor(
            name = GET_PATIENT_TASKS,
            description = "Return tasks for the latest matched patient visit, optionally filtered by status.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number used to resolve the latest matched visit."),
                McpToolField("status", "string", false, "Optional filter: PENDING or DONE.")
            ),
            category = TASKS,
            useWhen = "Use when the user asks what tasks are pending, done, assigned, or listed for a patient."
        ),
        McpToolDescriptor(
            name = SCHEDULE_FOLLOW_UP,
            description = "Schedule a follow-up reminder for the latest matched patient visit.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number used to resolve the latest matched visit."),
                McpToolField("scheduledDate", "string", true, "Follow-up date in YYYY-MM-DD format."),
                McpToolField("scheduledTime", "string", true, "Follow-up time in HH:mm 24-hour format."),
                McpToolField("reason", "string", false, "Reason for the follow-up.")
            ),
            category = FOLLOW_UPS,
            useWhen = "Use when the user asks to schedule, set, create, or update a follow-up reminder or next appointment."
        ),
        McpToolDescriptor(
            name = GET_DUE_FOLLOW_UPS,
            description = "Return follow-ups due today, overdue, upcoming, or on a specific date.",
            inputFields = listOf(
                McpToolField("date", "string", true, "Reference date in YYYY-MM-DD format."),
                McpToolField("status", "string", false, "Status filter. Defaults to PENDING."),
                McpToolField("mode", "string", false, "TODAY, OVERDUE, UPCOMING, or DUE. Defaults to DUE.")
            ),
            category = FOLLOW_UPS,
            useWhen = "Use when the user asks who needs follow-up today, overdue follow-ups, or upcoming reminders."
        ),
        McpToolDescriptor(
            name = MARK_FOLLOW_UP_DONE,
            description = "Mark a follow-up reminder as DONE.",
            inputFields = listOf(
                McpToolField("followUpId", "integer", false, "Follow-up id. Preferred when known."),
                McpToolField("patientName", "string", false, "Patient name to resolve when followUpId is not supplied."),
                McpToolField("roomNumber", "string", false, "Room number to resolve when followUpId is not supplied."),
                McpToolField("reason", "string", false, "Reason text to match when followUpId is not supplied.")
            ),
            category = FOLLOW_UPS,
            useWhen = "Use when the user asks to complete, finish, close, or mark a follow-up as done."
        ),
        McpToolDescriptor(
            name = RESCHEDULE_FOLLOW_UP,
            description = "Reschedule an existing follow-up reminder.",
            inputFields = listOf(
                McpToolField("followUpId", "integer", false, "Follow-up id. Preferred when known."),
                McpToolField("patientName", "string", false, "Patient name to resolve when followUpId is not supplied."),
                McpToolField("roomNumber", "string", false, "Room number to resolve when followUpId is not supplied."),
                McpToolField("reason", "string", false, "Existing reason text to match when followUpId is not supplied."),
                McpToolField("scheduledDate", "string", true, "New follow-up date in YYYY-MM-DD format."),
                McpToolField("scheduledTime", "string", true, "New follow-up time in HH:mm 24-hour format."),
                McpToolField("newReason", "string", false, "New reason text. Keeps old reason if blank.")
            ),
            category = FOLLOW_UPS,
            useWhen = "Use when the user asks to move, reschedule, postpone, or change a follow-up reminder."
        ),
        McpToolDescriptor(
            name = UPDATE_PATIENT_ROOM,
            description = "Update the room number on the latest visit that matches the given patient name and current room number.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("currentRoomNumber", "string", true, "Current room number on the matched visit."),
                McpToolField("newRoomNumber", "string", true, "New room number to store on the matched visit.")
            ),
            category = CLINICAL_VISITS,
            useWhen = "Use when the user asks to move a patient from one room or bed number to another."
        ),
        McpToolDescriptor(
            name = GET_PATIENT_CURRENT_PROCESS,
            description = "Load the latest matched visit and its patient, medicines, tasks, reports, and follow-up details.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number on the visit to resolve.")
            ),
            category = CARE_PROCESS,
            useWhen = "Use when the user asks what is currently happening with a patient, including active visit, medicines, tasks, reports, or follow-up."
        )
    )

    val toolGroups: List<McpToolGroup> = listOf(
        McpToolGroup(
            name = PATIENT_RECORDS,
            description = "Find or create the core patient identity record.",
            tools = tools.filter { it.category == PATIENT_RECORDS }.map { it.name }
        ),
        McpToolGroup(
            name = CLINICAL_VISITS,
            description = "Create or update visit-level clinical information such as room, symptoms, diagnosis, tasks, and medicines.",
            tools = tools.filter { it.category == CLINICAL_VISITS }.map { it.name }
        ),
        McpToolGroup(
            name = MEDICINES,
            description = "Read medication and prescription information for a resolved patient.",
            tools = tools.filter { it.category == MEDICINES }.map { it.name }
        ),
        McpToolGroup(
            name = REPORTS,
            description = "Read report and document information for a resolved patient visit.",
            tools = tools.filter { it.category == REPORTS }.map { it.name }
        ),
        McpToolGroup(
            name = TASKS,
            description = "Create and manage visit-level clinical tasks.",
            tools = tools.filter { it.category == TASKS }.map { it.name }
        ),
        McpToolGroup(
            name = FOLLOW_UPS,
            description = "Schedule follow-up reminders for patient visits.",
            tools = tools.filter { it.category == FOLLOW_UPS }.map { it.name }
        ),
        McpToolGroup(
            name = CARE_PROCESS,
            description = "Read the combined current clinical state for a patient and room.",
            tools = tools.filter { it.category == CARE_PROCESS }.map { it.name }
        )
    )

    val toolSelectionInstructions: String =
        toolGroups.joinToString(separator = "\n") { group ->
            val toolNames = group.tools.joinToString(", ")
            "${group.name}: ${group.description} Tools: $toolNames."
        }

    val mutatingToolNames: Set<String> = setOf(
        UPDATE_PATIENT_ROOM,
        ADD_PATIENT_TASK,
        UPDATE_PATIENT_TASK_STATUS,
        SCHEDULE_FOLLOW_UP,
        MARK_FOLLOW_UP_DONE,
        RESCHEDULE_FOLLOW_UP,
        ADD_OR_UPDATE_MEDICINE,
        REMOVE_PATIENT_MEDICINE,
        CREATE_PATIENT,
        CREATE_PATIENT_VISIT,
        CREATE_VISIT_ALIAS,
        DISCHARGE_PATIENT
    )

    fun isMutatingTool(name: String): Boolean = name in mutatingToolNames
}
