package com.medtrack.app.hybrid.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.medtrack.app.data.care.dao.CareWorkDao
import com.medtrack.app.data.care.entity.CareNotificationAttemptEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.hybrid.account.AccountSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchedulingOutboxProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workDao: CareWorkDao,
    private val accountSession: AccountSession
) {
    suspend fun processPending() {
        val owner = accountSession.ownerAccountId()
        workDao.pendingOutbox(owner).forEach { row ->
            val schedule = workDao.getReminderSchedule(row.scheduleId)
            val now = System.currentTimeMillis()
            if (schedule == null) {
                workDao.updateSchedulingOutbox(
                    row.copy(
                        state = CareEnums.SchedulingOutboxState.FAILED.name,
                        lastErrorCode = "SCHEDULE_MISSING",
                        attemptCount = row.attemptCount + 1
                    )
                )
                return@forEach
            }
            val applied = when (row.action) {
                CareEnums.SchedulingOutboxAction.CANCEL.name -> cancel(schedule.platformRequestKey)
                else -> schedule(schedule.triggerAt, schedule.id, schedule.platformRequestKey)
            }
            workDao.updateSchedulingOutbox(
                row.copy(
                    state = if (applied.ok) {
                        CareEnums.SchedulingOutboxState.APPLIED.name
                    } else {
                        CareEnums.SchedulingOutboxState.FAILED.name
                    },
                    lastErrorCode = applied.error,
                    attemptCount = row.attemptCount + 1
                )
            )
            if (!applied.ok && schedule.precision != CareEnums.ReminderPrecision.UNAVAILABLE.name) {
                workDao.updateReminderSchedule(
                    schedule.copy(precision = applied.precision ?: schedule.precision)
                )
            }
            workDao.insertNotificationAttempt(
                CareNotificationAttemptEntity(
                    id = CareIds.newId(),
                    ownerAccountId = owner,
                    scheduleId = schedule.id,
                    scheduleRevision = schedule.revision,
                    postAttemptedAt = now,
                    status = if (applied.ok) {
                        CareEnums.NotificationAttemptStatus.ACCEPTED_BY_OS.name
                    } else {
                        CareEnums.NotificationAttemptStatus.FAILED.name
                    },
                    reasonCode = applied.error,
                    createdAt = now,
                    createdBy = accountSession.workspace().actorPersonId
                )
            )
        }
    }

    private fun schedule(triggerAt: Long, scheduleId: String, key: String): ApplyResult {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(key, scheduleId)
        return try {
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                ApplyResult(true, precision = CareEnums.ReminderPrecision.INEXACT.name)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                ApplyResult(true, precision = CareEnums.ReminderPrecision.EXACT.name)
            }
        } catch (_: SecurityException) {
            ApplyResult(false, "PERMISSION", CareEnums.ReminderPrecision.UNAVAILABLE.name)
        }
    }

    private fun cancel(key: String): ApplyResult {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(key, key))
        return ApplyResult(true)
    }

    private fun pendingIntent(key: String, scheduleId: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_KEY, key)
        }
        return PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class ApplyResult(
        val ok: Boolean,
        val error: String? = null,
        val precision: String? = null
    )

    companion object {
        const val ACTION_FIRE = "com.medtrack.app.REMINDER_FIRE"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_KEY = "platformKey"
        const val ACTION_COMPLETE = "com.medtrack.app.REMINDER_COMPLETE"
        const val ACTION_NOTE = "com.medtrack.app.REMINDER_NOTE"
        const val ACTION_COMPLETE_WITH_NOTE = "com.medtrack.app.REMINDER_COMPLETE_WITH_NOTE"
    }
}
