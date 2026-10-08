package com.vignesh.jobmatcher

import com.vignesh.jobmatcher.sources.JobParsers
import com.vignesh.jobmatcher.sources.WorkdayFacetChoice
import com.vignesh.jobmatcher.sources.WorkdayTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures mirror the live response shapes probed on 2026-10-08. */
class JobParsersTest {

    @Test
    fun greenhouse_unescapesDoubleEncodedHtml_andJoinsOffices() {
        val json = """{"jobs":[{"id":4461450008,"title":"Applied AI Engineer",
            "absolute_url":"https://job-boards.greenhouse.io/anthropic/jobs/4461450008",
            "location":{"name":"Bengaluru"},"offices":[{"name":"India"}],
            "updated_at":"2026-08-21T21:32:54-04:00",
            "content":"&lt;p&gt;Build &lt;strong&gt;LLM&lt;/strong&gt; agents&lt;/p&gt;&lt;ul&gt;&lt;li&gt;RAG&lt;/li&gt;&lt;/ul&gt;"}]}"""
        val p = JobParsers.parseGreenhouse(json).single()
        assertEquals("4461450008", p.externalId)
        assertEquals("Bengaluru; India", p.location)
        assertEquals("2026-08-21", p.postedAt)
        assertTrue(p.description, p.description.contains("Build LLM agents"))
        assertTrue(p.description, p.description.contains("• RAG"))
    }

    @Test
    fun lever_mapsCountryCodeAndLists() {
        val json = """[{"id":"abc","text":"ML Engineer","hostedUrl":"https://jobs.lever.co/x/abc",
            "categories":{"location":"Bangalore","allLocations":["Bangalore","Hyderabad"]},
            "country":"IN","workplaceType":"hybrid","descriptionPlain":"About the role",
            "lists":[{"text":"Requirements","content":"<li>Python</li><li>PyTorch</li>"}],
            "createdAt":1786469891368}]"""
        val p = JobParsers.parseLever(json).single()
        assertEquals("Bangalore; Hyderabad; India", p.location)
        assertTrue(p.description.contains("Requirements"))
        assertTrue(p.description.contains("• PyTorch"))
        assertEquals(10, p.postedAt.length)
    }

    @Test
    fun ashby_skipsUnlisted_andAddsCountry() {
        val json = """{"jobs":[
            {"id":"1","title":"A","location":"Bengaluru","secondaryLocations":[],"isListed":true,
             "address":{"postalAddress":{"addressCountry":"India"}},"jobUrl":"https://jobs.ashbyhq.com/o/1",
             "descriptionPlain":"desc","publishedAt":"2026-03-12T16:38:15.322+00:00"},
            {"id":"2","title":"B","location":"SF","isListed":false,"jobUrl":"u","descriptionPlain":"d"}]}"""
        val p = JobParsers.parseAshby(json).single()
        assertEquals("Bengaluru; India", p.location)
        assertEquals("2026-03-12", p.postedAt)
    }

    @Test
    fun workday_listAndDetail() {
        val (rows, total) = JobParsers.parseWorkdayList(
            """{"total":547,"jobPostings":[{"title":"MLE","externalPath":"/job/India-Bengaluru/MLE_JR1","locationsText":"India, Bengaluru","postedOn":"Posted Today"}]}"""
        )
        assertEquals(547, total)
        assertEquals("/job/India-Bengaluru/MLE_JR1", rows.single().externalPath)

        val detail = JobParsers.parseWorkdayDetail(
            """{"jobPostingInfo":{"title":"MLE","jobDescription":"<p>LLM work</p>","location":"India, Bengaluru",
               "additionalLocations":["India, Hyderabad"],"jobReqId":"JR1","startDate":"2026-10-07",
               "externalUrl":"https://nvidia.wd5.myworkdayjobs.com/site/job/x"}}""",
            fallbackUrl = "fallback"
        )!!
        assertEquals("JR1", detail.externalId)
        assertEquals("India, Bengaluru; India, Hyderabad", detail.location)
        assertEquals("LLM work", detail.description)
    }

    @Test
    fun workdayFacet_prefersCountryLevel_andIgnoresIndiana() {
        val nvidiaLike = """{"facets":[{"facetParameter":"jobFamilyGroup","values":[{"descriptor":"Engineering","id":"e"}]},
            {"facetParameter":"locationMainGroup","values":[
              {"facetParameter":"locationHierarchy1","values":[{"descriptor":"India","id":"IN"},{"descriptor":"United States","id":"US"}]},
              {"facetParameter":"locations","values":[{"descriptor":"India, Bengaluru","id":"BLR"},{"descriptor":"Indiana - Indianapolis","id":"IND"}]}]}]}"""
        val keywords = listOf("india", "bengaluru")
        assertEquals(WorkdayFacetChoice("locationHierarchy1", listOf("IN")), JobParsers.parseWorkdayLocationFacet(nvidiaLike, keywords))

        val cityOnly = """{"facets":[{"facetParameter":"locations","values":[{"descriptor":"India, Bengaluru","id":"BLR"},{"descriptor":"Indiana - Indianapolis","id":"IND"}]}]}"""
        assertEquals(WorkdayFacetChoice("locations", listOf("BLR")), JobParsers.parseWorkdayLocationFacet(cityOnly, keywords))

        val noMatch = """{"facets":[{"facetParameter":"locations","values":[{"descriptor":"Indiana - Indianapolis","id":"IND"}]}]}"""
        assertEquals(emptyList<String>(), JobParsers.parseWorkdayLocationFacet(noMatch, keywords)!!.ids)
        assertNull(JobParsers.parseWorkdayLocationFacet("""{"facets":[]}""", keywords))
    }

    @Test
    fun amazon_buildsUrlAndQualifications() {
        val json = """{"jobs":[{"id_icims":"10557095","title":"Applied Scientist","normalized_location":"Bengaluru, Karnataka, IND",
            "location":"IN, KA, Bengaluru","job_path":"/en/jobs/10557095/applied-scientist","description":"Do science",
            "basic_qualifications":"- PhD<br/>- Python","preferred_qualifications":"","posted_date":"September 22, 2026"}]}"""
        val p = JobParsers.parseAmazon(json).single()
        assertEquals("https://www.amazon.jobs/en/jobs/10557095/applied-scientist", p.url)
        assertTrue(p.description.contains("Basic qualifications"))
        assertTrue(p.location.contains("Bengaluru"))
    }

    @Test
    fun workdayTarget_parsesLocaleAndDeepLinks() {
        val t = WorkdayTarget.parse("https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/job/x")!!
        assertEquals("nvidia", t.tenant)
        assertEquals("NVIDIAExternalCareerSite", t.site)
        assertEquals("https://nvidia.wd5.myworkdayjobs.com/wday/cxs/nvidia/NVIDIAExternalCareerSite/jobs", t.listUrl)
        assertNull(WorkdayTarget.parse("https://careers.google.com/jobs"))
    }
}
