package com.vignesh.jobmatcher.claude

import com.vignesh.jobmatcher.data.JsonCodec
import com.vignesh.jobmatcher.data.JsonCodec.str
import com.vignesh.jobmatcher.data.JsonCodec.strings
import com.vignesh.jobmatcher.model.CandidateProfile
import com.vignesh.jobmatcher.model.Tailoring
import org.json.JSONArray
import org.json.JSONObject

data class ScoreUpdate(
    val jobId: String,
    val score: Int,
    val verdict: String,
    val reasons: String,
    val matchedSkills: List<String>,
    val gaps: List<String>
)

data class FoundJob(
    val company: String,
    val title: String,
    val location: String,
    val url: String,
    val postedAt: String,
    val description: String,
    val score: ScoreUpdate?
)

/**
 * Parses replies pasted back from the Claude app. Tolerant of what chat UIs do to
 * output: markdown code fences around the JSON, prose before/after the tagged block,
 * and a missing closing tag when the copy got truncated at the end.
 */
object ClaudeResponseParser {

    private fun checkNotPrompt(raw: String) {
        if (raw.isBlank()) {
            error("Clipboard is empty. Copy Claude's full reply first, then tap paste again.")
        }
        if (raw.contains(PromptBuilder.PROMPT_MARKER)) {
            error("This looks like the prompt you copied, not Claude's reply. Paste Claude's actual response instead.")
        }
    }

    /** Text between <tag> and </tag>; tolerates a missing close tag. Null if the open tag is absent. */
    fun extractTag(raw: String, tag: String): String? {
        val open = "<$tag>"
        val start = raw.indexOf(open)
        if (start == -1) return null
        val from = start + open.length
        val end = raw.indexOf("</$tag>", from)
        return (if (end == -1) raw.substring(from) else raw.substring(from, end)).trim()
    }

    private fun stripFences(s: String): String =
        s.trim().removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()

    /** Tagged block if present, else the outermost [...] / {...} span in the text. */
    private fun jsonPayload(raw: String, tag: String, open: Char, close: Char): String {
        val tagged = extractTag(raw, tag)?.let(::stripFences)
        val text = tagged ?: raw
        val start = text.indexOf(open)
        val end = text.lastIndexOf(close)
        if (start == -1 || end <= start) {
            error("Couldn't find the <$tag> JSON in the pasted text. Make sure you copied Claude's complete reply.")
        }
        return text.substring(start, end + 1)
    }

    private fun scoreFrom(o: JSONObject, id: String): ScoreUpdate? {
        if (!o.has("score")) return null
        val score = o.optDouble("score", Double.NaN).takeIf { !it.isNaN() } ?: return null
        return ScoreUpdate(
            jobId = id,
            score = score.toInt().coerceIn(0, 100),
            verdict = o.str("verdict"),
            reasons = o.str("reasons"),
            matchedSkills = o.optJSONArray("matched_skills").strings(),
            gaps = o.optJSONArray("gaps").strings()
        )
    }

    fun parseScores(raw: String): Result<List<ScoreUpdate>> = runCatching {
        checkNotPrompt(raw)
        val arr = JSONArray(jsonPayload(raw, PromptBuilder.Kind.SCORING.tag, '[', ']'))
        val scores = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("id")
            if (id.isBlank()) null else scoreFrom(o, id)
        }
        if (scores.isEmpty()) error("The pasted reply had no job scores in it.")
        scores
    }

    fun parseFoundJobs(raw: String): Result<List<FoundJob>> = runCatching {
        checkNotPrompt(raw)
        val arr = JSONArray(jsonPayload(raw, PromptBuilder.Kind.SEARCH.tag, '[', ']'))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.str("url")
            val title = o.str("title")
            if (title.isBlank() || !url.startsWith("http")) return@mapNotNull null
            FoundJob(
                company = o.str("company"),
                title = title,
                location = o.str("location"),
                url = url,
                postedAt = o.str("posted_date"),
                description = o.str("description"),
                score = scoreFrom(o, id = "")
            )
        }
    }

    fun parseProfile(raw: String): Result<CandidateProfile> = runCatching {
        checkNotPrompt(raw)
        val profile = JsonCodec.profileFromJson(JSONObject(jsonPayload(raw, PromptBuilder.Kind.PROFILE.tag, '{', '}')))
        if (profile.skills.isEmpty()) error("The pasted profile has no skills -- copy Claude's full reply.")
        profile.copy(origin = "claude", updatedAt = System.currentTimeMillis())
    }

    fun parseTailoring(raw: String): Result<Tailoring> = runCatching {
        checkNotPrompt(raw)
        val t = Tailoring(
            summary = extractTag(raw, "match_summary").orEmpty(),
            resumeBullets = extractTag(raw, "resume_bullets").orEmpty(),
            coverLetter = extractTag(raw, "cover_letter").orEmpty(),
            gapPlan = extractTag(raw, "gap_plan").orEmpty(),
            referralMessage = extractTag(raw, "referral_message").orEmpty(),
            createdAt = System.currentTimeMillis()
        )
        if (listOf(t.summary, t.resumeBullets, t.coverLetter, t.gapPlan, t.referralMessage).all { it.isBlank() }) {
            error("Couldn't find the <match_summary>/<resume_bullets>/<cover_letter> tags. Copy Claude's complete reply.")
        }
        t
    }
}
