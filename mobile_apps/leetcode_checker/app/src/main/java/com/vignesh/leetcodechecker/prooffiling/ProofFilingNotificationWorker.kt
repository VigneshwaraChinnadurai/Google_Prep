package com.vignesh.leetcodechecker.prooffiling

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.vignesh.leetcodechecker.MainActivity
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * ProofFilingNotificationWorker - Handles weekly reminders for ProofFiling
 * 
 * Features:
 * - Weekly reminder notification on configured day/time (default: Friday 6 PM)
 * - Configurable via ProofFilingConfig
 * - Deep link to ProofFiling tab when tapped
 */
class ProofFilingNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "prooffiling_reminder"
        const val CHANNEL_ID = "prooffiling_notifications"
        const val NOTIFICATION_ID = 9001
        
        /**
         * Schedule the next notification based on config
         */
        fun scheduleNext(
            context: Context,
            dayOfWeek: Int = Calendar.FRIDAY,
            hour: Int = 18,
            minute: Int = 0
        ) {
            val workManager = WorkManager.getInstance(context)
            
            // Calculate delay until next occurrence
            val delay = calculateDelayUntilNext(dayOfWeek, hour, minute)
            
            val workRequest = OneTimeWorkRequestBuilder<ProofFilingNotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()
            
            // Replace any existing scheduled work
            workManager.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
        
        /**
         * Cancel any scheduled notifications
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
        
        /**
         * Calculate milliseconds until next occurrence of specified day/time
         */
        private fun calculateDelayUntilNext(
            targetDayOfWeek: Int,
            targetHour: Int,
            targetMinute: Int
        ): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, targetDayOfWeek)
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            // If target time has passed this week, move to next week
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.WEEK_OF_YEAR, 1)
            }
            
            return target.timeInMillis - now.timeInMillis
        }
        
        /**
         * Create notification channel (required for Android O+)
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "ProofFiling Reminders"
                val descriptionText = "Weekly reminders to update your ProofFiling"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    
    override suspend fun doWork(): Result {
        // Create notification channel if needed
        createNotificationChannel(applicationContext)
        
        // Show notification
        showNotification()
        
        // Schedule next week's notification
        val config = loadConfig()
        scheduleNext(
            applicationContext,
            dayOfWeek = config.reminderDayOfWeek,
            hour = config.reminderHour,
            minute = config.reminderMinute
        )
        
        return Result.success()
    }
    
    private fun showNotification() {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return // No permission, skip notification
            }
        }
        
        // Intent to open app
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_tab", "prooffiling")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setContentTitle("📊 Time to update your ProofFiling!")
            .setContentText("Record this week's wins, learnings, and evidence.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Handle missing notification permission
        }
    }
    
    private fun loadConfig(): ProofFilingConfig {
        val prefs = applicationContext.getSharedPreferences("prooffiling_prefs", Context.MODE_PRIVATE)
        val dayOfWeek = prefs.getInt("reminder_day", Calendar.FRIDAY)
        val hour = prefs.getInt("reminder_hour", 18)
        val minute = prefs.getInt("reminder_minute", 0)
        
        return ProofFilingConfig(
            reminderDayOfWeek = dayOfWeek,
            reminderHour = hour,
            reminderMinute = minute
        )
    }
}

/**
 * Extension functions for scheduling from ViewModel
 */
object ProofFilingNotificationScheduler {
    
    fun scheduleReminder(
        context: Context,
        config: ProofFilingConfig
    ) {
        ProofFilingNotificationWorker.createNotificationChannel(context)
        ProofFilingNotificationWorker.scheduleNext(
            context,
            dayOfWeek = config.reminderDayOfWeek,
            hour = config.reminderHour,
            minute = config.reminderMinute
        )
    }
    
    fun cancelReminder(context: Context) {
        ProofFilingNotificationWorker.cancel(context)
    }
}
