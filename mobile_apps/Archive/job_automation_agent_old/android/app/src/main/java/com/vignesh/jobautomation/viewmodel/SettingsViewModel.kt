package com.vignesh.jobautomation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.jobautomation.data.models.AgentStatus
import com.vignesh.jobautomation.data.models.CostStats
import com.vignesh.jobautomation.data.repository.JobAutomationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isConnected: Boolean = false,
    val backendUrl: String = "http://10.0.2.2:8000",
    val agentStatus: Map<String, AgentStatus?> = emptyMap(),
    val costStats: CostStats? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(
    private val repository: JobAutomationRepository = JobAutomationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                checkConnection()
                loadAgentStatus()
                loadCostStats()
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            repository.healthCheck().fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isConnected = true)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isConnected = false)
                }
            )
        }
    }

    private suspend fun loadAgentStatus() {
        repository.getAgentStatus().onSuccess { status ->
            _uiState.value = _uiState.value.copy(agentStatus = status)
        }
    }

    private suspend fun loadCostStats() {
        repository.getApiCost().onSuccess { stats ->
            _uiState.value = _uiState.value.copy(costStats = stats)
        }
    }

    fun toggleAgent(agentName: String) {
        // In a real implementation, this would call the backend to pause/resume the agent
        viewModelScope.launch {
            val current = _uiState.value.agentStatus[agentName]
            val updated = _uiState.value.agentStatus.toMutableMap()
            updated[agentName] = current?.copy(paused = !(current.paused ?: false))
            _uiState.value = _uiState.value.copy(agentStatus = updated)
        }
    }

    fun updateBackendUrl(url: String) {
        _uiState.value = _uiState.value.copy(backendUrl = url)
        // In a real implementation, this would update the API client
    }
}
