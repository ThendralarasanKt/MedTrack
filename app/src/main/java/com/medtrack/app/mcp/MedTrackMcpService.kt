package com.medtrack.app.mcp

import com.medtrack.app.data.db.model.PatientCurrentProcess
import com.medtrack.app.data.db.model.PatientVisitContext
import com.medtrack.app.data.repository.ClinicalRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedTrackMcpService @Inject constructor(
    private val clinicalRepository: ClinicalRepository
) {
    suspend fun updatePatientRoom(request: UpdatePatientRoomRequest): UpdatePatientRoomResponse {
        val patientName = request.patientName.trim()
        val currentRoomNumber = request.currentRoomNumber.trim()
        val newRoomNumber = request.newRoomNumber.trim()

        if (patientName.isBlank()) {
            return UpdatePatientRoomResponse(success = false, message = "patient_name is required")
        }
        if (currentRoomNumber.isBlank()) {
            return UpdatePatientRoomResponse(success = false, message = "current_room_number is required")
        }
        if (newRoomNumber.isBlank()) {
            return UpdatePatientRoomResponse(success = false, message = "new_room_number is required")
        }
        if (currentRoomNumber.equals(newRoomNumber, ignoreCase = true)) {
            return UpdatePatientRoomResponse(
                success = false,
                message = "new_room_number must be different from current_room_number"
            )
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, currentRoomNumber)
            .firstOrNull()
            ?: return UpdatePatientRoomResponse(
                success = false,
                message = buildRoomSuggestionMessage(patientName, currentRoomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(currentRoomNumber, ignoreCase = true) }
            )

        val updated = clinicalRepository.updateVisitRoomNo(context.visitId, newRoomNumber)
        if (updated <= 0) {
            return UpdatePatientRoomResponse(
                success = false,
                message = "Failed to update patient room"
            )
        }

        return UpdatePatientRoomResponse(
            success = true,
            message = "Room updated successfully for the latest matched visit.",
            patientId = context.patientId,
            patientName = context.patientName,
            visitId = context.visitId,
            oldRoomNumber = context.roomNo,
            newRoomNumber = newRoomNumber,
            visitDate = context.visitDate,
            visitTime = context.visitTime
        )
    }

    suspend fun getPatientCurrentProcess(
        request: GetPatientCurrentProcessRequest
    ): GetPatientCurrentProcessResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()

        if (patientName.isBlank()) {
            return GetPatientCurrentProcessResponse(
                success = false,
                message = "patient_name is required"
            )
        }
        if (roomNumber.isBlank()) {
            return GetPatientCurrentProcessResponse(
                success = false,
                message = "room_number is required"
            )
        }

        val currentProcess = clinicalRepository.getPatientCurrentProcess(patientName, roomNumber)
            ?: return GetPatientCurrentProcessResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        return currentProcess.toResponse()
    }

    fun listTools(): List<McpToolDescriptor> = MedTrackMcpCatalog.tools

    private fun PatientCurrentProcess.toResponse(): GetPatientCurrentProcessResponse =
        GetPatientCurrentProcessResponse(
            success = true,
            message = "Current patient process loaded successfully from the latest matched visit.",
            patient = context.toPatientSummary(),
            visit = context.toVisitSummary(),
            medicines = medicines.map {
                McpMedicineSummary(
                    medicineId = it.id,
                    name = it.name,
                    dosage = it.dosage,
                    duration = it.duration,
                    notes = it.notes
                )
            },
            tasks = tasks.map {
                McpTaskSummary(
                    taskId = it.id,
                    taskName = it.taskName,
                    assignedTo = it.assignedTo,
                    role = it.role,
                    instructions = it.instructions,
                    status = it.status
                )
            },
            reports = reports.map {
                McpReportSummary(
                    reportId = it.id,
                    fileName = it.fileName,
                    filePath = it.filePath,
                    fileType = it.fileType,
                    uploadedAt = it.uploadedAt
                )
            },
            followUp = followUp?.let {
                McpFollowUpSummary(
                    followUpId = it.id,
                    scheduledDate = it.scheduledDate,
                    scheduledTime = it.scheduledTime,
                    reason = it.reason,
                    status = it.status,
                    isNotified = it.isNotified,
                    notifiedAt = it.notifiedAt,
                    completedAt = it.completedAt
                )
            }
        )

    private fun PatientVisitContext.toPatientSummary(): McpPatientSummary =
        McpPatientSummary(
            patientId = patientId,
            patientName = patientName,
            age = age,
            sex = sex,
            contact = contact,
            address = address,
            medicalHistory = medHistory
        )

    private fun PatientVisitContext.toVisitSummary(): McpVisitSummary =
        McpVisitSummary(
            visitId = visitId,
            visitDate = visitDate,
            visitTime = visitTime,
            roomNumber = roomNo,
            symptoms = symptoms,
            diagnosis = diagnosis,
            progressNotes = progressNotes
        )

    private suspend fun buildRoomSuggestionMessage(
        patientName: String,
        currentRoomNumber: String
    ): String {
        val suggestedRoomNumber = clinicalRepository
            .findLatestVisitContextByPatientName(patientName)
            ?.roomNo
            ?.takeIf { it.isNotBlank() && !it.equals(currentRoomNumber, ignoreCase = true) }

        return if (suggestedRoomNumber != null) {
            "$patientName not found in $currentRoomNumber. Did you mean $suggestedRoomNumber?"
        } else {
            "No matching patient visit found for the given patient name and current room number"
        }
    }

    private suspend fun buildProcessSuggestionMessage(
        patientName: String,
        roomNumber: String
    ): String {
        val suggestedRoomNumber = clinicalRepository
            .findLatestVisitContextByPatientName(patientName)
            ?.roomNo
            ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }

        return if (suggestedRoomNumber != null) {
            "$patientName not found in $roomNumber. Did you mean $suggestedRoomNumber?"
        } else {
            "No matching patient visit found for the given patient name and room number"
        }
    }
}
