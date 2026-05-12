package com.vc.jobfinder.data.remote

import kotlinx.serialization.Serializable

@Serializable data class ResumeDto(
    val first_name: String,
    val last_name: String,
    val email: String,
    val phone: String? = null,
    val linkedin_url: String? = null,
    val github_url: String? = null,
    val portfolio_url: String? = null,
    val top_skills: List<String> = emptyList(),
    val summary: String? = null,
)

@Serializable data class JobDto(
    val id: String,
    val title: String,
    val company: String,
    val location: String? = null,
    val apply_url: String,
    val description: String = "",
)

@Serializable data class MatchResultDto(
    val id: String,
    val job: JobDto,
    val fit_score: Double,
    val fit_reasoning: String = "",
    val matched_skills: List<String> = emptyList(),
    val gap_skills: List<String> = emptyList(),
    val status: String = "new",
)

@Serializable data class FieldHintDto(
    val label: String,
    val value: String,
    val field_type: String = "text",
    val confidence: Double = 1.0,
)

@Serializable data class ApplyPacketDto(
    val match_id: String,
    val job_title: String,
    val company: String,
    val application_url: String,
    val cover_letter: String,
    val field_hints: List<FieldHintDto>,
    val tailored_resume_summary: String? = null,
    val generated_at: String,
)

@Serializable data class RunStartedDto(val run_id: String)

@Serializable data class RunEventDto(
    val progress: Double = 0.0,
    val message: String = "",
    val status: String = "running",
    val heartbeat: Boolean = false,
    val phase: String = "",
)

@Serializable data class PipelinePhaseDto(
    val status: String = "pending",
    val started_at: String? = null,
    val finished_at: String? = null,
    val message: String = "",
    val progress: Double = 0.0,
)

@Serializable data class PipelineStateDto(
    val phases: Map<String, PipelinePhaseDto> = emptyMap(),
    val current_phase: String? = null,
    val is_complete: Boolean = false,
    val can_resume: Boolean = false,
    val last_updated: String = "",
)

@Serializable data class CompetitionDto(
    val id: String,
    val platform: String,
    val title: String,
    val sponsor_company: String? = null,
    val matched_company: String? = null,
    val is_tracked_company: Boolean = false,
    val is_hiring: Boolean = false,
    val starts_at: String? = null,
    val ends_at: String? = null,
    val registration_url: String,
    val prize_pool: String? = null,
    val location: String? = null,
    val eligibility: String? = null,
    val tags: List<String> = emptyList(),
    val description_snippet: String = "",
)

@Serializable data class ConnectionInfoDto(
    val hostname: String = "",
    val local_ip: String = "",
    val os: String = "",
    val user: String = "",
    val python_version: String = "",
)
