package com.vignesh.leedcodecheckerollama

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vignesh.leedcodecheckerollama.BuildConfig
import com.vignesh.leedcodecheckerollama.data.AiGenerationResult
import com.vignesh.leedcodecheckerollama.data.DailyChallengeUiModel
import com.vignesh.leedcodecheckerollama.data.LeetCodeRepository
import com.vignesh.leedcodecheckerollama.data.OllamaModelInfo
import com.vignesh.leedcodecheckerollama.data.PipelineException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class LeetCodeUiState(
    val isLoading: Boolean = false,
    val challenge: DailyChallengeUiModel? = null,
    val aiCode: String? = null,
    val aiTestcaseValidation: String? = null,
    val aiExplanation: String? = null,
    val isAiLoading: Boolean = false,
    val error: String? = null,
    val aiError: String? = null,
    val aiDebugLog: String? = null,
    val infoMessage: String? = null,
    val isCompletedToday: Boolean = false,
    val isPushLoading: Boolean = false,
    val localRevisionPath: String? = null,
    val isHistoryLoading: Boolean = false,
    val revisionHistory: List<LocalRevisionHistoryItem> = emptyList(),
    val selectedHistoryItem: LocalRevisionHistoryItem? = null,
    val settings: AppSettings = AppSettings(),
    val catalogOllamaModels: List<OllamaModelInfo> = emptyList(),
    val installedOllamaModels: List<OllamaModelInfo> = emptyList(),
    val isModelActionLoading: Boolean = false,
    val modelDownloadProgress: String? = null
)

