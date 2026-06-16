package com.vignesh.leetcodechecker.prooffiling

import com.squareup.moshi.JsonClass
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * ProofFiling - Weekly career progress tracker
 * 
 * Captures:
 * - Wins: Accomplishments (big or small)
 * - Learnings: New concepts, patterns, mistakes learned from
 * - Evidence: Tangible proof of progress (LeetCode problems, projects, etc.)
 */

// ═══════════════════════════════════════════════════════════════════════════
// Core Data Models
// ═══════════════════════════════════════════════════════════════════════════

/**
 * A single ProofFiling entry for a week
 */
@JsonClass(generateAdapter = true)
data class ProofFilingEntry(
    val id: String = generateId(),
    val weekStartDate: String, // ISO format: 2026-06-15
    val weekEndDate: String,
    val createdAt: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    val lastUpdatedAt: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    
    // User-entered content
    val wins: List<WinItem> = emptyList(),
    val learnings: List<LearningItem> = emptyList(),
    val evidence: List<EvidenceItem> = emptyList(),
    
    // Auto-captured metrics
    val githubStats: GitHubWeeklyStats? = null,
    val leetcodeStats: LeetCodeWeeklyStats? = null,
    
    // LLM-generated summary
    val weeklySummary: String? = null,
    
    // Push status
    val isPushedToGitHub: Boolean = false,
    val pushedAt: String? = null,
    val commitSha: String? = null
) {
    companion object {
        private fun generateId(): String = 
            "pf_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}

@JsonClass(generateAdapter = true)
data class WinItem(
    val id: String = "win_${System.currentTimeMillis()}",
    val description: String,
    val category: WinCategory = WinCategory.OTHER,
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
)

enum class WinCategory {
    TECHNICAL,      // Code achievements, bug fixes, optimizations
    LEARNING,       // Completed courses, certifications
    PROJECT,        // Project milestones
    COLLABORATION,  // Helped someone, pair programming
    COMMUNICATION,  // Presentations, documentation
    CAREER,         // Interviews, networking
    OTHER
}

@JsonClass(generateAdapter = true)
data class LearningItem(
    val id: String = "learn_${System.currentTimeMillis()}",
    val description: String,
    val source: LearningSource = LearningSource.MANUAL,
    val category: LearningCategory = LearningCategory.OTHER,
    val relatedCommitUrl: String? = null,
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
)

enum class LearningSource {
    MANUAL,         // User-entered
    GITHUB_COMMIT,  // Auto-captured from commit messages
    LEETCODE,       // Learned from solving problems
    COURSE,         // Online course
    BOOK,           // Book/article
    OTHER
}

enum class LearningCategory {
    DSA,            // Data Structures & Algorithms
    SYSTEM_DESIGN,  // System design concepts
    LANGUAGE,       // Programming language features
    FRAMEWORK,      // Framework/library
    TOOL,           // Dev tools
    PATTERN,        // Design patterns
    DEBUGGING,      // Debugging techniques
    MISTAKE_LESSON, // Learned from a mistake
    OTHER
}

@JsonClass(generateAdapter = true)
data class EvidenceItem(
    val id: String = "ev_${System.currentTimeMillis()}",
    val description: String,
    val type: EvidenceType,
    val link: String? = null,
    val metrics: Map<String, String> = emptyMap(), // e.g., "problems_solved" -> "5"
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
)

enum class EvidenceType {
    LEETCODE,       // Solved X problems
    GITHUB_COMMIT,  // Made X commits
    GITHUB_PR,      // Created/merged PRs
    PROJECT,        // Built something tangible
    CERTIFICATION,  // Earned a cert
    ARTICLE,        // Published content
    OTHER
}

// ═══════════════════════════════════════════════════════════════════════════
// Integration Stats Models
// ═══════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class GitHubWeeklyStats(
    val username: String,
    val periodStart: String,
    val periodEnd: String,
    val totalCommits: Int = 0,
    val totalPRs: Int = 0,
    val totalIssues: Int = 0,
    val reposContributed: List<String> = emptyList(),
    val commitMessages: List<CommitInfo> = emptyList(),
    val contributionCount: Int = 0, // From contribution calendar
    val llmSummary: String? = null
)

@JsonClass(generateAdapter = true)
data class CommitInfo(
    val sha: String,
    val message: String,
    val repoName: String,
    val date: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class LeetCodeWeeklyStats(
    val username: String,
    val periodStart: String,
    val periodEnd: String,
    val previousTotalSolved: Int,
    val currentTotalSolved: Int,
    val problemsSolvedThisWeek: Int,
    val easyDelta: Int = 0,
    val mediumDelta: Int = 0,
    val hardDelta: Int = 0
)

// ═══════════════════════════════════════════════════════════════════════════
// Configuration Models
// ═══════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class ProofFilingConfig(
    // Scheduling
    val reminderDayOfWeek: Int = 5, // Friday (1=Monday, 7=Sunday)
    val reminderHour: Int = 18,     // 6 PM
    val reminderMinute: Int = 0,
    
    // GitHub push target
    val targetRepo: String = "proof_journal",
    val targetBranch: String = "main",
    val targetPath: String = "entries/",
    
    // Integrations (configurable list for future additions)
    val integrations: List<IntegrationConfig> = listOf(
        IntegrationConfig("GitHub", "VigneshwaraChinnadurai", true),
        IntegrationConfig("LeetCode", "rockingstarvic", true),
        IntegrationConfig("LinkedIn", "", false) // Manual entry
    )
)

@JsonClass(generateAdapter = true)
data class IntegrationConfig(
    val name: String,
    val profileUrl: String,
    val autoFetch: Boolean,
    val enabled: Boolean = true
)

// ═══════════════════════════════════════════════════════════════════════════
// UI State Models
// ═══════════════════════════════════════════════════════════════════════════

data class ProofFilingUIState(
    val isLoading: Boolean = false,
    val isFetchingStats: Boolean = false,
    val isPushing: Boolean = false,
    val currentEntry: ProofFilingEntry? = null,
    val historyEntries: List<ProofFilingEntry> = emptyList(),
    val config: ProofFilingConfig = ProofFilingConfig(),
    val error: String? = null,
    val successMessage: String? = null,
    val showPreview: Boolean = false,
    val previewMarkdown: String? = null
)

// ═══════════════════════════════════════════════════════════════════════════
// GitHub Push Models
// ═══════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class GitHubCreateFileRequest(
    val message: String,
    val content: String, // Base64 encoded
    val branch: String
)

