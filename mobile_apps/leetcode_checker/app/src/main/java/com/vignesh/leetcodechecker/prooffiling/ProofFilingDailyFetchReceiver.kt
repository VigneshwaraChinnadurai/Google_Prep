package com.vignesh.leetcodechecker.prooffiling

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.ConsistencyReminderScheduler
import com.vignesh.leetcodechecker.MainActivity
import com.vignesh.leetcodechecker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that auto-refreshes ProofFiling's GitHub and LeetCode stats
 * daily at 7 AM IST, so the current week's entry stays current without the user
 * having to remember to tap "Fetch GitHub"/"Fetch LeetCode" themselves.
 *
 * Deliberately does NOT call the LLM weekly-summary generator -- that stays a
 * manual, explicit action (the "Generate Summary" button), so this daily job
 * doesn't silently add a new recurring billed LLM call on top of raw stats fetch.
 */
class ProofFilingDailyFetchReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ProofFilingDailyFetch"
        private const val CHANNEL_ID = "prooffiling_daily_fetch_channel"
        private const val NOTIFICATION_ID = 10_050
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "ProofFiling daily auto-fetch triggered at 7 AM")

        ConsistencyReminderScheduler.scheduleDailyProofFilingFetch(context)

        val pendingResult = goAsync()
        scope.launch {
            try {
                fetchAndSave(context)
            } catch (e: Exception) {
                Log.e(TAG, "ProofFiling daily auto-fetch failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun fetchAndSave(context: Context) {
        val repository = ProofFilingRepository(context)
        val currentEntry = repository.getCurrentWeekEntry()

        val githubResult = repository.fetchGitHubStats()
        val githubStats = githubResult.getOrNull()

        val settings = AppSettingsStore.load(context)
        val previousTotal = repository.loadEntries()
            .firstOrNull { it.id != currentEntry.id }
            ?.leetcodeStats?.currentTotalSolved ?: 0
        val leetcodeResult = repository.fetchLeetCodeStats(settings.leetcodeUsername, previousTotal)
        val leetcodeStats = leetcodeResult.getOrNull()

        if (githubStats == null && leetcodeStats == null) {
            Log.w(TAG, "Both GitHub and LeetCode fetch failed, nothing to save")
            return
        }

        val updatedEntry = currentEntry.copy(
            githubStats = githubStats ?: currentEntry.githubStats,
            leetcodeStats = leetcodeStats ?: currentEntry.leetcodeStats
        )
        repository.saveEntry(updatedEntry)
        Log.i(TAG, "ProofFiling daily fetch saved: " +
            "${githubStats?.totalCommits ?: "?"} commits, ${leetcodeStats?.problemsSolvedThisWeek ?: "?"} problems solved")

        showNotification(context, githubStats?.totalCommits, leetcodeStats?.problemsSolvedThisWeek)
    }

    private fun showNotification(context: Context, commits: Int?, problemsSolved: Int?) {
        if (!canShowNotification(context)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val commitsText = commits?.let { "$it commits" } ?: "commits unavailable"
        val leetcodeText = problemsSolved?.let { "$it problems solved" } ?: "LeetCode unavailable"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.neural_brain)
            .setContentTitle("📊 ProofFiling updated")
            .setContentText("$commitsText this week, $leetcodeText")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun canShowNotification(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ProofFiling Daily Update",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Daily auto-refresh of this week's GitHub and LeetCode stats"
        }
        manager.createNotificationChannel(channel)
    }
}