class LeetCodeViewModel(
    application: Application,
    private val repository: LeetCodeRepository = LeetCodeRepository(application.applicationContext)
) : AndroidViewModel(application) {

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LeetCodeViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return LeetCodeViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }

    private val appContext = application.applicationContext
    private val initialSettings = AppSettingsStore.load(appContext)
    private val _uiState = MutableStateFlow(
        LeetCodeUiState(
            isCompletedToday = ConsistencyStorage.isCompletedToday(appContext),
            settings = initialSettings
        )
    )
    val uiState: StateFlow<LeetCodeUiState> = _uiState.asStateFlow()

    init {
        loadFromLocalStorage()
        refreshLocalRevisionHistory()
        refreshInstalledModels()
        refreshCatalogModels()
        viewModelScope.launch {
            repository.liveDebugLog.collectLatest { logText ->
                if (logText.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(aiDebugLog = logText)
                }
            }
        }
    }

    private fun loadFromLocalStorage() {
        val cachedChallenge = ConsistencyStorage.loadChallenge(appContext)
        val cachedAi = ConsistencyStorage.loadAi(appContext)

        _uiState.value = _uiState.value.copy(
            challenge = cachedChallenge,
            aiCode = cachedAi?.leetcodePythonCode,
            aiTestcaseValidation = cachedAi?.testcaseValidation,
            aiExplanation = cachedAi?.explanation,
            aiDebugLog = cachedAi?.debugLog,
            isCompletedToday = ConsistencyStorage.isCompletedToday(appContext),
            settings = AppSettingsStore.load(appContext)
        )
    }

    fun saveSettings(settings: AppSettings) {
        val sanitized = settings.copy(
            maxModelRetries = settings.maxModelRetries.coerceIn(1, 10),
            maxInputTokens = settings.maxInputTokens.coerceIn(1_024, 2_000_000),
            maxOutputTokens = settings.maxOutputTokens.coerceIn(256, 65_535),
            thinkingBudgetDivisor = settings.thinkingBudgetDivisor.coerceIn(1, 64),
            networkTimeoutMinutes = settings.networkTimeoutMinutes.coerceIn(1, 60),
            reminderStartHourIst = settings.reminderStartHourIst.coerceIn(0, 23),
            reminderEndHourIst = settings.reminderEndHourIst.coerceIn(0, 23),
            reminderIntervalHours = settings.reminderIntervalHours.coerceIn(1, 12)
        )
        AppSettingsStore.save(appContext, sanitized)
        ConsistencyReminderScheduler.ensureHourlyReminder(appContext)
        _uiState.value = _uiState.value.copy(
            settings = sanitized,
            infoMessage = "Settings saved successfully."
        )
    }

    fun refreshApiChallenge() {
        if (_uiState.value.isLoading || _uiState.value.isAiLoading) {
            _uiState.value = _uiState.value.copy(infoMessage = "A request is already running.")
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            infoMessage = "Refreshing LeetCode daily challenge..."
        )

        viewModelScope.launch {
            repository.fetchDailyChallenge()
                .onSuccess { challenge ->
                    ConsistencyStorage.saveChallenge(appContext, challenge)
                    // API refresh should not auto refresh LLM response.
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        challenge = challenge,
                        infoMessage = "LeetCode API content refreshed and stored locally.",
                        error = null,
                        isCompletedToday = ConsistencyStorage.isCompletedToday(appContext)
                    )

                    val aiCode = _uiState.value.aiCode
                    val aiExplanation = _uiState.value.aiExplanation
                    val aiValidation = _uiState.value.aiTestcaseValidation
                    if (!aiCode.isNullOrBlank() && !aiExplanation.isNullOrBlank()) {
                        saveRevisionFilesLocally(
                            challenge = challenge,
                            aiCode = aiCode,
                            aiExplanation = aiExplanation,
                            aiValidation = aiValidation.orEmpty()
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = throwable.message ?: "Could not refresh LeetCode daily challenge",
                        infoMessage = null
                    )
                }
        }
    }

    fun refreshLlmAnswer() {
        val challenge = _uiState.value.challenge
        if (challenge == null) {
            _uiState.value = _uiState.value.copy(
                infoMessage = "Refresh API first to load daily challenge content."
            )
            return
        }

        if (_uiState.value.isLoading || _uiState.value.isAiLoading) {
            _uiState.value = _uiState.value.copy(infoMessage = "A request is already running.")
            return
        }

        _uiState.value = _uiState.value.copy(
            isAiLoading = true,
            aiError = null,
            infoMessage = "Refreshing Ollama answer (manual confirmation accepted)."
        )

        viewModelScope.launch {
            repository.generateDetailedAnswer(challenge, forceRefresh = true)
                .onSuccess { answer ->
                    applyAiResult(answer)
                    ConsistencyStorage.saveAi(appContext, answer)
                    saveRevisionFilesLocally(
                        challenge = challenge,
                        aiCode = answer.leetcodePythonCode,
                        aiExplanation = answer.explanation,
                        aiValidation = answer.testcaseValidation
                    )
                    _uiState.value = _uiState.value.copy(
                        infoMessage = "Ollama response refreshed and stored locally."
                    )
                }
                .onFailure { throwable ->
                    val pipelineError = throwable as? PipelineException
                    _uiState.value = _uiState.value.copy(
                        isAiLoading = false,
                        aiError = throwable.message ?: "Could not refresh Ollama answer",
                        aiDebugLog = pipelineError?.debugLog ?: _uiState.value.aiDebugLog,
                        infoMessage = null
                    )
                }
        }
    }

    fun runOllamaDiagnostics() {
        if (_uiState.value.isLoading || _uiState.value.isAiLoading) {
            _uiState.value = _uiState.value.copy(infoMessage = "A request is already running.")
            return
        }

        _uiState.value = _uiState.value.copy(
            infoMessage = "Running Ollama connection diagnostics...",
            aiError = null
        )

        viewModelScope.launch {
            val report = repository.runOllamaConnectionDiagnostics()
            _uiState.value = _uiState.value.copy(
                aiDebugLog = report,
                infoMessage = "Diagnostics finished. Check Pipeline Logs for details."
            )
        }
    }

    fun refreshInstalledModels() {
        _uiState.value = _uiState.value.copy(
            isModelActionLoading = true,
            infoMessage = "Fetching Ollama model list..."
        )

        viewModelScope.launch {
            repository.listAvailableModels()
                .onSuccess { models ->
                    _uiState.value = _uiState.value.copy(
                        installedOllamaModels = models,
                        isModelActionLoading = false,
                        infoMessage = "Loaded ${models.size} models from Ollama server."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isModelActionLoading = false,
                        infoMessage = "Unable to load models: ${error.message}"
                    )
                }
        }
    }

    fun refreshCatalogModels() {
        _uiState.value = _uiState.value.copy(
            isModelActionLoading = true,
            infoMessage = "Fetching full Ollama model catalog..."
        )

        viewModelScope.launch {
            repository.listCatalogModels()
                .onSuccess { models ->
                    _uiState.value = _uiState.value.copy(
                        catalogOllamaModels = models,
                        isModelActionLoading = false,
                        infoMessage = "Loaded ${models.size} models from Ollama catalog."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isModelActionLoading = false,
                        infoMessage = "Unable to load model catalog: ${error.message}"
                    )
                }
        }
    }

    fun downloadOllamaModel(modelName: String) {
        val target = modelName.trim()
        if (target.isBlank()) {
            _uiState.value = _uiState.value.copy(infoMessage = "Enter a model name (example: qwen2.5:3b).")
            return
        }

        _uiState.value = _uiState.value.copy(
            isModelActionLoading = true,
            modelDownloadProgress = "Queued...",
            infoMessage = "Downloading model '$target' from Ollama..."
        )

        viewModelScope.launch {
            repository.downloadModel(target) { progressText ->
                _uiState.value = _uiState.value.copy(modelDownloadProgress = progressText)
            }
                .onSuccess {
                    val currentSettings = _uiState.value.settings
                    val preferred = currentSettings.preferredModelsCsv
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .toMutableList()

                    preferred.removeAll { it.equals(target, ignoreCase = true) }
                    preferred.add(0, target)

                    val updatedSettings = currentSettings.copy(
                        preferredModelsCsv = preferred.joinToString(",")
                    )

                    AppSettingsStore.save(appContext, updatedSettings)
                    _uiState.value = _uiState.value.copy(
                        settings = updatedSettings,
                        modelDownloadProgress = "Completed"
                    )

                    refreshInstalledModels()
                    _uiState.value = _uiState.value.copy(
                        isModelActionLoading = false,
                        infoMessage = "Model '$target' downloaded and selected for use."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isModelActionLoading = false,
                        modelDownloadProgress = null,
                        infoMessage = "Model download failed: ${error.message}"
                    )
                }
        }
    }

    fun setPreferredModel(modelName: String) {
        val target = modelName.trim()
        if (target.isBlank()) return

        val currentSettings = _uiState.value.settings
        val preferred = currentSettings.preferredModelsCsv
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()

        preferred.removeAll { it.equals(target, ignoreCase = true) }
        preferred.add(0, target)

        val updatedSettings = currentSettings.copy(preferredModelsCsv = preferred.joinToString(","))
        AppSettingsStore.save(appContext, updatedSettings)
        _uiState.value = _uiState.value.copy(
            settings = updatedSettings,
            infoMessage = "Preferred model set to '$target'."
        )
    }

    fun markCompletedToday() {
        ConsistencyStorage.markCompletedToday(appContext)
        _uiState.value = _uiState.value.copy(
            isCompletedToday = true,
            infoMessage = "Marked completed for today."
        )
    }

    fun unmarkCompletedToday() {
        ConsistencyStorage.unmarkCompletedToday(appContext)
        _uiState.value = _uiState.value.copy(
            isCompletedToday = false,
            infoMessage = "Unmarked — completion status cleared for today."
        )
    }

    fun refreshLocalRevisionHistory() {
        _uiState.value = _uiState.value.copy(isHistoryLoading = true)
        viewModelScope.launch {
            val items = RevisionExportManager.readLocalRevisionHistory(appContext)
            val selectedDate = _uiState.value.selectedHistoryItem?.folderDate
            val selected = items.firstOrNull { it.folderDate == selectedDate } ?: items.firstOrNull()
            _uiState.value = _uiState.value.copy(
                isHistoryLoading = false,
                revisionHistory = items,
                selectedHistoryItem = selected
            )
        }
    }

    fun selectHistoryItem(folderDate: String) {
        val selected = _uiState.value.revisionHistory.firstOrNull { it.folderDate == folderDate }
        _uiState.value = _uiState.value.copy(selectedHistoryItem = selected)
    }

    private fun applyAiResult(result: AiGenerationResult) {
        _uiState.value = _uiState.value.copy(
            aiCode = result.leetcodePythonCode,
            aiTestcaseValidation = result.testcaseValidation,
            aiExplanation = result.explanation,
            isAiLoading = false,
            aiError = null,
            aiDebugLog = result.debugLog
        )
    }

    fun pushRevisionFilesToGitHub() {
        val snapshot = _uiState.value
        val challenge = snapshot.challenge
        val aiCode = snapshot.aiCode
        val aiExplanation = snapshot.aiExplanation
        val aiValidation = snapshot.aiTestcaseValidation.orEmpty()

        if (challenge == null || aiCode.isNullOrBlank() || aiExplanation.isNullOrBlank()) {
            _uiState.value = snapshot.copy(
                infoMessage = "Refresh API and Ollama answer first. Then push revision files to GitHub."
            )
            return
        }

        if (snapshot.isPushLoading) {
            _uiState.value = snapshot.copy(infoMessage = "GitHub push is already in progress.")
            return
        }

        _uiState.value = snapshot.copy(
            isPushLoading = true,
            infoMessage = "Preparing revision files for GitHub push..."
        )

        viewModelScope.launch {
            runCatching {
                val settings = _uiState.value.settings
                val ownerInput = settings.githubOwnerOverride.ifBlank { BuildConfig.GITHUB_OWNER }
                val repoInput = settings.githubRepoOverride.ifBlank { BuildConfig.GITHUB_REPO }
                val owner = normalizeGitHubOwner(ownerInput, repoInput)
                val repo = normalizeGitHubRepo(repoInput)
                val branch = settings.githubBranchOverride.ifBlank { BuildConfig.GITHUB_BRANCH }
                val revisionFolder = settings.revisionFolderName.ifBlank { "Leetcode_QA_Revision" }

                val files = RevisionExportManager.buildRevisionFiles(
                    challenge = challenge,
                    aiCode = aiCode,
                    aiExplanation = aiExplanation,
                    aiValidation = aiValidation
                )

                val localPath = RevisionExportManager.writeLocalRevisionFiles(appContext, files)
                RevisionExportManager.pushToGitHub(
                    files = files,
                    token = BuildConfig.GITHUB_TOKEN,
                    owner = owner,
                    repo = repo,
                    branch = branch,
                    revisionRootFolder = revisionFolder
                )

                localPath
            }.onSuccess { localPath ->
                _uiState.value = _uiState.value.copy(
                    isPushLoading = false,
                    localRevisionPath = localPath,
                    infoMessage = "Pushed question.txt, answer.py, explanation.txt to GitHub successfully."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isPushLoading = false,
                    infoMessage = "GitHub push failed: ${error.message}"
                )
            }
        }
    }

    private suspend fun saveRevisionFilesLocally(
        challenge: DailyChallengeUiModel,
        aiCode: String,
        aiExplanation: String,
        aiValidation: String
    ) {
        runCatching {
            val files = RevisionExportManager.buildRevisionFiles(
                challenge = challenge,
                aiCode = aiCode,
                aiExplanation = aiExplanation,
                aiValidation = aiValidation
            )
            RevisionExportManager.writeLocalRevisionFiles(appContext, files)
        }.onSuccess { localPath ->
            _uiState.value = _uiState.value.copy(localRevisionPath = localPath)
            refreshLocalRevisionHistory()
        }
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    private fun normalizeGitHubRepo(repoInput: String): String {
        val trimmed = repoInput.trim()
        if (trimmed.contains("github.com/")) {
            val path = trimmed.substringAfter("github.com/")
            val parts = path.split('/').filter { it.isNotBlank() }
            if (parts.size >= 2) {
                return parts[1]
            }
        }
        return trimmed.substringAfterLast('/').ifBlank { trimmed }
    }

    private fun normalizeGitHubOwner(ownerInput: String, repoInput: String): String {
        val ownerTrimmed = ownerInput.trim()
        val repoTrimmed = repoInput.trim()

        if (repoTrimmed.contains("github.com/")) {
            val path = repoTrimmed.substringAfter("github.com/")
            val parts = path.split('/').filter { it.isNotBlank() }
            if (parts.size >= 2) {
                return parts[0]
            }
        }

        if (ownerTrimmed.contains('@')) {
            return ownerTrimmed.substringBefore('@').ifBlank { ownerTrimmed }
        }

        return ownerTrimmed.substringAfterLast('/').ifBlank { ownerTrimmed }
    }
}

