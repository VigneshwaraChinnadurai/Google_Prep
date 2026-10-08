package com.vignesh.jobmatcher

import com.vignesh.jobmatcher.data.JobRepository
import com.vignesh.jobmatcher.model.Company
import com.vignesh.jobmatcher.model.SourceType
import com.vignesh.jobmatcher.sources.EightfoldTarget
import com.vignesh.jobmatcher.sources.JobParsers
import com.vignesh.jobmatcher.sources.OracleTarget
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Eightfold / Oracle HCM / SmartRecruiters fixtures mirror live responses probed 2026-10-08. */
class NewSourcesTest {

    @Test
    fun eightfold_searchAndDetail() {
        val (rows, total) = JobParsers.parseEightfoldSearch(
            """{"status":200,"data":{"positions":[{"id":1970393556991771,"name":"Principal Applied Scientist",
               "locations":["India, Karnataka, Bangalore"]}],"count":53}}"""
        )
        assertEquals(53, total)
        assertEquals("1970393556991771" to "Principal Applied Scientist", rows.single())

        val p = JobParsers.parseEightfoldDetail(
            """{"status":200,"data":{"id":"1970393556991771","displayJobId":"200054398","name":"Principal Applied Scientist",
               "locations":["India, Karnataka, Bangalore"],"standardizedLocations":["Bengaluru, KA, IN"],"postedTs":1789644823,
               "jobDescription":"<b>Overview</b><br><p>Copilot agents</p>",
               "publicUrl":"https://apply.careers.microsoft.com/careers/job/1970393556991771"}}""",
            host = "https://apply.careers.microsoft.com"
        )!!
        assertEquals("200054398", p.externalId)
        assertTrue(p.location.contains("Bengaluru"))
        assertTrue(p.description, p.description.contains("Copilot agents"))
        assertTrue(p.postedAt, p.postedAt.startsWith("2026-"))
    }

    @Test
    fun eightfoldTarget_explicitAndDerivedDomain() {
        assertEquals("microsoft.com", EightfoldTarget.parse("https://apply.careers.microsoft.com?domain=microsoft.com")!!.domain)
        assertEquals("qualcomm.com", EightfoldTarget.parse("https://careers.qualcomm.com")!!.domain)
        assertEquals("morganstanley.com", EightfoldTarget.parse("https://morganstanley.eightfold.ai")!!.domain)
        assertTrue(EightfoldTarget.parse("https://careers.qualcomm.com")!!.searchUrl("machine learning", "India", 10)
            .endsWith("query=machine%20learning&location=India&start=10&num=10"))
    }

    @Test
    fun oracle_listDetailAndTarget() {
        val t = OracleTarget.parse("https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001/jobs")!!
        assertEquals("CX_1001", t.site)
        assertTrue(t.listUrl("machine learning", "India", 25).contains("finder=findReqs;siteNumber=CX_1001,keyword=%22machine%20learning%22,location=India,limit=25,offset=25"))
        assertNull(OracleTarget.parse("https://jpmc.fa.oraclecloud.com/"))

        val (rows, total) = JobParsers.parseOracleList(
            """{"items":[{"TotalJobsCount":39,"requisitionList":[{"Id":"210767444","Title":"Lead Software Engineer"}]}]}"""
        )
        assertEquals(39, total)
        assertEquals("210767444", rows.single().first)

        val p = JobParsers.parseOracleDetail(
            """{"items":[{"Id":"210767444","Title":"Lead Software Engineer","PrimaryLocation":"Bengaluru, Karnataka, India",
               "ExternalPostedStartDate":"2026-10-06T09:36:02+00:00","ExternalDescriptionStr":"<div>Build ML platforms</div>",
               "ExternalQualificationsStr":"<ul><li>Python</li></ul>","secondaryLocations":[{"Name":"Hyderabad, Telangana, India"}]}]}""",
            publicUrl = t.publicUrl("210767444")
        )!!
        assertEquals("Bengaluru, Karnataka, India; Hyderabad, Telangana, India", p.location)
        assertEquals("2026-10-06", p.postedAt)
        assertTrue(p.description.contains("• Python"))
        assertEquals("https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001/job/210767444", p.url)
    }

    @Test
    fun smartRecruiters_listAndDetail() {
        val (rows, total) = JobParsers.parseSmartRecruitersList(
            """{"offset":0,"limit":1,"totalFound":33,"content":[{"id":"744000154351019","name":"Staff Engineer - Full Stack"}]}"""
        )
        assertEquals(33, total)
        assertEquals("744000154351019", rows.single().first)
        val p = JobParsers.parseSmartRecruitersDetail(
            """{"id":"744000154351019","name":"Staff Engineer","releasedDate":"2026-10-08T06:17:26.279Z",
               "location":{"city":"Chennai","country":"in","remote":false,"fullLocation":"Chennai, Tamil Nadu, India"},
               "postingUrl":"https://jobs.smartrecruiters.com/Freshworks/744000154351019",
               "jobAd":{"sections":{"jobDescription":{"title":"Job Description","text":"<p>Build AI</p>"},
                 "qualifications":{"title":"Qualifications","text":"<ul><li>Python</li></ul>"}}}}"""
        )!!
        assertEquals("Chennai, Tamil Nadu, India; India", p.location)
        assertTrue(p.description.startsWith("Job Description\nBuild AI"))
        assertTrue(p.description.contains("• Python"))
    }

    @Test
    fun searchBatch_topChoiceAloneOncePerDay_thenRotates() {
        val now = 10L * 24 * 3600 * 1000
        val google = Company("google", "Google", SourceType.CLAUDE_SEARCH, priority = 1)
        val others = (1..6).map { Company("c$it", "C$it", SourceType.CLAUDE_SEARCH, lastFetchedAt = it.toLong()) }

        assertEquals(listOf("google"), JobRepository.nextSearchBatch(listOf(google) + others, 4, now).map { it.id })

        val searchedGoogle = google.copy(lastFetchedAt = now - 3600_000)
        assertEquals(listOf("c1", "c2", "c3", "c4"), JobRepository.nextSearchBatch(listOf(searchedGoogle) + others, 4, now).map { it.id })
    }

    @Test
    fun seededCompanies_areValidAndGoogleIsTopChoice() {
        val arr = JSONArray(File("src/main/assets/default_companies.json").readText())
        val companies = (0 until arr.length()).map { com.vignesh.jobmatcher.data.JsonCodec.companyFromJson(arr.getJSONObject(it)) }
        assertEquals(26, companies.size)
        assertEquals(companies.size, companies.map { it.id }.toSet().size)
        assertEquals(listOf("google"), companies.filter { it.priority == 1 }.map { it.id })
        companies.filter { it.source == SourceType.WORKDAY }.forEach {
            assertTrue(it.name, com.vignesh.jobmatcher.sources.WorkdayTarget.parse(it.identifier) != null)
        }
        companies.filter { it.source == SourceType.EIGHTFOLD }.forEach { assertTrue(it.name, EightfoldTarget.parse(it.identifier) != null) }
        companies.filter { it.source == SourceType.ORACLE_HCM }.forEach { assertTrue(it.name, OracleTarget.parse(it.identifier) != null) }
    }
}
