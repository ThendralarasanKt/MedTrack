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
    val inputFields: List<McpToolField>,
    val category: String = "General",
    val useWhen: String = ""
)

data class McpToolGroup(
    val name: String,
    val description: String,
    val tools: List<String>
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
    val patientId: String? = null,
    val patientName: String? = null,
    val visitId: String? = null,
    val oldRoomNumber: String? = null,
    val newRoomNumber: String? = null,
    val visitDate: String? = null,
    val visitTime: String? = null
)

data class GetPatientCurrentProcessRequest(
    val patientName: String,
    val roomNumber: String
)

data class GetPatientMedicinesRequest(
    val patientName: String,
    val roomNumber: String
)

data class GetPatientReportsRequest(
    val patientName: String,
    val roomNumber: String
)

data class GetPatientBloodReportsRequest(
    val patientName: String,
    val roomNumber: String
)

data class AddPatientTaskRequest(
    val patientName: String,
    val roomNumber: String,
    val taskTitle: String,
    val assignedTo: String = "",
    val role: String = "",
    val instructions: String = "",
    val status: String = "PENDING"
)

data class ScheduleFollowUpRequest(
    val patientName: String,
    val roomNumber: String,
    val scheduledDate: String,
    val scheduledTime: String,
    val reason: String = ""
)

data class AddOrUpdateMedicineRequest(
    val patientName: String,
    val roomNumber: String,
    val medicineName: String,
    val dosage: String,
    val frequency: String = "",
    val duration: String = "",
    val notes: String = ""
)

data class RemovePatientMedicineRequest(
    val patientName: String,
    val roomNumber: String,
    val medicineName: String
)

data class DischargePatientRequest(
    val patientName: String,
    val roomNumber: String
)

data class UpdatePatientTaskStatusRequest(
    val patientName: String,
    val roomNumber: String,
    val taskName: String,
    val status: String
)

data class GetPatientTasksRequest(
    val patientName: String,
    val roomNumber: String,
    val status: String = ""
)

data class GetDueFollowUpsRequest(
    val date: String,
    val status: String = "PENDING",
    val mode: String = "DUE"
)

data class MarkFollowUpDoneRequest(
    val followUpId: String? = null,
    val patientName: String = "",
    val roomNumber: String = "",
    val reason: String = ""
)

data class RescheduleFollowUpRequest(
    val followUpId: String? = null,
    val patientName: String = "",
    val roomNumber: String = "",
    val reason: String = "",
    val scheduledDate: String,
    val scheduledTime: String,
    val newReason: String = ""
)

data class SearchPatientRequest(
    val query: String,
    val limit: Int = 10
)

data class SearchPatientItem(
    val patientId: String,
    val patientName: String,
    val age: Int,
    val sex: String,
    val contact: String,
    val address: String,
    val medicalHistory: String,
    val latestRoomNumber: String?
)

data class SearchPatientResponse(
    val success: Boolean,
    val message: String,
    val query: String,
    val patients: List<SearchPatientItem> = emptyList()
)

data class CreatePatientRequest(
    val patientName: String,
    val age: Int,
    val sex: String,
    val contact: String,
    val address: String = "",
    val medicalHistory: String = ""
)

data class CreatePatientResponse(
    val success: Boolean,
    val message: String,
    val patientId: String? = null,
    val patientName: String? = null,
    val age: String? = null,
    val sex: String? = null,
    val contact: String? = null,
    val address: String? = null,
    val medicalHistory: String? = null,
    val createdAt: String? = null
)

data class CreateVisitRequest(
    val patientId: String? = null,
    val patientName: String = "",
    val roomNumber: String,
    val symptoms: String = "",
    val diagnosis: String = "",
    val progressNotes: String = "",
    val taskName: String = "",
    val taskAssignedTo: String = "",
    val taskRole: String = "",
    val taskInstructions: String = "",
    val taskStatus: String = "PENDING",
    val medicineName: String = "",
    val medicineDosage: String = "",
    val medicineDuration: String = "",
    val medicineNotes: String = ""
)

data class CreateVisitResponse(
    val success: Boolean,
    val message: String,
    val patient: McpPatientSummary? = null,
    val visit: McpVisitSummary? = null,
    val medicine: McpMedicineSummary? = null,
    val task: McpTaskSummary? = null
)

