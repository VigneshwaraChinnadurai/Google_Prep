package com.vignesh.leetcodechecker

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
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leetcodechecker.data.DailyChallengeStore
import com.vignesh.leetcodechecker.data.GraphQLRequest
import com.vignesh.leetcodechecker.data.LeetCodeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * BroadcastReceiver that auto-fetches the daily LeetCode challenge at 6 AM.
 * 
 * This ensures users always have the current day's challenge ready without
 * having to manually open the app and fetch it.
 * 
 * The fetched challenge is stored in SharedPreferences and a notification
 * is shown with the problem difficulty and title.
 */
class DailyChallengeFetchReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DailyFetchReceiver"
        private const val CHANNEL_ID = "leetcode_daily_fetch_channel"
        private const val NOTIFICATION_ID = 10_020
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Daily auto-fetch triggered at 6 AM")
        
        // Schedule next day's fetch
        ConsistencyReminderScheduler.scheduleDailyAutoFetch(context)
        
        // Perform the fetch in a coroutine
        scope.launch {
            try {
                fetchAndStore(context)
            } catch (e: Exception) {
                Log.e(TAG, "Auto-fetch failed", e)
                showErrorNotification(context, e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun fetchAndStore(context: Context) {
        // Create Retrofit instance
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
        
        val api = Retrofit.Builder()
            .baseUrl("https://leetcode.com/")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LeetCodeApi::class.java)
        
        // Fetch daily challenge
        val dailyQuery = """
            query questionOfToday {
              activeDailyCodingChallengeQuestion {
                date
                link
                question {
                  title
                  titleSlug
                  difficulty
                  questionFrontendId
                  topicTags {
                    name
                    slug
                  }
                }
              }
            }
        """.trimIndent()
        
        val dailyResponse = api.postQuery(GraphQLRequest(query = dailyQuery))
        val daily = dailyResponse.data?.activeDailyCodingChallengeQuestion
            ?: throw Exception("Daily challenge data not available")
        
        // Store the fetched challenge
        DailyChallengeStore.saveFetchedChallenge(
            context = context,
            questionId = daily.question.questionFrontendId,
            title = daily.question.title,
            titleSlug = daily.question.titleSlug,
            difficulty = daily.question.difficulty,
            date = daily.date,
            topics = daily.question.topicTags.map { it.name }
        )
        
        Log.i(TAG, "Auto-fetched: #${daily.question.questionFrontendId}. ${daily.question.title}")
        
        // Show notification
        showSuccessNotification(
            context = context,
            questionId = daily.question.questionFrontendId,
            title = daily.question.title,
            difficulty = daily.question.difficulty
        )
    }

    private fun showSuccessNotification(
        context: Context,
        questionId: String,
        title: String,
        difficulty: String
    ) {
        if (!canShowNotification(context)) return
        
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        
        val difficultyEmoji = when (difficulty.lowercase()) {
            "easy" -> "🟢"
            "medium" -> "🟡"
            "hard" -> "🔴"
            else -> "⚪"
        }
        
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val today = SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date())
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.neural_brain)
            .setContentTitle("📅 Daily LeetCode Ready!")
            .setContentText("$difficultyEmoji #$questionId. $title ($difficulty)")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$difficultyEmoji #$questionId. $title\n" +
                        "Difficulty: $difficulty\n" +
                        "Ready for $today — Open the app to solve it!"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showErrorNotification(context: Context, error: String) {
        if (!canShowNotification(context)) return
        
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.neural_brain)
            .setContentTitle("⚠️ Auto-Fetch Failed")
            .setContentText("Couldn't fetch today's challenge. Open app to retry.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Error: $error\n\nOpen the app and tap 'Fetch Challenge' to try again."))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        manager.notify(NOTIFICATION_ID + 1, notification)
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
            "Daily Challenge Auto-Fetch",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for automatically fetched daily LeetCode challenges"
        }
        manager.createNotificationChannel(channel)
    }
}
