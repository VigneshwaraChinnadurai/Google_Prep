package com.vignesh.leetcodechecker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leetcodechecker.data.DailyChallengeUiModel
import com.vignesh.leetcodechecker.data.LeetCodeRepository
import com.vignesh.leetcodechecker.data.PipelineException
import java.util.UUID

/**
 * Runs the "LLM Solve" Gemini call inside WorkManager instead of a plain
 * viewModelScope coroutine. A PARTIAL_WAKE_LOCK (the previous approach) only
 * keeps the CPU from sleeping -- it does nothing to stop the OS (especially
 * aggressive OEM battery management, e.g. Samsung's process freezing) from
 * killing the app's whole process once it's backgrounded, silently cutting off
 * an in-flight, already-billed API call. WorkManager persists the request and
 * keeps running (or gets rescheduled on a fresh process) independent of the
 * hosting Activity/ViewModel's lifecycle, and holds its own wake lock for the
 * duration of doWork(). The ViewModel observes progress for live UI updates
 * when the app happens to still be open, but doesn't need to be for the work
 * (and the local save, and the notification) to complete.
 */
class GeminiSolveWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "GeminiSolveWorker"
        private const val CHANNEL_ID = "gemini_solve_channel"
        private const val NOTIFICATION_ID = 10_060

        const val KEY_CHALLENGE_JSON = "challenge_json"
        const val KEY_RESULT_CODE = "result_code"
        const val KEY_RESULT_EXPLANATION = "result_explanation"
        const val KEY_RESULT_VALIDATION = "result_validation"
        const val KEY_RESULT_LOCAL_PATH = "result_local_path"
        const val KEY_ERROR = "error"
        const val KEY_ERROR_DEBUG_LOG = "error_debug_log"

        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        private val challengeAdapter = moshi.adapter(DailyChallengeUiModel::class.java)

        fun enqueue(context: Context, challenge: DailyChallengeUiModel): UUID {
            val challengeJson = challengeAdapter.toJson(challenge)
            val request = OneTimeWorkRequestBuilder<GeminiSolveWorker>()
                .setInputData(workDataOf(KEY_CHALLENGE_JSON to challengeJson))
                .addTag("gemini_solve_${challenge.questionId}")
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }
    }

    override suspend fun doWork(): Result {
        val challengeJson = inputData.getString(KEY_CHALLENGE_JSON) ?: return Result.failure()
        val challenge = runCatching { challengeAdapter.fromJson(challengeJson) }.getOrNull()
            ?: return Result.failure()

        val repository = LeetCodeRepository(applicationContext)
        val outcome = repository.generateDetailedAnswer(challenge, forceRefresh = true)

        return outcome.fold(
            onSuccess = { answer ->
                ConsistencyStorage.saveAi(applicationContext, answer)
                ConsistencyStorage.saveProblemToHistory(applicationContext, challenge, "Gemini", answer)

                val localPath = runCatching {
                    val files = RevisionExportManager.buildRevisionFiles(
                        challenge = challenge,
                        aiCode = answer.leetcodePythonCode,
                        aiExplanation = answer.explanation,
                        aiValidation = answer.testcaseValidation
                    )
                    RevisionExportManager.writeLocalRevisionFiles(applicationContext, files)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to save revision files locally", e)
                }.getOrNull()

                showNotification(challenge, success = true, error = null)
                Result.success(
                    workDataOf(
                        KEY_RESULT_CODE to answer.leetcodePythonCode,
                        KEY_RESULT_EXPLANATION to answer.explanation,
                        KEY_RESULT_VALIDATION to answer.testcaseValidation,
                        KEY_RESULT_LOCAL_PATH to localPath
                    )
                )
            },
            onFailure = { e ->
                Log.e(TAG, "Gemini solve failed", e)
                showNotification(challenge, success = false, error = e.message)
                val debugLog = (e as? PipelineException)?.debugLog
                Result.failure(
                    workDataOf(
                        KEY_ERROR to (e.message ?: "Unknown error"),
                        KEY_ERROR_DEBUG_LOG to debugLog
                    )
                )
            }
        )
    }

    private fun showNotification(challenge: DailyChallengeUiModel, success: Boolean, error: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AI Solve Results", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.neural_brain)
            .setContentTitle(if (success) "✅ AI solution ready" else "⚠️ AI solve failed")
            .setContentText(
                if (success) "#${challenge.questionId}. ${challenge.title} -- open the app to view it"
                else (error ?: "Something went wrong")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
