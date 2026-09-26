package com.vignesh.leetcodechecker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Per-reminder exact alarm scheduling, matching this app's existing daily-fetch alarm
 * pattern (ConsistencyReminderScheduler) but keyed per reminder ID instead of one fixed
 * daily time, since each reminder has its own independent due time.
 */
object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    const val EXTRA_REMINDER_ID = "reminder_id"

    fun schedule(context: Context, reminder: Reminder) {
        if (reminder.isCompleted) {
            cancel(context, reminder.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntentFor(context, reminder.id)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.dueAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.dueAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not permitted for reminder ${reminder.id}, using inexact: ${e.message}")
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.dueAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntentFor(context, reminderId))
    }

    /**
     * Re-arms every pending reminder -- AlarmManager alarms don't survive a device reboot,
     * so this is called from BootRescheduleReceiver to restore them.
     */
    fun rescheduleAll(context: Context) {
        ReminderStorage.loadReminders(context)
            .filter { !it.isCompleted }
            .forEach { schedule(context, it) }
    }

    private fun pendingIntentFor(context: Context, reminderId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
