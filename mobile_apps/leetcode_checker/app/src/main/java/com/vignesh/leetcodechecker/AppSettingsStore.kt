package com.vignesh.leetcodechecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Represents a saved Ollama host configuration with a friendly name.
 */
data class SavedOllamaHost(
    val id: String = UUID.randomUUID().toString(),
    val name: String,           // e.g., "Home PC", "Mobile Ollama", "Office Server"
    val url: String,            // e.g., "http://192.168.1.107:11434"
    val preferredModels: String = "qwen2.5:3b"
)

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
    val githubBranchOverride: String = "",
    val chatbotBackendUrl: String = "",  // empty = auto-detect (emulator vs device)
    // ── Ollama Settings ─────────────────────────────────────────
    val ollamaBaseUrl: String = "http://127.0.0.1:11434",
    val ollamaPreferredModels: String = "qwen2.5:3b",
    // ── Local LLM Settings (llama.cpp) ──────────────────────────
    val ollamaBackend: String = "ollama",  // "ollama" or "local"
    val localModelPath: String = "",       // Path to .gguf model file
    val localContextSize: Int = 2048,      // Context window size
    val localMaxTokens: Int = 512          // Max tokens to generate
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
                githubBranchOverride = json.optString("githubBranchOverride", ""),
                chatbotBackendUrl = json.optString("chatbotBackendUrl", ""),
                ollamaBaseUrl = json.optString("ollamaBaseUrl", "http://127.0.0.1:11434"),
                ollamaPreferredModels = json.optString("ollamaPreferredModels", "qwen2.5:3b"),
                ollamaBackend = json.optString("ollamaBackend", "ollama"),
                localModelPath = json.optString("localModelPath", ""),
                localContextSize = json.optInt("localContextSize", 2048),
                localMaxTokens = json.optInt("localMaxTokens", 512)
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
            .put("chatbotBackendUrl", settings.chatbotBackendUrl)
            .put("ollamaBaseUrl", settings.ollamaBaseUrl)
            .put("ollamaPreferredModels", settings.ollamaPreferredModels)
            .put("ollamaBackend", settings.ollamaBackend)
            .put("localModelPath", settings.localModelPath)
            .put("localContextSize", settings.localContextSize)
            .put("localMaxTokens", settings.localMaxTokens)
            .toString()

        prefs(context).edit().putString(KEY_SETTINGS, json).apply()
    }
}

/**
 * Storage for saved Ollama host configurations.
 */
object SavedOllamaHostsStore {
    private const val PREFS = "ollama_hosts_prefs"
    private const val KEY_HOSTS = "saved_hosts_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadHosts(context: Context): List<SavedOllamaHost> {
        val raw = prefs(context).getString(KEY_HOSTS, null) ?: return getDefaultHosts()
        return runCatching {
            val jsonArray = JSONArray(raw)
            val hosts = mutableListOf<SavedOllamaHost>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                hosts.add(
                    SavedOllamaHost(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Unnamed"),
                        url = obj.optString("url", "http://127.0.0.1:11434"),
                        preferredModels = obj.optString("preferredModels", "qwen2.5:3b")
                    )
                )
            }
            if (hosts.isEmpty()) getDefaultHosts() else hosts
        }.getOrElse { getDefaultHosts() }
    }

    fun saveHosts(context: Context, hosts: List<SavedOllamaHost>) {
        val jsonArray = JSONArray()
        hosts.forEach { host ->
            jsonArray.put(
                JSONObject()
                    .put("id", host.id)
                    .put("name", host.name)
                    .put("url", host.url)
                    .put("preferredModels", host.preferredModels)
            )
        }
        prefs(context).edit().putString(KEY_HOSTS, jsonArray.toString()).apply()
    }

    fun addHost(context: Context, host: SavedOllamaHost) {
        val current = loadHosts(context).toMutableList()
        current.add(host)
        saveHosts(context, current)
    }

    fun updateHost(context: Context, host: SavedOllamaHost) {
        val current = loadHosts(context).toMutableList()
        val index = current.indexOfFirst { it.id == host.id }
        if (index >= 0) {
            current[index] = host
            saveHosts(context, current)
        }
    }

    fun deleteHost(context: Context, hostId: String) {
        val current = loadHosts(context).toMutableList()
        current.removeAll { it.id == hostId }
        saveHosts(context, current)
    }

    private fun getDefaultHosts(): List<SavedOllamaHost> = listOf(
        SavedOllamaHost(
            id = "default-localhost",
            name = "Localhost (ADB)",
            url = "http://127.0.0.1:11434",
            preferredModels = "qwen2.5:3b"
        )
    )
}
