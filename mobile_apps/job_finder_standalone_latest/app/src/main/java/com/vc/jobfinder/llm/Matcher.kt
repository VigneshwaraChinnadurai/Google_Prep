package com.vc.jobfinder.llm

import com.vc.jobfinder.domain.Job
import com.vc.jobfinder.domain.MatchResult
import com.vc.jobfinder.domain.Resume
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ScoreJson(
    val fit_score: Double,
    val fit_reasoning: String,
    val matched_skills: List<String> = emptyList(),
    val gap_skills: List<String> = emptyList(),
)

@Singleton
class Matcher @Inject constructor(
    private val gemini: GeminiClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val systemPrompt = """
        You evaluate whether a candidate fits a specific job. Be honest and conservative.
        Score 0.0–1.0 where 1.0 means strong yes, 0.5 means borderline, 0.2 means clearly weak fit.
        Penalize seniority mismatch, missing must-haves, and stack mismatches.
        Reward direct experience, measurable impact, and stack overlap.
        Return STRICT JSON matching this schema, no preamble, no markdown:
        {"fit_score": float, "fit_reasoning": string (1-2 sentences),
         "matched_skills": [string], "gap_skills": [string]}
    """.trimIndent()

    suspend fun score(resume: Resume, job: Job, model: String = "gemini-2.5-flash"): MatchResult {
        val prompt = buildString {
            appendLine("# CANDIDATE")
            appendLine(resume.rawText.take(8_000))
            appendLine()
            appendLine("# JOB")
            appendLine("Title: ${job.title}")
            appendLine("Company: ${job.company}")
            job.location?.let { appendLine("Location: $it") }
            appendLine()
            appendLine("Description:")
            appendLine(job.description.take(4_000))
        }

        val text = gemini.generate(
            prompt = prompt,
            systemPrompt = systemPrompt,
            model = model,
            temperature = 0.1f,
            responseMimeType = "application/json",
        )

        // Be defensive — even with responseMimeType, models sometimes wrap in ```json
        val cleaned = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val parsed = try {
            json.decodeFromString(ScoreJson.serializer(), cleaned)
        } catch (t: Throwable) {
            // Fallback: produce a low-confidence result rather than crashing the batch.
            ScoreJson(
                fit_score = 0.0,
                fit_reasoning = "Could not parse model output: ${t.message?.take(80)}",
            )
        }

        return MatchResult(
            id = job.id,
            job = job,
            fitScore = parsed.fit_score.coerceIn(0.0, 1.0),
            fitReasoning = parsed.fit_reasoning,
            matchedSkills = parsed.matched_skills,
            gapSkills = parsed.gap_skills,
        )
    }
}
