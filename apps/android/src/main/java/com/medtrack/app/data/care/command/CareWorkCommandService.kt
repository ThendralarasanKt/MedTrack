package com.medtrack.app.data.care.command

import androidx.room.withTransaction
import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.dao.CareWorkDao
import com.medtrack.app.data.care.entity.*
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.data.db.converters.ClinicalTimeConverters
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

data class CreateReferralRequest(
    val admissionId: String,
    val direction: CareEnums.ReferralDirection,
    val reason: String,
    val requestedAt: ClinicalTime,
    val priority: CareEnums.ReferralPriority = CareEnums.ReferralPriority.UNSPECIFIED,
    val requesterPersonId: String? = null,
    val requesterTeamId: String? = null,
    val requestedPersonId: String? = null,
    val requestedTeamId: String? = null,
    val requestedSpecialty: String? = null,
    val operationId: String = CareIds.newId()
)

data class RecordReferralMilestoneRequest(
    val referralId: String,
    val kind: CareEnums.ReferralMilestoneKind,
    val encounterId: String? = null,
    val patientSeenReported: Boolean = false,
    val note: String? = null,
    val operationId: String = CareIds.newId()
)

data class AssignTaskRequest(
    val admissionId: String,
    val kind: CareEnums.CareTaskKind = CareEnums.CareTaskKind.OTHER,
    val title: String,
    val instructions: String? = null,
    val priority: CareEnums.CareTaskPriority = CareEnums.CareTaskPriority.ROUTINE,
    val followUpOwnerPersonId: String,
    val dueAt: Long? = null,
    val dueZoneId: String? = null,
    val assigneePersonId: String? = null,
    val referralId: String? = null,
    val scheduleReminder: Boolean = false,
    val operationId: String = CareIds.newId()
)

data class RespondToTaskRequest(
    val taskId: String,
    val action: CareEnums.TaskResponseAction,
    val note: String? = null,
    val performedAt: ClinicalTime = ClinicalTime.instant(System.currentTimeMillis()),
    val newDueAt: Long? = null,
    val clearDue: Boolean = false,
    val expectedTaskVersion: Int? = null,
    val operationId: String = CareIds.newId()
)

