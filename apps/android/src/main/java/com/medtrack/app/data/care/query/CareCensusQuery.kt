package com.medtrack.app.data.care.query

import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.dao.CareTherapyDao
import com.medtrack.app.data.care.dao.CareWorkDao
import com.medtrack.app.data.care.entity.CareHospitalEpisodeEntity
import com.medtrack.app.data.care.entity.CareLocationAssignmentEntity
import com.medtrack.app.data.care.entity.CareObservationEntity
import com.medtrack.app.data.care.entity.CareTaskEntity
import com.medtrack.app.data.care.model.CareEnums
import javax.inject.Inject
import javax.inject.Singleton

data class CensusAdmissionRow(
    val admissionId: String,
    val patientId: String,
    val hospitalId: String,
    val kind: String,
    val status: String,
    val currentLocationId: String?,
    val openTaskCount: Int
)

/**
 * Derived census / workspace read models (PM-011).
 * Not separately editable — rebuilt from care_* sources.
 */
@Singleton
class CareCensusQuery @Inject constructor(
    private val careDao: CareDao,
    private val workDao: CareWorkDao,
    private val therapyDao: CareTherapyDao
) {
    suspend fun activeCensus(ownerAccountId: String): List<CensusAdmissionRow> =
        censusRows(workDao.activeEpisodes(ownerAccountId), ownerAccountId)

    suspend fun dischargedCensus(ownerAccountId: String): List<CensusAdmissionRow> =
        censusRows(
            careDao.episodesByStatus(ownerAccountId, CareEnums.EpisodeStatus.DISCHARGED.name),
            ownerAccountId
        )

    suspend fun hydrate(row: CensusAdmissionRow): CensusPatient? {
        val patient = careDao.getPatient(row.patientId) ?: return null
        val person = careDao.getPerson(patient.personId) ?: return null
        val location = row.currentLocationId?.let { careDao.getLocation(it) }
        val ward = location?.parentLocationId?.let { careDao.getLocation(it) }
        val involvement = careDao.involvementsForAdmission(row.admissionId)
            .firstOrNull { it.status == CareEnums.InvolvementStatus.ACTIVE.name }
        val problems = careDao.problemsForPatient(patient.ownerAccountId, row.patientId)
            .filter { it.status == CareEnums.ProblemStatus.ACTIVE.name }
        val nextTask = workDao.tasksForAdmission(row.admissionId)
            .firstOrNull {
                it.status == CareEnums.CareTaskStatus.PENDING.name ||
                    it.status == CareEnums.CareTaskStatus.IN_PROGRESS.name
            }
        return CensusPatient(
            admissionId = row.admissionId,
            patientId = row.patientId,
            personId = person.id,
            displayName = person.displayName,
            reportedAge = patient.reportedAge,
            reportedSex = patient.sexConceptId,
            locationLabel = location?.label ?: "No bed",
            wardLabel = ward?.label ?: "Unassigned",
            openTaskCount = row.openTaskCount,
            involvementRole = involvement?.role ?: CareEnums.InvolvementRole.PRIMARY_TEAM.name,
            episodeStatus = row.status,
            problemSummary = problems.firstOrNull()?.description,
            nextTaskTitle = nextTask?.title
        )
    }

    suspend fun censusPatients(
        ownerAccountId: String,
        discharged: Boolean = false
    ): List<CensusPatient> {
        val rows = if (discharged) dischargedCensus(ownerAccountId) else activeCensus(ownerAccountId)
        return rows.mapNotNull { hydrate(it) }
    }

    suspend fun patientHub(admissionId: String): PatientHub? {
        val episode = careDao.getEpisode(admissionId) ?: return null
        val row = CensusAdmissionRow(
            admissionId = episode.id,
            patientId = episode.patientId,
            hospitalId = episode.hospitalId,
            kind = episode.kind,
            status = episode.status,
            currentLocationId = careDao.currentLocationAssignment(admissionId)?.locationId,
            openTaskCount = workDao.tasksForAdmission(admissionId).count {
                it.status == CareEnums.CareTaskStatus.PENDING.name ||
                    it.status == CareEnums.CareTaskStatus.IN_PROGRESS.name
            }
        )
        val census = hydrate(row) ?: return null
        val notes = careDao.encountersForAdmission(admissionId)
            .flatMap { careDao.notesForEncounter(it.id) }
        val medications = therapyDao.ordersForAdmission(admissionId).map { order ->
            val definition = therapyDao.getMedicationDefinition(order.medicationId)
            HubMedication(
                orderId = order.id,
                medicationId = order.medicationId,
                name = definition?.displayName ?: order.medicationId,
                dose = order.doseText
                    ?: listOfNotNull(order.doseValue, order.doseUnit).joinToString(" ").ifBlank { "Dose not recorded" },
                schedule = order.schedule.originalText,
                status = order.status,
                active = order.status == CareEnums.MedicationOrderStatus.ACTIVE.name ||
                    order.status == CareEnums.MedicationOrderStatus.HELD.name
            )
        }
        val investigations = therapyDao.investigationOrdersForAdmission(admissionId).map { order ->
            HubLabItem(
                id = order.id,
                title = order.testName,
                kind = order.kind,
                status = order.status,
                detail = null
            )
        }
        val reports = therapyDao.diagnosticReportsForAdmission(admissionId).map { report ->
            HubLabItem(
                id = report.id,
                title = report.kind.replace('_', ' '),
                kind = report.kind,
                status = report.status,
                detail = report.narrative
            )
        }
        val vitals = careDao.observationsForPatient(episode.patientId).map { observation ->
            val scope = when (observation.admissionId) {
                admissionId -> VitalScope.CURRENT
                null -> VitalScope.PATIENT_LEVEL
                else -> VitalScope.PRIOR_ADMISSION
            }
            HubVital(
                name = observation.name,
                value = observation.displayValue(),
                observedAt = observation.observedAt.displayLabel(),
                unit = observation.unit,
                scope = scope,
                scopeLabel = when (scope) {
                    VitalScope.CURRENT -> "Current admission"
                    VitalScope.PRIOR_ADMISSION -> "Prior admission"
                    VitalScope.PATIENT_LEVEL -> "Patient-level history"
                }
            )
        }
        return PatientHub(
            census = census,
            problems = careDao.problemsForPatient(episode.ownerAccountId, episode.patientId),
            tasks = workDao.tasksForAdmission(admissionId),
            events = careDao.eventsForAdmission(admissionId),
            planNote = notes.lastOrNull()?.text,
            medications = medications,
            investigations = investigations,
            reports = reports,
            vitals = vitals,
            allergies = careDao.allergiesForPatient(episode.ownerAccountId, episode.patientId),
            timeline = careDao.eventsForAdmission(admissionId).map { event ->
                HubTimelineItem(
                    eventId = event.id,
                    title = timelineTitle(event.eventType),
                    whenLabel = event.effectiveTime.displayLabel(),
                    eventType = event.eventType
                )
            }
        )
    }

    suspend fun inbox(ownerAccountId: String): List<InboxItem> =
        careDao.unresolvedIntakes(ownerAccountId).map { InboxItem(it) }

    suspend fun openWorkQueue(ownerAccountId: String): List<CareTaskEntity> =
        workDao.openTasksForOwner(ownerAccountId)

    suspend fun episodeOrNull(admissionId: String): CareHospitalEpisodeEntity? =
        careDao.getEpisode(admissionId)

    suspend fun currentLocation(admissionId: String): CareLocationAssignmentEntity? =
        careDao.currentLocationAssignment(admissionId)

    private suspend fun censusRows(
        episodes: List<CareHospitalEpisodeEntity>,
        ownerAccountId: String
    ): List<CensusAdmissionRow> {
        val openTasks = workDao.openTasksForOwner(ownerAccountId).groupBy { it.admissionId }
        return episodes.map { episode ->
            val location = careDao.currentLocationAssignment(episode.id)
            CensusAdmissionRow(
                admissionId = episode.id,
                patientId = episode.patientId,
                hospitalId = episode.hospitalId,
                kind = episode.kind,
                status = episode.status,
                currentLocationId = location?.locationId,
                openTaskCount = openTasks[episode.id]?.size ?: 0
            )
        }
    }
}

private fun CareObservationEntity.displayValue(): String = when {
    !numericValue.isNullOrBlank() -> listOfNotNull(numericValue, unit).joinToString(" ")
    !textValue.isNullOrBlank() -> textValue.orEmpty()
    booleanValue != null -> if (booleanValue == true) "Yes" else "No"
    else -> valueKind
}
