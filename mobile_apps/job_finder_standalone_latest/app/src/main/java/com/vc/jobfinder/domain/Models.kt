package com.vc.jobfinder.domain

data class Company(
    val name: String,
    val ats: String,
    val slug: String,
    val locationFilter: String? = null,
)

data class Resume(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val linkedinUrl: String?,
    val githubUrl: String?,
    val portfolioUrl: String?,
    val topSkills: List<String>,
    val rawText: String,
) {
    val fullName: String get() = "$firstName $lastName"
}

data class Job(
    val id: String,
    val title: String,
    val company: String,
    val location: String?,
    val applyUrl: String,
    val description: String,
    val postedAt: String? = null,
)

data class MatchResult(
    val id: String,
    val job: Job,
    val fitScore: Double,
    val fitReasoning: String,
    val matchedSkills: List<String>,
    val gapSkills: List<String>,
    val status: String = "new",
)

enum class Platform(val key: String, val displayName: String) {
    HACKEREARTH("hackerearth", "HackerEarth"),
    HACKERRANK("hackerrank",   "HackerRank"),
    UNSTOP("unstop",           "Unstop"),
    KAGGLE("kaggle",           "Kaggle");

    companion object {
        fun fromKey(k: String): Platform? = entries.firstOrNull { it.key == k }
    }
}

data class Competition(
    val id: String,
    val platform: Platform,
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
)

data class ApplyPacket(
    val matchId: String,
    val coverLetter: String,
    val fieldHints: List<FieldHint>,
)

data class FieldHint(
    val label: String,
    val value: String,
    val fieldType: String = "text",
)
