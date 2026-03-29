package com.vignesh.jobautomation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.jobautomation.data.repository.JobAutomationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val totalJobs: Int = 0,
    val newJobs: Int = 0,
    val totalApplications: Int = 0,
    val activeApplications: Int = 0,
    val interviews: Int = 0,
    val apiCost: Double = 0.0,
    val chatResponse: String? = null,
    val recentActivity: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class DashboardViewModel(
    private val repository: JobAutomationRepository = JobAutomationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Load status summary
                repository.getStatusSummary().onSuccess { summary ->
                    _uiState.value = _uiState.value.copy(
                        totalJobs = summary.stats?.totalJobs ?: 0,
                        newJobs = summary.stats?.newJobs ?: 0,
                        recentActivity = summary.recentActivity ?: emptyList()
                    )
                }

                // Load application stats
                repository.getApplicationStats().onSuccess { stats ->
                    _uiState.value = _uiState.value.copy(
                        totalApplications = stats.total,
                        activeApplications = stats.active,
                        interviews = stats.byStatus["INTERVIEW_SCHEDULED"] ?: 0
                    )
                }

                // Load API cost
                repository.getApiCost().onSuccess { cost ->
                    _uiState.value = _uiState.value.copy(
                        apiCost = cost.dailyCost
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun sendChatMessage(message: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.chat(message).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        chatResponse = response.response,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            )
        }
    }

    fun runScoutAgent() {
        runAgent { repository.runScoutAgent() }
    }

    fun runAnalystAgent() {
        runAgent { repository.runAnalystAgent() }
    }

    fun runApplicantAgent() {
        runAgent { repository.runApplicantAgent() }
    }

    fun runTrackerAgent() {
        runAgent { repository.runTrackerAgent() }
    }

    private fun runAgent(block: suspend () -> Result<com.vignesh.jobautomation.data.models.AgentResult>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            block().fold(
                onSuccess = { result ->
                    _uiState.value = _uiState.value.copy(
                        chatResponse = "Agent completed: ${result.message}",
                        isLoading = false
                    )
                    loadDashboard() // Refresh stats
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            )
        }
    }
}
