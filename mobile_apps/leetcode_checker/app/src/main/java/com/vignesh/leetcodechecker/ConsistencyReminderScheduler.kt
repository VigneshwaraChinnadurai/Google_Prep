package com.vignesh.leetcodechecker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar
import java.util.TimeZone

object ConsistencyReminderScheduler {
    private const val TAG = "ConsistencyScheduler"
    private const val REMINDER_REQUEST_CODE = 10_001
    private const val AUTO_FETCH_REQUEST_CODE = 10_010
    
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
    
    /**
     * Schedule daily auto-fetch of the LeetCode challenge at 6 AM IST.
     * This ensures the user always has the daily challenge ready even without opening the app.
     */
    fun scheduleDailyAutoFetch(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = autoFetchPendingIntent(context)
        
        // Calculate next 6 AM IST
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(ist)
        val triggerTime = Calendar.getInstance(ist).apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If 6 AM has already passed today, schedule for tomorrow
            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        Log.i(TAG, "Scheduling daily auto-fetch for ${triggerTime.time}")
        
        // Use setExactAndAllowWhileIdle for more reliable execution
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fall back to inexact alarm if exact alarm permission not granted
            Log.w(TAG, "Exact alarm not permitted, using inexact: ${e.message}")
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerTime.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    }
    
    /**
     * Cancel the daily auto-fetch alarm.
     */
    fun cancelDailyAutoFetch(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = autoFetchPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        Log.i(TAG, "Cancelled daily auto-fetch alarm")
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ConsistencyReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun autoFetchPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyChallengeFetchReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            AUTO_FETCH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