data class McpMedicineHistoryItem(
    val visitId: String,
    val visitDate: String,
    val visitTime: String,
    val roomNumber: String,
    val medicineId: String,
    val medicineName: String,
    val dosage: String,
    val duration: String,
    val notes: String
)

data class GetPatientMedicinesResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patient: McpPatientSummary? = null,
    val roomNumber: String? = null,
    val anchorVisitId: String? = null,
    val medicines: List<McpMedicineHistoryItem> = emptyList()
)

data class McpReportHistoryItem(
    val visitId: String,
    val visitDate: String,
    val visitTime: String,
    val roomNumber: String,
    val reportId: String,
    val fileName: String,
    val filePath: String,
    val fileType: String,
    val uploadedAt: String
)

data class GetPatientReportsResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patient: McpPatientSummary? = null,
    val roomNumber: String? = null,
    val anchorVisitId: String? = null,
    val reports: List<McpReportHistoryItem> = emptyList()
)

data class GetPatientBloodReportsResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patient: McpPatientSummary? = null,
    val roomNumber: String? = null,
    val visitId: String? = null,
    val bloodReports: List<McpReportHistoryItem> = emptyList()
)

data class AddPatientTaskResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patient: McpPatientSummary? = null,
    val visit: McpVisitSummary? = null,
    val task: McpTaskSummary? = null
)

data class ScheduleFollowUpResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patient: McpPatientSummary? = null,
    val visit: McpVisitSummary? = null,
    val followUp: McpFollowUpSummary? = null
)

data class AddOrUpdateMedicineResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val action: String? = null,
    val patient: McpPatientSummary? = null,
    val visit: McpVisitSummary? = null,
    val medicine: McpMedicineSummary? = null
)

data class RemovePatientMedicineResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patient: McpPatientSummary? = null,
    val visit: McpVisitSummary? = null,
    val removedMedicine: McpMedicineSummary? = null
)

data class DischargePatientResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patient: McpPatientSummary? = null,
    val visit: McpVisitSummary? = null,
    val dischargedAt: String? = null
)

data class UpdatePatientTaskStatusResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patient: McpPatientSummary? = null,
    val visit: McpVisitSummary? = null,
    val task: McpTaskSummary? = null,
    val matches: List<McpTaskSummary> = emptyList()
)

data class GetPatientTasksResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patient: McpPatientSummary? = null,
    val visit: McpVisitSummary? = null,
    val tasks: List<McpTaskSummary> = emptyList()
)

data class McpFollowUpWithPatientSummary(
    val followUpId: String,
    val patientId: String,
    val patientName: String,
    val visitId: String,
    val roomNumber: String,
    val scheduledDate: String,
    val scheduledTime: String,
    val reason: String,
    val status: String,
    val isNotified: Boolean,
    val notifiedAt: String?,
    val completedAt: String?
)

data class GetDueFollowUpsResponse(
    val success: Boolean,
    val message: String,
    val date: String,
    val mode: String,
    val status: String,
    val followUps: List<McpFollowUpWithPatientSummary> = emptyList()
)

data class MarkFollowUpDoneResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val followUp: McpFollowUpWithPatientSummary? = null
)

data class RescheduleFollowUpResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val followUp: McpFollowUpWithPatientSummary? = null
)

data class McpPatientSummary(
    val patientId: String,
    val patientName: String,
    val age: Int,
    val sex: String,
    val contact: String,
    val address: String,
    val medicalHistory: String
)

data class McpVisitSummary(
    val visitId: String,
    val visitDate: String,
    val visitTime: String,
    val roomNumber: String,
    val symptoms: String,
    val diagnosis: String,
    val progressNotes: String
)

data class McpMedicineSummary(
    val medicineId: String,
    val name: String,
    val dosage: String,
    val duration: String,
    val notes: String
)

data class McpTaskSummary(
    val taskId: String,
    val taskName: String,
    val assignedTo: String,
    val role: String,
    val instructions: String,
    val status: String
)

data class McpReportSummary(
    val reportId: String,
    val fileName: String,
    val filePath: String,
    val fileType: String,
    val uploadedAt: String
)

data class McpFollowUpSummary(
    val followUpId: String,
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
