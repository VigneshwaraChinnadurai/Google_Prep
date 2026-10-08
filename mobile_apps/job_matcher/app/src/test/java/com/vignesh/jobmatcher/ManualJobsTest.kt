package com.vignesh.jobmatcher

import com.vignesh.jobmatcher.claude.ClaudeResponseParser
import com.vignesh.jobmatcher.claude.PromptBuilder
import com.vignesh.jobmatcher.data.JsonCodec
import com.vignesh.jobmatcher.matching.LocalScorer
import com.vignesh.jobmatcher.model.AppSettings
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobOrigin
import com.vignesh.jobmatcher.model.SourceType
import com.vignesh.jobmatcher.sources.JobLink
import com.vignesh.jobmatcher.sources.ManualJobImporter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Search terms, links added by hand, and the Auto/Manual tag. */
class ManualJobsTest {

    private val profile = JsonCodec.profileFromJson(JSONObject(File("src/main/assets/default_profile.json").readText()))

    // ---- Search terms ------------------------------------------------------------------

    @Test
    fun searchTerms_matchTitleVariants() {
        val scorer = LocalScorer(profile, AppSettings(searchTerms = listOf("Gen AI Engineer", "AI Architect", "Software Engineer 3")))
        assertTrue(scorer.matchesSearchTerm("Software Development Engineer III, Prime Video"))
        assertTrue(scorer.matchesSearchTerm("Senior Generative AI Engineer"))
        assertTrue(scorer.matchesSearchTerm("Principal Architect - AI Platform"))
        assertFalse(scorer.matchesSearchTerm("Software Engineer II"))
        assertFalse(scorer.matchesSearchTerm("Solutions Architect, Storage"))
    }

    @Test
    fun searchTerm_titleCountsAsTargetTitle() {
        val jd = "Build services on AWS with Python and SQL. Some machine learning exposure. 6+ years."
        val without = LocalScorer(profile, AppSettings(searchTerms = emptyList())).score("Software Development Engineer III", jd).score
        val with = LocalScorer(profile, AppSettings(searchTerms = listOf("Software Engineer 3"))).score("Software Development Engineer III", jd).score
        assertTrue("$without -> $with", with > without)
        assertTrue("with=$with", with >= AppSettings().prefilterThreshold)
    }

    @Test
    fun settings_searchTermsRoundTripKeepingCase_andDefaultForOldInstalls() {
        val s = AppSettings(searchTerms = listOf("Gen AI Engineer", "Software Engineer 3"))
        assertEquals(s.searchTerms, JsonCodec.settingsFromJson(JsonCodec.settingsToJson(s)).searchTerms)
        // Settings saved before this field existed fall back to the defaults.
        assertEquals(AppSettings.DEFAULT_SEARCH_TERMS, JsonCodec.settingsFromJson(JSONObject().put("matchThreshold", 80)).searchTerms)
    }

    // ---- Link detection ----------------------------------------------------------------

    @Test
    fun extractUrl_fromSharedText() {
        assertEquals(
            "https://www.linkedin.com/jobs/view/4474679765",
            JobLink.extractUrl("Check out this job at Sequoia: https://www.linkedin.com/jobs/view/4474679765.")
        )
        assertNull(JobLink.extractUrl("no link here"))
    }

    @Test
    fun detect_knownCareersSystems() {
        assertTrue(JobLink.detect("https://job-boards.greenhouse.io/anthropic/jobs/4461450008") is JobLink.Greenhouse)
        assertTrue(JobLink.detect("https://jobs.lever.co/palantir/6ed76ce8-4156-4b60-b120-403538bd66cd") is JobLink.Lever)
        assertTrue(JobLink.detect("https://jobs.ashbyhq.com/openai/8fb1615c-34bf-47c4-a1d1-b7b2f836bbd3") is JobLink.Ashby)

        val wd = JobLink.detect("https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/job/India-Bengaluru/MLE_JR1/apply")
        assertTrue(wd is JobLink.Workday)
        assertEquals("/job/India-Bengaluru/MLE_JR1", (wd as JobLink.Workday).externalPath)

        val ms = JobLink.detect("https://apply.careers.microsoft.com/careers/job/1970393556991771")
        assertTrue(ms is JobLink.Eightfold)
        assertEquals("microsoft.com", (ms as JobLink.Eightfold).target.domain)

        val sr = JobLink.detect("https://jobs.smartrecruiters.com/Freshworks/744000154351019-staff-engineer-full-stack")
        assertEquals("744000154351019", (sr as JobLink.SmartRecruiters).id)

        val jp = JobLink.detect("https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001/job/210767444")
        assertEquals("210767444", (jp as JobLink.Oracle).id)

        val li = JobLink.detect("https://in.linkedin.com/jobs/view/lead-machine-learning-engineer-at-sequoia-4474679765?trk=x")
        assertEquals("https://www.linkedin.com/jobs/view/4474679765", (li as JobLink.LinkedIn).url)
        val liSearch = JobLink.detect("https://www.linkedin.com/jobs/collections/recommended/?currentJobId=4474679765")
        assertTrue(liSearch is JobLink.LinkedIn)

        assertTrue(JobLink.detect("https://www.naukri.com/job-listings-ml-engineer-123") is JobLink.Generic)
    }

