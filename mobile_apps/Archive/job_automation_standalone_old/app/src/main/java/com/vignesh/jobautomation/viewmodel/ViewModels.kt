package com.vignesh.jobautomation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vignesh.jobautomation.data.database.*
import com.vignesh.jobautomation.data.repository.ApplicationStats
import com.vignesh.jobautomation.data.repository.JobAutomationRepository
import com.vignesh.jobautomation.data.repository.JobCounts
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ==================== Dashboard ViewModel ====================

data class DashboardUiState(
    val jobCounts: JobCounts = JobCounts(0, 0, 0, 0, 0),
    val applicationStats: ApplicationStats = ApplicationStats(0, 0, 0, 0, 0),
    val recentJobs: List<JobWithCompany> = emptyList(),
    val hasProfile: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JobAutomationRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    init {
        loadDashboard()
    }
    
    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // Combine all flows
                combine(
                    repository.getProfile(),
                    repository.getJobCounts(),
                    repository.getApplicationStats(),
                    repository.getHighMatchJobs(60f)
                ) { profile, jobCounts, appStats, recentJobs ->
                    DashboardUiState(
                        hasProfile = profile != null,
                        jobCounts = jobCounts,
                        applicationStats = appStats,
                        recentJobs = recentJobs.take(5),
                        isLoading = false
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

// ==================== Profile ViewModel ====================

data class ProfileUiState(
    val profile: ProfileEntity? = null,
    val skills: Map<String, List<String>> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JobAutomationRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    init {
        loadProfile()
    }
    
    private fun loadProfile() {
        viewModelScope.launch {
            repository.getProfile().collect { profile ->
                _uiState.update { 
                    it.copy(
                        profile = profile,
                        skills = profile?.let { p -> repository.getProfileSkills(p) } ?: emptyMap(),
                        isLoading = false
                    )
                }
            }
        }
    }
    
    fun saveProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = false) }
            try {
                repository.saveProfile(profile)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
    
    fun updateSkills(skills: Map<String, List<String>>) {
        viewModelScope.launch {
            try {
                repository.updateProfileSkills(skills)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}

// ==================== Jobs ViewModel ====================

data class JobsUiState(
    val jobs: List<JobWithCompany> = emptyList(),
    val selectedJob: JobWithCompany? = null,
    val filterStatus: JobStatus? = null,
    val isLoading: Boolean = true,
    val isAnalyzing: Boolean = false,
    val error: String? = null
)

class JobsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JobAutomationRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(JobsUiState())
    val uiState: StateFlow<JobsUiState> = _uiState.asStateFlow()
    
    init {
        loadJobs()
    }
    
    private fun loadJobs() {
        viewModelScope.launch {
            repository.getAllJobs().collect { jobs ->
                val filtered = _uiState.value.filterStatus?.let { status ->
                    jobs.filter { it.job.status == status }
                } ?: jobs
                _uiState.update { it.copy(jobs = filtered, isLoading = false) }
            }
        }
    }
    
    fun setFilter(status: JobStatus?) {
        viewModelScope.launch {
            _uiState.update { it.copy(filterStatus = status) }
            if (status == null) {
                repository.getAllJobs().collect { jobs ->
                    _uiState.update { it.copy(jobs = jobs) }
                }
            } else {
                repository.getJobsByStatus(status).collect { jobs ->
                    _uiState.update { it.copy(jobs = jobs) }
                }
            }
        }
    }
    
    fun selectJob(job: JobWithCompany?) {
        _uiState.update { it.copy(selectedJob = job) }
    }
    
    fun addJobManually(title: String, company: String, url: String, location: String?, remoteType: String?, description: String?) {
        viewModelScope.launch {
            try {
                repository.addJobManually(title, company, url, location, remoteType, description)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun analyzeJob(jobId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }
            try {
                repository.analyzeJob(jobId)
                _uiState.update { it.copy(isAnalyzing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false, error = e.message) }
            }
        }
    }
    
    fun applyToJob(jobId: Int) {
        viewModelScope.launch {
            try {
                repository.applyToJob(jobId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}

// ==================== Applications ViewModel ====================

data class ApplicationsUiState(
    val applications: List<ApplicationWithJob> = emptyList(),
    val selectedApplication: ApplicationWithJob? = null,
    val filterStatus: ApplicationStatus? = null,
    val isLoading: Boolean = true,
    val isGeneratingPrep: Boolean = false,
    val interviewPrep: String? = null,
    val error: String? = null
)

class ApplicationsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JobAutomationRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(ApplicationsUiState())
    val uiState: StateFlow<ApplicationsUiState> = _uiState.asStateFlow()
    
    init {
        loadApplications()
    }
    
    private fun loadApplications() {
        viewModelScope.launch {
            repository.getAllApplications().collect { apps ->
                val filtered = _uiState.value.filterStatus?.let { status ->
                    apps.filter { it.application.status == status }
                } ?: apps
                _uiState.update { it.copy(applications = filtered, isLoading = false) }
            }
        }
    }
    
    fun setFilter(status: ApplicationStatus?) {
        viewModelScope.launch {
            _uiState.update { it.copy(filterStatus = status) }
            if (status == null) {
                repository.getAllApplications().collect { apps ->
                    _uiState.update { it.copy(applications = apps) }
                }
            } else {
                repository.getApplicationsByStatus(status).collect { apps ->
                    _uiState.update { it.copy(applications = apps) }
                }
            }
        }
    }
    
    fun selectApplication(app: ApplicationWithJob?) {
        _uiState.update { it.copy(selectedApplication = app, interviewPrep = null) }
    }
    
    fun updateStatus(applicationId: Int, status: ApplicationStatus) {
        viewModelScope.launch {
            try {
                repository.updateApplicationStatus(applicationId, status)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun generateInterviewPrep(applicationId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPrep = true) }
            try {
                val prep = repository.generateInterviewPrep(applicationId)
                val prepText = buildString {
                    appendLine("## Company Research")
                    prep.companyResearch.keyFacts.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("## Likely Questions")
                    prep.likelyQuestions.forEach { q ->
                        appendLine("**Q: ${q.question}**")
                        q.suggestedAnswerPoints.forEach { appendLine("  - $it") }
                    }
                    appendLine()
                    appendLine("## Questions to Ask")
                    prep.questionsToAsk.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("## Topics to Review")
                    prep.technicalTopics.forEach { appendLine("- $it") }
                }
                _uiState.update { it.copy(isGeneratingPrep = false, interviewPrep = prepText) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingPrep = false, error = e.message) }
            }
        }
    }
}

// ==================== Companies ViewModel ====================

data class CompaniesUiState(
    val companies: List<CompanyEntity> = emptyList(),
    val filterPreference: CompanyPreference? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class CompaniesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JobAutomationRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(CompaniesUiState())
    val uiState: StateFlow<CompaniesUiState> = _uiState.asStateFlow()
    
    init {
        loadCompanies()
    }
    
    private fun loadCompanies() {
        viewModelScope.launch {
            repository.getAllCompanies().collect { companies ->
                val filtered = _uiState.value.filterPreference?.let { pref ->
                    companies.filter { it.preference == pref }
                } ?: companies
                _uiState.update { it.copy(companies = filtered, isLoading = false) }
            }
        }
    }
    
    fun setFilter(preference: CompanyPreference?) {
        viewModelScope.launch {
            _uiState.update { it.copy(filterPreference = preference) }
            if (preference == null) {
                repository.getAllCompanies().collect { companies ->
                    _uiState.update { it.copy(companies = companies) }
                }
            } else {
                repository.getCompaniesByPreference(preference).collect { companies ->
                    _uiState.update { it.copy(companies = companies) }
                }
            }
        }
    }
    
    fun addCompany(name: String, website: String?, careersUrl: String?, preference: CompanyPreference) {
        viewModelScope.launch {
            try {
                repository.addCompany(CompanyEntity(
                    name = name,
                    normalizedName = name.lowercase().replace(Regex("[^a-z0-9]"), ""),
                    website = website,
                    careersUrl = careersUrl,
                    preference = preference
                ))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun updatePreference(companyId: Int, preference: CompanyPreference) {
        viewModelScope.launch {
            try {
                repository.updateCompanyPreference(companyId, preference)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}

// ==================== Chat ViewModel ====================

data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val isLoading: Boolean = false,
    val contextType: String? = null,
    val contextId: Int? = null,
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JobAutomationRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    init {
        loadChatHistory()
    }
    
    private fun loadChatHistory() {
        viewModelScope.launch {
            repository.getChatHistory().collect { messages ->
                _uiState.update { it.copy(messages = messages.reversed()) }
            }
        }
    }
    
    fun setContext(type: String?, id: Int?) {
        _uiState.update { it.copy(contextType = type, contextId = id) }
    }
    
    fun sendMessage(message: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.sendChatMessage(
                    message = message,
                    contextType = _uiState.value.contextType,
                    contextId = _uiState.value.contextId
                )
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
        }
    }
}
