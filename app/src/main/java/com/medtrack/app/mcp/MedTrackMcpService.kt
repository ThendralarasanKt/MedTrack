package com.medtrack.app.mcp

import com.medtrack.app.data.db.model.PatientCurrentProcess
import com.medtrack.app.data.db.model.PatientListItem
import com.medtrack.app.data.db.model.PatientVisitContext
import com.medtrack.app.data.db.model.FollowUpWithPatient
import com.medtrack.app.data.db.entity.FollowUpEntity
import com.medtrack.app.data.db.entity.MedicineEntity
import com.medtrack.app.data.db.entity.PatientEntity
import com.medtrack.app.data.db.entity.TaskEntity
import com.medtrack.app.data.db.entity.VisitEntity
import com.medtrack.app.data.repository.ClinicalRepository
import com.medtrack.app.data.repository.PatientRepository
import com.medtrack.app.notification.FollowUpNotificationScheduler
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class MedTrackMcpService @Inject constructor(
    private val clinicalRepository: ClinicalRepository,
    private val patientRepository: PatientRepository,
    private val followUpNotificationScheduler: FollowUpNotificationScheduler
) {
    suspend fun createPatient(request: CreatePatientRequest): CreatePatientResponse {
        val patientName = request.patientName.trim()
        val sex = request.sex.trim()
        val contact = request.contact.trim()
        val address = request.address.trim()
        val medicalHistory = request.medicalHistory.trim()

        if (patientName.isBlank()) {
            return CreatePatientResponse(success = false, message = "patient_name is required")
        }
        if (request.age <= 0) {
            return CreatePatientResponse(success = false, message = "age must be greater than zero")
        }
        if (sex.isBlank()) {
            return CreatePatientResponse(success = false, message = "sex is required")
        }
        if (contact.isBlank()) {
            return CreatePatientResponse(success = false, message = "contact is required")
        }

        val patient = PatientEntity(
            name = patientName,
            age = request.age,
            sex = normalizeSex(sex),
            contact = contact,
            address = address,
            medHistory = medicalHistory
        )
        val patientId = patientRepository.insertPatient(patient).toInt()

        return CreatePatientResponse(
            success = true,
            message = "Patient created successfully.",
            patientId = patientId,
            patientName = patient.name,
            age = patient.age,
            sex = patient.sex,
            contact = patient.contact,
            address = patient.address,
            medicalHistory = patient.medHistory,
            createdAt = patient.createdAt
        )
    }

    suspend fun searchPatient(request: SearchPatientRequest): SearchPatientResponse {
        val query = request.query.trim()
        val limit = request.limit.coerceIn(1, 25)

        if (query.isBlank()) {
            return SearchPatientResponse(
                success = false,
                message = "query is required",
                query = query
            )
        }

        val patients = patientRepository
            .searchPatientListItems(query)
            .first()
            .take(limit)
            .map { it.toSearchPatientItem() }

        return SearchPatientResponse(
            success = true,
            message = if (patients.isEmpty()) {
                "No patients found for '$query'."
            } else {
                "Found ${patients.size} patient match(es) for '$query'."
            },
            query = query,
            patients = patients
        )
    }

    suspend fun createVisit(request: CreateVisitRequest): CreateVisitResponse {
        val patient = resolvePatient(request)
            ?: return CreateVisitResponse(
                success = false,
                message = "No matching patient found. Provide patientId or an exact patientName."
            )
        val roomNumber = request.roomNumber.trim()
        val symptoms = request.symptoms.trim()
        val diagnosis = request.diagnosis.trim()
        val progressNotes = request.progressNotes.trim()

        if (roomNumber.isBlank()) {
            return CreateVisitResponse(success = false, message = "room_number is required")
        }
        if (symptoms.isBlank() && diagnosis.isBlank()) {
            return CreateVisitResponse(
                success = false,
                message = "symptoms or diagnosis is required"
            )
        }

        val visit = VisitEntity(
            patientId = patient.id,
            roomNo = roomNumber,
            symptoms = symptoms,
            diagnosis = diagnosis,
            progressNotes = progressNotes
        )
        val visitId = clinicalRepository.insertVisit(visit).toInt()
        val createdVisit = visit.copy(id = visitId)

        val task = request.taskName.trim().takeIf { it.isNotBlank() }?.let { taskName ->
            val entity = TaskEntity(
                visitId = visitId,
                taskName = taskName,
                assignedTo = request.taskAssignedTo.trim(),
                role = request.taskRole.trim(),
                instructions = request.taskInstructions.trim(),
                status = normalizeTaskStatus(request.taskStatus)
            )
            clinicalRepository.insertTask(entity)
            entity.toSummary(taskId = 0)
        }

        val medicine = request.medicineName.trim().takeIf { it.isNotBlank() }?.let { medicineName ->
            val entity = MedicineEntity(
                visitId = visitId,
                name = medicineName,
                dosage = request.medicineDosage.trim(),
                duration = request.medicineDuration.trim(),
                notes = request.medicineNotes.trim()
            )
            clinicalRepository.insertMedicine(entity)
            entity.toSummary(medicineId = 0)
        }

        return CreateVisitResponse(
            success = true,
            message = "Visit created successfully.",
            patient = patient.toSummary(),
            visit = McpVisitSummary(
                visitId = createdVisit.id,
                visitDate = createdVisit.visitDate,
                visitTime = createdVisit.visitTime,
                roomNumber = createdVisit.roomNo,
                symptoms = createdVisit.symptoms,
                diagnosis = createdVisit.diagnosis,
                progressNotes = createdVisit.progressNotes
            ),
            medicine = medicine,
            task = task
        )
    }

    suspend fun dischargePatient(request: DischargePatientRequest): DischargePatientResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()
        if (patientName.isBlank()) {
            return DischargePatientResponse(success = false, message = "patient_name is required")
        }
        if (roomNumber.isBlank()) {
            return DischargePatientResponse(success = false, message = "room_number is required")
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, roomNumber)
            .firstOrNull()
            ?: return DischargePatientResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        val dischargedAt = java.time.LocalDateTime.now().toString()
        val updated = patientRepository.dischargePatient(context.patientId, dischargedAt)
        if (updated <= 0) {
            return DischargePatientResponse(success = false, message = "Failed to discharge patient.")
        }
        return DischargePatientResponse(
            success = true,
            message = "Patient discharged successfully. Record will auto-delete after 30 days.",
            patient = context.toPatientSummary(),
            visit = context.toVisitSummary(),
            dischargedAt = dischargedAt
        )
    }

    suspend fun getPatientMedicines(
        request: GetPatientMedicinesRequest
    ): GetPatientMedicinesResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()

        if (patientName.isBlank()) {
            return GetPatientMedicinesResponse(success = false, message = "patient_name is required")
        }
        if (roomNumber.isBlank()) {
            return GetPatientMedicinesResponse(success = false, message = "room_number is required")
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, roomNumber)
            .firstOrNull()
            ?: return GetPatientMedicinesResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        val visits = clinicalRepository.getVisitsForPatient(context.patientId).first()
        val medicines = visits.flatMap { visit ->
            clinicalRepository.getMedicinesForVisitNow(visit.id).map { medicine ->
                McpMedicineHistoryItem(
                    visitId = visit.id,
                    visitDate = visit.visitDate,
                    visitTime = visit.visitTime,
                    roomNumber = visit.roomNo,
                    medicineId = medicine.id,
                    medicineName = medicine.name,
                    dosage = medicine.dosage,
                    duration = medicine.duration,
                    notes = medicine.notes
                )
            }
        }

        return GetPatientMedicinesResponse(
            success = true,
            message = if (medicines.isEmpty()) {
                "No medicines found for ${context.patientName}."
            } else {
                "Found ${medicines.size} medicine record(s) for ${context.patientName}."
            },
            patient = context.toPatientSummary(),
            roomNumber = roomNumber,
            anchorVisitId = context.visitId,
            medicines = medicines
        )
    }

    suspend fun getPatientReports(
        request: GetPatientReportsRequest
    ): GetPatientReportsResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()

        if (patientName.isBlank()) {
            return GetPatientReportsResponse(success = false, message = "patient_name is required")
        }
        if (roomNumber.isBlank()) {
            return GetPatientReportsResponse(success = false, message = "room_number is required")
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, roomNumber)
            .firstOrNull()
            ?: return GetPatientReportsResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        val visits = clinicalRepository.getVisitsForPatient(context.patientId).first()
        val reports = visits
            .flatMap { visit ->
                clinicalRepository.getReportsForVisitNow(visit.id).map { report ->
                    McpReportHistoryItem(
                        visitId = visit.id,
                        visitDate = visit.visitDate,
                        visitTime = visit.visitTime,
                        roomNumber = visit.roomNo,
                        reportId = report.id,
                        fileName = report.fileName,
                        filePath = report.filePath,
                        fileType = report.fileType,
                        uploadedAt = report.uploadedAt
                    )
                }
            }
            .sortedWith(
                compareByDescending<McpReportHistoryItem> { it.uploadedAt }
                    .thenByDescending { it.reportId }
            )

        return GetPatientReportsResponse(
            success = true,
            message = if (reports.isEmpty()) {
                "No reports found for ${context.patientName}."
            } else {
                "Found ${reports.size} report(s) across all visits for ${context.patientName}."
            },
            patient = context.toPatientSummary(),
            roomNumber = roomNumber,
            anchorVisitId = context.visitId,
            reports = reports
        )
    }

    suspend fun getPatientBloodReports(
        request: GetPatientBloodReportsRequest
    ): GetPatientBloodReportsResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()

        if (patientName.isBlank()) {
            return GetPatientBloodReportsResponse(success = false, message = "patient_name is required")
        }
        if (roomNumber.isBlank()) {
            return GetPatientBloodReportsResponse(success = false, message = "room_number is required")
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, roomNumber)
            .firstOrNull()
            ?: return GetPatientBloodReportsResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        val bloodReports = clinicalRepository.getReportsForVisitNow(context.visitId)
            .filter { isBloodReport(it.fileName, it.fileType) }
            .map { report ->
                McpReportHistoryItem(
                    visitId = context.visitId,
                    visitDate = context.visitDate,
                    visitTime = context.visitTime,
                    roomNumber = context.roomNo,
                    reportId = report.id,
                    fileName = report.fileName,
                    filePath = report.filePath,
                    fileType = report.fileType,
                    uploadedAt = report.uploadedAt
                )
            }

        return GetPatientBloodReportsResponse(
            success = true,
            message = if (bloodReports.isEmpty()) {
                "No blood reports found for ${context.patientName} in room $roomNumber."
            } else {
                "Found ${bloodReports.size} blood report(s) for ${context.patientName} in room $roomNumber."
            },
            patient = context.toPatientSummary(),
            roomNumber = roomNumber,
            visitId = context.visitId,
            bloodReports = bloodReports
        )
    }

    suspend fun addPatientTask(request: AddPatientTaskRequest): AddPatientTaskResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()
        val taskTitle = request.taskTitle.trim()

        if (patientName.isBlank()) {
            return AddPatientTaskResponse(success = false, message = "patient_name is required")
        }
        if (roomNumber.isBlank()) {
            return AddPatientTaskResponse(success = false, message = "room_number is required")
        }
        if (taskTitle.isBlank()) {
            return AddPatientTaskResponse(success = false, message = "task_title is required")
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, roomNumber)
            .firstOrNull()
            ?: return AddPatientTaskResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        val task = TaskEntity(
            visitId = context.visitId,
            taskName = taskTitle,
            assignedTo = request.assignedTo.trim(),
            role = request.role.trim(),
            instructions = request.instructions.trim(),
            status = normalizeTaskStatus(request.status)
        )
        val taskId = clinicalRepository.insertTask(task).toInt()

        return AddPatientTaskResponse(
            success = true,
            message = "Task added successfully.",
            patient = context.toPatientSummary(),
            visit = context.toVisitSummary(),
            task = task.toSummary(taskId)
        )
    }

    suspend fun updatePatientTaskStatus(
        request: UpdatePatientTaskStatusRequest
    ): UpdatePatientTaskStatusResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()
        val taskName = request.taskName.trim()
        val status = normalizeTaskStatus(request.status)

        if (patientName.isBlank()) {
            return UpdatePatientTaskStatusResponse(success = false, message = "patient_name is required")
        }
        if (roomNumber.isBlank()) {
            return UpdatePatientTaskStatusResponse(success = false, message = "room_number is required")
        }
        if (taskName.isBlank()) {
            return UpdatePatientTaskStatusResponse(success = false, message = "task_name is required")
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, roomNumber)
            .firstOrNull()
            ?: return UpdatePatientTaskStatusResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        val matches = clinicalRepository.getTasksForVisitNow(context.visitId)
            .filter { it.taskName.contains(taskName, ignoreCase = true) }

        if (matches.isEmpty()) {
            return UpdatePatientTaskStatusResponse(
                success = false,
                message = "No matching task found for ${context.patientName} in room $roomNumber.",
                patient = context.toPatientSummary(),
                visit = context.toVisitSummary()
            )
        }
        if (matches.size > 1) {
            return UpdatePatientTaskStatusResponse(
                success = false,
                message = "Multiple matching tasks found. Use a more specific task name.",
                patient = context.toPatientSummary(),
                visit = context.toVisitSummary(),
                matches = matches.map { it.toSummary(it.id) }
            )
        }

        val task = matches.single()
        clinicalRepository.updateTaskStatus(task.id, status == "DONE")
        val updated = task.copy(status = status)
        return UpdatePatientTaskStatusResponse(
            success = true,
            message = "Task status updated successfully.",
            patient = context.toPatientSummary(),
            visit = context.toVisitSummary(),
            task = updated.toSummary(updated.id)
        )
    }

    suspend fun getPatientTasks(request: GetPatientTasksRequest): GetPatientTasksResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()
        val status = request.status.trim().uppercase()

        if (patientName.isBlank()) {
            return GetPatientTasksResponse(success = false, message = "patient_name is required")
        }
        if (roomNumber.isBlank()) {
            return GetPatientTasksResponse(success = false, message = "room_number is required")
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, roomNumber)
            .firstOrNull()
            ?: return GetPatientTasksResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        val tasks = clinicalRepository.getTasksForVisitNow(context.visitId)
            .filter { status.isBlank() || it.status.equals(status, ignoreCase = true) }
            .map { it.toSummary(it.id) }

        return GetPatientTasksResponse(
            success = true,
            message = "Found ${tasks.size} task(s) for ${context.patientName}.",
            patient = context.toPatientSummary(),
            visit = context.toVisitSummary(),
            tasks = tasks
        )
    }

    suspend fun scheduleFollowUp(request: ScheduleFollowUpRequest): ScheduleFollowUpResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()
        val scheduledDate = request.scheduledDate.trim()
        val scheduledTime = request.scheduledTime.trim().take(5)
        val reason = request.reason.trim()

        if (patientName.isBlank()) {
            return ScheduleFollowUpResponse(success = false, message = "patient_name is required")
        }
        if (roomNumber.isBlank()) {
            return ScheduleFollowUpResponse(success = false, message = "room_number is required")
        }
        if (!isValidDate(scheduledDate)) {
            return ScheduleFollowUpResponse(success = false, message = "scheduled_date must be YYYY-MM-DD")
        }
        if (!isValidTime(scheduledTime)) {
            return ScheduleFollowUpResponse(success = false, message = "scheduled_time must be HH:mm")
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, roomNumber)
            .firstOrNull()
            ?: return ScheduleFollowUpResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        val followUp = FollowUpEntity(
            patientId = context.patientId,
            visitId = context.visitId,
            scheduledDate = scheduledDate,
            scheduledTime = scheduledTime,
            reason = reason.ifBlank { "Follow-up for visit #${context.visitId}" }
        )
        val followUpId = clinicalRepository.insertFollowUp(followUp).toInt()
        followUpNotificationScheduler.schedule(followUpId, scheduledDate, scheduledTime)

        return ScheduleFollowUpResponse(
            success = true,
            message = "Follow-up scheduled successfully.",
            patient = context.toPatientSummary(),
            visit = context.toVisitSummary(),
            followUp = McpFollowUpSummary(
                followUpId = followUpId,
                scheduledDate = scheduledDate,
                scheduledTime = scheduledTime,
                reason = followUp.reason,
                status = followUp.status,
                isNotified = followUp.isNotified,
                notifiedAt = followUp.notifiedAt,
                completedAt = followUp.completedAt
            )
        )
    }

    suspend fun getDueFollowUps(request: GetDueFollowUpsRequest): GetDueFollowUpsResponse {
        val date = request.date.trim()
        val status = request.status.trim().ifBlank { "PENDING" }.uppercase()
        val mode = request.mode.trim().ifBlank { "DUE" }.uppercase()

        if (!isValidDate(date)) {
            return GetDueFollowUpsResponse(
                success = false,
                message = "date must be YYYY-MM-DD",
                date = date,
                mode = mode,
                status = status
            )
        }

        val followUps = clinicalRepository.getAllFollowUpsWithPatientsNow()
            .filter { status == "ANY" || it.status.equals(status, ignoreCase = true) }
            .filter {
                when (mode) {
                    "TODAY" -> it.scheduledDate == date
                    "OVERDUE" -> it.scheduledDate < date
                    "UPCOMING" -> it.scheduledDate > date
                    else -> it.scheduledDate <= date
                }
            }
            .map { it.toSummary() }

        return GetDueFollowUpsResponse(
            success = true,
            message = "Found ${followUps.size} follow-up(s).",
            date = date,
            mode = mode,
            status = status,
            followUps = followUps
        )
    }

    suspend fun markFollowUpDone(request: MarkFollowUpDoneRequest): MarkFollowUpDoneResponse {
        val followUp = resolveFollowUp(
            followUpId = request.followUpId,
            patientName = request.patientName,
            roomNumber = request.roomNumber,
            reason = request.reason
        ) ?: return MarkFollowUpDoneResponse(
            success = false,
            message = "No matching follow-up found."
        )

        val completedAt = java.time.LocalDateTime.now().toString()
        clinicalRepository.markFollowUpDone(followUp.id, completedAt)
        followUpNotificationScheduler.cancel(followUp.id)

        return MarkFollowUpDoneResponse(
            success = true,
            message = "Follow-up marked done successfully.",
            followUp = followUp.copy(status = "DONE", completedAt = completedAt).toSummary()
        )
    }

    suspend fun rescheduleFollowUp(request: RescheduleFollowUpRequest): RescheduleFollowUpResponse {
        val scheduledDate = request.scheduledDate.trim()
        val scheduledTime = request.scheduledTime.trim().take(5)
        if (!isValidDate(scheduledDate)) {
            return RescheduleFollowUpResponse(success = false, message = "scheduled_date must be YYYY-MM-DD")
        }
        if (!isValidTime(scheduledTime)) {
            return RescheduleFollowUpResponse(success = false, message = "scheduled_time must be HH:mm")
        }

        val followUp = resolveFollowUp(
            followUpId = request.followUpId,
            patientName = request.patientName,
            roomNumber = request.roomNumber,
            reason = request.reason
        ) ?: return RescheduleFollowUpResponse(
            success = false,
            message = "No matching follow-up found."
        )

        val entity = clinicalRepository.getFollowUpById(followUp.id)
            ?: return RescheduleFollowUpResponse(success = false, message = "Follow-up row not found.")
        val updated = entity.copy(
            scheduledDate = scheduledDate,
            scheduledTime = scheduledTime,
            reason = request.newReason.trim().ifBlank { entity.reason },
            status = "PENDING",
            isNotified = false,
            notifiedAt = null,
            completedAt = null
        )
        clinicalRepository.updateFollowUp(updated)
        followUpNotificationScheduler.cancel(updated.id)
        followUpNotificationScheduler.schedule(updated.id, scheduledDate, scheduledTime)

        return RescheduleFollowUpResponse(
            success = true,
            message = "Follow-up rescheduled successfully.",
            followUp = followUp.copy(
                scheduledDate = updated.scheduledDate,
                scheduledTime = updated.scheduledTime,
                reason = updated.reason,
                status = updated.status,
                isNotified = updated.isNotified,
                notifiedAt = updated.notifiedAt,
                completedAt = updated.completedAt
            ).toSummary()
        )
    }

    suspend fun addOrUpdateMedicine(
        request: AddOrUpdateMedicineRequest
    ): AddOrUpdateMedicineResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()
        val medicineName = request.medicineName.trim()
        val dosage = request.dosage.trim()

        if (patientName.isBlank()) {
            return AddOrUpdateMedicineResponse(success = false, message = "patient_name is required")
        }
        if (roomNumber.isBlank()) {
            return AddOrUpdateMedicineResponse(success = false, message = "room_number is required")
        }
        if (medicineName.isBlank()) {
            return AddOrUpdateMedicineResponse(success = false, message = "medicine_name is required")
        }
        if (dosage.isBlank()) {
            return AddOrUpdateMedicineResponse(success = false, message = "dosage is required")
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, roomNumber)
            .firstOrNull()
            ?: return AddOrUpdateMedicineResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        val notes = buildMedicineNotes(request.frequency.trim(), request.notes.trim())
        val existing = clinicalRepository.getMedicinesForVisitNow(context.visitId)
            .firstOrNull { it.name.trim().equals(medicineName, ignoreCase = true) }

        val action: String
        val saved: MedicineEntity
        if (existing == null) {
            val medicine = MedicineEntity(
                visitId = context.visitId,
                name = medicineName,
                dosage = dosage,
                duration = request.duration.trim(),
                notes = notes
            )
            val medicineId = clinicalRepository.insertMedicine(medicine).toInt()
            action = "CREATED"
            saved = medicine.copy(id = medicineId)
        } else {
            saved = existing.copy(
                name = medicineName,
                dosage = dosage,
                duration = request.duration.trim(),
                notes = notes
            )
            clinicalRepository.updateMedicine(saved)
            action = "UPDATED"
        }

        return AddOrUpdateMedicineResponse(
            success = true,
            message = if (action == "CREATED") {
                "Medicine added successfully."
            } else {
                "Medicine updated successfully."
            },
            action = action,
            patient = context.toPatientSummary(),
            visit = context.toVisitSummary(),
            medicine = saved.toSummary(saved.id)
        )
    }

    suspend fun removePatientMedicine(
        request: RemovePatientMedicineRequest
    ): RemovePatientMedicineResponse {
        val patientName = request.patientName.trim()
        val roomNumber = request.roomNumber.trim()
        val medicineName = request.medicineName.trim()

        if (patientName.isBlank()) {
            return RemovePatientMedicineResponse(success = false, message = "patient_name is required")
        }
        if (roomNumber.isBlank()) {
            return RemovePatientMedicineResponse(success = false, message = "room_number is required")
        }
        if (medicineName.isBlank()) {
            return RemovePatientMedicineResponse(success = false, message = "medicine_name is required")
        }

        val context = clinicalRepository
            .findVisitContextsByPatientNameAndRoom(patientName, roomNumber)
            .firstOrNull()
            ?: return RemovePatientMedicineResponse(
                success = false,
                message = buildProcessSuggestionMessage(patientName, roomNumber),
                suggestedRoomNumber = clinicalRepository
                    .findLatestVisitContextByPatientName(patientName)
                    ?.roomNo
                    ?.takeIf { it.isNotBlank() && !it.equals(roomNumber, ignoreCase = true) }
            )

        val medicine = clinicalRepository.getMedicinesForVisitNow(context.visitId)
            .firstOrNull { it.name.trim().equals(medicineName, ignoreCase = true) }
            ?: return RemovePatientMedicineResponse(
                success = false,
                message = "No matching medicine found for ${context.patientName} in room $roomNumber.",
                patient = context.toPatientSummary(),
                visit = context.toVisitSummary()
            )

        clinicalRepository.deleteMedicine(medicine)

        return RemovePatientMedicineResponse(
            success = true,
            message = "Medicine removed successfully.",
            patient = context.toPatientSummary(),
            visit = context.toVisitSummary(),
            removedMedicine = medicine.toSummary(medicine.id)
        )
    }

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

    private fun normalizeSex(value: String): String =
        when (value.trim().lowercase()) {
            "m", "male" -> "Male"
            "f", "female" -> "Female"
            "o", "other" -> "Other"
            else -> value.trim()
        }

    private suspend fun resolvePatient(request: CreateVisitRequest): PatientEntity? {
        request.patientId?.takeIf { it > 0 }?.let { patientId ->
            return patientRepository.getPatientById(patientId)
        }

        val patientName = request.patientName.trim()
        if (patientName.isBlank()) return null

        val matches = patientRepository.searchPatientListItems(patientName).first()
        return matches
            .firstOrNull { it.name.equals(patientName, ignoreCase = true) }
            ?.let { patientRepository.getPatientById(it.id) }
            ?: matches
                .singleOrNull()
                ?.let { patientRepository.getPatientById(it.id) }
    }

    private fun normalizeTaskStatus(value: String): String =
        if (value.equals("DONE", ignoreCase = true)) "DONE" else "PENDING"

    private fun isBloodReport(fileName: String, fileType: String): Boolean {
        val text = "$fileName $fileType".lowercase()
        return listOf(
            "blood",
            "cbc",
            "rbc",
            "wbc",
            "hemoglobin",
            "haemoglobin",
            "platelet",
            "platelets"
        ).any { keyword -> keyword in text }
    }

    private fun isValidDate(value: String): Boolean =
        runCatching { LocalDate.parse(value) }.isSuccess

    private fun isValidTime(value: String): Boolean =
        runCatching { LocalTime.parse(value) }.isSuccess

    private fun buildMedicineNotes(frequency: String, notes: String): String =
        listOf(
            frequency.takeIf { it.isNotBlank() }?.let { "Frequency: $it" },
            notes.takeIf { it.isNotBlank() }
        ).filterNotNull().joinToString(". ")

    private suspend fun resolveFollowUp(
        followUpId: Int?,
        patientName: String,
        roomNumber: String,
        reason: String
    ): FollowUpWithPatient? {
        followUpId?.takeIf { it > 0 }?.let { id ->
            return clinicalRepository.getFollowUpWithPatientById(id)
        }

        val name = patientName.trim()
        val room = roomNumber.trim()
        val reasonText = reason.trim()
        if (name.isBlank() || room.isBlank()) return null

        val matches = clinicalRepository.getAllFollowUpsWithPatientsNow()
            .filter { it.patientName.equals(name, ignoreCase = true) }
            .filter { it.roomNo.equals(room, ignoreCase = true) }
            .filter {
                reasonText.isBlank() ||
                    it.reason.contains(reasonText, ignoreCase = true)
            }

        return matches.singleOrNull() ?: matches.firstOrNull()
    }

    private fun PatientEntity.toSummary(): McpPatientSummary =
        McpPatientSummary(
            patientId = id,
            patientName = name,
            age = age,
            sex = sex,
            contact = contact,
            address = address,
            medicalHistory = medHistory
        )

    private fun MedicineEntity.toSummary(medicineId: Int): McpMedicineSummary =
        McpMedicineSummary(
            medicineId = medicineId,
            name = name,
            dosage = dosage,
            duration = duration,
            notes = notes
        )

    private fun TaskEntity.toSummary(taskId: Int): McpTaskSummary =
        McpTaskSummary(
            taskId = taskId,
            taskName = taskName,
            assignedTo = assignedTo,
            role = role,
            instructions = instructions,
            status = status
        )

    private fun FollowUpWithPatient.toSummary(): McpFollowUpWithPatientSummary =
        McpFollowUpWithPatientSummary(
            followUpId = id,
            patientId = patientId,
            patientName = patientName,
            visitId = visitId,
            roomNumber = roomNo,
            scheduledDate = scheduledDate,
            scheduledTime = scheduledTime,
            reason = reason,
            status = status,
            isNotified = isNotified,
            notifiedAt = notifiedAt,
            completedAt = completedAt
        )

    private fun PatientListItem.toSearchPatientItem(): SearchPatientItem =
        SearchPatientItem(
            patientId = id,
            patientName = name,
            age = age,
            sex = sex,
            contact = contact,
            address = address,
            medicalHistory = medHistory,
            latestRoomNumber = latestRoomNo
        )

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
