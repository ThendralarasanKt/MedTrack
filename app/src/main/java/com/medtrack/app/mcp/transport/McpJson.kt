package com.medtrack.app.mcp.transport

import com.medtrack.app.mcp.AddOrUpdateMedicineResponse
import com.medtrack.app.mcp.AddPatientTaskResponse
import com.medtrack.app.mcp.CreatePatientResponse
import com.medtrack.app.mcp.CreateVisitResponse
import com.medtrack.app.mcp.DischargePatientResponse
import com.medtrack.app.mcp.GetPatientBloodReportsResponse
import com.medtrack.app.mcp.GetPatientCurrentProcessResponse
import com.medtrack.app.mcp.GetDueFollowUpsResponse
import com.medtrack.app.mcp.GetPatientMedicinesResponse
import com.medtrack.app.mcp.GetPatientReportsResponse
import com.medtrack.app.mcp.GetPatientTasksResponse
import com.medtrack.app.mcp.McpFollowUpSummary
import com.medtrack.app.mcp.McpFollowUpWithPatientSummary
import com.medtrack.app.mcp.McpMedicineHistoryItem
import com.medtrack.app.mcp.McpMedicineSummary
import com.medtrack.app.mcp.McpPatientSummary
import com.medtrack.app.mcp.McpReportHistoryItem
import com.medtrack.app.mcp.McpReportSummary
import com.medtrack.app.mcp.McpTaskSummary
import com.medtrack.app.mcp.McpToolDescriptor
import com.medtrack.app.mcp.McpToolField
import com.medtrack.app.mcp.McpToolGroup
import com.medtrack.app.mcp.McpVisitSummary
import com.medtrack.app.mcp.MarkFollowUpDoneResponse
import com.medtrack.app.mcp.RemovePatientMedicineResponse
import com.medtrack.app.mcp.RescheduleFollowUpResponse
import com.medtrack.app.mcp.ScheduleFollowUpResponse
import com.medtrack.app.mcp.SearchPatientItem
import com.medtrack.app.mcp.SearchPatientResponse
import com.medtrack.app.mcp.UpdatePatientRoomResponse
import com.medtrack.app.mcp.UpdatePatientTaskStatusResponse
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
        .put("category", category)
        .put("useWhen", useWhen)
        .put(
            "inputSchema",
            JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", required)
                .put("additionalProperties", false)
        )
}

internal fun McpToolGroup.toJson(): JSONObject =
    JSONObject()
        .put("name", name)
        .put("description", description)
        .put("tools", JSONArray(tools))

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

internal fun SearchPatientResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("query", query)
        .put("patients", JSONArray(patients.map { it.toJson() }))

internal fun CreatePatientResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("patientId", patientId)
        .put("patientName", patientName)
        .put("age", age)
        .put("sex", sex)
        .put("contact", contact)
        .put("address", address)
        .put("medicalHistory", medicalHistory)
        .put("createdAt", createdAt)

internal fun CreateVisitResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("patient", patient?.toJson())
        .put("visit", visit?.toJson())
        .put("medicine", medicine?.toJson())
        .put("task", task?.toJson())

internal fun DischargePatientResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patient", patient?.toJson())
        .put("visit", visit?.toJson())
        .put("dischargedAt", dischargedAt)

internal fun GetPatientMedicinesResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patient", patient?.toJson())
        .put("roomNumber", roomNumber)
        .put("anchorVisitId", anchorVisitId)
        .put("medicines", JSONArray(medicines.map { it.toJson() }))

internal fun AddOrUpdateMedicineResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("action", action)
        .put("patient", patient?.toJson())
        .put("visit", visit?.toJson())
        .put("medicine", medicine?.toJson())

internal fun RemovePatientMedicineResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patient", patient?.toJson())
        .put("visit", visit?.toJson())
        .put("removedMedicine", removedMedicine?.toJson())

internal fun GetPatientReportsResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patient", patient?.toJson())
        .put("roomNumber", roomNumber)
        .put("anchorVisitId", anchorVisitId)
        .put("reports", JSONArray(reports.map { it.toJson() }))

internal fun GetPatientBloodReportsResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patient", patient?.toJson())
        .put("roomNumber", roomNumber)
        .put("visitId", visitId)
        .put("bloodReports", JSONArray(bloodReports.map { it.toJson() }))

internal fun AddPatientTaskResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patient", patient?.toJson())
        .put("visit", visit?.toJson())
        .put("task", task?.toJson())

internal fun UpdatePatientTaskStatusResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patient", patient?.toJson())
        .put("visit", visit?.toJson())
        .put("task", task?.toJson())
        .put("matches", JSONArray(matches.map { it.toJson() }))

internal fun GetPatientTasksResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patient", patient?.toJson())
        .put("visit", visit?.toJson())
        .put("tasks", JSONArray(tasks.map { it.toJson() }))

internal fun ScheduleFollowUpResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("patient", patient?.toJson())
        .put("visit", visit?.toJson())
        .put("followUp", followUp?.toJson())

internal fun GetDueFollowUpsResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("date", date)
        .put("mode", mode)
        .put("status", status)
        .put("followUps", JSONArray(followUps.map { it.toJson() }))

internal fun MarkFollowUpDoneResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("followUp", followUp?.toJson())

internal fun RescheduleFollowUpResponse.toStructuredContent(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("message", message)
        .put("suggestedRoomNumber", suggestedRoomNumber)
        .put("followUp", followUp?.toJson())

private fun SearchPatientItem.toJson(): JSONObject =
    JSONObject()
        .put("patientId", patientId)
        .put("patientName", patientName)
        .put("age", age)
        .put("sex", sex)
        .put("contact", contact)
        .put("address", address)
        .put("medicalHistory", medicalHistory)
        .put("latestRoomNumber", latestRoomNumber)

private fun McpMedicineHistoryItem.toJson(): JSONObject =
    JSONObject()
        .put("visitId", visitId)
        .put("visitDate", visitDate)
        .put("visitTime", visitTime)
        .put("roomNumber", roomNumber)
        .put("medicineId", medicineId)
        .put("medicineName", medicineName)
        .put("dosage", dosage)
        .put("duration", duration)
        .put("notes", notes)

private fun McpReportHistoryItem.toJson(): JSONObject =
    JSONObject()
        .put("visitId", visitId)
        .put("visitDate", visitDate)
        .put("visitTime", visitTime)
        .put("roomNumber", roomNumber)
        .put("reportId", reportId)
        .put("fileName", fileName)
        .put("filePath", filePath)
        .put("fileType", fileType)
        .put("uploadedAt", uploadedAt)

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

private fun McpFollowUpWithPatientSummary.toJson(): JSONObject =
    JSONObject()
        .put("followUpId", followUpId)
        .put("patientId", patientId)
        .put("patientName", patientName)
        .put("visitId", visitId)
        .put("roomNumber", roomNumber)
        .put("scheduledDate", scheduledDate)
        .put("scheduledTime", scheduledTime)
        .put("reason", reason)
        .put("status", status)
        .put("isNotified", isNotified)
        .put("notifiedAt", notifiedAt)
        .put("completedAt", completedAt)
