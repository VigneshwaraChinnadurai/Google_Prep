package com.vignesh.jobautomation.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ==================== Status Models ====================

@JsonClass(generateAdapter = true)
data class StatusResponse(
    val status: String,
    val timestamp: String,
    val database: DatabaseStats?,
    val scheduler: Map<String, AgentStatus>?,
    @Json(name = "api_cost") val apiCost: CostStats?
)

@JsonClass(generateAdapter = true)
data class DatabaseStats(
    val jobs: JobStats?,
    val applications: ApplicationCountStats?,
    val companies: Int?
)

@JsonClass(generateAdapter = true)
data class JobStats(
    val total: Int,
    val new: Int,
    val analyzed: Int
)

@JsonClass(generateAdapter = true)
data class ApplicationCountStats(
    val total: Int,
    @Json(name = "by_status") val byStatus: Map<String, Int>?
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String,
    val timestamp: String
)

@JsonClass(generateAdapter = true)
data class CostStats(
    @Json(name = "daily_cost") val dailyCost: Double,
    @Json(name = "daily_budget") val dailyBudget: Double,
    @Json(name = "budget_remaining") val budgetRemaining: Double,
    @Json(name = "total_calls") val totalCalls: Int,
    @Json(name = "total_input_tokens") val totalInputTokens: Int,
    @Json(name = "total_output_tokens") val totalOutputTokens: Int
)

// ==================== Profile Models ====================

@JsonClass(generateAdapter = true)
data class Profile(
    val id: Int,
    @Json(name = "full_name") val fullName: String,
    val email: String,
    val phone: String?,
    val location: String?,
    @Json(name = "linkedin_url") val linkedinUrl: String?,
    @Json(name = "github_url") val githubUrl: String?,
    val summary: String?,
    val skills: Map<String, List<String>>?,
    val experience: List<Experience>?,
    val education: List<Education>?,
    val projects: List<Project>?,
    @Json(name = "desired_roles") val desiredRoles: List<String>?,
    @Json(name = "desired_locations") val desiredLocations: List<String>?,
    @Json(name = "min_salary") val minSalary: Int?,
    @Json(name = "max_salary") val maxSalary: Int?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?
)

@JsonClass(generateAdapter = true)
data class Experience(
    val company: String,
    val title: String,
    @Json(name = "start_date") val startDate: String?,
    @Json(name = "end_date") val endDate: String?,
    val description: String?
)

@JsonClass(generateAdapter = true)
data class Education(
    val institution: String,
    val degree: String?,
    val field: String?,
    @Json(name = "graduation_year") val graduationYear: Int?
)

@JsonClass(generateAdapter = true)
data class Project(
    val name: String,
    val description: String?,
    val technologies: List<String>?
)

@JsonClass(generateAdapter = true)
data class ProfileUpdate(
    @Json(name = "full_name") val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val summary: String? = null,
    @Json(name = "desired_roles") val desiredRoles: List<String>? = null,
    @Json(name = "desired_locations") val desiredLocations: List<String>? = null
)

// ==================== Job Models ====================

@JsonClass(generateAdapter = true)
data class Job(
    val id: Int,
    val title: String,
    @Json(name = "company_name") val companyName: String?,
    val location: String?,
    @Json(name = "remote_type") val remoteType: String?,
    val status: String,
    @Json(name = "match_score") val matchScore: Float?,
    val url: String,
    @Json(name = "easy_apply_available") val easyApplyAvailable: Boolean?,
    @Json(name = "discovered_at") val discoveredAt: String?,
    @Json(name = "posted_date") val postedDate: String?
)

