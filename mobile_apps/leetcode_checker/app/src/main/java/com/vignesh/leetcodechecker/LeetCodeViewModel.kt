package com.vignesh.leetcodechecker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.leetcodechecker.BuildConfig
import com.vignesh.leetcodechecker.data.AiGenerationResult
import com.vignesh.leetcodechecker.data.DailyChallengeUiModel
import com.vignesh.leetcodechecker.data.LeetCodeRepository
import com.vignesh.leetcodechecker.data.PipelineException
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
    val settings: AppSettings = AppSettings()
)

class LeetCodeViewModel(
    application: Application,
    private val repository: LeetCodeRepository = LeetCodeRepository(application.applicationContext)
) : AndroidViewModel(application) {

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
            infoMessage = "Refreshing LLM answer (manual confirmation accepted)."
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
                        infoMessage = "LLM response refreshed and stored locally."
                    )
                }
                .onFailure { throwable ->
                    val pipelineError = throwable as? PipelineException
                    _uiState.value = _uiState.value.copy(
                        isAiLoading = false,
                        aiError = throwable.message ?: "Could not refresh LLM answer",
                        aiDebugLog = pipelineError?.debugLog ?: _uiState.value.aiDebugLog,
                        infoMessage = null
                    )
                }
        }
    }

    fun markCompletedToday() {
        ConsistencyStorage.markCompletedToday(appContext)
        _uiState.value = _uiState.value.copy(
            isCompletedToday = true,
            infoMessage = "Marked completed for today."
        )
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
                infoMessage = "Refresh API and LLM first. Then push revision files to GitHub."
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
