package com.medtrack.app.mcp

import com.medtrack.app.data.care.CareCatalogBootstrap
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.data.care.command.AssignTaskRequest
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.data.care.command.ChangeMedicationOrderRequest
import com.medtrack.app.data.care.command.CreatePatientAndAdmissionRequest
import com.medtrack.app.data.care.command.RecordEncounterRequest
import com.medtrack.app.data.care.command.RecordProblemRequest
import com.medtrack.app.data.care.command.RecordTransferRequest
import com.medtrack.app.data.care.command.RespondToTaskRequest
import com.medtrack.app.data.care.dao.CareWorkDao
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.care.model.Regimen
import com.medtrack.app.data.care.query.CareCensusQuery
import com.medtrack.app.data.care.query.CensusPatient
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Singleton
class MedTrackMcpService @Inject constructor(
    private val writePath: CareWritePath,
    private val censusQuery: CareCensusQuery,
    private val catalog: CareCatalogBootstrap,
    private val workDao: CareWorkDao,
    private val accountSession: AccountSession
) {
    private val workspace get() = accountSession.workspace()
    private val owner get() = accountSession.ownerAccountId()

    fun listTools() = MedTrackMcpCatalog.tools

    suspend fun searchPatient(request: SearchPatientRequest): SearchPatientResponse {
        val patients = censusQuery.censusPatients(owner) + censusQuery.censusPatients(owner, discharged = true)
        val q = request.query.trim()
        val matches = patients.filter {
            q.isBlank() ||
                it.displayName.contains(q, true) ||
                it.locationLabel.contains(q, true) ||
                it.patientId.contains(q, true)
        }.take(request.limit)
        return SearchPatientResponse(
            success = true,
            message = "Found ${matches.size} patient(s).",
            query = request.query,
            patients = matches.map { it.toSearchItem() }
        )
    }

    suspend fun createPatient(request: CreatePatientRequest): CreatePatientResponse {
        val cat = catalog.ensure()
        val created = writePath.admissions.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest(
                patientDisplayName = request.patientName.trim(),
                hospitalId = cat.hospitalId
            )
        )
        if (request.medicalHistory.isNotBlank()) {
            writePath.clinical.recordProblem(
                workspace,
                RecordProblemRequest(
                    patientId = created.patientId,
                    admissionId = created.admissionId,
                    description = request.medicalHistory,
                    onset = ClinicalTime.instant(System.currentTimeMillis())
                )
            )
        }
        return CreatePatientResponse(
            success = true,
            message = "Admitted ${request.patientName}.",
            patientId = created.patientId,
            patientName = request.patientName,
            age = request.age.toString(),
            sex = request.sex,
            contact = request.contact,
            address = request.address,
            medicalHistory = request.medicalHistory
        )
    }

    suspend fun createVisit(request: CreateVisitRequest): CreateVisitResponse {
        val patient = resolvePatient(request.patientId, request.patientName, request.roomNumber)
            ?: return CreateVisitResponse(false, "Patient not found.")
        val bed = catalog.findBed(request.roomNumber)
        if (bed != null) {
            writePath.admissions.recordTransfer(
                workspace,
                RecordTransferRequest(
                    admissionId = patient.admissionId,
                    effectiveAt = ClinicalTime.instant(System.currentTimeMillis()),
                    locationId = bed.id
                )
            )
        }
        val summary = listOf(request.symptoms, request.diagnosis, request.progressNotes)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { "Rounds note" }
        writePath.clinical.recordEncounter(
            workspace,
            RecordEncounterRequest(
                admissionId = patient.admissionId,
                reviewedAt = ClinicalTime.instant(System.currentTimeMillis()),
                summary = summary,
                noteText = request.progressNotes.ifBlank { null }
            )
        )
        if (request.diagnosis.isNotBlank()) {
            writePath.clinical.recordProblem(
                workspace,
                RecordProblemRequest(
                    patientId = patient.patientId,
                    admissionId = patient.admissionId,
                    description = request.diagnosis,
                    onset = ClinicalTime.instant(System.currentTimeMillis())
                )
            )
        }
        if (request.taskName.isNotBlank()) {
            writePath.work.assignTask(
                workspace,
                AssignTaskRequest(
                    admissionId = patient.admissionId,
                    title = request.taskName,
                    instructions = request.taskInstructions.ifBlank { null },
                    followUpOwnerPersonId = workspace.actorPersonId
                )
            )
        }
        if (request.medicineName.isNotBlank()) {
            writePath.therapy.changeMedicationOrder(
                workspace,
                ChangeMedicationOrderRequest(
                    admissionId = patient.admissionId,
                    medicationDisplayName = request.medicineName,
                    doseText = request.medicineDosage.ifBlank { null },
                    schedule = Regimen.textOnly(request.medicineDuration.ifBlank { "as directed" }),
                    reason = request.medicineNotes.ifBlank { null }
                )
            )
        }
        return CreateVisitResponse(true, "Recorded encounter for ${patient.displayName}.")
    }

    suspend fun dischargePatient(request: DischargePatientRequest): DischargePatientResponse {
        val patient = resolvePatient(null, request.patientName, request.roomNumber)
            ?: return DischargePatientResponse(false, "Patient not found.")
        writePath.admissions.dischargeAdmission(workspace, patient.admissionId)
        return DischargePatientResponse(true, "Discharged ${patient.displayName}.")
    }

    suspend fun updatePatientRoom(request: UpdatePatientRoomRequest): UpdatePatientRoomResponse {
        val patient = resolvePatient(null, request.patientName, request.currentRoomNumber)
            ?: return UpdatePatientRoomResponse(false, "Patient not found.")
        val bed = catalog.findBed(request.newRoomNumber)
            ?: return UpdatePatientRoomResponse(false, "Bed not found: ${request.newRoomNumber}")
        writePath.admissions.recordTransfer(
            workspace,
            RecordTransferRequest(
                admissionId = patient.admissionId,
                effectiveAt = ClinicalTime.instant(System.currentTimeMillis()),
                locationId = bed.id
            )
        )
        return UpdatePatientRoomResponse(
            success = true,
            message = "Moved ${patient.displayName} to ${bed.label}.",
            patientId = patient.patientId,
            patientName = patient.displayName,
            oldRoomNumber = request.currentRoomNumber,
            newRoomNumber = bed.label
        )
    }

    suspend fun addPatientTask(request: AddPatientTaskRequest): AddPatientTaskResponse {
        val patient = resolvePatient(null, request.patientName, request.roomNumber)
            ?: return AddPatientTaskResponse(false, "Patient not found.")
        writePath.work.assignTask(
            workspace,
            AssignTaskRequest(
                admissionId = patient.admissionId,
                title = request.taskTitle,
                instructions = request.instructions.ifBlank { null },
                followUpOwnerPersonId = workspace.actorPersonId
            )
        )
        return AddPatientTaskResponse(true, "Task added for ${patient.displayName}.")
    }

    suspend fun updatePatientTaskStatus(request: UpdatePatientTaskStatusRequest): UpdatePatientTaskStatusResponse {
        val patient = resolvePatient(null, request.patientName, request.roomNumber)
            ?: return UpdatePatientTaskStatusResponse(false, "Patient not found.")
        val task = workDao.tasksForAdmission(patient.admissionId)
            .firstOrNull { it.title.equals(request.taskName, true) }
            ?: return UpdatePatientTaskStatusResponse(false, "Task not found.")
        val action = if (request.status.equals("DONE", true) || request.status.equals("COMPLETED", true)) {
            CareEnums.TaskResponseAction.COMPLETE
        } else {
            CareEnums.TaskResponseAction.NOTE
        }
        writePath.work.respondToTask(
            workspace,
            RespondToTaskRequest(
                taskId = task.id,
                action = action,
                note = if (action == CareEnums.TaskResponseAction.NOTE) request.status else null
            )
        )
        return UpdatePatientTaskStatusResponse(true, "Updated ${task.title}.")
    }

    suspend fun getPatientTasks(request: GetPatientTasksRequest): GetPatientTasksResponse {
        val patient = resolvePatient(null, request.patientName, request.roomNumber)
            ?: return GetPatientTasksResponse(false, "Patient not found.")
        val tasks = workDao.tasksForAdmission(patient.admissionId)
        return GetPatientTasksResponse(true, "${tasks.size} task(s).", tasks = tasks.map {
            McpTaskSummary(
                taskId = it.id,
                taskName = it.title,
                assignedTo = "",
                role = it.kind,
                instructions = it.instructions.orEmpty(),
                status = it.status
            )
        })
    }

    suspend fun scheduleFollowUp(request: ScheduleFollowUpRequest): ScheduleFollowUpResponse {
        val patient = resolvePatient(null, request.patientName, request.roomNumber)
            ?: return ScheduleFollowUpResponse(false, "Patient not found.")
        val due = runCatching {
            val date = LocalDate.parse(request.scheduledDate.take(10))
            val time = LocalTime.parse(request.scheduledTime.take(5))
            date.atTime(time).atZone(ZoneId.of(accountSession.timeZone())).toInstant().toEpochMilli()
        }.getOrNull()
        writePath.work.assignTask(
            workspace,
            AssignTaskRequest(
                admissionId = patient.admissionId,
                kind = CareEnums.CareTaskKind.REVIEW,
                title = request.reason.ifBlank { "Follow-up" },
                followUpOwnerPersonId = workspace.actorPersonId,
                dueAt = due,
                dueZoneId = if (due != null) accountSession.timeZone() else null,
                scheduleReminder = due != null
            )
        )
        return ScheduleFollowUpResponse(true, "Follow-up scheduled.")
    }

    suspend fun getDueFollowUps(request: GetDueFollowUpsRequest): GetDueFollowUpsResponse {
        val tasks = censusQuery.openWorkQueue(owner)
        return GetDueFollowUpsResponse(
            success = true,
            message = "${tasks.size} open task(s).",
            date = request.date,
            mode = request.mode,
            status = request.status
        )
    }

    suspend fun markFollowUpDone(request: MarkFollowUpDoneRequest): MarkFollowUpDoneResponse {
        val taskId = request.followUpId
        if (!taskId.isNullOrBlank()) {
            writePath.work.respondToTask(
                workspace,
                RespondToTaskRequest(taskId = taskId, action = CareEnums.TaskResponseAction.COMPLETE)
            )
            return MarkFollowUpDoneResponse(true, "Marked done.")
        }
        val patient = resolvePatient(null, request.patientName, request.roomNumber)
            ?: return MarkFollowUpDoneResponse(false, "Patient not found.")
        val task = workDao.tasksForAdmission(patient.admissionId)
            .firstOrNull { it.title.contains(request.reason, true) || request.reason.isBlank() }
            ?: return MarkFollowUpDoneResponse(false, "No matching task.")
        writePath.work.respondToTask(
            workspace,
            RespondToTaskRequest(taskId = task.id, action = CareEnums.TaskResponseAction.COMPLETE)
        )
        return MarkFollowUpDoneResponse(true, "Marked done.")
    }

    suspend fun rescheduleFollowUp(request: RescheduleFollowUpRequest): RescheduleFollowUpResponse {
        val due = runCatching {
            val date = LocalDate.parse(request.scheduledDate.take(10))
            val time = LocalTime.parse(request.scheduledTime.take(5))
            date.atTime(time).atZone(ZoneId.of(accountSession.timeZone())).toInstant().toEpochMilli()
        }.getOrNull() ?: return RescheduleFollowUpResponse(false, "Invalid date/time.")
        val taskId = request.followUpId ?: return RescheduleFollowUpResponse(false, "followUpId required.")
        writePath.work.respondToTask(
            workspace,
            RespondToTaskRequest(
                taskId = taskId,
                action = CareEnums.TaskResponseAction.RESCHEDULE,
                newDueAt = due
            )
        )
        return RescheduleFollowUpResponse(true, "Rescheduled.")
    }

    suspend fun getPatientMedicines(request: GetPatientMedicinesRequest): GetPatientMedicinesResponse {
        val patient = resolvePatient(null, request.patientName, request.roomNumber)
            ?: return GetPatientMedicinesResponse(false, "Patient not found.")
        return GetPatientMedicinesResponse(true, "Medication orders live on the care therapy model.", patient = patient.toSummary())
    }

    suspend fun addOrUpdateMedicine(request: AddOrUpdateMedicineRequest): AddOrUpdateMedicineResponse {
        val patient = resolvePatient(null, request.patientName, request.roomNumber)
            ?: return AddOrUpdateMedicineResponse(false, "Patient not found.")
        writePath.therapy.changeMedicationOrder(
            workspace,
            ChangeMedicationOrderRequest(
                admissionId = patient.admissionId,
                medicationDisplayName = request.medicineName,
                doseText = request.dosage.ifBlank { null },
                schedule = Regimen.textOnly(request.frequency.ifBlank { request.duration.ifBlank { "as directed" } }),
                reason = request.notes.ifBlank { null }
            )
        )
        return AddOrUpdateMedicineResponse(true, "Medication order recorded.")
    }

    suspend fun removePatientMedicine(request: RemovePatientMedicineRequest): RemovePatientMedicineResponse {
        return RemovePatientMedicineResponse(false, "Stop a medication through a HOLD/STOP order; history is kept.")
    }

    suspend fun getPatientReports(request: GetPatientReportsRequest): GetPatientReportsResponse {
        return GetPatientReportsResponse(true, "No scanned reports on this admission yet.")
    }

    suspend fun getPatientBloodReports(request: GetPatientBloodReportsRequest): GetPatientBloodReportsResponse {
        return GetPatientBloodReportsResponse(true, "No blood reports on this admission yet.")
    }

    suspend fun getPatientCurrentProcess(request: GetPatientCurrentProcessRequest): GetPatientCurrentProcessResponse {
        val patient = resolvePatient(null, request.patientName, request.roomNumber)
            ?: return GetPatientCurrentProcessResponse(false, "Patient not found.")
        val hub = censusQuery.patientHub(patient.admissionId)
        val tasks = hub?.tasks.orEmpty()
        return GetPatientCurrentProcessResponse(
            success = true,
            message = "${patient.displayName} at ${patient.locationLabel}. ${tasks.size} task(s). ${hub?.census?.problemSummary.orEmpty()}"
        )
    }

    private suspend fun resolvePatient(
        patientId: String?,
        patientName: String,
        roomNumber: String
    ): CensusPatient? {
        val all = censusQuery.censusPatients(owner)
        if (!patientId.isNullOrBlank()) {
            all.firstOrNull { it.patientId == patientId || it.admissionId == patientId }?.let { return it }
        }
        val byName = all.filter { it.displayName.equals(patientName.trim(), true) }
        if (byName.size == 1) return byName.first()
        return byName.firstOrNull { it.locationLabel.contains(roomNumber.trim(), true) }
            ?: all.firstOrNull { it.locationLabel.contains(roomNumber.trim(), true) && roomNumber.isNotBlank() }
    }

    private fun CensusPatient.toSearchItem() = SearchPatientItem(
        patientId = patientId,
        patientName = displayName,
        age = reportedAge?.toIntOrNull() ?: 0,
        sex = "",
        contact = "",
        address = "",
        medicalHistory = problemSummary.orEmpty(),
        latestRoomNumber = locationLabel
    )

    private fun CensusPatient.toSummary() = McpPatientSummary(
        patientId = patientId,
        patientName = displayName,
        age = reportedAge?.toIntOrNull() ?: 0,
        sex = "",
        contact = "",
        address = "",
        medicalHistory = problemSummary.orEmpty()
    )
}