@JsonClass(generateAdapter = true)
data class JobDetail(
    val id: Int,
    val title: String,
    @Json(name = "company_name") val companyName: String?,
    @Json(name = "company_id") val companyId: Int?,
    val location: String?,
    @Json(name = "remote_type") val remoteType: String?,
    @Json(name = "job_type") val jobType: String?,
    @Json(name = "experience_level") val experienceLevel: String?,
    val status: String,
    @Json(name = "match_score") val matchScore: Float?,
    @Json(name = "match_justification") val matchJustification: MatchJustification?,
    @Json(name = "description_full") val descriptionFull: String?,
    val url: String,
    @Json(name = "salary_min") val salaryMin: Int?,
    @Json(name = "salary_max") val salaryMax: Int?,
    @Json(name = "easy_apply_available") val easyApplyAvailable: Boolean?,
    @Json(name = "analyzed_at") val analyzedAt: String?,
    @Json(name = "discovered_at") val discoveredAt: String?,
    val application: Application?
)

@JsonClass(generateAdapter = true)
data class MatchJustification(
    val summary: String?,
    val strengths: List<String>?,
    val weaknesses: List<String>?,
    @Json(name = "missing_skills") val missingSkills: List<String>?,
    val recommendations: List<String>?
)

@JsonClass(generateAdapter = true)
data class JobsResponse(
    val jobs: List<Job>,
    val count: Int,
    val skip: Int,
    val limit: Int
)

@JsonClass(generateAdapter = true)
data class AnalysisResult(
    @Json(name = "job_id") val jobId: Int?,
    @Json(name = "match_score") val matchScore: Float?,
    val justification: MatchJustification?,
    val error: String?
)

@JsonClass(generateAdapter = true)
data class ApplicationResult(
    val success: Boolean?,
    val method: String?,
    val log: List<String>?,
    val error: String?,
    @Json(name = "requires_manual") val requiresManual: Boolean?
)

@JsonClass(generateAdapter = true)
data class CustomizedResume(
    @Json(name = "optimized_summary") val optimizedSummary: String?,
    @Json(name = "highlighted_skills") val highlightedSkills: List<String>?,
    @Json(name = "experience_bullets") val experienceBullets: List<Map<String, Any>>?,
    @Json(name = "keywords_added") val keywordsAdded: List<String>?,
    @Json(name = "ats_score_estimate") val atsScoreEstimate: Int?
)

// ==================== Application Models ====================

@JsonClass(generateAdapter = true)
data class Application(
    val id: Int,
    @Json(name = "job_id") val jobId: Int,
    @Json(name = "applied_at") val appliedAt: String?,
    @Json(name = "applied_via") val appliedVia: String?,
    val status: String,
    @Json(name = "status_updated_at") val statusUpdatedAt: String?,
    @Json(name = "interview_dates") val interviewDates: List<String>?,
    @Json(name = "offer_salary") val offerSalary: Int?,
    val notes: String?,
    @Json(name = "auto_applied") val autoApplied: Boolean?
)

@JsonClass(generateAdapter = true)
data class ApplicationDetail(
    val id: Int,
    @Json(name = "job_id") val jobId: Int,
    @Json(name = "applied_at") val appliedAt: String?,
    @Json(name = "applied_via") val appliedVia: String?,
    val status: String,
    @Json(name = "status_history") val statusHistory: List<StatusHistoryEntry>?,
    @Json(name = "interview_dates") val interviewDates: List<String>?,
    val notes: String?,
    val job: JobDetail?,
    val emails: List<Email>?,
    @Json(name = "interview_prep") val interviewPrep: InterviewPrep?
)

@JsonClass(generateAdapter = true)
data class StatusHistoryEntry(
    val status: String,
    val timestamp: String,
    val source: String?
)

@JsonClass(generateAdapter = true)
data class Email(
    val id: Int,
    val subject: String?,
    val sender: String?,
    @Json(name = "received_at") val receivedAt: String?,
    val classification: String?,
    @Json(name = "classification_confidence") val classificationConfidence: Float?
)

@JsonClass(generateAdapter = true)
data class ApplicationsResponse(
    val applications: List<ApplicationWithJob>,
    val count: Int,
    val skip: Int,
    val limit: Int
)

@JsonClass(generateAdapter = true)
data class ApplicationWithJob(
    val id: Int,
    @Json(name = "job_id") val jobId: Int,
    @Json(name = "applied_at") val appliedAt: String?,
    val status: String,
    @Json(name = "auto_applied") val autoApplied: Boolean?,
    val job: JobSummary?
)

