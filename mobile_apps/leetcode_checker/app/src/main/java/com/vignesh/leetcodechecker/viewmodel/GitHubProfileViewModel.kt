package com.vignesh.leetcodechecker.viewmodel

import android.content.Context
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
    val selectedYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val savedUsername: String? = null
)

private const val PREFS_NAME = "github_profile_prefs"
private const val KEY_USERNAME = "github_username"

/**
 * ViewModel for GitHub Profile and Contributions
 */
class GitHubProfileViewModel : ViewModel() {
    
    private val repository = GitHubRepository()
    
    private val _state = MutableStateFlow(GitHubProfileState())
    val state: StateFlow<GitHubProfileState> = _state.asStateFlow()
    
    /**
     * Get saved GitHub username from SharedPreferences
     */
    fun getSavedUsername(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USERNAME, null)
    }
    
    /**
     * Save username and load profile
     */
    fun saveAndLoadProfile(context: Context, username: String) {
        // Save username
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USERNAME, username).apply()
        
        // Load profile
        loadProfile(username)
    }
    
    fun loadProfile(username: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            val targetUsername = username ?: _state.value.savedUsername
            
            if (targetUsername.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Please enter your GitHub username"
                )
                return@launch
            }
            
            val result = repository.getUserProfile(targetUsername)
            
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
                        savedUsername = targetUsername,
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
    
    fun refresh(context: Context) {
        val savedUsername = getSavedUsername(context)
        if (!savedUsername.isNullOrBlank()) {
            loadProfile(savedUsername)
        } else {
            _state.value = _state.value.copy(
                error = "Please enter your GitHub username first"
            )
        }
    }
}
