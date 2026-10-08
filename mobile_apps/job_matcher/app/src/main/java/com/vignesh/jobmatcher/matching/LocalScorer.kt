package com.vignesh.jobmatcher.matching

import com.vignesh.jobmatcher.model.AppSettings
import com.vignesh.jobmatcher.model.CandidateProfile

data class LocalScore(val score: Int, val matchedSkills: List<String>)

/**
 * Cheap on-device relevance score (0-100) that decides which postings are worth a Claude
 * round-trip. It is deliberately generous -- Claude makes the real 80% call; this only has
 * to keep obviously-irrelevant roles (backend-only, sales, junior) out of the paste flow.
 *
 *   score = 50% skill coverage + 35% title fit + 15% seniority fit
 *
 * with two relevance gates, calibrated on ~440 live India postings (Amazon + NVIDIA,
 * Oct 2026) where big-company JDs mention ML/AWS/Python almost everywhere:
 *  - no AI/ML signal in the title at all -> capped at 40 (analyst, PM, generic SDE roles)
 *  - no core (weight-3) skill anywhere   -> capped at 25
 */
class LocalScorer(private val profile: CandidateProfile, private val settings: AppSettings) {

    private val skillPatterns: List<Triple<String, Int, List<Regex>>> = profile.skills.map { skill ->
        Triple(skill.name, skill.weight, (listOf(skill.name) + skill.aliases).map { wordRegex(it) })
    }
    private val titlePatterns = profile.targetTitles.map { wordRegex(it) }

    fun isLocationAllowed(location: String): Boolean =
        settings.locationKeywords.isEmpty() || settings.locationKeywords.any { location.contains(it, ignoreCase = true) }

    fun isTitleAllowed(title: String): Boolean =
        settings.excludeTitleKeywords.none { wordRegex(it).containsMatchIn(title) }

    fun score(title: String, description: String): LocalScore {
        val text = "$title\n$description"
        val matched = skillPatterns.filter { (_, _, patterns) -> patterns.any { it.containsMatchIn(text) } }
        val points = matched.sumOf { it.second }
        val skillScore = (points / SKILL_POINTS_FOR_FULL).coerceAtMost(1.0)
        val coreHit = matched.any { it.second >= 3 }

        val titleScore = when {
            titlePatterns.any { it.containsMatchIn(title) } -> 1.0
            else -> {
                val hits = TITLE_TOKENS.count { wordRegex(it).containsMatchIn(title) }
                (hits / 2.0).coerceAtMost(1.0) * 0.7
            }
        }

        val seniorityScore = seniorityFit(title, description)
        var total = (100 * (0.50 * skillScore + 0.35 * titleScore + 0.15 * seniorityScore)).toInt()
        if (titleScore == 0.0) total = total.coerceAtMost(40)
        if (!coreHit) total = total.coerceAtMost(25)
        return LocalScore(total.coerceIn(0, 100), matched.map { it.first })
    }

    private fun seniorityFit(title: String, description: String): Double {
        val years = profile.yearsExperience
        if (JUNIOR.any { wordRegex(it).containsMatchIn(title) }) return 0.1
        // Level-numbered titles ("Data Engineer II", "Product Manager-Tech III") -- I/II sit below 8 YOE.
        if (LEVEL_REGEX.find(title)?.groupValues?.get(1)?.let { it == "I" || it == "II" } == true) return 0.2
        val required = YEARS_REGEX.findAll(description)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1..25 }
            .maxOrNull()
        val titleLevel = if (SENIOR.any { wordRegex(it).containsMatchIn(title) }) 1.0 else 0.7
        return when {
            required == null -> titleLevel
            years > 0 && required > years + 4 -> 0.3
            years > 0 && required > years + 1 -> 0.7
            else -> titleLevel
        }
    }

    companion object {
        private const val SKILL_POINTS_FOR_FULL = 18.0
        private val TITLE_TOKENS = listOf(
            "machine learning", "ml", "ai", "artificial intelligence", "data scientist", "scientist",
            "applied", "genai", "generative", "llm", "nlp", "architect", "deep learning", "research"
        )
        private val SENIOR = listOf("senior", "sr", "staff", "lead", "principal", "manager", "architect", "head", "director")
        private val JUNIOR = listOf("intern", "internship", "junior", "jr", "new grad", "graduate", "entry level", "associate engineer")
        private val LEVEL_REGEX = Regex("(?<![A-Za-z])(III|II|I)(?![A-Za-z])")
        private val YEARS_REGEX = Regex("(\\d{1,2})\\s*\\+?\\s*(?:-\\s*\\d{1,2}\\s*)?(?:years|yrs)", RegexOption.IGNORE_CASE)

        private val regexCache = HashMap<String, Regex>()

        /** Case-insensitive whole-word match; tolerant of terms with symbols like "a/b testing". */
        fun wordRegex(term: String): Regex = synchronized(regexCache) {
            regexCache.getOrPut(term.lowercase()) {
                Regex("(?<![A-Za-z0-9])" + Regex.escape(term.trim()) + "(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
            }
        }
    }
}
