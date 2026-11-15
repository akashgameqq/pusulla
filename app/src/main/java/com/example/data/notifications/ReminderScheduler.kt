package com.example.data.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ScheduleItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val CHANNEL_ID = "pusula_schedule_reminders"

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Program Hatırlatıcısı"
        val body = intent.getStringExtra("body") ?: "Planlanan etkinliğiniz yaklaşıyor."
        val notifId = intent.getIntExtra("notifId", 1001)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pusula Program Hatırlatıcıları",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            notifId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        manager.notify(notifId, notification)
    }
}

object ReminderScheduler {
    fun scheduleReminder(context: Context, item: ScheduleItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = item.id.ifBlank { "${item.dateKey}_${item.startTime}_${item.title}" }.hashCode()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", "Pusula: ${item.title}")
            putExtra("body", "${item.startTime} • ${item.category} (${item.durationMinutes} dk)")
            putExtra("notifId", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (item.reminderMinutesBefore <= 0 || item.isCompleted) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val triggerMillis = runCatching {
            val date = LocalDate.parse(item.dateKey)
            val time = LocalTime.parse(item.startTime)
            val targetDateTime = LocalDateTime.of(date, time)
                .minusMinutes(item.reminderMinutesBefore.toLong())
            targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull() ?: return

        if (triggerMillis <= System.currentTimeMillis()) return

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerMillis,
            pendingIntent
        )
    }

    fun cancelReminder(context: Context, itemId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
