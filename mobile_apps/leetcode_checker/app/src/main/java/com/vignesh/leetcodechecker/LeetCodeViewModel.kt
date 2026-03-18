package com.vignesh.leetcodechecker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.leetcodechecker.data.DailyChallengeUiModel
import com.vignesh.leetcodechecker.data.LeetCodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LeetCodeUiState(
    val isLoading: Boolean = false,
    val challenge: DailyChallengeUiModel? = null,
    val error: String? = null
)

class LeetCodeViewModel(
    private val repository: LeetCodeRepository = LeetCodeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeetCodeUiState())
    val uiState: StateFlow<LeetCodeUiState> = _uiState.asStateFlow()

    fun fetchDailyChallenge() {
        viewModelScope.launch {
            _uiState.value = LeetCodeUiState(isLoading = true)

            repository.fetchDailyChallenge()
                .onSuccess { challenge ->
                    _uiState.value = LeetCodeUiState(challenge = challenge)
                }
                .onFailure { throwable ->
                    _uiState.value = LeetCodeUiState(
                        error = throwable.message ?: "Could not fetch LeetCode daily challenge"
                    )
                }
        }
    }
}
