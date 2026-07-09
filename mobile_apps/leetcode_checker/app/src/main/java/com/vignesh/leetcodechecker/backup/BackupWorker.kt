package com.vignesh.leetcodechecker.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vignesh.leetcodechecker.AppSettingsStore
import java.util.concurrent.TimeUnit

/**
 * Weekly background backup: writes a redacted-secrets snapshot to the folder the
 * user picked in Global Settings. No-ops quietly if no folder has been chosen.
 */
class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "weekly_backup"
        private const val TAG = "BackupWorker"

        /**
         * Ensure the weekly job is scheduled. Uses KEEP so calling this on every
         * app launch (the established convention here) doesn't keep resetting a
         * 7-day timer and prevent it from ever firing.
         */
        fun ensureScheduled(context: Context) {
            val settings = AppSettingsStore.load(context)
            if (settings.backupFolderUri.isBlank()) return

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val settings = AppSettingsStore.load(applicationContext)
        val folderUriString = settings.backupFolderUri
        if (folderUriString.isBlank()) return Result.success()

        val uri = runCatching { Uri.parse(folderUriString) }.getOrNull()
            ?: return Result.failure()

        val backupResult = BackupManager.createBackup(applicationContext, uri, redactSecrets = true)
        return backupResult.fold(
            onSuccess = {
                AppSettingsStore.save(applicationContext, settings.copy(lastBackupTimeMillis = System.currentTimeMillis()))
                Result.success()
            },
            onFailure = { e ->
                Log.e(TAG, "Weekly backup failed", e)
                Result.failure()
            }
        )
    }
}
