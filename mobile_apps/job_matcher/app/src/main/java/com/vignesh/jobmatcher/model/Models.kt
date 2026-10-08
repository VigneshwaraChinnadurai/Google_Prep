package com.vignesh.jobmatcher.model

/**
 * Where a company's postings come from. Every "automatic" source is a public, key-less
 * careers API the app calls itself; CLAUDE_SEARCH is for companies with no such API
 * (e.g. Google) -- the app builds a web-search prompt for Claude and parses the pasted reply.
 */
enum class SourceType(val label: String, val automatic: Boolean, val identifierHint: String) {
    GREENHOUSE("Greenhouse", true, "Board token, e.g. 'anthropic' from boards.greenhouse.io/anthropic"),
    LEVER("Lever", true, "Company slug, e.g. 'palantir' from jobs.lever.co/palantir"),
    ASHBY("Ashby", true, "Org slug, e.g. 'openai' from jobs.ashbyhq.com/openai"),
    WORKDAY("Workday", true, "Careers URL, e.g. https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite"),
    AMAZON("Amazon Jobs", true, "Not needed -- uses amazon.jobs search"),
    EIGHTFOLD("Eightfold", true, "Careers host + domain, e.g. https://apply.careers.microsoft.com?domain=microsoft.com"),
    ORACLE_HCM("Oracle HCM", true, "Candidate-experience URL, e.g. https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001"),
    SMARTRECRUITERS("SmartRecruiters", true, "Company id, e.g. 'Freshworks' from jobs.smartrecruiters.com/Freshworks"),
    CLAUDE_SEARCH("Claude web search", false, "Careers page URL (optional, helps Claude search)"),
    /** Job-level only (never a company source): a posting you added from a link. */
    MANUAL_LINK("Added from a link", false, "");

    /** Sources a company can be configured with (MANUAL_LINK is per-job only). */
    val selectableForCompany: Boolean get() = this != MANUAL_LINK

    companion object {
        fun parse(value: String?): SourceType =
            entries.firstOrNull { it.name == value } ?: CLAUDE_SEARCH
    }
}

data class Company(
    val id: String,
    val name: String,
    val source: SourceType,
    /** Greenhouse board token / Lever slug / Ashby org / Workday careers URL / careers page URL. */
    val identifier: String = "",
    /** 1 = top choice (Google), 2 = target, 3 = backup. Top-choice matches sort first. */
    val priority: Int = 2,
    val enabled: Boolean = true,
    val lastFetchedAt: Long = 0,
    val lastFetchCount: Int = 0,
    val lastError: String = ""
) {
    companion object {
        val PRIORITY_LABELS = mapOf(1 to "⭐ Top choice", 2 to "🎯 Target", 3 to "Backup")
    }
}

/** How a job entered the app: found by the app's own pipeline, or added by you from a link. */
enum class JobOrigin(val label: String) {
    AUTOMATIC("🤖 Auto"),
    MANUAL("✋ Manual");

    companion object {
        fun parse(value: String?): JobOrigin = entries.firstOrNull { it.name == value } ?: AUTOMATIC
    }
}

enum class JobStatus(val label: String, val emoji: String) {
    NEW("New", "🆕"),
    SAVED("Saved", "⭐"),
    APPLIED("Applied", "📨"),
    INTERVIEWING("Interviewing", "🗣️"),
    OFFER("Offer", "🎉"),
    REJECTED("Rejected", "❌"),
    DISMISSED("Dismissed", "🚫");

    companion object {
        fun parse(value: String?): JobStatus = entries.firstOrNull { it.name == value } ?: NEW
        val TRACKED = listOf(SAVED, APPLIED, INTERVIEWING, OFFER, REJECTED)
    }
}

/** Claude's tailored application material for one job (see PromptBuilder.tailoringPrompt). */
data class Tailoring(
    val summary: String = "",
    val resumeBullets: String = "",
    val coverLetter: String = "",
    val gapPlan: String = "",
    val referralMessage: String = "",
    val createdAt: Long = 0
)

