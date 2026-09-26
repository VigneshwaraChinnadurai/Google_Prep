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
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val reminder = ReminderStorage.loadReminders(context).firstOrNull { it.id == reminderId } ?: return
        if (reminder.isCompleted) return

        postNotification(context, reminder)

        if (reminder.repeat != ReminderRepeat.NONE) {
            val updated = reminder.copy(dueAtMillis = nextOccurrence(reminder.dueAtMillis, reminder.repeat))
            ReminderStorage.updateReminder(context, updated)
            ReminderScheduler.schedule(context, updated)
        }
    }

    /** Advances past now in case the device was off across one or more occurrences. */
    private fun nextOccurrence(fromMillis: Long, repeat: ReminderRepeat): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
        do {
            when (repeat) {
                ReminderRepeat.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                ReminderRepeat.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                ReminderRepeat.MONTHLY -> cal.add(Calendar.MONTH, 1)
                ReminderRepeat.YEARLY -> cal.add(Calendar.YEAR, 1)
                ReminderRepeat.NONE -> return cal.timeInMillis
            }
        } while (cal.timeInMillis <= System.currentTimeMillis())
        return cal.timeInMillis
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.neural_brain)
            .setContentTitle(reminder.title)
            .setContentText(reminder.notes.ifBlank { "Reminder due now" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
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
