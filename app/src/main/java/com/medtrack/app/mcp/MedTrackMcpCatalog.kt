package com.medtrack.app.mcp

object MedTrackMcpCatalog {
    const val UPDATE_PATIENT_ROOM = "update_patient_room"
    const val GET_PATIENT_CURRENT_PROCESS = "get_patient_current_process"

    val tools: List<McpToolDescriptor> = listOf(
        McpToolDescriptor(
            name = UPDATE_PATIENT_ROOM,
            description = "Update the room number on the latest visit that matches the given patient name and current room number.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("currentRoomNumber", "string", true, "Current room number on the matched visit."),
                McpToolField("newRoomNumber", "string", true, "New room number to store on the matched visit.")
            )
        ),
        McpToolDescriptor(
            name = GET_PATIENT_CURRENT_PROCESS,
            description = "Load the latest matched visit and its patient, medicines, tasks, reports, and follow-up details.",
            inputFields = listOf(
                McpToolField("patientName", "string", true, "Exact patient name, matched case-insensitively."),
                McpToolField("roomNumber", "string", true, "Room number on the visit to resolve.")
            )
        )
    )
}
