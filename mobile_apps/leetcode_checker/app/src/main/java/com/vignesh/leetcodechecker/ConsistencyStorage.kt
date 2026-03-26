package com.vignesh.leetcodechecker

import android.content.Context
import com.vignesh.leetcodechecker.data.AiGenerationResult
import com.vignesh.leetcodechecker.data.DailyChallengeUiModel
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ConsistencyStorage {
    private const val PREFS = "leetcode_consistency_prefs"
    private const val KEY_CHALLENGE = "cached_challenge_json"
    private const val KEY_AI = "cached_ai_json"
    private const val KEY_COMPLETED_PREFIX = "completed_"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveChallenge(context: Context, challenge: DailyChallengeUiModel) {
        val json = JSONObject()
            .put("date", challenge.date)
            .put("title", challenge.title)
            .put("titleSlug", challenge.titleSlug)
            .put("difficulty", challenge.difficulty)
            .put("questionId", challenge.questionId)
            .put("tags", challenge.tags.joinToString("||"))
            .put("url", challenge.url)
            .put("descriptionPreview", challenge.descriptionPreview)
            .put("fullStatement", challenge.fullStatement)
            .put("pythonStarterCode", challenge.pythonStarterCode)
            .put("exampleTestcases", challenge.exampleTestcases)
            .toString()
        prefs(context).edit().putString(KEY_CHALLENGE, json).apply()
    }

    fun loadChallenge(context: Context): DailyChallengeUiModel? {
        val raw = prefs(context).getString(KEY_CHALLENGE, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            DailyChallengeUiModel(
                date = json.optString("date"),
                title = json.optString("title"),
                titleSlug = json.optString("titleSlug"),
                difficulty = json.optString("difficulty"),
                questionId = json.optString("questionId"),
                tags = json.optString("tags").split("||").filter { it.isNotBlank() },
                url = json.optString("url"),
                descriptionPreview = json.optString("descriptionPreview"),
                fullStatement = json.optString("fullStatement"),
                pythonStarterCode = json.optString("pythonStarterCode"),
                exampleTestcases = json.optString("exampleTestcases")
            )
        }.getOrNull()
    }

    fun saveAi(context: Context, result: AiGenerationResult) {
        val json = JSONObject()
            .put("leetcodePythonCode", result.leetcodePythonCode)
            .put("testcaseValidation", result.testcaseValidation)
            .put("explanation", result.explanation)
            .put("rawResponse", result.rawResponse)
            .put("debugLog", result.debugLog)
            .toString()
        prefs(context).edit().putString(KEY_AI, json).apply()
    }

    fun loadAi(context: Context): AiGenerationResult? {
        val raw = prefs(context).getString(KEY_AI, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            AiGenerationResult(
                leetcodePythonCode = json.optString("leetcodePythonCode"),
                testcaseValidation = json.optString("testcaseValidation"),
                explanation = json.optString("explanation"),
                rawResponse = json.optString("rawResponse"),
                debugLog = json.optString("debugLog")
            )
        }.getOrNull()
    }

    fun clearAi(context: Context) {
        prefs(context).edit().remove(KEY_AI).apply()
    }

    fun markCompletedToday(context: Context) {
        prefs(context).edit().putBoolean(KEY_COMPLETED_PREFIX + istDateKey(), true).apply()
    }

    fun unmarkCompletedToday(context: Context) {
        prefs(context).edit().putBoolean(KEY_COMPLETED_PREFIX + istDateKey(), false).apply()
    }

    fun isCompletedToday(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_COMPLETED_PREFIX + istDateKey(), false)
    }

    fun istDateKey(now: Date = Date()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return formatter.format(now)
    }

    fun istHour(now: Date = Date()): Int {
        val formatter = SimpleDateFormat("H", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return formatter.format(now).toIntOrNull() ?: 0
    }
}
