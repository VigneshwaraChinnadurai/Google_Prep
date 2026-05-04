package com.vignesh.jobautomation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.jobautomation.data.models.ApplicationDetail
import com.vignesh.jobautomation.data.models.ApplicationStats
import com.vignesh.jobautomation.data.models.ApplicationWithJob
import com.vignesh.jobautomation.data.repository.JobAutomationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ApplicationsUiState(
    val applications: List<ApplicationWithJob> = emptyList(),
    val stats: ApplicationStats? = null,
    val selectedApplication: ApplicationDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ApplicationsViewModel(
    private val repository: JobAutomationRepository = JobAutomationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApplicationsUiState())
    val uiState: StateFlow<ApplicationsUiState> = _uiState.asStateFlow()

    fun loadApplications(status: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Load applications
                repository.getApplications(status = status).onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        applications = response.applications
                    )
                }

                // Load stats
                repository.getApplicationStats().onSuccess { stats ->
                    _uiState.value = _uiState.value.copy(stats = stats)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun selectApplication(applicationId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.getApplication(applicationId).fold(
                onSuccess = { detail ->
                    _uiState.value = _uiState.value.copy(
                        selectedApplication = detail,
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

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedApplication = null)
    }

    fun generateInterviewPrep(applicationId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.generateInterviewPrep(applicationId).fold(
                onSuccess = { prep ->
                    // Could show in a dialog or navigate to a new screen
                    _uiState.value = _uiState.value.copy(isLoading = false)
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
