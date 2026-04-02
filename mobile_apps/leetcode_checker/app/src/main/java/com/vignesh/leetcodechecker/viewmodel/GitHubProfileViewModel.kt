package com.vignesh.leetcodechecker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.leetcodechecker.api.ContributionDay
import com.vignesh.leetcodechecker.api.GitHubUser
import com.vignesh.leetcodechecker.repository.GitHubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for GitHub Profile Screen
 */
data class GitHubProfileState(
    val isLoading: Boolean = false,
    val user: GitHubUser? = null,
    val error: String? = null,
    val contributionDays: List<ContributionDay> = emptyList(),
    val totalContributions: Int = 0,
    val selectedYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
)

/**
 * ViewModel for GitHub Profile and Contributions
 */
class GitHubProfileViewModel : ViewModel() {
    
    private val repository = GitHubRepository()
    
    private val _state = MutableStateFlow(GitHubProfileState())
    val state: StateFlow<GitHubProfileState> = _state.asStateFlow()
    
    init {
        loadProfile()
    }
    
    fun loadProfile(username: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            val result = if (username != null) {
                repository.getUserProfile(username)
            } else {
                repository.getUserProfile()
            }
            
            result.fold(
                onSuccess = { user ->
                    val contributionDays = user.contributionsCollection
                        ?.contributionCalendar
                        ?.weeks
                        ?.flatMap { it.contributionDays ?: emptyList() }
                        ?: emptyList()
                    
                    val totalContributions = user.contributionsCollection
                        ?.contributionCalendar
                        ?.totalContributions ?: 0
                    
                    _state.value = _state.value.copy(
                        isLoading = false,
                        user = user,
                        contributionDays = contributionDays,
                        totalContributions = totalContributions,
                        error = null
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message ?: "Unknown error"
                    )
                }
            )
        }
    }
    
    fun refresh() {
        loadProfile()
    }
}
