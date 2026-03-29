package com.vignesh.jobautomation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.jobautomation.data.models.Company
import com.vignesh.jobautomation.data.models.CompanyCreate
import com.vignesh.jobautomation.data.models.PreferenceUpdate
import com.vignesh.jobautomation.data.repository.JobAutomationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CompaniesUiState(
    val companies: List<Company> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class CompaniesViewModel(
    private val repository: JobAutomationRepository = JobAutomationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompaniesUiState())
    val uiState: StateFlow<CompaniesUiState> = _uiState.asStateFlow()

    fun loadCompanies(preference: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.getCompanies(preference = preference).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        companies = response.companies,
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

    fun updatePreference(companyId: Int, preference: String) {
        viewModelScope.launch {
            repository.updateCompanyPreference(companyId, PreferenceUpdate(preference)).fold(
                onSuccess = {
                    // Update local list
                    val updated = _uiState.value.companies.map { company ->
                        if (company.id == companyId) company.copy(preference = preference)
                        else company
                    }
                    _uiState.value = _uiState.value.copy(companies = updated)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            )
        }
    }

    fun addCompany(name: String, type: String, careersPage: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.createCompany(
                CompanyCreate(
                    name = name,
                    companyType = type,
                    careersPage = careersPage
                )
            ).fold(
                onSuccess = {
                    loadCompanies() // Refresh list
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

    fun seedDefaults() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.seedDefaultCompanies().fold(
                onSuccess = {
                    loadCompanies() // Refresh list
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
