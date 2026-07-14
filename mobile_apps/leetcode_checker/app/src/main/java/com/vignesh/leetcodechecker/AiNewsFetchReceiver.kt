package com.vignesh.leetcodechecker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vignesh.leetcodechecker.data.AINewsRepository
import com.vignesh.leetcodechecker.data.AINewsStorage
import com.vignesh.leetcodechecker.data.NewsArticle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that auto-fetches AI/ML News at 5 AM IST daily and posts
 * one notification per new article, so nothing has to be checked manually.
 *
 * Notifications are deliberately not auto-cancelling: opening one (or the app
 * itself) does not clear it from the shade. It stays until swiped away, so a
 * skimmed-but-not-yet-acted-on item doesn't silently disappear.
 */
class AiNewsFetchReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AiNewsFetchReceiver"
        private const val CHANNEL_ID = "ai_ml_news_channel"
        private const val ERROR_NOTIFICATION_ID = 10_040
        private const val NOTIFICATION_ID_BASE = 20_000
        // Real feeds (esp. arXiv) can list a burst of items in one day; cap the
        // notification count per run so a busy day doesn't flood the shade.
        private const val MAX_NOTIFICATIONS_PER_RUN = 15
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "AI/ML News auto-fetch triggered at 5 AM")

        // Schedule next day's fetch
        ConsistencyReminderScheduler.scheduleDailyAiNewsFetch(context)

        // goAsync() keeps the process alive for the fetch -- see DailyChallengeFetchReceiver
        // for why this matters when the alarm cold-starts the process overnight.
        val pendingResult = goAsync()
        scope.launch {
            try {
                fetchAndNotify(context)
            } catch (e: Exception) {
                Log.e(TAG, "AI/ML News auto-fetch failed", e)
                showErrorNotification(context, e.message ?: "Unknown error")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun fetchAndNotify(context: Context) {
        val articles = AINewsRepository(context).fetchAINews(forceRefresh = true).getOrThrow()

        if (!AINewsStorage.hasSeededNotifiedBaseline(context)) {
            // First time this ever runs: don't burst-notify for every article that
            // already existed before today. Just record them as seen and start
            // notifying from the next genuinely-new article onward.
            AINewsStorage.markArticlesNotified(context, articles.mapNotNull { it.url })
            Log.i(TAG, "Seeded notification baseline with ${articles.size} existing articles")
            return
        }

        val alreadyNotified = AINewsStorage.getNotifiedArticleUrls(context)
        val newArticles = articles.filter { !it.url.isNullOrBlank() && it.url !in alreadyNotified }

        if (newArticles.isEmpty()) {
            Log.i(TAG, "No new AI/ML news since last check")
            return
        }

        if (!canShowNotification(context)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val toNotify = newArticles.take(MAX_NOTIFICATIONS_PER_RUN)
        toNotify.forEach { article -> showArticleNotification(context, manager, article) }

        // Mark every fetched article (not just the notified subset) as seen so a
        // capped-out article isn't re-queued and notified again tomorrow.
        AINewsStorage.markArticlesNotified(context, newArticles.mapNotNull { it.url })
        Log.i(TAG, "Posted ${toNotify.size} new AI/ML news notifications")
    }

    private fun showArticleNotification(context: Context, manager: NotificationManager, article: NewsArticle) {
        val openIntent = if (!article.url.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        val notificationId = NOTIFICATION_ID_BASE + (article.url ?: article.title.orEmpty()).hashCode().and(0x0FFFFFFF)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sourceName = article.source?.name ?: article.source_id ?: "AI/ML News"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.neural_brain)
            .setContentTitle("📰 $sourceName")
            .setContentText(article.title ?: "New article")
            .setStyle(NotificationCompat.BigTextStyle().bigText(article.title ?: "New article"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .build()

        manager.notify(notificationId, notification)
    }

    private fun showErrorNotification(context: Context, error: String) {
        if (!canShowNotification(context)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.neural_brain)
            .setContentTitle("⚠️ AI/ML News Fetch Failed")
            .setContentText("Couldn't fetch news. Open the app to retry.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Error: $error"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        manager.notify(ERROR_NOTIFICATION_ID, notification)
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
            "AI/ML News",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "One notification per new AI/ML news article, fetched daily at 5 AM"
        }
        manager.createNotificationChannel(channel)
    }
}
