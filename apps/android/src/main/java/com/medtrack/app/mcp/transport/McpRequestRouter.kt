package com.medtrack.app.mcp.transport

import com.medtrack.app.mcp.AddPatientTaskRequest
import com.medtrack.app.mcp.AddOrUpdateMedicineRequest
import com.medtrack.app.mcp.CreatePatientRequest
import com.medtrack.app.mcp.CreateVisitRequest
import com.medtrack.app.mcp.DischargePatientRequest
import com.medtrack.app.mcp.GetPatientBloodReportsRequest
import com.medtrack.app.mcp.GetPatientCurrentProcessRequest
import com.medtrack.app.mcp.GetPatientMedicinesRequest
import com.medtrack.app.mcp.GetPatientReportsRequest
import com.medtrack.app.mcp.GetPatientTasksRequest
import com.medtrack.app.mcp.GetDueFollowUpsRequest
import com.medtrack.app.mcp.MarkFollowUpDoneRequest
import com.medtrack.app.mcp.MedTrackMcpCatalog
import com.medtrack.app.mcp.MedTrackMcpService
import com.medtrack.app.mcp.RemovePatientMedicineRequest
import com.medtrack.app.mcp.RescheduleFollowUpRequest
import com.medtrack.app.mcp.ScheduleFollowUpRequest
import com.medtrack.app.mcp.SearchPatientRequest
import com.medtrack.app.mcp.UpdatePatientRoomRequest
import com.medtrack.app.mcp.UpdatePatientTaskStatusRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class McpRequestRouter @Inject constructor(
    private val mcpService: MedTrackMcpService
) {
    fun handle(requestJson: JSONObject): McpProtocolResponse {
        val method = requestJson.optString("method")
        val id = requestJson.opt("id")
        val params = requestJson.optJSONObject("params") ?: JSONObject()

        if (requestJson.optString("jsonrpc") != "2.0") {
            return McpProtocolResponse.json(
                statusCode = 400,
                body = errorBody(id, -32600, "Invalid Request: jsonrpc must be '2.0'")
            )
        }

        if (method.isBlank()) {
            return McpProtocolResponse.json(
                statusCode = 400,
                body = errorBody(id, -32600, "Invalid Request: method is required")
            )
        }

        if (id == null || id == JSONObject.NULL) {
            handleNotification(method, params)
            return McpProtocolResponse.empty(202)
        }

        val result = when (method) {
            "initialize" -> successBody(id, initializeResult())
            "ping" -> successBody(id, JSONObject())
            "tools/list" -> successBody(id, listToolsResult())
            "tools/call" -> handleToolCall(id, params)
            else -> errorBody(id, -32601, "Method not found: $method")
        }

        return McpProtocolResponse.json(200, result)
    }

    private fun handleNotification(method: String, params: JSONObject) {
        when (method) {
            "notifications/initialized" -> Unit
            "notifications/cancelled" -> Unit
            else -> Unit
        }
    }

    private fun initializeResult(): JSONObject =
        JSONObject()
            .put("protocolVersion", MCP_PROTOCOL_VERSION)
            .put(
                "capabilities",
                JSONObject().put(
                    "tools",
                    JSONObject().put("listChanged", false)
                )
            )
            .put(
                "serverInfo",
                JSONObject()
                    .put("name", SERVER_NAME)
                    .put("version", SERVER_VERSION)
            )
            .put(
                "instructions",
                "Use tools/list to discover MedTrack tools, then tools/call to invoke them. " +
                    "Choose tools by category:\n${MedTrackMcpCatalog.toolSelectionInstructions}"
            )

    private fun listToolsResult(): JSONObject =
        JSONObject()
            .put("toolGroups", JSONArray(MedTrackMcpCatalog.toolGroups.map { it.toJson() }))
            .put(
                "tools",
                JSONArray(mcpService.listTools().map { it.toToolJson() })
            )

    private fun handleToolCall(id: Any, params: JSONObject): JSONObject {
        val toolName = params.optString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()

        val result = when (toolName) {
            MedTrackMcpCatalog.SEARCH_PATIENT -> runBlocking {
                mcpService.searchPatient(
                    SearchPatientRequest(
                        query = arg(arguments, "query", "query"),
                        limit = arguments.optInt("limit", 10).takeIf { it > 0 } ?: 10
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.CREATE_PATIENT -> runBlocking {
                mcpService.createPatient(
                    CreatePatientRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        age = arguments.optInt("age", 0),
                        sex = arg(arguments, "sex", "sex"),
                        contact = arg(arguments, "contact", "contact"),
                        address = arg(arguments, "address", "address"),
                        medicalHistory = arg(arguments, "medicalHistory", "medical_history")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.CREATE_PATIENT_VISIT,
            MedTrackMcpCatalog.CREATE_VISIT_ALIAS -> runBlocking {
                mcpService.createVisit(
                    CreateVisitRequest(
                        patientId = arg(arguments, "patientId", "patient_id").ifBlank { null },
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number"),
                        symptoms = arg(arguments, "symptoms", "symptoms"),
                        diagnosis = arg(arguments, "diagnosis", "diagnosis"),
                        progressNotes = arg(arguments, "progressNotes", "progress_notes"),
                        taskName = arg(arguments, "taskName", "task_name"),
                        taskAssignedTo = arg(arguments, "taskAssignedTo", "task_assigned_to"),
                        taskRole = arg(arguments, "taskRole", "task_role"),
                        taskInstructions = arg(arguments, "taskInstructions", "task_instructions"),
                        taskStatus = arg(arguments, "taskStatus", "task_status").ifBlank { "PENDING" },
                        medicineName = arg(arguments, "medicineName", "medicine_name"),
                        medicineDosage = arg(arguments, "medicineDosage", "medicine_dosage"),
                        medicineDuration = arg(arguments, "medicineDuration", "medicine_duration"),
                        medicineNotes = arg(arguments, "medicineNotes", "medicine_notes")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.DISCHARGE_PATIENT -> runBlocking {
                mcpService.dischargePatient(
                    DischargePatientRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.GET_PATIENT_MEDICINES -> runBlocking {
                mcpService.getPatientMedicines(
                    GetPatientMedicinesRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.ADD_OR_UPDATE_MEDICINE -> runBlocking {
                mcpService.addOrUpdateMedicine(
                    AddOrUpdateMedicineRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number"),
                        medicineName = arg(arguments, "medicineName", "medicine_name"),
                        dosage = arg(arguments, "dosage", "dosage"),
                        frequency = arg(arguments, "frequency", "frequency"),
                        duration = arg(arguments, "duration", "duration"),
                        notes = arg(arguments, "notes", "notes")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.REMOVE_PATIENT_MEDICINE -> runBlocking {
                mcpService.removePatientMedicine(
                    RemovePatientMedicineRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number"),
                        medicineName = arg(arguments, "medicineName", "medicine_name")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.GET_PATIENT_REPORTS -> runBlocking {
                mcpService.getPatientReports(
                    GetPatientReportsRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.GET_PATIENT_BLOOD_REPORTS -> runBlocking {
                mcpService.getPatientBloodReports(
                    GetPatientBloodReportsRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.ADD_PATIENT_TASK -> runBlocking {
                mcpService.addPatientTask(
                    AddPatientTaskRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number"),
                        taskTitle = arg(arguments, "taskTitle", "task_title"),
                        assignedTo = arg(arguments, "assignedTo", "assigned_to"),
                        role = arg(arguments, "role", "role"),
                        instructions = arg(arguments, "instructions", "instructions"),
                        status = arg(arguments, "status", "status").ifBlank { "PENDING" }
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.UPDATE_PATIENT_TASK_STATUS -> runBlocking {
                mcpService.updatePatientTaskStatus(
                    UpdatePatientTaskStatusRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number"),
                        taskName = arg(arguments, "taskName", "task_name"),
                        status = arg(arguments, "status", "status")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.GET_PATIENT_TASKS -> runBlocking {
                mcpService.getPatientTasks(
                    GetPatientTasksRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number"),
                        status = arg(arguments, "status", "status")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.SCHEDULE_FOLLOW_UP -> runBlocking {
                mcpService.scheduleFollowUp(
                    ScheduleFollowUpRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number"),
                        scheduledDate = arg(arguments, "scheduledDate", "scheduled_date"),
                        scheduledTime = arg(arguments, "scheduledTime", "scheduled_time"),
                        reason = arg(arguments, "reason", "reason")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.GET_DUE_FOLLOW_UPS -> runBlocking {
                mcpService.getDueFollowUps(
                    GetDueFollowUpsRequest(
                        date = arg(arguments, "date", "date"),
                        status = arg(arguments, "status", "status").ifBlank { "PENDING" },
                        mode = arg(arguments, "mode", "mode").ifBlank { "DUE" }
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.MARK_FOLLOW_UP_DONE -> runBlocking {
                mcpService.markFollowUpDone(
                    MarkFollowUpDoneRequest(
                        followUpId = arg(arguments, "followUpId", "follow_up_id").ifBlank { null },
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number"),
                        reason = arg(arguments, "reason", "reason")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.RESCHEDULE_FOLLOW_UP -> runBlocking {
                mcpService.rescheduleFollowUp(
                    RescheduleFollowUpRequest(
                        followUpId = arg(arguments, "followUpId", "follow_up_id").ifBlank { null },
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number"),
                        reason = arg(arguments, "reason", "reason"),
                        scheduledDate = arg(arguments, "scheduledDate", "scheduled_date"),
                        scheduledTime = arg(arguments, "scheduledTime", "scheduled_time"),
                        newReason = arg(arguments, "newReason", "new_reason")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.UPDATE_PATIENT_ROOM -> runBlocking {
                mcpService.updatePatientRoom(
                    UpdatePatientRoomRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        currentRoomNumber = arg(arguments, "currentRoomNumber", "current_room_number"),
                        newRoomNumber = arg(arguments, "newRoomNumber", "new_room_number")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.GET_PATIENT_CURRENT_PROCESS -> runBlocking {
                mcpService.getPatientCurrentProcess(
                    GetPatientCurrentProcessRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            else -> return errorBody(id, -32602, "Unknown tool: $toolName")
        }

        return successBody(id, result)
    }

    private fun arg(arguments: JSONObject, camelCase: String, snakeCase: String): String =
        arguments.optStringOrNull(camelCase)
            ?: arguments.optStringOrNull(snakeCase)
            ?: ""

    private fun toolResult(
        text: String,
        structuredContent: JSONObject,
        isError: Boolean
    ): JSONObject =
        JSONObject()
            .put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", text)
                )
            )
            .put("structuredContent", structuredContent)
            .put("isError", isError)

    private fun successBody(id: Any, result: JSONObject): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("result", result)

    private fun errorBody(id: Any?, code: Int, message: String): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id ?: JSONObject.NULL)
            .put(
                "error",
                JSONObject()
                    .put("code", code)
                    .put("message", message)
            )

    companion object {
        const val MCP_PROTOCOL_VERSION = "2025-11-25"
        private const val SERVER_NAME = "medtrack-local-mcp"
        private const val SERVER_VERSION = "1.0.0"
    }
}
