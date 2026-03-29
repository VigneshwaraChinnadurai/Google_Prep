package com.vignesh.jobautomation.data.repository

import com.vignesh.jobautomation.data.api.JobAutomationApi
import com.vignesh.jobautomation.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing all API interactions.
 */
class JobAutomationRepository(
    private val api: JobAutomationApi = JobAutomationApi.create()
) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ==================== Status ====================

    suspend fun getStatus(): Result<StatusResponse> = safeApiCall {
        api.getStatus()
    }

    suspend fun healthCheck(): Result<HealthResponse> = safeApiCall {
        api.healthCheck()
    }

    // ==================== Profile ====================

    suspend fun getProfile(): Result<Profile> = safeApiCall {
        api.getProfile()
    }

    suspend fun updateProfile(update: ProfileUpdate): Result<Profile> = safeApiCall {
        api.updateProfile(update)
    }

    // ==================== Jobs ====================

    suspend fun getJobs(
        skip: Int = 0,
        limit: Int = 50,
        status: String? = null,
        minScore: Float? = null,
        search: String? = null
    ): Result<JobsResponse> = safeApiCall {
        api.getJobs(skip, limit, status, minScore, search)
    }

    suspend fun getJob(jobId: Int): Result<JobDetail> = safeApiCall {
        api.getJob(jobId)
    }

    suspend fun analyzeJob(jobId: Int): Result<AnalysisResult> = safeApiCall {
        api.analyzeJob(jobId)
    }

    suspend fun applyToJob(jobId: Int): Result<ApplicationResult> = safeApiCall {
        api.applyToJob(jobId)
    }

    suspend fun customizeResume(jobId: Int): Result<CustomizedResume> = safeApiCall {
        api.customizeResume(jobId)
    }

    // ==================== Applications ====================

    suspend fun getApplications(
        skip: Int = 0,
        limit: Int = 50,
        status: String? = null
    ): Result<ApplicationsResponse> = safeApiCall {
        api.getApplications(skip, limit, status)
    }

    suspend fun getApplicationStats(): Result<ApplicationStats> = safeApiCall {
        api.getApplicationStats()
    }

    suspend fun getApplication(applicationId: Int): Result<ApplicationDetail> = safeApiCall {
        api.getApplication(applicationId)
    }

    suspend fun updateApplication(
        applicationId: Int,
        update: ApplicationUpdate
    ): Result<Application> = safeApiCall {
        api.updateApplication(applicationId, update)
    }

    suspend fun generateInterviewPrep(applicationId: Int): Result<InterviewPrep> = safeApiCall {
        api.generateInterviewPrep(applicationId)
    }

    // ==================== Companies ====================

    suspend fun getCompanies(
        skip: Int = 0,
        limit: Int = 100,
        preference: String? = null
    ): Result<CompaniesResponse> = safeApiCall {
        api.getCompanies(skip, limit, preference)
    }

    suspend fun getCompany(companyId: Int): Result<Company> = safeApiCall {
        api.getCompany(companyId)
    }

    suspend fun createCompany(company: CompanyCreate): Result<Company> = safeApiCall {
        api.createCompany(company)
    }

    suspend fun updateCompanyPreference(
        companyId: Int,
        update: PreferenceUpdate
    ): Result<Company> = safeApiCall {
        api.updateCompanyPreference(companyId, update)
    }

    suspend fun seedDefaultCompanies(): Result<SeedResult> = safeApiCall {
        api.seedDefaultCompanies()
    }

    // ==================== Chat ====================

    suspend fun chat(message: String): Result<ChatResponse> = safeApiCall {
        api.chat(ChatRequest(message))
    }

    suspend fun getStatusSummary(): Result<StatusSummary> = safeApiCall {
        api.getStatusSummary()
    }

    suspend fun getApiCost(): Result<CostStats> = safeApiCall {
        api.getApiCost()
    }

    // ==================== Agents ====================

    suspend fun getAgentStatus(): Result<Map<String, AgentStatus>> = safeApiCall {
        api.getAgentStatus()
    }

    suspend fun getAgentRuns(
        agentName: String? = null,
        limit: Int = 20
    ): Result<AgentRunsResponse> = safeApiCall {
        api.getAgentRuns(agentName, limit)
    }

    suspend fun runScoutAgent(): Result<AgentResult> = safeApiCall {
        api.runScoutAgent()
    }

    suspend fun runAnalystAgent(): Result<AgentResult> = safeApiCall {
        api.runAnalystAgent()
    }

    suspend fun runApplicantAgent(): Result<AgentResult> = safeApiCall {
        api.runApplicantAgent()
    }

    suspend fun runTrackerAgent(): Result<AgentResult> = safeApiCall {
        api.runTrackerAgent()
    }

    suspend fun runFullPipeline(): Result<PipelineResult> = safeApiCall {
        api.runFullPipeline()
    }

    // ==================== Helper ====================

    private suspend fun <T> safeApiCall(call: suspend () -> retrofit2.Response<T>): Result<T> {
        _isLoading.value = true
        _error.value = null
        
        return try {
            val response = call()
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorMsg = "Error: ${response.code()} - ${response.message()}"
                _error.value = errorMsg
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            _error.value = e.message ?: "Unknown error"
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
