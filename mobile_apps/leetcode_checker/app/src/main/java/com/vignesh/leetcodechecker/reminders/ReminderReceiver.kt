package com.vignesh.leetcodechecker.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vignesh.leetcodechecker.MainActivity
import com.vignesh.leetcodechecker.R

private const val ACTION_SNOOZE = "com.vignesh.leetcodechecker.reminders.ACTION_SNOOZE"
private const val SNOOZE_MINUTES = 10

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return

        if (intent.action == ACTION_SNOOZE) {
            val reminder = ReminderStorage.loadReminders(context).firstOrNull { it.id == reminderId } ?: return
            val snoozed = reminder.copy(dueAtMillis = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
            ReminderStorage.updateReminder(context, snoozed)
            ReminderScheduler.schedule(context, snoozed)
            com.vignesh.leetcodechecker.widget.ReminderWidgetProvider.updateAllWidgets(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(reminder.id.hashCode())
            return
        }

        val reminder = ReminderStorage.loadReminders(context).firstOrNull { it.id == reminderId } ?: return
        if (reminder.isCompleted) return

        postNotification(context, reminder)

        if (reminder.repeatRule.unit != RepeatUnit.NONE) {
            val updated = reminder.copy(dueAtMillis = RepeatEngine.nextOccurrence(reminder.dueAtMillis, reminder.repeatRule))
            ReminderStorage.updateReminder(context, updated)
            ReminderScheduler.schedule(context, updated)
        }
        com.vignesh.leetcodechecker.widget.ReminderWidgetProvider.updateAllWidgets(context)
    }

    private fun postNotification(context: Context, reminder: Reminder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val openIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode() + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.neural_brain)
            .setContentTitle((if (reminder.isImportant) "⭐ " else "") + reminder.title)
            .setContentText(reminder.notes.ifBlank { "Reminder due now" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Snooze $SNOOZE_MINUTES min", snoozePendingIntent)
            .build()

        manager.notify(reminder.id.hashCode(), notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    companion object {
        private const val CHANNEL_ID = "leetcode_reminders_channel"
    }
}
