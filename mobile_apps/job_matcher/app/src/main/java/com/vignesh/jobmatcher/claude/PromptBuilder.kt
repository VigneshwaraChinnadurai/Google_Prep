package com.vignesh.jobmatcher.claude

import com.vignesh.jobmatcher.model.AppSettings
import com.vignesh.jobmatcher.model.CandidateProfile
import com.vignesh.jobmatcher.model.Company
import com.vignesh.jobmatcher.model.Job

/**
 * Builds the prompts for the "Claude (manual)" workflow: the app copies a prompt to the
 * clipboard and hands it to the Claude app, the user copies Claude's reply back, and
 * ClaudeResponseParser reads the tagged block out of it. No API key involved.
 *
 * Every prompt starts with PROMPT_MARKER so the parser can recognise -- and reject with a
 * clear message -- the prompt itself being pasted back instead of Claude's reply (its
 * format examples would otherwise parse as a "valid" empty answer).
 */
object PromptBuilder {
    const val PROMPT_MARKER = "### JOB-MATCHER PROMPT"

    enum class Kind(val tag: String) {
        PROFILE("profile_json"),
        SCORING("job_scores"),
        SEARCH("jobs_json"),
        TAILORING("match_summary")
    }

    private fun header(kind: Kind) = "$PROMPT_MARKER [${kind.name}] ###"

    private fun profileBlock(profile: CandidateProfile): String = buildString {
        appendLine("Headline: ${profile.headline}")
        appendLine("Years of experience: ${profile.yearsExperience}")
        appendLine("Target titles: ${profile.targetTitles.joinToString()}")
        appendLine("Core skills: ${profile.skills.filter { it.weight >= 3 }.joinToString { it.name }}")
        appendLine("Solid skills: ${profile.skills.filter { it.weight == 2 }.joinToString { it.name }}")
        append("Familiar: ${profile.skills.filter { it.weight <= 1 }.joinToString { it.name }}")
    }

    private fun resumeBlock(resume: String) = "<resume>\n${resume.trim()}\n</resume>"

    // ---- 1. Profile analysis -------------------------------------------------------

    fun profilePrompt(resume: String): String = """
${header(Kind.PROFILE)}
You are an expert technical recruiter. Analyse the resume below and produce a structured
candidate profile that an on-device keyword matcher will use to pre-filter job postings.

${resumeBlock(resume)}

Rules:
- "skills": 25-40 entries. weight 3 = core strength demonstrated in recent production work,
  2 = solid hands-on experience, 1 = familiar. "aliases" = other spellings/terms recruiters
  use in job descriptions for the same skill (lowercase, specific -- avoid generic English
  words like "attention" or "architecture" that would match unrelated postings).
- "target_titles": 12-20 lowercase job-title phrases this person is realistically
  competitive for at their current seniority (not junior roles).
- "search_terms": 4-8 short keyword queries for careers-site search boxes.
- "years_experience": integer total professional years.

Reply with ONLY this block, valid JSON inside, nothing before or after:
<profile_json>
{"headline": "...", "years_experience": 0, "target_titles": ["..."],
 "skills": [{"name": "...", "aliases": ["..."], "weight": 3}],
 "search_terms": ["..."]}
</profile_json>
""".trim()

    // ---- 2. Batch scoring ----------------------------------------------------------

    fun scoringPrompt(
        resume: String,
        profile: CandidateProfile,
        jobs: List<Job>,
        settings: AppSettings
    ): String = buildString {
        appendLine(header(Kind.SCORING))
        appendLine(
            """
You are a strict, calibrated hiring-manager screener. Score how well the candidate matches
each job posting below, from 0 to 100. Be honest -- do not inflate. The candidate only wants
to see roles scoring ${settings.matchThreshold}+, so the threshold must mean something.

Scoring rubric (sum to 100):
- 40  Core skills & domain overlap (the posting's must-have technical requirements)
- 25  Seniority & scope fit (years required, IC vs. manager/architect level, team scope)
- 20  Hard requirements met (degree e.g. PhD, specific languages, clearance, domain licences)
- 15  Career-trajectory fit (does this role move the candidate forward from their current level)

Calibration: 90-100 apply today, meets every must-have; 80-89 strong, minor gaps;
65-79 partial, notable gaps; below 65 poor fit. A role requiring a PhD or 4+ years more
experience than the candidate has must score below 70.
""".trim()
        )
        appendLine()
        appendLine("CANDIDATE PROFILE")
        appendLine(profileBlock(profile))
        if (settings.includeResumeInScoring) {
            appendLine()
            appendLine(resumeBlock(resume))
        }
        appendLine()
        appendLine("JOB POSTINGS (${jobs.size})")
        jobs.forEach { job ->
            appendLine()
            appendLine("<job id=\"${job.id}\">")
            appendLine("Company: ${job.companyName}")
            appendLine("Title: ${job.title}")
            appendLine("Location: ${job.location}")
            appendLine("Description:")
            appendLine(job.description.take(settings.maxDescriptionChars).trim())
            appendLine("</job>")
        }
        appendLine()
        appendLine(
            """
Reply with ONLY this block -- a JSON array with exactly one object per job, using each
job's exact id attribute, nothing before or after:
<job_scores>
[{"id": "<exact job id>", "score": 0, "verdict": "Strong match | Good match | Partial | Poor",
  "reasons": "2-3 sentences: the decisive factors", "matched_skills": ["..."], "gaps": ["..."]}]
</job_scores>
""".trim()
        )
    }

