package com.vignesh.leetcodechecker.viewmodel

import android.app.Application
import android.os.PowerManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.leetcodechecker.AppSettings
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.ConsistencyStorage
import com.vignesh.leetcodechecker.SavedOllamaHost
import com.vignesh.leetcodechecker.SavedOllamaHostsStore
import com.vignesh.leetcodechecker.data.*
import com.vignesh.leetcodechecker.llm.*
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
    val modelDownloadProgress: String? = null,
    // Local GGUF model management
    val downloadedLocalModels: List<DownloadedModel> = emptyList(),
    val localModelDownloadProgress: DownloadProgress? = null,
    val showModelManager: Boolean = false,
    // Saved Ollama hosts
    val savedHosts: List<SavedOllamaHost> = emptyList(),
    val showAddHostDialog: Boolean = false
)

/**
 * ViewModel for the Ollama LeetCode Checker tab.
 * Handles daily challenge fetch, Ollama LLM generation, and model management.
 */
class OllamaViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = OllamaRepository(appContext)
    private val modelDownloadManager = ModelDownloadManager(appContext)
    
    // Wake lock to keep CPU running during LLM inference
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LeetCodeChecker:OllamaWakeLock"
            )
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(30 * 60 * 1000L) // 30 minutes max
            }
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    private val _uiState = MutableStateFlow(
        OllamaUiState(settings = AppSettingsStore.load(appContext))
    )
    val uiState: StateFlow<OllamaUiState> = _uiState.asStateFlow()

    init {
        // Load cached challenge if available
        loadCachedState()
        // Load saved hosts
        loadSavedHosts()
        // Refresh model lists on startup
        refreshInstalledModels()
        refreshCatalogModels()
        // Refresh local models
        refreshLocalModels()
        // Observe live debug log from repository
        viewModelScope.launch {
            repository.liveDebugLog.collectLatest { logText ->
                if (logText.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(aiDebugLog = logText)
                }
            }
        }
        // Observe local model download progress
        viewModelScope.launch {
            modelDownloadManager.downloadProgress.collectLatest { progress ->
                _uiState.value = _uiState.value.copy(localModelDownloadProgress = progress)
                // Auto-refresh when download completes
                if (progress?.status == DownloadStatus.COMPLETED) {
                    refreshLocalModels()
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

        // Acquire wake lock to prevent sleep during LLM inference
        acquireWakeLock()
        
        viewModelScope.launch {
            try {
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
                        // Also save to persistent history for offline mode
                        ConsistencyStorage.saveProblemToHistory(appContext, challenge, "Ollama", answer)
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
            } finally {
                // Always release wake lock when done
                releaseWakeLock()
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

    fun saveOllamaSettings(
        baseUrl: String, 
        models: String, 
        backend: String = "ollama",
        localModelPath: String = "",
        localContextSize: Int = 2048,
        localMaxTokens: Int = 512
    ) {
        val current = _uiState.value.settings
        val updated = current.copy(
            ollamaBaseUrl = baseUrl.trim(),
            ollamaPreferredModels = models.trim(),
            ollamaBackend = backend.trim(),
            localModelPath = localModelPath.trim(),
            localContextSize = localContextSize.coerceIn(512, 8192),
            localMaxTokens = localMaxTokens.coerceIn(64, 4096)
        )
        AppSettingsStore.save(appContext, updated)
        _uiState.value = _uiState.value.copy(
            settings = updated,
            infoMessage = if (backend == "local") "Local LLM settings saved." else "Ollama settings saved."
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

    // ════════════════════════════════════════════════════════════════════════
    // Saved Ollama Hosts Management
    // ════════════════════════════════════════════════════════════════════════

    private fun loadSavedHosts() {
        val hosts = SavedOllamaHostsStore.loadHosts(appContext)
        _uiState.value = _uiState.value.copy(savedHosts = hosts)
    }

    fun showAddHostDialog() {
        _uiState.value = _uiState.value.copy(showAddHostDialog = true)
    }

    fun hideAddHostDialog() {
        _uiState.value = _uiState.value.copy(showAddHostDialog = false)
    }

    fun addSavedHost(name: String, url: String, preferredModels: String) {
        if (name.isBlank() || url.isBlank()) {
            _uiState.value = _uiState.value.copy(infoMessage = "Name and URL are required.")
            return
        }
        val host = SavedOllamaHost(
            name = name.trim(),
            url = url.trim(),
            preferredModels = preferredModels.trim().ifBlank { "qwen2.5:3b" }
        )
        SavedOllamaHostsStore.addHost(appContext, host)
        loadSavedHosts()
        _uiState.value = _uiState.value.copy(
            showAddHostDialog = false,
            infoMessage = "Host '${host.name}' saved."
        )
    }

    fun deleteSavedHost(hostId: String) {
        val hosts = _uiState.value.savedHosts
        val host = hosts.find { it.id == hostId }
        if (hosts.size <= 1 && host != null) {
            _uiState.value = _uiState.value.copy(infoMessage = "Cannot delete the last host.")
            return
        }
        SavedOllamaHostsStore.deleteHost(appContext, hostId)
        loadSavedHosts()
        _uiState.value = _uiState.value.copy(
            infoMessage = host?.let { "Host '${it.name}' deleted." } ?: "Host deleted."
        )
    }

    fun selectSavedHost(host: SavedOllamaHost) {
        // Update current settings with selected host's URL and models
        val current = _uiState.value.settings
        val updated = current.copy(
            ollamaBaseUrl = host.url,
            ollamaPreferredModels = host.preferredModels
        )
        AppSettingsStore.save(appContext, updated)
        _uiState.value = _uiState.value.copy(
            settings = updated,
            infoMessage = "Switched to '${host.name}'"
        )
        // Refresh models list with new connection
        refreshInstalledModels()
    }

    fun updateSavedHost(host: SavedOllamaHost) {
        SavedOllamaHostsStore.updateHost(appContext, host)
        loadSavedHosts()
        _uiState.value = _uiState.value.copy(infoMessage = "Host '${host.name}' updated.")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Local GGUF Model Management
    // ════════════════════════════════════════════════════════════════════════

    fun showModelManager() {
        refreshLocalModels()
        _uiState.value = _uiState.value.copy(showModelManager = true)
    }

    fun hideModelManager() {
        _uiState.value = _uiState.value.copy(showModelManager = false)
        modelDownloadManager.clearProgress()
    }

    fun refreshLocalModels() {
        val downloaded = modelDownloadManager.getDownloadedModels()
        _uiState.value = _uiState.value.copy(downloadedLocalModels = downloaded)
    }

    fun downloadLocalModel(modelId: String) {
        viewModelScope.launch {
            modelDownloadManager.downloadModel(modelId)
                .onSuccess { modelPath ->
                    _uiState.value = _uiState.value.copy(
                        infoMessage = "Model downloaded successfully!"
                    )
                    refreshLocalModels()
                    // Auto-select this model
                    selectLocalModel(modelPath)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        infoMessage = "Download failed: ${error.message}"
                    )
                }
        }
    }

    fun deleteLocalModel(fileName: String) {
        val success = modelDownloadManager.deleteModel(fileName)
        if (success) {
            refreshLocalModels()
            _uiState.value = _uiState.value.copy(
                infoMessage = "Model deleted."
            )
            // Clear selection if deleted model was selected
            val current = _uiState.value.settings
            if (current.localModelPath.contains(fileName)) {
                val updated = current.copy(localModelPath = "")
                AppSettingsStore.save(appContext, updated)
                _uiState.value = _uiState.value.copy(settings = updated)
            }
        }
    }

    fun selectLocalModel(modelPath: String) {
        val current = _uiState.value.settings
        val updated = current.copy(
            localModelPath = modelPath,
            ollamaBackend = "local"  // Auto-switch to local backend
        )
        AppSettingsStore.save(appContext, updated)
        _uiState.value = _uiState.value.copy(
            settings = updated,
            infoMessage = "Model selected. Backend switched to Local."
        )
    }

    fun getAvailableModels() = ModelDownloadManager.AVAILABLE_MODELS
    
    override fun onCleared() {
        super.onCleared()
        releaseWakeLock()
    }
}