data class Job(
    /** "<source>:<companyId>:<externalId>" -- stable across re-fetches. */
    val id: String,
    val companyId: String,
    val companyName: String,
    val title: String,
    val location: String,
    val url: String,
    val description: String,
    val postedAt: String = "",
    val source: SourceType,
    val origin: JobOrigin = JobOrigin.AUTOMATIC,
    val firstSeenAt: Long = 0,
    val lastSeenAt: Long = 0,
    /** Set when an automatic source stops listing the job. */
    val closed: Boolean = false,

    val localScore: Int = 0,
    val localMatchedSkills: List<String> = emptyList(),

    /** Null until Claude has scored it. */
    val claudeScore: Int? = null,
    val claudeVerdict: String = "",
    val claudeReasons: String = "",
    val matchedSkills: List<String> = emptyList(),
    val gaps: List<String> = emptyList(),
    val scoredAt: Long = 0,

    val status: JobStatus = JobStatus.NEW,
    val statusUpdatedAt: Long = 0,
    val appliedAt: Long = 0,
    val notes: String = "",
    val tailoring: Tailoring? = null
) {
    val isScored: Boolean get() = claudeScore != null
    val isManual: Boolean get() = origin == JobOrigin.MANUAL

    /** A manual job whose posting couldn't be read in full (login wall, JS-only page). */
    val needsDetails: Boolean get() = isManual && description.length < MIN_DESCRIPTION_CHARS

    companion object {
        const val MIN_DESCRIPTION_CHARS = 300
    }
}

data class ProfileSkill(
    val name: String,
    val aliases: List<String> = emptyList(),
    /** 3 = core strength, 2 = solid, 1 = familiar. */
    val weight: Int = 2
)

/**
 * Structured view of the resume used by the on-device pre-filter. Seeded from
 * assets/default_profile.json and replaceable by Claude's Profile Analysis output.
 */
data class CandidateProfile(
    val headline: String = "",
    val yearsExperience: Int = 0,
    val targetTitles: List<String> = emptyList(),
    val skills: List<ProfileSkill> = emptyList(),
    /** Keywords sent to search-based APIs (Workday, Amazon). */
    val searchTerms: List<String> = emptyList(),
    val updatedAt: Long = 0,
    /** "default" (bundled) or "claude" (from a pasted Profile Analysis reply). */
    val origin: String = "default"
)

data class AppSettings(
    /**
     * Your search terms -- the job titles/keywords queried on every careers site and given
     * to Claude web search. They also count as target titles in the on-device score.
     */
    val searchTerms: List<String> = DEFAULT_SEARCH_TERMS,
    /** Claude-score cutoff for the Matches tab (the "80%"). */
    val matchThreshold: Int = 80,
    /** On-device score a job needs before it's worth sending to Claude. */
    val prefilterThreshold: Int = 50,
    /** Companies per Claude web-search prompt (top-choice companies always go alone). */
    val searchBatchSize: Int = 4,
    /** Jobs per Claude scoring prompt. */
    val scoringBatchSize: Int = 12,
    /** Max description characters per job inside a scoring prompt. */
    val maxDescriptionChars: Int = 2000,
    val includeResumeInScoring: Boolean = true,
    /** A posting must mention one of these in its location to be kept. */
    val locationKeywords: List<String> = DEFAULT_LOCATIONS,
    /** Titles containing any of these are dropped before scoring. */
    val excludeTitleKeywords: List<String> = DEFAULT_EXCLUDES,
    val autoFetchEnabled: Boolean = true,
    val autoFetchHour: Int = 7
) {
    companion object {
        val DEFAULT_SEARCH_TERMS = listOf(
            "Gen AI Engineer", "AI Architect", "Machine Learning Engineer",
            "Applied Scientist", "Data Scientist", "Software Engineer 3"
        )
        val DEFAULT_LOCATIONS = listOf(
            "india", "bengaluru", "bangalore", "hyderabad", "chennai", "pune",
            "mumbai", "gurgaon", "gurugram", "noida", "delhi"
        )
        val DEFAULT_EXCLUDES = listOf(
            "intern", "internship", "new grad", "graduate", "apprentice", "junior",
            "account executive", "sales", "recruiter", "marketing", "legal", "counsel",
            "accountant", "payroll", "facilities", "administrative"
        )
    }
}
