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
    private const val KEY_SAVED_PROBLEMS = "saved_problems_history"  // Persistent storage

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
            .put("htmlContent", challenge.htmlContent)
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
                htmlContent = json.optString("htmlContent"),
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

    // ════════════════════════════════════════════════════════════════════
    // Ollama-specific cache (separate keys so Gemini & Ollama don't clash)
    // ════════════════════════════════════════════════════════════════════

    private const val KEY_OLLAMA_CHALLENGE = "ollama_cached_challenge_json"
    private const val KEY_OLLAMA_AI = "ollama_cached_ai_json"

    fun saveOllamaChallenge(context: Context, challenge: DailyChallengeUiModel) {
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
            .put("htmlContent", challenge.htmlContent)
            .put("pythonStarterCode", challenge.pythonStarterCode)
            .put("exampleTestcases", challenge.exampleTestcases)
            .toString()
        prefs(context).edit().putString(KEY_OLLAMA_CHALLENGE, json).apply()
    }

    fun loadOllamaChallenge(context: Context): DailyChallengeUiModel? {
        val raw = prefs(context).getString(KEY_OLLAMA_CHALLENGE, null) ?: return null
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
                htmlContent = json.optString("htmlContent"),
                pythonStarterCode = json.optString("pythonStarterCode"),
                exampleTestcases = json.optString("exampleTestcases")
            )
        }.getOrNull()
    }

    fun saveOllamaAi(context: Context, result: AiGenerationResult) {
        val json = JSONObject()
            .put("leetcodePythonCode", result.leetcodePythonCode)
            .put("testcaseValidation", result.testcaseValidation)
            .put("explanation", result.explanation)
            .put("rawResponse", result.rawResponse)
            .put("debugLog", result.debugLog)
            .toString()
        prefs(context).edit().putString(KEY_OLLAMA_AI, json).apply()
    }

    fun loadOllamaAi(context: Context): AiGenerationResult? {
        val raw = prefs(context).getString(KEY_OLLAMA_AI, null) ?: return null
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
    
    // ════════════════════════════════════════════════════════════════════
    // PERSISTENT SAVED PROBLEMS HISTORY (for Offline Mode - never auto-deleted)
    // ════════════════════════════════════════════════════════════════════
    
    data class SavedProblem(
        val id: String,  // titleSlug
        val date: String,
        val title: String,
        val titleSlug: String,
        val difficulty: String,
        val questionId: String,
        val tags: List<String>,
        val url: String,
        val descriptionPreview: String,
        val fullStatement: String,
        val htmlContent: String,
        val pythonStarterCode: String,
        val exampleTestcases: String,
        val source: String,  // "Gemini" or "Ollama"
        val solution: String? = null,
        val explanation: String? = null,
        val savedAt: Long = System.currentTimeMillis()
    )
    
    fun saveProblemToHistory(context: Context, challenge: DailyChallengeUiModel, source: String, solution: AiGenerationResult? = null) {
        val problems = loadSavedProblemsHistory(context).toMutableList()
        
        // Remove existing if same titleSlug exists (update)
        problems.removeAll { it.titleSlug == challenge.titleSlug }
        
        // Add new
        problems.add(SavedProblem(
            id = challenge.titleSlug,
            date = challenge.date,
            title = challenge.title,
            titleSlug = challenge.titleSlug,
            difficulty = challenge.difficulty,
            questionId = challenge.questionId,
            tags = challenge.tags,
            url = challenge.url,
            descriptionPreview = challenge.descriptionPreview,
            fullStatement = challenge.fullStatement,
            htmlContent = challenge.htmlContent,
            pythonStarterCode = challenge.pythonStarterCode,
            exampleTestcases = challenge.exampleTestcases,
            source = source,
            solution = solution?.leetcodePythonCode,
            explanation = solution?.explanation
        ))
        
        // Save to SharedPreferences
        val jsonArray = org.json.JSONArray()
        problems.forEach { p ->
            jsonArray.put(org.json.JSONObject().apply {
                put("id", p.id)
                put("date", p.date)
                put("title", p.title)
                put("titleSlug", p.titleSlug)
                put("difficulty", p.difficulty)
                put("questionId", p.questionId)
                put("tags", p.tags.joinToString("||"))
                put("url", p.url)
                put("descriptionPreview", p.descriptionPreview)
                put("fullStatement", p.fullStatement)
                put("htmlContent", p.htmlContent)
                put("pythonStarterCode", p.pythonStarterCode)
                put("exampleTestcases", p.exampleTestcases)
                put("source", p.source)
                put("solution", p.solution ?: "")
                put("explanation", p.explanation ?: "")
                put("savedAt", p.savedAt)
            })
        }
        prefs(context).edit().putString(KEY_SAVED_PROBLEMS, jsonArray.toString()).apply()
    }
    
    fun loadSavedProblemsHistory(context: Context): List<SavedProblem> {
        val raw = prefs(context).getString(KEY_SAVED_PROBLEMS, null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SavedProblem(
                    id = obj.optString("id"),
                    date = obj.optString("date"),
                    title = obj.optString("title"),
                    titleSlug = obj.optString("titleSlug"),
                    difficulty = obj.optString("difficulty"),
                    questionId = obj.optString("questionId"),
                    tags = obj.optString("tags").split("||").filter { it.isNotBlank() },
                    url = obj.optString("url"),
                    descriptionPreview = obj.optString("descriptionPreview"),
                    fullStatement = obj.optString("fullStatement"),
                    htmlContent = obj.optString("htmlContent"),
                    pythonStarterCode = obj.optString("pythonStarterCode"),
                    exampleTestcases = obj.optString("exampleTestcases"),
                    source = obj.optString("source"),
                    solution = obj.optString("solution").takeIf { it.isNotBlank() },
                    explanation = obj.optString("explanation").takeIf { it.isNotBlank() },
                    savedAt = obj.optLong("savedAt", System.currentTimeMillis())
                )
            }.sortedByDescending { it.savedAt }
        }.getOrElse { emptyList() }
    }
    
    fun deleteSavedProblem(context: Context, titleSlug: String) {
        val problems = loadSavedProblemsHistory(context).toMutableList()
        problems.removeAll { it.titleSlug == titleSlug }
        
        val jsonArray = org.json.JSONArray()
        problems.forEach { p ->
            jsonArray.put(org.json.JSONObject().apply {
                put("id", p.id)
                put("date", p.date)
                put("title", p.title)
                put("titleSlug", p.titleSlug)
                put("difficulty", p.difficulty)
                put("questionId", p.questionId)
                put("tags", p.tags.joinToString("||"))
                put("url", p.url)
                put("descriptionPreview", p.descriptionPreview)
                put("fullStatement", p.fullStatement)
                put("htmlContent", p.htmlContent)
                put("pythonStarterCode", p.pythonStarterCode)
                put("exampleTestcases", p.exampleTestcases)
                put("source", p.source)
                put("solution", p.solution ?: "")
                put("explanation", p.explanation ?: "")
                put("savedAt", p.savedAt)
            })
        }
        prefs(context).edit().putString(KEY_SAVED_PROBLEMS, jsonArray.toString()).apply()
    }
}
