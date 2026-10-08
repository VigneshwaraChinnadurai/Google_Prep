package com.vignesh.jobmatcher.work

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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vignesh.jobmatcher.MainActivity
import com.vignesh.jobmatcher.R
import com.vignesh.jobmatcher.data.AppStorage
import com.vignesh.jobmatcher.data.JobRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Once a day: fetch every automatic-source company and notify if new postings cleared the
 * on-device pre-filter -- i.e. there's a fresh batch waiting to be scored in Claude.
 */
class DailyFetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val report = JobRepository(applicationContext).fetchAll()
        val pending = JobRepository(applicationContext).pendingScoring().size
        if (report.newShortlisted > 0) {
            notify(
                applicationContext,
                "${report.newShortlisted} new job${if (report.newShortlisted == 1) "" else "s"} worth a look",
                "$pending waiting for Claude scoring. Open Discover → Copy scoring prompt."
            )
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_job_fetch"
        private const val CHANNEL_ID = "new_jobs"

        /** (Re)schedules the daily run at the configured hour, or cancels it if disabled. */
        fun schedule(context: Context) {
            val settings = AppStorage.loadSettings(context)
            val wm = WorkManager.getInstance(context)
            if (!settings.autoFetchEnabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, settings.autoFetchHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            val request = PeriodicWorkRequestBuilder<DailyFetchWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // UPDATE keeps the existing period but applies the new initial delay/constraints.
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun notify(context: Context, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            if (Build.VERSION.SDK_INT >= 26) {
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "New job matches", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(1001, notification)
        }
    }
}