@JsonClass(generateAdapter = true)
data class GitHubUpdateFileRequest(
    val message: String,
    val content: String, // Base64 encoded
    val sha: String,
    val branch: String
)

@JsonClass(generateAdapter = true)
data class GitHubFileResponse(
    val content: GitHubContentInfo?,
    val commit: GitHubCommitInfo?
)

@JsonClass(generateAdapter = true)
data class GitHubContentInfo(
    val name: String?,
    val path: String?,
    val sha: String?,
    val size: Int?,
    val url: String?,
    val html_url: String?,
    val download_url: String?
)

@JsonClass(generateAdapter = true)
data class GitHubCommitInfo(
    val sha: String?,
    val message: String?,
    val html_url: String?
)

// ═══════════════════════════════════════════════════════════════════════════
// Markdown Generation Helper
// ═══════════════════════════════════════════════════════════════════════════

fun ProofFilingEntry.toMarkdown(): String = buildString {
    appendLine("# 📊 Weekly ProofFiling Report")
    appendLine()
    appendLine("**Week:** $weekStartDate → $weekEndDate")
    appendLine("**Created:** $createdAt")
    appendLine("**Last Updated:** $lastUpdatedAt")
    appendLine()
    
    // Weekly Summary (if generated)
    if (!weeklySummary.isNullOrBlank()) {
        appendLine("## 📝 Weekly Summary")
        appendLine()
        appendLine(weeklySummary)
        appendLine()
    }
    
    // Wins Section
    appendLine("## 🏆 Wins")
    appendLine()
    if (wins.isEmpty()) {
        appendLine("_No wins logged this week._")
    } else {
        wins.forEach { win ->
            appendLine("- **[${win.category.name}]** ${win.description}")
        }
    }
    appendLine()
    
    // Learnings Section
    appendLine("## 📚 Learnings")
    appendLine()
    if (learnings.isEmpty()) {
        appendLine("_No learnings logged this week._")
    } else {
        learnings.forEach { learning ->
            val sourceTag = if (learning.source != LearningSource.MANUAL) " _(${learning.source.name})_" else ""
            appendLine("- **[${learning.category.name}]** ${learning.description}$sourceTag")
            learning.relatedCommitUrl?.let { url ->
                appendLine("  - Related: [$url]($url)")
            }
        }
    }
    appendLine()
    
    // Evidence Section
    appendLine("## 📊 Evidence")
    appendLine()
    if (evidence.isEmpty()) {
        appendLine("_No evidence logged this week._")
    } else {
        evidence.forEach { ev ->
            appendLine("- **[${ev.type.name}]** ${ev.description}")
            ev.metrics.forEach { (key, value) ->
                appendLine("  - $key: $value")
            }
            ev.link?.let { link ->
                appendLine("  - Link: [$link]($link)")
            }
        }
    }
    appendLine()
    
    // GitHub Stats
    githubStats?.let { stats ->
        appendLine("## 🐙 GitHub Activity")
        appendLine()
        appendLine("- **Commits:** ${stats.totalCommits}")
        appendLine("- **Pull Requests:** ${stats.totalPRs}")
        appendLine("- **Issues:** ${stats.totalIssues}")
        appendLine("- **Contributions:** ${stats.contributionCount}")
        appendLine("- **Repos Touched:** ${stats.reposContributed.joinToString(", ")}")
        appendLine()
        
        if (!stats.llmSummary.isNullOrBlank()) {
            appendLine("### Summary (AI Generated)")
            appendLine(stats.llmSummary)
            appendLine()
        }
        
        if (stats.commitMessages.isNotEmpty()) {
            appendLine("### Commit Highlights")
            stats.commitMessages.take(10).forEach { commit ->
                appendLine("- `${commit.repoName}`: ${commit.message.lines().first()}")
            }
            appendLine()
        }
    }
    
    // LeetCode Stats
    leetcodeStats?.let { stats ->
        appendLine("## 💻 LeetCode Progress")
        appendLine()
        appendLine("- **Problems Solved This Week:** ${stats.problemsSolvedThisWeek}")
        appendLine("- **Easy:** +${stats.easyDelta}")
        appendLine("- **Medium:** +${stats.mediumDelta}")
        appendLine("- **Hard:** +${stats.hardDelta}")
        appendLine("- **Total:** ${stats.previousTotalSolved} → ${stats.currentTotalSolved}")
        appendLine()
    }
    
    // Footer
    appendLine("---")
    appendLine("_Generated by ProofFiling App | [View on GitHub](https://github.com/VigneshwaraChinnadurai/proof_journal)_")
}