@Singleton
class CareWorkCommandService @Inject constructor(
    private val database: AppDatabase,
    private val careDao: CareDao,
    private val workDao: CareWorkDao
) {
    private val clinicalTimeConverters = ClinicalTimeConverters()
    private val provenance = CareProvenanceWriter(careDao)

    suspend fun createReferral(
        workspace: CareWorkspace,
        request: CreateReferralRequest
    ): String {
        require(
            request.requestedPersonId != null ||
                request.requestedTeamId != null ||
                !request.requestedSpecialty.isNullOrBlank()
        ) { "Referral requires requested person, team, or specialty" }
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.admissionId, request.direction.name, request.reason,
                clinicalTimeConverters.fromClinicalTime(request.requestedAt).orEmpty(),
                request.priority.name,
                request.requestedPersonId.orEmpty(),
                request.requestedTeamId.orEmpty(),
                request.requestedSpecialty.orEmpty()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let {
                return@withTransaction JSONObject(it.resultReferences).getString("referralId")
            }
            val episode = careDao.getEpisode(request.admissionId) ?: error("Admission not found")
            require(episode.ownerAccountId == workspace.ownerAccountId)
            val now = System.currentTimeMillis()
            val referralId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace, episode.patientId, request.admissionId,
                "REFERRAL_CREATED", request.requestedAt, request.operationId, now = now
            )
            workDao.insertReferral(
                CareReferralEntity(
                    id = referralId,
                    ownerAccountId = workspace.ownerAccountId,
                    admissionId = request.admissionId,
                    direction = request.direction.name,
                    requesterPersonId = request.requesterPersonId,
                    requesterTeamId = request.requesterTeamId,
                    requestedPersonId = request.requestedPersonId,
                    requestedTeamId = request.requestedTeamId,
                    requestedSpecialty = request.requestedSpecialty,
                    reason = request.reason,
                    requestedAt = request.requestedAt,
                    priority = request.priority.name,
                    status = CareEnums.ReferralStatus.OPEN.name,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            val refs = JSONObject().put("referralId", referralId).put("eventId", eventId).toString()
            provenance.commitOperation(workspace, request.operationId, requestHash, refs, now)
            referralId
        }
    }

    suspend fun recordReferralMilestone(
        workspace: CareWorkspace,
        request: RecordReferralMilestoneRequest
    ): String {
        if (request.kind == CareEnums.ReferralMilestoneKind.PATIENT_SEEN) {
            require(request.encounterId != null || request.patientSeenReported) {
                "PATIENT_SEEN requires encounter or explicit reported evidence"
            }
        }
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.referralId, request.kind.name,
                request.encounterId.orEmpty(),
                request.patientSeenReported.toString(),
                request.note.orEmpty()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let {
                return@withTransaction JSONObject(it.resultReferences).getString("milestoneId")
            }
            val referral = workDao.getReferral(request.referralId) ?: error("Referral not found")
            require(referral.ownerAccountId == workspace.ownerAccountId)
            val episode = careDao.getEpisode(referral.admissionId) ?: error("Admission not found")
            val now = System.currentTimeMillis()
            val milestoneId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace, episode.patientId, referral.admissionId,
                "REFERRAL_MILESTONE", ClinicalTime.instant(now), request.operationId, now = now
            )
            workDao.insertReferralMilestone(
                CareReferralMilestoneEntity(
                    id = milestoneId,
                    ownerAccountId = workspace.ownerAccountId,
                    referralId = request.referralId,
                    kind = request.kind.name,
                    encounterId = request.encounterId,
                    note = request.note,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            if (request.kind == CareEnums.ReferralMilestoneKind.CLOSED ||
                request.kind == CareEnums.ReferralMilestoneKind.CANCELLED
            ) {
                workDao.updateReferral(
                    referral.copy(
                        status = if (request.kind == CareEnums.ReferralMilestoneKind.CLOSED) {
                            CareEnums.ReferralStatus.CLOSED.name
                        } else {
                            CareEnums.ReferralStatus.CANCELLED.name
                        },
                        version = referral.version + 1
                    )
                )
            }
            val refs = JSONObject().put("milestoneId", milestoneId).put("eventId", eventId).toString()
            provenance.commitOperation(workspace, request.operationId, requestHash, refs, now)
            milestoneId
        }
    }

    suspend fun assignTask(
        workspace: CareWorkspace,
        request: AssignTaskRequest
    ): String {
        if (request.dueAt != null) {
            require(!request.dueZoneId.isNullOrBlank()) { "dueAt requires dueZoneId" }
        }
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.admissionId, request.kind.name, request.title,
                request.instructions.orEmpty(), request.priority.name,
                request.followUpOwnerPersonId,
                request.dueAt?.toString().orEmpty(),
                request.dueZoneId.orEmpty(),
                request.assigneePersonId.orEmpty(),
                request.referralId.orEmpty(),
                request.scheduleReminder.toString()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let {
                return@withTransaction JSONObject(it.resultReferences).getString("taskId")
            }
            val episode = careDao.getEpisode(request.admissionId) ?: error("Admission not found")
            require(episode.ownerAccountId == workspace.ownerAccountId)
            val now = System.currentTimeMillis()
            val taskId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace, episode.patientId, request.admissionId,
                "TASK_ASSIGNED", ClinicalTime.instant(now), request.operationId, now = now
            )
            workDao.insertCareTask(
                CareTaskEntity(
                    id = taskId,
                    ownerAccountId = workspace.ownerAccountId,
                    admissionId = request.admissionId,
                    kind = request.kind.name,
                    title = request.title,
                    instructions = request.instructions,
                    status = CareEnums.CareTaskStatus.PENDING.name,
                    priority = request.priority.name,
                    followUpOwnerPersonId = request.followUpOwnerPersonId,
                    dueAt = request.dueAt,
                    dueZoneId = request.dueZoneId,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            request.assigneePersonId?.let { personId ->
                workDao.insertTaskAssignment(
                    CareTaskAssignmentEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        taskId = taskId,
                        personId = personId,
                        startsAt = ClinicalTime.instant(now),
                        eventId = eventId,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            }
            request.referralId?.let { referralId ->
                workDao.insertTaskClinicalLink(
                    CareTaskClinicalLinkEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        taskId = taskId,
                        referralId = referralId,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            }
            var scheduleId: String? = null
            if (request.scheduleReminder && request.dueAt != null) {
                scheduleId = CareIds.newId()
                val key = "task:$taskId:r1"
                workDao.insertReminderSchedule(
                    CareReminderScheduleEntity(
                        id = scheduleId,
                        ownerAccountId = workspace.ownerAccountId,
                        careTaskId = taskId,
                        triggerAt = request.dueAt,
                        zoneId = request.dueZoneId!!,
                        revision = 1,
                        state = CareEnums.ReminderState.ACTIVE.name,
                        precision = CareEnums.ReminderPrecision.EXACT.name,
                        platformRequestKey = key,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
                workDao.insertSchedulingOutbox(
                    CareSchedulingOutboxEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        scheduleId = scheduleId,
                        scheduleRevision = 1,
                        action = CareEnums.SchedulingOutboxAction.SCHEDULE.name,
                        state = CareEnums.SchedulingOutboxState.PENDING.name,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            }
            val refsJson = JSONObject().put("taskId", taskId).put("eventId", eventId)
            scheduleId?.let { refsJson.put("scheduleId", it) }
            val refs = refsJson.toString()
            provenance.commitOperation(workspace, request.operationId, requestHash, refs, now)
            taskId
        }
    }

    suspend fun scheduleIdForTask(taskId: String): String? =
        workDao.remindersForTask(taskId).lastOrNull()?.id

    suspend fun respondToTask(
        workspace: CareWorkspace,
        request: RespondToTaskRequest
    ): String {
        when (request.action) {
            CareEnums.TaskResponseAction.NOTE,
            CareEnums.TaskResponseAction.COMPLETE_WITH_NOTE,
            CareEnums.TaskResponseAction.CANCEL ->
                require(!request.note.isNullOrBlank()) { "${request.action} requires note" }
            CareEnums.TaskResponseAction.RESCHEDULE ->
                require(request.newDueAt != null || request.clearDue) {
                    "RESCHEDULE requires newDueAt or clearDue"
                }
            else -> Unit
        }
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.taskId, request.action.name, request.note.orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.performedAt).orEmpty(),
                request.newDueAt?.toString().orEmpty(),
                request.clearDue.toString()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let {
                return@withTransaction JSONObject(it.resultReferences).getString("responseId")
            }
            val task = workDao.getCareTask(request.taskId) ?: error("Task not found")
            require(task.ownerAccountId == workspace.ownerAccountId)
            request.expectedTaskVersion?.let { expected ->
                if (task.version != expected) {
                    throw StaleRecordException("CareTask", task.id, expected, task.version)
                }
            }
            val episode = careDao.getEpisode(task.admissionId) ?: error("Admission not found")
            val now = System.currentTimeMillis()
            val responseId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace, episode.patientId, task.admissionId,
                "TASK_RESPONSE", request.performedAt, request.operationId, now = now
            )
            workDao.insertTaskResponse(
                CareTaskResponseEntity(
                    id = responseId,
                    ownerAccountId = workspace.ownerAccountId,
                    taskId = request.taskId,
                    action = request.action.name,
                    actorPersonId = workspace.actorPersonId,
                    note = request.note,
                    performedAt = request.performedAt,
                    oldDueAt = task.dueAt,
                    newDueAt = when {
                        request.clearDue -> null
                        request.newDueAt != null -> request.newDueAt
                        else -> task.dueAt
                    },
                    eventId = eventId,
                    operationId = request.operationId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            val updated = when (request.action) {
                CareEnums.TaskResponseAction.COMPLETE,
                CareEnums.TaskResponseAction.COMPLETE_WITH_NOTE ->
                    task.copy(
                        status = CareEnums.CareTaskStatus.COMPLETED.name,
                        completedAt = request.performedAt,
                        version = task.version + 1,
                        eventId = eventId
                    )
                CareEnums.TaskResponseAction.CANCEL ->
                    task.copy(
                        status = CareEnums.CareTaskStatus.CANCELLED.name,
                        version = task.version + 1,
                        eventId = eventId
                    )
                CareEnums.TaskResponseAction.REOPEN ->
                    task.copy(
                        status = CareEnums.CareTaskStatus.PENDING.name,
                        completedAt = null,
                        version = task.version + 1,
                        eventId = eventId
                    )
                CareEnums.TaskResponseAction.RESCHEDULE ->
                    task.copy(
                        dueAt = if (request.clearDue) null else request.newDueAt,
                        dueZoneId = if (request.clearDue) null else task.dueZoneId,
                        version = task.version + 1,
                        eventId = eventId
                    )
                CareEnums.TaskResponseAction.NOTE ->
                    task.copy(version = task.version + 1, eventId = eventId)
            }
            workDao.updateCareTask(updated)
            if (request.action == CareEnums.TaskResponseAction.RESCHEDULE &&
                request.newDueAt != null &&
                !task.dueZoneId.isNullOrBlank()
            ) {
                val prior = workDao.remindersForTask(task.id).lastOrNull()
                prior?.let {
                    workDao.updateReminderSchedule(
                        it.copy(state = CareEnums.ReminderState.CANCELLED.name, version = it.version + 1)
                    )
                    workDao.insertSchedulingOutbox(
                        CareSchedulingOutboxEntity(
                            id = CareIds.newId(),
                            ownerAccountId = workspace.ownerAccountId,
                            scheduleId = it.id,
                            scheduleRevision = it.revision,
                            action = CareEnums.SchedulingOutboxAction.CANCEL.name,
                            state = CareEnums.SchedulingOutboxState.PENDING.name,
                            createdAt = now,
                            createdBy = workspace.actorPersonId
                        )
                    )
                }
                val revision = (prior?.revision ?: 0) + 1
                val scheduleId = CareIds.newId()
                workDao.insertReminderSchedule(
                    CareReminderScheduleEntity(
                        id = scheduleId,
                        ownerAccountId = workspace.ownerAccountId,
                        careTaskId = task.id,
                        triggerAt = request.newDueAt,
                        zoneId = task.dueZoneId,
                        revision = revision,
                        state = CareEnums.ReminderState.ACTIVE.name,
                        precision = CareEnums.ReminderPrecision.EXACT.name,
                        platformRequestKey = "task:${task.id}:r$revision",
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
                workDao.insertSchedulingOutbox(
                    CareSchedulingOutboxEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        scheduleId = scheduleId,
                        scheduleRevision = revision,
                        action = CareEnums.SchedulingOutboxAction.SCHEDULE.name,
                        state = CareEnums.SchedulingOutboxState.PENDING.name,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            }
            val refs = JSONObject().put("responseId", responseId).put("eventId", eventId).toString()
            provenance.commitOperation(workspace, request.operationId, requestHash, refs, now)
            responseId
        }
    }
}
