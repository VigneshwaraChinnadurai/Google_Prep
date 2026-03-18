package com.vignesh.leedcodecheckerollama

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object ConsistencyReminderScheduler {
    fun ensureHourlyReminder(context: Context) {
        val settings = AppSettingsStore.load(context)
        val intervalHours = settings.reminderIntervalHours.coerceIn(1, 12)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = reminderPendingIntent(context)

        val firstTrigger = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstTrigger,
            AlarmManager.INTERVAL_HOUR * intervalHours,
            pendingIntent
        )
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ConsistencyReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            10_001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
