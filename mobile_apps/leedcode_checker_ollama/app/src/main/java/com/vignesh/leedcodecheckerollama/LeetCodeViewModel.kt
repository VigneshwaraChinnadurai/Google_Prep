package com.vignesh.leedcodecheckerollama

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.leedcodecheckerollama.data.AiGenerationResult
import com.vignesh.leedcodecheckerollama.data.DailyChallengeUiModel
import com.vignesh.leedcodecheckerollama.data.LeetCodeRepository
import com.vignesh.leedcodecheckerollama.data.PipelineException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LeetCodeUiState(
    val isLoading: Boolean = false,
    val isModelLoading: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val selectedModel: String? = null,
    val challenge: DailyChallengeUiModel? = null,
    val aiCode: String? = null,
    val aiTestcaseValidation: String? = null,
    val aiExplanation: String? = null,
    val isAiLoading: Boolean = false,
    val error: String? = null,
    val aiError: String? = null,
    val aiDebugLog: String? = null,
    val infoMessage: String? = null
)

class LeetCodeViewModel(
    private val repository: LeetCodeRepository = LeetCodeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LeetCodeUiState(selectedModel = repository.defaultConfiguredModel())
    )
    val uiState: StateFlow<LeetCodeUiState> = _uiState.asStateFlow()

    private var pendingRunRequested = false
    private var nextAllowedRunAtMillis = 0L

    init {
        viewModelScope.launch {
            repository.liveDebugLog.collectLatest { logText ->
                if (logText.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(aiDebugLog = logText)
                }
            }
        }

        refreshAvailableModels()
    }

    fun refreshAvailableModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isModelLoading = true)
            repository.listAvailableModels()
                .onSuccess { models ->
                    val currentSelected = _uiState.value.selectedModel
                    val defaultModel = repository.defaultConfiguredModel()
                    val nextSelected = when {
                        !currentSelected.isNullOrBlank() && models.contains(currentSelected) -> currentSelected
                        models.contains(defaultModel) -> defaultModel
                        models.isNotEmpty() -> models.first()
                        else -> currentSelected ?: defaultModel
                    }

                    _uiState.value = _uiState.value.copy(
                        isModelLoading = false,
                        availableModels = models,
                        selectedModel = nextSelected,
                        infoMessage = if (models.isEmpty()) {
                            "No Ollama models found yet. Pull one on your phone-side Ollama runtime."
                        } else {
                            "Loaded ${models.size} local Ollama model(s)."
                        }
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isModelLoading = false,
                        infoMessage = throwable.message ?: "Failed to load Ollama model list."
                    )
                }
        }
    }

    fun selectModel(model: String) {
        if (model.isBlank()) return
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }

    fun fetchDailyChallenge() {
        if (_uiState.value.selectedModel.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                infoMessage = "Select an Ollama model before running the pipeline."
            )
            return
        }

        val now = System.currentTimeMillis()
        if (now < nextAllowedRunAtMillis) {
            val waitSeconds = ((nextAllowedRunAtMillis - now) / 1000).coerceAtLeast(1)
            _uiState.value = _uiState.value.copy(
                infoMessage = "Recent successful run detected. Retry after about $waitSeconds seconds."
            )
            return
        }

        if (_uiState.value.isLoading || _uiState.value.isAiLoading) {
            pendingRunRequested = true
            _uiState.value = _uiState.value.copy(
                infoMessage = "Flow is already running. Waiting for current run to finish."
            )
            return
        }

        startPipelineRun()
    }

    private fun startPipelineRun() {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            challenge = null,
            aiCode = null,
            aiTestcaseValidation = null,
            aiExplanation = null,
            isAiLoading = false,
            error = null,
            aiError = null,
            aiDebugLog = null,
            infoMessage = null
        )

        viewModelScope.launch {
            repository.fetchDailyChallenge()
                .onSuccess { challenge ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        challenge = challenge,
                        isAiLoading = true,
                        error = null,
                        aiError = null
                    )
                    fetchAiAnswer(challenge)
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = throwable.message ?: "Could not fetch LeetCode daily challenge"
                    )

                    if (pendingRunRequested) {
                        pendingRunRequested = false
                        _uiState.value = _uiState.value.copy(
                            infoMessage = "Previous run failed. Executing queued retry now."
                        )
                        startPipelineRun()
                    }
                }
        }
    }

    private suspend fun fetchAiAnswer(challenge: DailyChallengeUiModel) {
        repository.generateDetailedAnswer(challenge, _uiState.value.selectedModel)
            .onSuccess { answer ->
                applyAiResult(answer)

                if (pendingRunRequested) {
                    pendingRunRequested = false
                    nextAllowedRunAtMillis = System.currentTimeMillis() + 60_000L
                    _uiState.value = _uiState.value.copy(
                        infoMessage = "Flow completed successfully. Queued runs canceled. Retry after one minute."
                    )
                }
            }
            .onFailure { throwable ->
                val pipelineError = throwable as? PipelineException
                _uiState.value = _uiState.value.copy(
                    isAiLoading = false,
                    aiError = throwable.message ?: "Could not generate AI answer",
                    aiDebugLog = pipelineError?.debugLog ?: _uiState.value.aiDebugLog
                )

                if (pendingRunRequested) {
                    pendingRunRequested = false
                    _uiState.value = _uiState.value.copy(
                        infoMessage = "Previous run failed. Executing queued retry now."
                    )
                    startPipelineRun()
                }
            }
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

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }
}

