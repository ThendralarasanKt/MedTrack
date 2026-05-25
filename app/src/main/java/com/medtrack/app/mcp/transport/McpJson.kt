package com.medtrack.app.mcp.transport

import com.medtrack.app.mcp.GetPatientCurrentProcessResponse
import com.medtrack.app.mcp.McpFollowUpSummary
import com.medtrack.app.mcp.McpMedicineSummary
import com.medtrack.app.mcp.McpPatientSummary
import com.medtrack.app.mcp.McpReportSummary
import com.medtrack.app.mcp.McpTaskSummary
import com.medtrack.app.mcp.McpToolDescriptor
import com.medtrack.app.mcp.McpToolField
import com.medtrack.app.mcp.McpVisitSummary
import com.medtrack.app.mcp.UpdatePatientRoomResponse
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name)

internal fun McpToolDescriptor.toToolJson(): JSONObject {
    val properties = JSONObject()
    val required = JSONArray()

    inputFields.forEach { field ->
        properties.put(field.name, field.toSchemaJson())
        if (field.required) {
            required.put(field.name)
        }
    }

    return JSONObject()
        .put("name", name)
        .put("description", description)
        .put(
            "inputSchema",
            JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", required)
                .put("additionalProperties", false)
        )
}

private fun McpToolField.toSchemaJson(): JSONObject =
    JSONObject()
        .put("type", type)
        .put("description", description)

internal fun UpdatePatientRoomResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patientId", patientId)
        .put("patientName", patientName)
        .put("visitId", visitId)
        .put("oldRoomNumber", oldRoomNumber)
        .put("newRoomNumber", newRoomNumber)
        .put("visitDate", visitDate)
        .put("visitTime", visitTime)

internal fun GetPatientCurrentProcessResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patient", patient?.toJson())
        .put("visit", visit?.toJson())
        .put("medicines", JSONArray(medicines.map { it.toJson() }))
        .put("tasks", JSONArray(tasks.map { it.toJson() }))
        .put("reports", JSONArray(reports.map { it.toJson() }))
        .put("followUp", followUp?.toJson())

private fun McpPatientSummary.toJson(): JSONObject =
    JSONObject()
        .put("patientId", patientId)
        .put("patientName", patientName)
        .put("age", age)
        .put("sex", sex)
        .put("contact", contact)
        .put("address", address)
        .put("medicalHistory", medicalHistory)

private fun McpVisitSummary.toJson(): JSONObject =
    JSONObject()
        .put("visitId", visitId)
        .put("visitDate", visitDate)
        .put("visitTime", visitTime)
        .put("roomNumber", roomNumber)
        .put("symptoms", symptoms)
        .put("diagnosis", diagnosis)
        .put("progressNotes", progressNotes)

private fun McpMedicineSummary.toJson(): JSONObject =
    JSONObject()
        .put("medicineId", medicineId)
        .put("name", name)
        .put("dosage", dosage)
        .put("duration", duration)
        .put("notes", notes)

private fun McpTaskSummary.toJson(): JSONObject =
    JSONObject()
        .put("taskId", taskId)
        .put("taskName", taskName)
        .put("assignedTo", assignedTo)
        .put("role", role)
        .put("instructions", instructions)
        .put("status", status)

private fun McpReportSummary.toJson(): JSONObject =
    JSONObject()
        .put("reportId", reportId)
        .put("fileName", fileName)
        .put("filePath", filePath)
        .put("fileType", fileType)
        .put("uploadedAt", uploadedAt)

private fun McpFollowUpSummary.toJson(): JSONObject =
    JSONObject()
        .put("followUpId", followUpId)
        .put("scheduledDate", scheduledDate)
        .put("scheduledTime", scheduledTime)
        .put("reason", reason)
        .put("status", status)
        .put("isNotified", isNotified)
        .put("notifiedAt", notifiedAt)
        .put("completedAt", completedAt)
