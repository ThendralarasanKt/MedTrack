package com.medtrack.app.hybrid.reminder

import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.data.care.command.RespondToTaskRequest
import com.medtrack.app.data.care.dao.CareWorkDao
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.hybrid.account.AccountSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderActionHandler @Inject constructor(
    private val workDao: CareWorkDao,
    private val writePath: CareWritePath,
    private val accountSession: AccountSession
) {
    suspend fun handle(scheduleId: String, action: String) {
        val schedule = workDao.getReminderSchedule(scheduleId) ?: return
        val taskId = schedule.careTaskId ?: return
        val task = workDao.getCareTask(taskId) ?: return
        if (task.ownerAccountId != accountSession.ownerAccountId()) return
        if (task.status == CareEnums.CareTaskStatus.COMPLETED.name) return
        val responseAction = when (action) {
            SchedulingOutboxProcessor.ACTION_COMPLETE -> CareEnums.TaskResponseAction.COMPLETE
            SchedulingOutboxProcessor.ACTION_COMPLETE_WITH_NOTE -> CareEnums.TaskResponseAction.COMPLETE_WITH_NOTE
            else -> CareEnums.TaskResponseAction.NOTE
        }
        writePath.work.respondToTask(
            accountSession.workspace(),
            RespondToTaskRequest(
                taskId = taskId,
                action = responseAction,
                note = if (responseAction != CareEnums.TaskResponseAction.COMPLETE) {
                    "Notification note"
                } else {
                    null
                },
                expectedTaskVersion = task.version
            )
        )
    }
}
