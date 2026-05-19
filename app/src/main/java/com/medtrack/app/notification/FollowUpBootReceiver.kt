package com.medtrack.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.medtrack.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FollowUpBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medtrack_db"
                ).fallbackToDestructiveMigration().build()

                try {
                    val scheduler = FollowUpNotificationScheduler(context.applicationContext)
                    db.followUpDao().getPendingFollowUps().forEach { followUp ->
                        scheduler.schedule(
                            followUpId = followUp.id,
                            scheduledDate = followUp.scheduledDate,
                            scheduledTime = followUp.scheduledTime
                        )
                    }
                } finally {
                    db.close()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
