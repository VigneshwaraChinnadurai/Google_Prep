package com.vc.jobfinder.llm

import com.vc.jobfinder.domain.ApplyPacket
import com.vc.jobfinder.domain.FieldHint
import com.vc.jobfinder.domain.MatchResult
import com.vc.jobfinder.domain.Resume
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverLetterGenerator @Inject constructor(
    private val gemini: GeminiClient,
) {
    private val systemPrompt =
        "You write concise, role-specific cover letters (180-220 words). " +
        "Match tone to seniority. No clichés. No 'I am writing to apply'. " +
        "Output the letter only — no salutation placeholders, no signature."

    suspend fun build(
        resume: Resume,
        match: MatchResult,
        model: String = "gemini-2.5-flash",
    ): ApplyPacket {
        val prompt = buildString {
            appendLine("Candidate: ${resume.fullName}")
            appendLine("Resume excerpt:")
            appendLine(resume.rawText.take(3_000))
            appendLine()
            appendLine("Job: ${match.job.title} at ${match.job.company}")
            appendLine("Description:")
            appendLine(match.job.description.take(2_000))
            appendLine()
            appendLine("Why this is a fit (per scoring): ${match.fitReasoning}")
            appendLine("Skills overlap: ${match.matchedSkills.joinToString()}")
            appendLine()
            appendLine("Write the cover letter.")
        }

        val cover = gemini.generate(
            prompt = prompt,
            systemPrompt = systemPrompt,
            model = model,
            temperature = 0.4f,
        ).trim()

        return ApplyPacket(
            matchId = match.id,
            coverLetter = cover,
            fieldHints = buildHints(resume),
        )
    }

    private fun buildHints(r: Resume): List<FieldHint> = listOfNotNull(
        FieldHint("First name", r.firstName),
        FieldHint("Last name",  r.lastName),
        FieldHint("Email",      r.email, "email"),
        r.phone?.let { FieldHint("Phone", it) },
        r.linkedinUrl?.let { FieldHint("LinkedIn", it, "url") },
        r.githubUrl?.let  { FieldHint("GitHub",   it, "url") },
        r.portfolioUrl?.let { FieldHint("Portfolio", it, "url") },
    ).filter { it.value.isNotBlank() }
}
