package com.medtrack.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.medtrack.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class FollowUpNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val followUpId = intent.getIntExtra(EXTRA_FOLLOW_UP_ID, -1)
                if (followUpId <= 0) return@launch

                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medtrack_db"
                ).fallbackToDestructiveMigration().build()

                try {
                    val dao = db.followUpDao()
                    when (intent.action) {
                        ACTION_DONE -> {
                            dao.markDone(followUpId, LocalDateTime.now().toString())
                            FollowUpNotificationScheduler(context.applicationContext).cancel(followUpId)
                        }
                        else -> {
                            val followUp = dao.getFollowUpWithPatientById(followUpId) ?: return@launch
                            if (followUp.status == "DONE") return@launch

                            dao.markNotified(followUpId, LocalDateTime.now().toString())
                            FollowUpNotificationScheduler(context.applicationContext)
                                .showNotification(followUp)
                        }
                    }
                } finally {
                    db.close()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_ALARM = "com.medtrack.app.notification.ACTION_FOLLOW_UP_ALARM"
        private const val ACTION_DONE = "com.medtrack.app.notification.ACTION_FOLLOW_UP_DONE"
        private const val EXTRA_FOLLOW_UP_ID = "follow_up_id"

        fun alarmIntent(context: Context, followUpId: Int): Intent =
            Intent(context, FollowUpNotificationReceiver::class.java).apply {
                action = ACTION_ALARM
                putExtra(EXTRA_FOLLOW_UP_ID, followUpId)
            }

        fun doneIntent(context: Context, followUpId: Int) =
            android.app.PendingIntent.getBroadcast(
                context,
                followUpId + 100_000,
                Intent(context, FollowUpNotificationReceiver::class.java).apply {
                    action = ACTION_DONE
                    putExtra(EXTRA_FOLLOW_UP_ID, followUpId)
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )
    }
}
