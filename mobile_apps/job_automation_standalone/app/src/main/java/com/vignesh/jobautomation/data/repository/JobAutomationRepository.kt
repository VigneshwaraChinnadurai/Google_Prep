package com.vignesh.jobautomation.data.repository

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.jobautomation.ai.GeminiClient
import com.vignesh.jobautomation.ai.InterviewPrepResult
import com.vignesh.jobautomation.ai.JobAnalysisResult
import com.vignesh.jobautomation.ai.ResumeCustomization
import com.vignesh.jobautomation.ai.WeaknessAnalysis
import com.vignesh.jobautomation.data.database.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Main repository for job automation data and AI operations.
 */
class JobAutomationRepository private constructor(
    private val database: JobAutomationDatabase,
    private val gemini: GeminiClient
) {
    private val profileDao = database.profileDao()
    private val companyDao = database.companyDao()
    private val jobDao = database.jobDao()
    private val applicationDao = database.applicationDao()
    private val chatDao = database.chatDao()
    
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    
    companion object {
        @Volatile
        private var INSTANCE: JobAutomationRepository? = null
        
        fun getInstance(context: Context): JobAutomationRepository {
            return INSTANCE ?: synchronized(this) {
                val db = JobAutomationDatabase.getDatabase(context)
                val gemini = GeminiClient.getInstance()
                JobAutomationRepository(db, gemini).also { INSTANCE = it }
            }
        }
    }
    
    // ==================== Profile ====================
    
    fun getProfile(): Flow<ProfileEntity?> = profileDao.getProfile()
    
    suspend fun getProfileOnce(): ProfileEntity? = profileDao.getProfileOnce()
    
    suspend fun saveProfile(profile: ProfileEntity) {
        profileDao.upsertProfile(profile.copy(updatedAt = System.currentTimeMillis()))
    }
    
    suspend fun updateProfileSkills(skills: Map<String, List<String>>) {
        val current = profileDao.getProfileOnce() ?: return
        val skillsJson = moshi.adapter<Map<String, List<String>>>(
            Types.newParameterizedType(Map::class.java, String::class.java, 
                Types.newParameterizedType(List::class.java, String::class.java))
        ).toJson(skills)
        profileDao.upsertProfile(current.copy(skillsJson = skillsJson, updatedAt = System.currentTimeMillis()))
    }
    
    fun getProfileSkills(profile: ProfileEntity): Map<String, List<String>> {
        return try {
            moshi.adapter<Map<String, List<String>>>(
                Types.newParameterizedType(Map::class.java, String::class.java,
                    Types.newParameterizedType(List::class.java, String::class.java))
            ).fromJson(profile.skillsJson) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    // ==================== Companies ====================
    
    fun getAllCompanies(): Flow<List<CompanyEntity>> = companyDao.getAllCompanies()
    
    fun getCompaniesByPreference(preference: CompanyPreference): Flow<List<CompanyEntity>> =
        companyDao.getCompaniesByPreference(preference)
    
    suspend fun addCompany(company: CompanyEntity): Long = companyDao.insertCompany(company)
    
    suspend fun updateCompanyPreference(companyId: Int, preference: CompanyPreference) {
        companyDao.updatePreference(companyId, preference)
    }
    
    suspend fun getOrCreateCompany(name: String): CompanyEntity {
        val normalized = name.lowercase().replace(Regex("[^a-z0-9]"), "")
        val existing = companyDao.getCompanyByNormalizedName(normalized)
        if (existing != null) return existing
        
        val newCompany = CompanyEntity(
            name = name,
            normalizedName = normalized
        )
        val id = companyDao.insertCompany(newCompany)
        return companyDao.getCompanyById(id.toInt()) ?: newCompany.copy(id = id.toInt())
    }
    
    // ==================== Jobs ====================
    
    fun getAllJobs(): Flow<List<JobWithCompany>> = jobDao.getAllJobs()
    
    fun getJobsByStatus(status: JobStatus): Flow<List<JobWithCompany>> = jobDao.getJobsByStatus(status)
    
    fun getReadyToApplyJobs(): Flow<List<JobWithCompany>> = 
        jobDao.getJobsByStatus(JobStatus.READY_TO_APPLY)
    
    fun getHighMatchJobs(minScore: Float = 70f): Flow<List<JobWithCompany>> =
        jobDao.getJobsByMinScore(minScore)
    
    suspend fun getJobById(id: Int): JobWithCompany? = jobDao.getJobById(id)
    
    suspend fun addJob(job: JobEntity): Long = jobDao.insertJob(job)
    
    suspend fun addJobManually(
        title: String,
        companyName: String,
        url: String,
        location: String? = null,
        remoteType: String? = null,
        description: String? = null
    ): Long {
        val company = getOrCreateCompany(companyName)
        val job = JobEntity(
            title = title,
            companyName = companyName,
            companyId = company.id,
            location = location,
            remoteType = remoteType,
            description = description,
            url = url,
            source = "manual"
        )
        return jobDao.insertJob(job)
    }
    
    suspend fun analyzeJob(jobId: Int): JobAnalysisResult {
        val jobWithCompany = jobDao.getJobById(jobId) ?: throw IllegalArgumentException("Job not found")
        val profile = profileDao.getProfileOnce() ?: throw IllegalStateException("Profile not configured")
        
        val skills = getProfileSkills(profile)
        
        val result = gemini.analyzeJobFitment(
            profileSummary = profile.summary ?: "",
            skills = skills,
            experience = profile.experienceJson,
            desiredRoles = profile.desiredRoles,
            jobDescription = jobWithCompany.job.descriptionFull ?: jobWithCompany.job.description ?: ""
        )
        
        // Update job with analysis results
        val newStatus = when {
            result.matchScore >= 70 && result.recommendation == "APPLY" -> JobStatus.READY_TO_APPLY
            result.matchScore >= 50 -> JobStatus.ANALYZED
            else -> JobStatus.SKIPPED
        }
        
        jobDao.updateAnalysis(
            id = jobId,
            status = newStatus,
            score = result.matchScore,
            justification = result.summary,
            strengths = moshi.adapter<List<String>>(
                Types.newParameterizedType(List::class.java, String::class.java)
            ).toJson(result.strengths),
            weaknesses = moshi.adapter<List<String>>(
                Types.newParameterizedType(List::class.java, String::class.java)
            ).toJson(result.weaknesses)
        )
        
        return result
    }
    
    fun getJobCounts(): Flow<JobCounts> {
        return jobDao.getAllJobs().map { jobs ->
            JobCounts(
                total = jobs.size,
                new = jobs.count { it.job.status == JobStatus.NEW },
                analyzed = jobs.count { it.job.status == JobStatus.ANALYZED },
                readyToApply = jobs.count { it.job.status == JobStatus.READY_TO_APPLY },
                applied = jobs.count { it.job.status == JobStatus.APPLIED }
            )
        }
    }
    
    // ==================== Applications ====================
    
    fun getAllApplications(): Flow<List<ApplicationWithJob>> = applicationDao.getAllApplications()
    
    fun getApplicationsByStatus(status: ApplicationStatus): Flow<List<ApplicationWithJob>> =
        applicationDao.getApplicationsByStatus(status)
    
    suspend fun getApplicationById(id: Int): ApplicationWithJob? = applicationDao.getApplicationById(id)
    
    suspend fun applyToJob(jobId: Int, resumeUsed: String? = null, coverLetter: String? = null): Long {
        // Check if already applied
        val existing = applicationDao.getApplicationByJobId(jobId)
        if (existing != null) {
            throw IllegalStateException("Already applied to this job")
        }
        
        // Create application record
        val application = ApplicationEntity(
            jobId = jobId,
            resumeUsed = resumeUsed,
            coverLetter = coverLetter
        )
        val applicationId = applicationDao.insertApplication(application)
        
        // Update job status
        val job = jobDao.getJobById(jobId)
        if (job != null) {
            jobDao.updateJob(job.job.copy(status = JobStatus.APPLIED))
        }
        
        return applicationId
    }
    
    suspend fun updateApplicationStatus(applicationId: Int, status: ApplicationStatus) {
        applicationDao.updateStatus(applicationId, status)
    }
    
    fun getApplicationStats(): Flow<ApplicationStats> {
        return applicationDao.getAllApplications().map { apps ->
            ApplicationStats(
                total = apps.size,
                submitted = apps.count { it.application.status == ApplicationStatus.SUBMITTED },
                interviewing = apps.count { 
                    it.application.status == ApplicationStatus.INTERVIEW_SCHEDULED ||
                    it.application.status == ApplicationStatus.INTERVIEWED
                },
                offers = apps.count { it.application.status == ApplicationStatus.OFFER_RECEIVED },
                rejected = apps.count { it.application.status == ApplicationStatus.REJECTED }
            )
        }
    }
    
    // ==================== AI Features ====================
    
    suspend fun customizeResumeForJob(jobId: Int): ResumeCustomization {
        val job = jobDao.getJobById(jobId) ?: throw IllegalArgumentException("Job not found")
        val profile = profileDao.getProfileOnce() ?: throw IllegalStateException("Profile not configured")
        
        val profileJson = buildString {
            appendLine("{")
            appendLine("  \"name\": \"${profile.fullName}\",")
            appendLine("  \"summary\": \"${profile.summary ?: ""}\",")
            appendLine("  \"skills\": ${profile.skillsJson},")
            appendLine("  \"experience\": ${profile.experienceJson},")
            appendLine("  \"education\": ${profile.educationJson}")
            appendLine("}")
        }
        
        return gemini.customizeResume(
            profileJson = profileJson,
            jobDescription = job.job.descriptionFull ?: job.job.description ?: ""
        )
    }
    
    suspend fun generateInterviewPrep(applicationId: Int): InterviewPrepResult {
        val app = applicationDao.getApplicationById(applicationId) 
            ?: throw IllegalArgumentException("Application not found")
        val profile = profileDao.getProfileOnce() ?: throw IllegalStateException("Profile not configured")
        
        return gemini.generateInterviewPrep(
            jobTitle = app.job.title,
            companyName = app.job.companyName,
            jobDescription = app.job.descriptionFull ?: app.job.description ?: "",
            profileSummary = "${profile.fullName}\n${profile.summary ?: ""}"
        )
    }
    
    suspend fun analyzeSkillGaps(): WeaknessAnalysis {
        val profile = profileDao.getProfileOnce() ?: throw IllegalStateException("Profile not configured")
        val skills = getProfileSkills(profile)
        
        // Get recent job weaknesses from analyzed jobs
        val recentJobs = jobDao.getJobsByStatus(JobStatus.ANALYZED).first().take(10)
        val weaknesses = recentJobs.flatMap { job ->
            try {
                moshi.adapter<List<String>>(
                    Types.newParameterizedType(List::class.java, String::class.java)
                ).fromJson(job.job.weaknessesJson ?: "[]") ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }.distinct()
        
        return gemini.analyzeWeaknesses(
            currentSkills = skills,
            targetRoles = profile.desiredRoles,
            recentJobMisses = weaknesses.take(10)
        )
    }
    
    // ==================== Chat ====================
    
    fun getChatHistory(limit: Int = 50): Flow<List<ChatMessageEntity>> = chatDao.getRecentMessages(limit)
    
    suspend fun sendChatMessage(message: String, contextType: String? = null, contextId: Int? = null): String {
        // Save user message
        chatDao.insertMessage(ChatMessageEntity(
            role = "user",
            content = message,
            contextType = contextType,
            contextId = contextId
        ))
        
        // Build context
        var context: String? = null
        if (contextType == "job" && contextId != null) {
            val job = jobDao.getJobById(contextId)
            if (job != null) {
                context = "Job: ${job.job.title} at ${job.job.companyName}\nDescription: ${job.job.description?.take(500) ?: ""}"
            }
        } else if (contextType == "application" && contextId != null) {
            val app = applicationDao.getApplicationById(contextId)
            if (app != null) {
                context = "Application: ${app.job.title} at ${app.job.companyName}, Status: ${app.application.status}"
            }
        }
        
        // Get chat history
        val history = chatDao.getRecentMessages(10).first().reversed().map { 
            Pair(it.role, it.content) 
        }
        
        // Get AI response
        val response = gemini.chat(message, context, history)
        
        // Save assistant message
        chatDao.insertMessage(ChatMessageEntity(
            role = "assistant",
            content = response,
            contextType = contextType,
            contextId = contextId
        ))
        
        return response
    }
    
    suspend fun clearChatHistory() = chatDao.clearHistory()
}

// ==================== Stats Data Classes ====================

data class JobCounts(
    val total: Int,
    val new: Int,
    val analyzed: Int,
    val readyToApply: Int,
    val applied: Int
)

data class ApplicationStats(
    val total: Int,
    val submitted: Int,
    val interviewing: Int,
    val offers: Int,
    val rejected: Int
)
