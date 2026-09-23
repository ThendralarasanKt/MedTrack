package com.medtrack.app.hybrid.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.medtrack.app.MainActivity
import com.medtrack.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var processor: SchedulingOutboxProcessor
    @Inject lateinit var actions: ReminderActionHandler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { processor.processPending() }
                    pending.finish()
                }
            }
            SchedulingOutboxProcessor.ACTION_FIRE -> showNotification(context, intent)
            SchedulingOutboxProcessor.ACTION_COMPLETE,
            SchedulingOutboxProcessor.ACTION_NOTE,
            SchedulingOutboxProcessor.ACTION_COMPLETE_WITH_NOTE -> {
                val pending = goAsync()
                val scheduleId = intent.getStringExtra(SchedulingOutboxProcessor.EXTRA_SCHEDULE_ID).orEmpty()
                val action = intent.action.orEmpty()
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { actions.handle(scheduleId, action) }
                    pending.finish()
                }
            }
        }
    }

    private fun showNotification(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(SchedulingOutboxProcessor.EXTRA_SCHEDULE_ID).orEmpty()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val complete = actionIntent(context, SchedulingOutboxProcessor.ACTION_COMPLETE, scheduleId, 1)
        val note = actionIntent(context, SchedulingOutboxProcessor.ACTION_NOTE, scheduleId, 2)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body))
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, context.getString(R.string.reminder_complete), complete)
            .addAction(0, context.getString(R.string.reminder_add_note), note)
            .setAutoCancel(true)
            .build()
        manager.notify(scheduleId.hashCode(), notification)
    }

    private fun actionIntent(context: Context, action: String, scheduleId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(SchedulingOutboxProcessor.EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode + scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val CHANNEL = "clinical-reminders"
    }
}
