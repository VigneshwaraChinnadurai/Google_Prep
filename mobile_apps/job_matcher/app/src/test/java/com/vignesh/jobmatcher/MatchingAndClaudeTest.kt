package com.vignesh.jobmatcher

import com.vignesh.jobmatcher.claude.ClaudeResponseParser
import com.vignesh.jobmatcher.claude.PromptBuilder
import com.vignesh.jobmatcher.data.JsonCodec
import com.vignesh.jobmatcher.matching.LocalScorer
import com.vignesh.jobmatcher.model.AppSettings
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.SourceType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MatchingAndClaudeTest {

    private val profile = JsonCodec.profileFromJson(
        JSONObject(File("src/main/assets/default_profile.json").readText())
    )
    private val scorer = LocalScorer(profile, AppSettings())

    @Test
    fun strongGenAiRole_clearsPrefilter() {
        val s = scorer.score(
            "Senior Machine Learning Engineer, GenAI",
            "Build LLM-powered agentic systems with RAG, vector databases and PyTorch on AWS. " +
                "Fine-tuning with LoRA, evals, MLOps. 7+ years of experience in machine learning and Python."
        )
        assertTrue("score=${s.score}", s.score >= 80)
        assertTrue(s.matchedSkills.contains("RAG"))
    }

    @Test
    fun genericBackendRole_isGatedBelowPrefilter() {
        val s = scorer.score(
            "Senior Backend Engineer",
            "Java microservices, SQL, Docker, Kubernetes and AWS. 6+ years building distributed systems."
        )
        assertTrue("score=${s.score}", s.score < AppSettings().prefilterThreshold)
    }

    @Test
    fun overQualifiedRequirement_lowersSeniorityFit() {
        val fits = scorer.score("Staff ML Engineer", "LLM RAG machine learning. 8+ years required.").score
        val stretch = scorer.score("Staff ML Engineer", "LLM RAG machine learning. 15+ years required.").score
        assertTrue("$fits vs $stretch", fits > stretch)
    }

    @Test
    fun filters_locationAndTitle() {
        assertTrue(scorer.isLocationAllowed("IN, KA, Bengaluru"))
        assertFalse(scorer.isLocationAllowed("US, CA, Santa Clara"))
        assertFalse(scorer.isTitleAllowed("Machine Learning Intern"))
        assertTrue(scorer.isTitleAllowed("Internal Tools ML Engineer")) // whole-word: "intern" ≠ "internal"
    }

    @Test
    fun scores_parseThroughFencesAndProse() {
        val reply = """Here are the scores:
            <job_scores>
            ```json
            [{"id":"GREENHOUSE:anthropic:1","score":86.0,"verdict":"Strong match","reasons":"r","matched_skills":["RAG"],"gaps":[]},
             {"id":"LEVER:x:2","score":55,"verdict":"Poor","reasons":"r2","matched_skills":[],"gaps":["Go"]}]
            ```
            </job_scores>
            Let me know if you need more."""
        val scores = ClaudeResponseParser.parseScores(reply).getOrThrow()
        assertEquals(2, scores.size)
        assertEquals(86, scores[0].score)
        assertEquals(listOf("Go"), scores[1].gaps)
    }

    @Test
    fun pastingThePromptBack_isRejectedClearly() {
        val job = Job(id = "a:b:c", companyId = "b", companyName = "B", title = "T", location = "India",
            url = "https://x", description = "d", source = SourceType.LEVER)
        val prompt = PromptBuilder.scoringPrompt("resume", profile, listOf(job), AppSettings())
        val err = ClaudeResponseParser.parseScores(prompt).exceptionOrNull()
        assertTrue(err?.message.orEmpty().contains("prompt you copied"))
    }

    @Test
    fun foundJobs_dropEntriesWithoutRealUrls() {
        val reply = """<jobs_json>[
            {"company":"Google","title":"ML Engineer","location":"Bengaluru","url":"https://www.google.com/about/careers/applications/jobs/results/1",
             "description":"d","score":88,"verdict":"Strong","reasons":"r","matched_skills":[],"gaps":[]},
            {"company":"Google","title":"No URL","location":"Bengaluru","url":"","score":90}]</jobs_json>"""
        val found = ClaudeResponseParser.parseFoundJobs(reply).getOrThrow()
        assertEquals(1, found.size)
        assertEquals(88, found[0].score?.score)
    }

    @Test
    fun tailoring_toleratesMissingFinalCloseTag() {
        val reply = "<match_summary>Fit</match_summary><resume_bullets>- a</resume_bullets>" +
            "<cover_letter>Dear</cover_letter><gap_plan>Go</gap_plan><referral_message>Hi there"
        val t = ClaudeResponseParser.parseTailoring(reply).getOrThrow()
        assertEquals("Hi there", t.referralMessage)
        assertEquals("Fit", t.summary)
    }

    @Test
    fun profile_parsesStringOrObjectSkills() {
        val reply = """<profile_json>{"headline":"h","years_experience":8,"target_titles":["ml engineer"],
            "skills":["Python",{"name":"RAG","aliases":["retrieval"],"weight":5}],"search_terms":["llm"]}</profile_json>"""
        val p = ClaudeResponseParser.parseProfile(reply).getOrThrow()
        assertEquals(2, p.skills.size)
        assertEquals(3, p.skills[1].weight) // clamped
        assertEquals("claude", p.origin)
    }
}
