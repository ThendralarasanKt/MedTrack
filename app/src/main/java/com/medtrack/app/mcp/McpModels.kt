package com.medtrack.app.mcp

data class McpToolField(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)

data class McpToolDescriptor(
    val name: String,
    val description: String,
    val inputFields: List<McpToolField>
)

data class UpdatePatientRoomRequest(
    val patientName: String,
    val currentRoomNumber: String,
    val newRoomNumber: String
)

data class UpdatePatientRoomResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patientId: Int? = null,
    val patientName: String? = null,
    val visitId: Int? = null,
    val oldRoomNumber: String? = null,
    val newRoomNumber: String? = null,
    val visitDate: String? = null,
    val visitTime: String? = null
)

data class GetPatientCurrentProcessRequest(
    val patientName: String,
    val roomNumber: String
)

data class McpPatientSummary(
    val patientId: Int,
    val patientName: String,
    val age: Int,
    val sex: String,
    val contact: String,
    val address: String,
    val medicalHistory: String
)

data class McpVisitSummary(
    val visitId: Int,
    val visitDate: String,
    val visitTime: String,
    val roomNumber: String,
    val symptoms: String,
    val diagnosis: String,
    val progressNotes: String
)

data class McpMedicineSummary(
    val medicineId: Int,
    val name: String,
    val dosage: String,
    val duration: String,
    val notes: String
)

data class McpTaskSummary(
    val taskId: Int,
    val taskName: String,
    val assignedTo: String,
    val role: String,
    val instructions: String,
    val status: String
)

data class McpReportSummary(
    val reportId: Int,
    val fileName: String,
    val filePath: String,
    val fileType: String,
    val uploadedAt: String
)

data class McpFollowUpSummary(
    val followUpId: Int,
    val scheduledDate: String,
    val scheduledTime: String,
    val reason: String,
    val status: String,
    val isNotified: Boolean,
    val notifiedAt: String?,
    val completedAt: String?
)

data class GetPatientCurrentProcessResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patient: McpPatientSummary? = null,
    val visit: McpVisitSummary? = null,
    val medicines: List<McpMedicineSummary> = emptyList(),
    val tasks: List<McpTaskSummary> = emptyList(),
    val reports: List<McpReportSummary> = emptyList(),
    val followUp: McpFollowUpSummary? = null
)