@JsonClass(generateAdapter = true)
data class JobSummary(
    val id: Int,
    val title: String,
    @Json(name = "company_name") val companyName: String?,
    val location: String?,
    @Json(name = "match_score") val matchScore: Float?,
    val url: String?
)

@JsonClass(generateAdapter = true)
data class ApplicationStats(
    @Json(name = "by_status") val byStatus: Map<String, Int>,
    val total: Int,
    val active: Int,
    val successful: Int,
    val rejected: Int
)

@JsonClass(generateAdapter = true)
data class ApplicationUpdate(
    val status: String? = null,
    val notes: String? = null,
    @Json(name = "interview_notes") val interviewNotes: String? = null
)

@JsonClass(generateAdapter = true)
data class InterviewPrep(
    @Json(name = "company_research") val companyResearch: String?,
    @Json(name = "role_analysis") val roleAnalysis: String?,
    @Json(name = "behavioral_questions") val behavioralQuestions: List<Map<String, Any>>?,
    @Json(name = "technical_questions") val technicalQuestions: List<Map<String, Any>>?,
    @Json(name = "questions_for_interviewer") val questionsForInterviewer: List<Map<String, Any>>?,
    @Json(name = "talking_points") val talkingPoints: List<String>?
)

// ==================== Company Models ====================

@JsonClass(generateAdapter = true)
data class Company(
    val id: Int,
    val name: String,
    @Json(name = "company_type") val companyType: String?,
    val industry: String?,
    val size: String?,
    val website: String?,
    @Json(name = "careers_page") val careersPage: String?,
    val preference: String?,
    @Json(name = "preference_notes") val preferenceNotes: String?,
    val headquarters: String?,
    @Json(name = "glassdoor_rating") val glassdoorRating: Float?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "job_count") val jobCount: Int?
)

@JsonClass(generateAdapter = true)
data class CompaniesResponse(
    val companies: List<Company>,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class CompanyCreate(
    val name: String,
    @Json(name = "company_type") val companyType: String = "OTHER",
    val industry: String? = null,
    val website: String? = null,
    @Json(name = "careers_page") val careersPage: String? = null,
    val preference: String = "NEUTRAL"
)

@JsonClass(generateAdapter = true)
data class PreferenceUpdate(
    val preference: String,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class SeedResult(
    val message: String,
    val created: List<String>,
    val skipped: List<String>
)

// ==================== Chat Models ====================

@JsonClass(generateAdapter = true)
data class ChatRequest(
    val message: String,
    val context: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    val response: String,
    @Json(name = "context_used") val contextUsed: Boolean?,
    val cost: Double?,
    val timestamp: String?
)

@JsonClass(generateAdapter = true)
data class StatusSummary(
    val summary: String,
    val data: Map<String, Any>?,
    val timestamp: String?
)

// ==================== Agent Models ====================

@JsonClass(generateAdapter = true)
data class AgentStatus(
    val id: String?,
    val name: String?,
    @Json(name = "next_run") val nextRun: String?,
    val paused: Boolean?
)

@JsonClass(generateAdapter = true)
data class AgentRun(
    val id: Int,
    @Json(name = "agent_name") val agentName: String,
    @Json(name = "started_at") val startedAt: String?,
    @Json(name = "completed_at") val completedAt: String?,
    val status: String?,
    @Json(name = "items_processed") val itemsProcessed: Int?,
    @Json(name = "items_succeeded") val itemsSucceeded: Int?,
    @Json(name = "items_failed") val itemsFailed: Int?,
    @Json(name = "api_calls") val apiCalls: Int?,
    @Json(name = "estimated_cost") val estimatedCost: Double?
)

@JsonClass(generateAdapter = true)
data class AgentRunsResponse(
    val runs: List<AgentRun>,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class AgentResult(
    val status: String?,
    val agent: String?,
    val result: Map<String, Any>?,
    val stats: Map<String, Any>?,
    val error: String?
)

@JsonClass(generateAdapter = true)
data class PipelineResult(
    @Json(name = "pipeline_results") val pipelineResults: List<Map<String, Any>>
)
