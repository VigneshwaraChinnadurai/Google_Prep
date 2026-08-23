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
    val leetcodeUsername: String = "rockingstarvic",
    // "gemini" (paid API, automatic) or "claude_manual" (no API key -- copies the
    // prompt to the clipboard and hands off to the Claude app; user pastes the
    // reply back in). See LeetCodeRepository.buildManualSolvePrompt/parseManualResponse.
    val llmProvider: String = "gemini",
    val credlyUsername: String = "vigneshwarachinnadurai",
    val mediumUsername: String = "rockingstarvic",
    val linkedinUsername: String = "vigneshwarac",
    val chatbotBackendUrl: String = "",  // empty = auto-detect (emulator vs device)
    // ── Ollama Settings ─────────────────────────────────────────
    val ollamaBaseUrl: String = "http://127.0.0.1:11434",
    val ollamaPreferredModels: String = "qwen2.5:3b",
    // ── Local LLM Settings (llama.cpp) ──────────────────────────
    val ollamaBackend: String = "ollama",  // "ollama" or "local"
    val localModelPath: String = "",       // Path to .gguf model file
    val localContextSize: Int = 2048,      // Context window size
    val localMaxTokens: Int = 512,         // Max tokens to generate
    
    // ── Global API Keys ─────────────────────────────────────────
    val globalGithubToken: String = "",
    val globalGeminiApiKey: String = "",

    // ── Backup ───────────────────────────────────────────────────
    val backupFolderUri: String = "",
    val lastBackupTimeMillis: Long = 0L,

    // ── Email Notifications (Gmail SMTP, App Password -- not OAuth) ────
    val notificationEmailFrom: String = "",
    val notificationEmailAppPassword: String = "",
    val notificationEmailTo: String = "",
    val emailOnGithubPushEnabled: Boolean = false,

    // ── Text-to-Speech (Book Reader) ────────────────────────────
    val ttsProvider: String = "android",  // "android" (free, offline) or "elevenlabs" (paid, natural)
    val elevenLabsApiKey: String = "",
    val elevenLabsVoiceId: String = "21m00Tcm4TlvDq8ikWAM",  // ElevenLabs' public "Rachel" voice

    // ── App Lock ─────────────────────────────────────────────────
    // Defaults on (like a payment app); auto-skipped at runtime on devices with no
    // biometric/PIN/pattern enrolled at all, so this can't lock anyone out.
    val requireBiometricLock: Boolean = true
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
                // .trim(): a stray leading/trailing space typed into one of these fields once
                // silently broke GitHub user resolution (a config value, not a compile-time
                // bug) -- trimming on every load self-heals that instead of requiring a re-save.
                githubOwnerOverride = json.optString("githubOwnerOverride", "").trim(),
                githubRepoOverride = json.optString("githubRepoOverride", "").trim(),
                githubBranchOverride = json.optString("githubBranchOverride", "").trim(),
                leetcodeUsername = json.optString("leetcodeUsername", "rockingstarvic").trim(),
                llmProvider = json.optString("llmProvider", "gemini"),
                credlyUsername = json.optString("credlyUsername", "vigneshwarachinnadurai").trim(),
                mediumUsername = json.optString("mediumUsername", "rockingstarvic").trim(),
                linkedinUsername = json.optString("linkedinUsername", "vigneshwarac").trim(),
                chatbotBackendUrl = json.optString("chatbotBackendUrl", ""),
                ollamaBaseUrl = json.optString("ollamaBaseUrl", "http://127.0.0.1:11434"),
                ollamaPreferredModels = json.optString("ollamaPreferredModels", "qwen2.5:3b"),
                ollamaBackend = json.optString("ollamaBackend", "ollama"),
                localModelPath = json.optString("localModelPath", ""),
                localContextSize = json.optInt("localContextSize", 2048),
                localMaxTokens = json.optInt("localMaxTokens", 512),
                globalGithubToken = json.optString("globalGithubToken", ""),
                globalGeminiApiKey = json.optString("globalGeminiApiKey", ""),
                backupFolderUri = json.optString("backupFolderUri", ""),
                lastBackupTimeMillis = json.optLong("lastBackupTimeMillis", 0L),
                notificationEmailFrom = json.optString("notificationEmailFrom", "").trim(),
                notificationEmailAppPassword = json.optString("notificationEmailAppPassword", ""),
                notificationEmailTo = json.optString("notificationEmailTo", "").trim(),
                emailOnGithubPushEnabled = json.optBoolean("emailOnGithubPushEnabled", false),
                ttsProvider = json.optString("ttsProvider", "android"),
                elevenLabsApiKey = json.optString("elevenLabsApiKey", ""),
                elevenLabsVoiceId = json.optString("elevenLabsVoiceId", "21m00Tcm4TlvDq8ikWAM").trim()
                    .ifBlank { "21m00Tcm4TlvDq8ikWAM" },
                requireBiometricLock = json.optBoolean("requireBiometricLock", true)
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
            .put("leetcodeUsername", settings.leetcodeUsername)
            .put("llmProvider", settings.llmProvider)
            .put("credlyUsername", settings.credlyUsername)
            .put("mediumUsername", settings.mediumUsername)
            .put("linkedinUsername", settings.linkedinUsername)
            .put("chatbotBackendUrl", settings.chatbotBackendUrl)
            .put("ollamaBaseUrl", settings.ollamaBaseUrl)
            .put("ollamaPreferredModels", settings.ollamaPreferredModels)
            .put("ollamaBackend", settings.ollamaBackend)
            .put("localModelPath", settings.localModelPath)
            .put("localContextSize", settings.localContextSize)
            .put("localMaxTokens", settings.localMaxTokens)
            .put("globalGithubToken", settings.globalGithubToken)
            .put("globalGeminiApiKey", settings.globalGeminiApiKey)
            .put("backupFolderUri", settings.backupFolderUri)
            .put("lastBackupTimeMillis", settings.lastBackupTimeMillis)
            .put("notificationEmailFrom", settings.notificationEmailFrom)
            .put("notificationEmailAppPassword", settings.notificationEmailAppPassword)
            .put("notificationEmailTo", settings.notificationEmailTo)
            .put("emailOnGithubPushEnabled", settings.emailOnGithubPushEnabled)
            .put("ttsProvider", settings.ttsProvider)
            .put("elevenLabsApiKey", settings.elevenLabsApiKey)
            .put("elevenLabsVoiceId", settings.elevenLabsVoiceId)
            .put("requireBiometricLock", settings.requireBiometricLock)
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
