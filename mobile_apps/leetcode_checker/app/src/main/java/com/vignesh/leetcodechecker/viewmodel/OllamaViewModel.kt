package com.vignesh.leetcodechecker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.leetcodechecker.AppSettings
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.ConsistencyStorage
import com.vignesh.leetcodechecker.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * UI state for the Ollama LeetCode tab.
 */
data class OllamaUiState(
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
    val settings: AppSettings = AppSettings(),
    // Ollama model management
    val installedModels: List<OllamaModelInfo> = emptyList(),
    val catalogModels: List<OllamaModelInfo> = emptyList(),
    val isModelActionLoading: Boolean = false,
    val modelDownloadProgress: String? = null
)

/**
 * ViewModel for the Ollama LeetCode Checker tab.
 * Handles daily challenge fetch, Ollama LLM generation, and model management.
 */
class OllamaViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = OllamaRepository(appContext)

    private val _uiState = MutableStateFlow(
        OllamaUiState(settings = AppSettingsStore.load(appContext))
    )
    val uiState: StateFlow<OllamaUiState> = _uiState.asStateFlow()

    init {
        // Load cached challenge if available
        loadCachedState()
        // Refresh model lists on startup
        refreshInstalledModels()
        refreshCatalogModels()
        // Observe live debug log from repository
        viewModelScope.launch {
            repository.liveDebugLog.collectLatest { logText ->
                if (logText.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(aiDebugLog = logText)
                }
            }
        }
    }

    private fun loadCachedState() {
        val cachedChallenge = ConsistencyStorage.loadOllamaChallenge(appContext)
        val cachedAi = ConsistencyStorage.loadOllamaAi(appContext)
        _uiState.value = _uiState.value.copy(
            challenge = cachedChallenge,
            aiCode = cachedAi?.leetcodePythonCode,
            aiTestcaseValidation = cachedAi?.testcaseValidation,
            aiExplanation = cachedAi?.explanation,
            aiDebugLog = cachedAi?.debugLog,
            settings = AppSettingsStore.load(appContext)
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // Challenge Fetch
    // ════════════════════════════════════════════════════════════════════════

    fun refreshApiChallenge() {
        if (_uiState.value.isLoading || _uiState.value.isAiLoading) {
            _uiState.value = _uiState.value.copy(infoMessage = "A request is already running.")
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true, error = null,
            infoMessage = "Refreshing LeetCode daily challenge..."
        )

        viewModelScope.launch {
            repository.fetchDailyChallenge()
                .onSuccess { challenge ->
                    ConsistencyStorage.saveOllamaChallenge(appContext, challenge)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        challenge = challenge,
                        infoMessage = "LeetCode challenge refreshed.",
                        error = null
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = throwable.message ?: "Could not refresh challenge",
                        infoMessage = null
                    )
                }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Ollama LLM Answer
    // ════════════════════════════════════════════════════════════════════════

    fun refreshOllamaAnswer() {
        val challenge = _uiState.value.challenge
        if (challenge == null) {
            _uiState.value = _uiState.value.copy(
                infoMessage = "Refresh API first to load daily challenge."
            )
            return
        }

        if (_uiState.value.isLoading || _uiState.value.isAiLoading) {
            _uiState.value = _uiState.value.copy(infoMessage = "A request is already running.")
            return
        }

        _uiState.value = _uiState.value.copy(
            isAiLoading = true, aiError = null,
            infoMessage = "Generating Ollama answer..."
        )

        viewModelScope.launch {
            repository.generateDetailedAnswer(challenge, forceRefresh = true)
                .onSuccess { answer ->
                    _uiState.value = _uiState.value.copy(
                        aiCode = answer.leetcodePythonCode,
                        aiTestcaseValidation = answer.testcaseValidation,
                        aiExplanation = answer.explanation,
                        isAiLoading = false,
                        aiError = null,
                        aiDebugLog = answer.debugLog,
                        infoMessage = "Ollama answer generated."
                    )
                    ConsistencyStorage.saveOllamaAi(appContext, answer)
                }
                .onFailure { throwable ->
                    val pipelineError = throwable as? PipelineException
                    _uiState.value = _uiState.value.copy(
                        isAiLoading = false,
                        aiError = throwable.message ?: "Ollama generation failed",
                        aiDebugLog = pipelineError?.debugLog ?: _uiState.value.aiDebugLog,
                        infoMessage = null
                    )
                }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Diagnostics
    // ════════════════════════════════════════════════════════════════════════

    fun runDiagnostics() {
        if (_uiState.value.isLoading || _uiState.value.isAiLoading) {
            _uiState.value = _uiState.value.copy(infoMessage = "A request is already running.")
            return
        }

        _uiState.value = _uiState.value.copy(
            infoMessage = "Running Ollama diagnostics...", aiError = null
        )

        viewModelScope.launch {
            val report = repository.runOllamaConnectionDiagnostics()
            _uiState.value = _uiState.value.copy(
                aiDebugLog = report,
                infoMessage = "Diagnostics finished. Check logs."
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Model Management
    // ════════════════════════════════════════════════════════════════════════

    fun refreshInstalledModels() {
        _uiState.value = _uiState.value.copy(isModelActionLoading = true)

        viewModelScope.launch {
            repository.listAvailableModels()
                .onSuccess { models ->
                    _uiState.value = _uiState.value.copy(
                        installedModels = models,
                        isModelActionLoading = false,
                        infoMessage = "Loaded ${models.size} installed models."
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
        viewModelScope.launch {
            repository.listCatalogModels()
                .onSuccess { models ->
                    _uiState.value = _uiState.value.copy(catalogModels = models)
                }
                .onFailure { /* silent — catalog is optional */ }
        }
    }

    fun downloadModel(modelName: String) {
        val target = modelName.trim()
        if (target.isBlank()) {
            _uiState.value = _uiState.value.copy(infoMessage = "Enter a model name (e.g. qwen2.5:3b).")
            return
        }

        _uiState.value = _uiState.value.copy(
            isModelActionLoading = true,
            modelDownloadProgress = "Queued...",
            infoMessage = "Downloading '$target'..."
        )

        viewModelScope.launch {
            repository.downloadModel(target) { progressText ->
                _uiState.value = _uiState.value.copy(modelDownloadProgress = progressText)
            }
                .onSuccess {
                    // Auto-set as preferred model
                    val current = _uiState.value.settings
                    val preferred = current.ollamaPreferredModels
                        .split(',').map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                    preferred.removeAll { it.equals(target, ignoreCase = true) }
                    preferred.add(0, target)
                    val updated = current.copy(ollamaPreferredModels = preferred.joinToString(","))
                    AppSettingsStore.save(appContext, updated)

                    _uiState.value = _uiState.value.copy(
                        settings = updated,
                        modelDownloadProgress = "Completed"
                    )
                    refreshInstalledModels()
                    _uiState.value = _uiState.value.copy(
                        isModelActionLoading = false,
                        infoMessage = "Model '$target' downloaded and selected."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isModelActionLoading = false,
                        modelDownloadProgress = null,
                        infoMessage = "Download failed: ${error.message}"
                    )
                }
        }
    }

    fun setPreferredModel(modelName: String) {
        val target = modelName.trim()
        if (target.isBlank()) return

        val current = _uiState.value.settings
        val preferred = current.ollamaPreferredModels
            .split(',').map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        preferred.removeAll { it.equals(target, ignoreCase = true) }
        preferred.add(0, target)
        val updated = current.copy(ollamaPreferredModels = preferred.joinToString(","))
        AppSettingsStore.save(appContext, updated)

        _uiState.value = _uiState.value.copy(
            settings = updated,
            infoMessage = "Preferred model: '$target'"
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // Ollama Settings
    // ════════════════════════════════════════════════════════════════════════

    fun saveOllamaSettings(baseUrl: String, models: String) {
        val current = _uiState.value.settings
        val updated = current.copy(
            ollamaBaseUrl = baseUrl.trim(),
            ollamaPreferredModels = models.trim()
        )
        AppSettingsStore.save(appContext, updated)
        _uiState.value = _uiState.value.copy(
            settings = updated,
            infoMessage = "Ollama settings saved."
        )
    }

    fun saveFullSettings(settings: AppSettings) {
        AppSettingsStore.save(appContext, settings)
        _uiState.value = _uiState.value.copy(
            settings = settings,
            infoMessage = "All settings saved."
        )
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, aiError = null)
    }
}
