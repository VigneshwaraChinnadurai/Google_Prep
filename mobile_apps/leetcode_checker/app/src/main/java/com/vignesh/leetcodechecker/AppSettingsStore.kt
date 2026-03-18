package com.vignesh.leetcodechecker

import android.content.Context
import org.json.JSONObject

data class AppSettings(
    val landingTitle: String = "Vignesh Daily Activities Checker",
    val checkerTitle: String = "LeetCode Consistency Checker",
    val consistencyButtonLabel: String = "LeetCode Consistency Checker",
    val promptName: String = "Prompt for Leetcode_solver",
    val preferredModelsCsv: String = "gemini-2.5-pro,gemini-pro-latest",
    val maxModelRetries: Int = 3,
    val maxInputTokens: Int = 1_048_576,
    val maxOutputTokens: Int = 65_535,
    val thinkingBudgetDivisor: Int = 4,
    val networkTimeoutMinutes: Int = 15,
    val reminderStartHourIst: Int = 9,
    val reminderEndHourIst: Int = 22,
    val reminderIntervalHours: Int = 1,
    val revisionFolderName: String = "Leetcode_QA_Revision",
    val githubOwnerOverride: String = "",
    val githubRepoOverride: String = "",
    val githubBranchOverride: String = ""
)

object AppSettingsStore {
    private const val PREFS = "leetcode_settings_prefs"
    private const val KEY_SETTINGS = "app_settings_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): AppSettings {
        val raw = prefs(context).getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching {
            val json = JSONObject(raw)
            AppSettings(
                landingTitle = json.optString("landingTitle", "Vignesh Daily Activities Checker"),
                checkerTitle = json.optString("checkerTitle", "LeetCode Consistency Checker"),
                consistencyButtonLabel = json.optString("consistencyButtonLabel", "LeetCode Consistency Checker"),
                promptName = json.optString("promptName", "Prompt for Leetcode_solver"),
                preferredModelsCsv = json.optString("preferredModelsCsv", "gemini-2.5-pro,gemini-pro-latest"),
                maxModelRetries = json.optInt("maxModelRetries", 3),
                maxInputTokens = json.optInt("maxInputTokens", 1_048_576),
                maxOutputTokens = json.optInt("maxOutputTokens", 65_535),
                thinkingBudgetDivisor = json.optInt("thinkingBudgetDivisor", 4),
                networkTimeoutMinutes = json.optInt("networkTimeoutMinutes", 15),
                reminderStartHourIst = json.optInt("reminderStartHourIst", 9),
                reminderEndHourIst = json.optInt("reminderEndHourIst", 22),
                reminderIntervalHours = json.optInt("reminderIntervalHours", 1),
                revisionFolderName = json.optString("revisionFolderName", "Leetcode_QA_Revision"),
                githubOwnerOverride = json.optString("githubOwnerOverride", ""),
                githubRepoOverride = json.optString("githubRepoOverride", ""),
                githubBranchOverride = json.optString("githubBranchOverride", "")
            )
        }.getOrElse { AppSettings() }
    }

    fun save(context: Context, settings: AppSettings) {
        val json = JSONObject()
            .put("landingTitle", settings.landingTitle)
            .put("checkerTitle", settings.checkerTitle)
            .put("consistencyButtonLabel", settings.consistencyButtonLabel)
            .put("promptName", settings.promptName)
            .put("preferredModelsCsv", settings.preferredModelsCsv)
            .put("maxModelRetries", settings.maxModelRetries)
            .put("maxInputTokens", settings.maxInputTokens)
            .put("maxOutputTokens", settings.maxOutputTokens)
            .put("thinkingBudgetDivisor", settings.thinkingBudgetDivisor)
            .put("networkTimeoutMinutes", settings.networkTimeoutMinutes)
            .put("reminderStartHourIst", settings.reminderStartHourIst)
            .put("reminderEndHourIst", settings.reminderEndHourIst)
            .put("reminderIntervalHours", settings.reminderIntervalHours)
            .put("revisionFolderName", settings.revisionFolderName)
            .put("githubOwnerOverride", settings.githubOwnerOverride)
            .put("githubRepoOverride", settings.githubRepoOverride)
            .put("githubBranchOverride", settings.githubBranchOverride)
            .toString()

        prefs(context).edit().putString(KEY_SETTINGS, json).apply()
    }
}
