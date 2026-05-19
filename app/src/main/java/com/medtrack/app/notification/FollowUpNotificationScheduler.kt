package com.medtrack.app.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.ActivityCompat
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.medtrack.app.MainActivity
import com.medtrack.app.R
import com.medtrack.app.data.db.model.FollowUpWithPatient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowUpNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Follow-Up Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Loud follow-up appointment reminders"
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }

        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    fun schedule(followUpId: Int, scheduledDate: String, scheduledTime: String) {
        val triggerAtMillis = runCatching {
            LocalDateTime.of(
                LocalDate.parse(scheduledDate),
                LocalTime.parse(scheduledTime.take(5))
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull() ?: return

        if (triggerAtMillis <= System.currentTimeMillis()) {
            showDueNotificationNow(followUpId)
            return
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            followUpId,
            FollowUpNotificationReceiver.alarmIntent(context, followUpId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancel(followUpId: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            followUpId,
            FollowUpNotificationReceiver.alarmIntent(context, followUpId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        context.getSystemService<AlarmManager>()?.cancel(pendingIntent)
        pendingIntent.cancel()
        NotificationManagerCompat.from(context).cancel(followUpId)
    }

    fun showNotification(followUp: FollowUpWithPatient) {
        if (!canPostNotifications()) return

        createNotificationChannel()

        val reason = followUp.reason.trim().ifBlank { "follow up" }
        val roomText = followUp.roomNo.trim().uppercase()
        val detailText = buildString {
            append("Hey, you need to ")
            append(reason.replaceFirstChar { it.lowercase() })
            append(" for ")
            append(followUp.patientName)
            if (roomText.isNotBlank()) append(" - Room $roomText")
        }
        val detail = SpannableString(detailText).apply {
            val start = detailText.indexOf(followUp.patientName)
            if (start >= 0) {
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    start + followUp.patientName.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        val openIntent = MainActivity.intentForPatient(context, followUp.patientId)
        val rescheduleIntent = MainActivity.intentForFollowUps(context)
        val doneIntent = FollowUpNotificationReceiver.doneIntent(context, followUp.id)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reason.replaceFirstChar { it.uppercase() })
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setContentIntent(openIntent)
            .addAction(0, "Reschedule", rescheduleIntent)
            .addAction(0, "Done", doneIntent)
            .build()

        NotificationManagerCompat.from(context).notify(followUp.id, notification)
    }

    private fun showDueNotificationNow(followUpId: Int) {
        context.sendBroadcast(FollowUpNotificationReceiver.alarmIntent(context, followUpId))
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "follow_up_channel"
    }
}