    // ---- 3. Claude web search (companies without a careers API) --------------------

    fun searchPrompt(
        resume: String,
        profile: CandidateProfile,
        companies: List<Company>,
        alreadyKnown: List<Job>,
        settings: AppSettings
    ): String = buildString {
        appendLine(header(Kind.SEARCH))
        appendLine(
            """
Use web search to find CURRENTLY OPEN job postings at the companies below that fit this
candidate, located in: ${settings.locationKeywords.joinToString()}.

Strict rules:
- Only postings on the company's OFFICIAL careers site (not LinkedIn/Naukri/Indeed or other
  aggregators), and only ones you actually found open today. Never invent or guess a URL --
  if you can't find a direct posting URL, leave the job out.
- Skip junior/intern/new-grad roles and roles outside the listed locations.
- Return at most 15 roles per company, best matches first, and only roles scoring 60+.
- Score each one 0-100 with this rubric: 40 core skills & domain, 25 seniority & scope,
  20 hard requirements (PhD, languages, etc.), 15 career-trajectory fit. Be strict:
  80+ means a strong, apply-today match.
""".trim()
        )
        appendLine()
        appendLine("COMPANIES")
        companies.forEach { c ->
            appendLine("- ${c.name}" + if (c.identifier.isNotBlank()) " (careers site: ${c.identifier})" else "")
        }
        if (alreadyKnown.isNotEmpty()) {
            appendLine()
            appendLine("Already tracked -- do NOT return these again:")
            alreadyKnown.take(60).forEach { appendLine("- ${it.companyName}: ${it.title} (${it.url})") }
        }
        appendLine()
        appendLine("CANDIDATE PROFILE")
        appendLine(profileBlock(profile))
        appendLine()
        appendLine(resumeBlock(resume))
        appendLine()
        appendLine(
            """
Reply with ONLY this block, valid JSON inside, nothing before or after:
<jobs_json>
[{"company": "...", "title": "...", "location": "...", "url": "https://...",
  "posted_date": "YYYY-MM-DD or empty", "description": "120-250 words: team, key responsibilities, must-have requirements",
  "score": 0, "verdict": "Strong match | Good match | Partial", "reasons": "2-3 sentences",
  "matched_skills": ["..."], "gaps": ["..."]}]
</jobs_json>
""".trim()
        )
    }

    // ---- 4. Per-job tailoring ------------------------------------------------------

    fun tailoringPrompt(resume: String, job: Job): String = """
${header(Kind.TAILORING)}
You are an expert career coach for senior AI/ML candidates. Using ONLY facts from the
resume (never invent experience, numbers, employers or skills), prepare application
material for the job below.

${resumeBlock(resume)}

<job>
Company: ${job.companyName}
Title: ${job.title}
Location: ${job.location}
URL: ${job.url}
Description:
${job.description.trim()}
</job>

Reply with ONLY these five tagged sections, in this order:
<match_summary>3-5 sentences: why this candidate fits, and the 1-2 biggest risks a screener will see.</match_summary>
<resume_bullets>6-8 rewritten resume bullets that mirror this posting's language and lead with the most relevant achievements (keep every metric truthful).</resume_bullets>
<cover_letter>A concise cover letter (under 250 words), specific to this company and role.</cover_letter>
<gap_plan>Each gap vs. the posting, with how to address it in the interview or a 1-2 week prep action.</gap_plan>
<referral_message>A short LinkedIn message (under 80 words) asking an employee at ${job.companyName} for a referral to this role.</referral_message>
""".trim()
}