    // ---- HTML extraction ---------------------------------------------------------------

    @Test
    fun parseHtml_linkedInPublicPage() {
        val html = """<html><body>
            <h1 class="top-card-layout__title topcard__title">Lead Machine Learning Engineer</h1>
            <a class="topcard__org-name-link topcard__flavor--black-link" href="#">Sequoia</a>
            <span class="topcard__flavor topcard__flavor--bullet"> Bangalore Urban, Karnataka, India </span>
            <span class="posted-time-ago__text">1 day ago</span>
            <div class="show-more-less-html__markup"><p>Role Overview: build LLM platforms.</p><ul><li>Python</li><li>RAG</li></ul></div>
            <li class="description__job-criteria-item"><h3 class="description__job-criteria-subheader">Seniority level</h3>
              <span class="description__job-criteria-text">Mid-Senior level</span></li>
            </body></html>"""
        val job = ManualJobImporter.parseJobHtml(html, "https://www.linkedin.com/jobs/view/4474679765")!!
        assertEquals("Lead Machine Learning Engineer", job.posting.title)
        assertEquals("Sequoia", job.company)
        assertEquals("Bangalore Urban, Karnataka, India", job.posting.location)
        assertTrue(job.posting.description, job.posting.description.contains("• RAG"))
        assertTrue(job.posting.description.contains("Seniority level: Mid-Senior level"))
    }

    @Test
    fun parseHtml_jsonLdJobPosting_inGraph() {
        val desc = "<p>" + "Design GenAI agents and RAG pipelines. ".repeat(12) + "</p>"
        val html = """<html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[{"@type":"Organization","name":"X"},
             {"@type":"JobPosting","title":"AI Architect","datePosted":"2026-10-01T00:00:00Z",
              "hiringOrganization":{"@type":"Organization","name":"Acme AI"},
              "jobLocation":[{"@type":"Place","address":{"addressLocality":"Bengaluru","addressRegion":"KA","addressCountry":"IN"}}],
              "description":"${desc.replace("\"", "\\\"")}"}]}
            </script></head><body></body></html>"""
        val job = ManualJobImporter.parseJobHtml(html, "https://acme.ai/careers/123")!!
        assertEquals("AI Architect", job.posting.title)
        assertEquals("Acme AI", job.company)
        assertEquals("Bengaluru, KA, IN", job.posting.location)
        assertEquals("2026-10-01", job.posting.postedAt)
        assertTrue(job.complete)
    }

    @Test
    fun parseHtml_metaOnly_isIncomplete() {
        val html = """<html><head><title>ML Engineer - Naukri</title>
            <meta property="og:description" content="ML Engineer role in Bengaluru"></head><body></body></html>"""
        val job = ManualJobImporter.parseJobHtml(html, "https://www.naukri.com/x")!!
        assertEquals("ML Engineer - Naukri", job.posting.title)
        assertFalse(job.complete)
    }

    // ---- Claude reads the link ---------------------------------------------------------

    @Test
    fun readJobPrompt_andReply() {
        val prompt = PromptBuilder.readJobPrompt("https://www.naukri.com/x", "resume", profile, "ML Engineer")
        assertTrue(prompt.contains("https://www.naukri.com/x"))
        assertTrue(ClaudeResponseParser.parseJobDetails(prompt).exceptionOrNull()?.message.orEmpty().contains("prompt you copied"))

        val reply = """<job_details>{"company":"Acme","title":"ML Engineer","location":"Bengaluru","posted_date":"",
            "description":"Build models","score":84,"verdict":"Good match","reasons":"r","matched_skills":["RAG"],"gaps":[]}</job_details>"""
        val f = ClaudeResponseParser.parseJobDetails(reply).getOrThrow()
        assertEquals("ML Engineer", f.title)
        assertEquals(84, f.score?.score)

        val couldNotOpen = """<job_details>{"company":"","title":"ML Engineer","location":"","description":"I could not open the page.","score":null}</job_details>"""
        assertNull(ClaudeResponseParser.parseJobDetails(couldNotOpen).getOrThrow().score)
    }

    // ---- Auto / Manual tag -------------------------------------------------------------

    @Test
    fun jobOrigin_roundTrips_andOldJobsAreAutomatic() {
        val manual = Job(id = "MANUAL_LINK:x:1", companyId = "x", companyName = "X", title = "T", location = "",
            url = "https://x", description = "", source = SourceType.MANUAL_LINK, origin = JobOrigin.MANUAL)
        val back = JsonCodec.jobFromJson(JsonCodec.jobToJson(manual))
        assertEquals(JobOrigin.MANUAL, back.origin)
        assertTrue(back.needsDetails)

        val old = JsonCodec.jobToJson(manual).apply { remove("origin") }
        assertEquals(JobOrigin.AUTOMATIC, JsonCodec.jobFromJson(old).origin)
    }
}
