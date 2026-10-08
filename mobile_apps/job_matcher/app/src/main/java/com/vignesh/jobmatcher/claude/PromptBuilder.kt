package com.vignesh.jobmatcher.claude

import com.vignesh.jobmatcher.data.ContactInfo
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
        TAILORING("match_summary"),
        READ_JOB("job_details"),
        CONTACTS("contacts")
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
        val topChoiceSolo = companies.size == 1 && companies.single().priority == 1
        val perCompany = if (topChoiceSolo) 25 else 15
        appendLine(header(Kind.SEARCH))
        appendLine(
            """
Use web search to find CURRENTLY OPEN job postings at the companies below that fit this
candidate, located in: ${settings.locationKeywords.joinToString()}.
""".trim()
        )
        if (settings.searchTerms.isNotEmpty()) {
            appendLine("Roles the candidate is targeting (search for each): ${settings.searchTerms.joinToString()}.")
        }
        if (topChoiceSolo) {
            appendLine(
                "This is the candidate's #1 target company -- search its careers site thoroughly " +
                    "(several queries: ML, applied AI, GenAI/LLM, data science, AI architecture, ML management)."
            )
        }
        appendLine()
        appendLine(
            """
Strict rules:
- Only postings on the company's OFFICIAL careers site -- not third-party job boards such as
  Naukri, Indeed, Glassdoor or other companies' LinkedIn pages -- and only ones you actually
  found open today. Never invent or guess a URL; if you can't find a direct posting URL,
  leave the job out.
- Skip junior/intern/new-grad roles and roles outside the listed locations.
- Return at most $perCompany roles per company, best matches first, and only roles scoring 60+.
- Score each one 0-100 with this rubric: 40 core skills & domain, 25 seniority & scope,
  20 hard requirements (PhD, languages, etc.), 15 career-trajectory fit. Be strict:
  80+ means a strong, apply-today match.
- If a company has nothing suitable open, that's a valid answer -- return fewer (or zero) jobs.
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

    // ---- 4. Read a job from a link the user added -----------------------------------

    fun readJobPrompt(
        url: String,
        resume: String,
        profile: CandidateProfile,
        partialText: String
    ): String = buildString {
        appendLine(header(Kind.READ_JOB))
        appendLine(
            """
Open this job posting and read it in full: $url

The app could not read the whole posting itself (login wall or JavaScript-only page).
If you also cannot open it, say so in "description" and set "score" to null -- do NOT guess
the job's content from its title or URL.
""".trim()
        )
        if (partialText.isNotBlank()) {
            appendLine()
            appendLine("What the app could read so far:")
            appendLine(partialText.take(1500))
        }
        appendLine()
        appendLine("Then score how well the candidate below matches it, 0-100 with this rubric: 40 core skills")
        appendLine("& domain, 25 seniority & scope, 20 hard requirements (PhD, languages, etc.), 15 career-")
        appendLine("trajectory fit. Be strict: 80+ means a strong, apply-today match.")
        appendLine()
        appendLine("CANDIDATE PROFILE")
        appendLine(profileBlock(profile))
        appendLine()
        appendLine(resumeBlock(resume))
        appendLine()
        appendLine(
            """
Reply with ONLY this block, valid JSON inside, nothing before or after:
<job_details>
{"company": "...", "title": "...", "location": "...", "posted_date": "YYYY-MM-DD or empty",
 "description": "the full job description: responsibilities, requirements, qualifications (up to ~600 words)",
 "score": 0, "verdict": "Strong match | Good match | Partial | Poor", "reasons": "2-3 sentences",
 "matched_skills": ["..."], "gaps": ["..."]}
</job_details>
""".trim()
        )
    }

    // ---- 6. Find people to contact on LinkedIn -------------------------------------

    fun findContactsPrompt(job: Job, locations: List<String>): String = """
${header(Kind.CONTACTS)}
Use web search to find people on LinkedIn whom a candidate applying to this job could
professionally contact about it:

<job>
Company: ${job.companyName}
Title: ${job.title}
Location: ${job.location}
Posting: ${job.url}
</job>

Look for, in this order:
1. Recruiters / talent-acquisition partners at ${job.companyName} who recruit for engineering,
   machine-learning, AI or data roles, preferably based in ${locations.take(4).joinToString()}.
2. The likely hiring manager or team lead for this team, if the posting or public information
   identifies the team.
3. One to three employees in similar roles in that team or org who could give a referral.

Strict rules:
- Only people you actually found, each with their public LinkedIn profile URL
  (https://www.linkedin.com/in/...). Never guess, construct or shorten a URL; if you can't find
  the profile URL, leave the person out.
- Prefer people whose CURRENT employer is ${job.companyName}; skip anyone who has left.
- No personal email addresses or phone numbers -- LinkedIn profile URLs only.
- Up to 8 people, most useful first, with one short line on why each is relevant.
- If you find nobody suitable, return an empty list.

Reply with ONLY this block, valid JSON inside, nothing before or after:
<contacts>
[{"name": "...", "title": "their current title", "type": "recruiter | hiring_manager | employee",
  "profile_url": "https://www.linkedin.com/in/...", "why": "one line"}]
</contacts>
""".trim()

    // ---- 5. Per-job tailoring ------------------------------------------------------

    /**
     * The application kit for one shortlisted job. Gaps are handled honestly: the cover
     * letter names the most material ones with concrete steps and a realistic timeline,
     * using the calibration below so the timeline is believable rather than optimistic.
     */
    fun tailoringPrompt(resume: String, job: Job, contact: ContactInfo): String = """
${header(Kind.TAILORING)}
You are an expert career coach for senior AI/ML candidates. Using ONLY facts from the resume
(never invent experience, numbers, employers, skills or certifications), prepare a complete
application kit for the job below.

${resumeBlock(resume)}

<job>
Company: ${job.companyName}
Title: ${job.title}
Location: ${job.location}
URL: ${job.url}
Description:
${job.description.trim()}
</job>

CANDIDATE CONTACT (for sign-offs): ${contact.name} | ${contact.contactLine}

HOW TO HANDLE GAPS (applies to the gap plan AND the cover letter)
- Be honest. Name a gap plainly; never claim it is already closed.
- Give concrete mitigation steps (a specific course, project, reading, or applying an adjacent
  skill the candidate already has) and a GENUINE, REASONABLE timeline judged from the
  candidate's actual starting point in the resume:
    * new tool/library/framework adjacent to what they already use: 1-3 weeks
    * new programming language or cloud platform: 1-3 months to working proficiency
    * new domain or deep specialisation (e.g. a research area): 3-6 months
    * degrees, a PhD, or years of experience cannot be closed quickly -- say so honestly and
      offer the closest real substitute from the resume (equivalent production work, scale,
      leadership), not a timeline.
- If a gap is a big task, it is fine -- and better -- to say politely that it will take some
  time, while making clear the candidate is committed to it and would genuinely enjoy
  working on it.

Reply with ONLY these six tagged sections, in this order, plain text inside each (no markdown
headings, no code fences):

<match_summary>
3-5 sentences: why this candidate fits this specific role (the 2-3 strongest proof points),
and the 1-2 biggest doubts a screener will have.
</match_summary>

<resume_bullets>
6-8 resume bullets rewritten to mirror this posting's language and priorities, leading with the
most relevant achievements. Keep every metric, employer and technology truthful. One bullet per
line, each starting with "• ".
</resume_bullets>

<cover_letter>
A formal cover letter of 250-350 words, ready to send:
- Open with "Dear Hiring Team at ${job.companyName}," (or a named hiring manager if the posting names one).
- Paragraph 1: the exact role and why this company and role specifically.
- Paragraphs 2-3: the most relevant achievements, with real metrics from the resume.
- One short, honest paragraph on the 1-2 most material gaps: acknowledge each, state the
  concrete steps being taken, and a realistic timeline per the rules above (if it's a big
  task, say politely that it will take some time but that the candidate is eager to work on it).
- Close with thanks and availability, then sign off exactly:
Sincerely,
${contact.name}
Do NOT include a letterhead, address block or date (the app adds them), and no placeholders
like [Company Address].
</cover_letter>

<gap_plan>
For each real gap vs. the posting (most important first), four short lines:
Gap: ...
Why it matters for this role: ...
Mitigation: concrete steps (specific courses/projects/resources)
Timeline: realistic estimate per the rules above (or "not short-term" with the substitute)
Interview line: one honest sentence to say when asked about it
</gap_plan>

<connection_note>
A LinkedIn connection-request note, at most 280 characters (LinkedIn's limit is 300). Formal,
no emojis. Mention the exact role title and that the candidate has applied or is applying; add
the job link only if it still fits within 280 characters. Start with "Dear {Name}," -- keep
{Name} literally, the app fills it per contact.
</connection_note>

<referral_message>
A very formal LinkedIn message / InMail / email (130-180 words) to a recruiter or employee:
- Start with "Dear {Name}," -- keep {Name} literally, the app fills it per contact.
- One sentence on who the candidate is (current role, years of experience).
- The exact role title, the job ID if the posting shows one, and the job posting link: ${job.url}
- The two most relevant qualifications for this role.
- A courteous ask: consideration for the role, a referral, or a brief 15-minute conversation.
- State that the resume and cover letter are attached.
- Thank them, then sign off exactly:
Best regards,
${contact.name}
${contact.contactLine}
No emojis, no flattery, no pressure.
</referral_message>
""".trim()
}
