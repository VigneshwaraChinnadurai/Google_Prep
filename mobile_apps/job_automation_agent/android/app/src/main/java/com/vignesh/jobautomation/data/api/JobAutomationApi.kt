package com.vignesh.jobautomation.data.api

import com.vignesh.jobautomation.BuildConfig
import com.vignesh.jobautomation.data.models.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * Job Automation Agent API interface.
 */
interface JobAutomationApi {
    
    // ==================== Status ====================
    
    @GET("api/status")
    suspend fun getStatus(): Response<StatusResponse>
    
    @GET("api/health")
    suspend fun healthCheck(): Response<HealthResponse>
    
    // ==================== Profile ====================
    
    @GET("api/profile")
    suspend fun getProfile(): Response<Profile>
    
    @PUT("api/profile")
    suspend fun updateProfile(@Body profile: ProfileUpdate): Response<Profile>
    
    @POST("api/profile/import-json")
    suspend fun importProfile(@Body profileJson: Map<String, Any>): Response<Profile>
    
    // ==================== Jobs ====================
    
    @GET("api/jobs")
    suspend fun getJobs(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("status") status: String? = null,
        @Query("min_score") minScore: Float? = null,
        @Query("search") search: String? = null
    ): Response<JobsResponse>
    
    @GET("api/jobs/{jobId}")
    suspend fun getJob(@Path("jobId") jobId: Int): Response<JobDetail>
    
    @POST("api/jobs/{jobId}/analyze")
    suspend fun analyzeJob(@Path("jobId") jobId: Int): Response<AnalysisResult>
    
    @POST("api/jobs/{jobId}/apply")
    suspend fun applyToJob(@Path("jobId") jobId: Int): Response<ApplicationResult>
    
    @POST("api/jobs/{jobId}/customize-resume")
    suspend fun customizeResume(@Path("jobId") jobId: Int): Response<CustomizedResume>
    
    // ==================== Applications ====================
    
    @GET("api/applications")
    suspend fun getApplications(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("status") status: String? = null
    ): Response<ApplicationsResponse>
    
    @GET("api/applications/stats")
    suspend fun getApplicationStats(): Response<ApplicationStats>
    
    @GET("api/applications/{applicationId}")
    suspend fun getApplication(@Path("applicationId") applicationId: Int): Response<ApplicationDetail>
    
    @PUT("api/applications/{applicationId}")
    suspend fun updateApplication(
        @Path("applicationId") applicationId: Int,
        @Body update: ApplicationUpdate
    ): Response<Application>
    
    @POST("api/applications/{applicationId}/generate-prep")
    suspend fun generateInterviewPrep(@Path("applicationId") applicationId: Int): Response<InterviewPrep>
    
    // ==================== Companies ====================
    
    @GET("api/companies")
    suspend fun getCompanies(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("preference") preference: String? = null
    ): Response<CompaniesResponse>
    
    @GET("api/companies/{companyId}")
    suspend fun getCompany(@Path("companyId") companyId: Int): Response<Company>
    
    @POST("api/companies")
    suspend fun createCompany(@Body company: CompanyCreate): Response<Company>
    
    @PUT("api/companies/{companyId}/preference")
    suspend fun updateCompanyPreference(
        @Path("companyId") companyId: Int,
        @Body update: PreferenceUpdate
    ): Response<Company>
    
    @POST("api/companies/seed-defaults")
    suspend fun seedDefaultCompanies(): Response<SeedResult>
    
    // ==================== Chat ====================
    
    @POST("api/chat")
    suspend fun chat(@Body message: ChatRequest): Response<ChatResponse>
    
    @POST("api/chat/status")
    suspend fun getStatusSummary(): Response<StatusSummary>
    
    @GET("api/chat/cost")
    suspend fun getApiCost(): Response<CostStats>
    
    // ==================== Agents ====================
    
    @GET("api/agents/status")
    suspend fun getAgentStatus(): Response<Map<String, AgentStatus>>
    
    @GET("api/agents/runs")
    suspend fun getAgentRuns(
        @Query("agent_name") agentName: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<AgentRunsResponse>
    
    @POST("api/agents/scout/run")
    suspend fun runScoutAgent(): Response<AgentResult>
    
    @POST("api/agents/analyst/run")
    suspend fun runAnalystAgent(): Response<AgentResult>
    
    @POST("api/agents/applicant/run")
    suspend fun runApplicantAgent(): Response<AgentResult>
    
    @POST("api/agents/tracker/run")
    suspend fun runTrackerAgent(): Response<AgentResult>
    
    @POST("api/agents/pipeline/run")
    suspend fun runFullPipeline(): Response<PipelineResult>
    
    companion object {
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 120L
        private const val WRITE_TIMEOUT = 60L
        
        fun create(): JobAutomationApi {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .build()
            
            return Retrofit.Builder()
                .baseUrl(BuildConfig.BACKEND_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(JobAutomationApi::class.java)
        }
    }
}
