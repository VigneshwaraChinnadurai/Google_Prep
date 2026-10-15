package com.vc.jobfinder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resume")
data class ResumeEntity(
    @PrimaryKey val id: Int = 0,         // singleton row
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val linkedinUrl: String?,
    val githubUrl: String?,
    val portfolioUrl: String?,
    val rawText: String,
    val updatedAt: Long,
)

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val title: String,
    val company: String,
    val location: String?,
    val applyUrl: String,
    val description: String,
    val postedAt: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,           // == job id
    val jobTitle: String,
    val jobCompany: String,
    val jobLocation: String?,
    val jobApplyUrl: String,
    val jobDescription: String,
    val fitScore: Double,
    val fitReasoning: String,
    val matchedSkillsCsv: String,
    val gapSkillsCsv: String,
    @ColumnInfo(defaultValue = "new") val status: String,
    val scoredAt: Long,
)

@Entity(tableName = "competitions")
data class CompetitionEntity(
    @PrimaryKey val id: String,
    val platformKey: String,
    val title: String,
    val sponsorCompany: String?,
    val matchedCompany: String?,
    val isTrackedCompany: Boolean,
    val isHiring: Boolean,
    val startsAt: String?,
    val endsAt: String?,
    val registrationUrl: String,
    val prizePool: String?,
    val descriptionSnippet: String,
    val fetchedAt: Long,
)
