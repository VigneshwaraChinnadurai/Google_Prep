package com.vignesh.jobautomation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.jobautomation.data.models.Profile
import com.vignesh.jobautomation.data.repository.JobAutomationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: Profile? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ProfileViewModel(
    private val repository: JobAutomationRepository = JobAutomationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.getProfile().fold(
                onSuccess = { profile ->
                    _uiState.value = _uiState.value.copy(
                        profile = profile,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    // Profile might not exist yet - that's okay
                    _uiState.value = _uiState.value.copy(
                        profile = null,
                        isLoading = false
                    )
                }
            )
        }
    }
}
