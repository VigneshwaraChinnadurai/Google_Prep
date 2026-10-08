package com.vignesh.jobmatcher

import com.vignesh.jobmatcher.model.Company
import com.vignesh.jobmatcher.model.SourceType
import com.vignesh.jobmatcher.sources.JobFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Hits the real careers APIs to catch response-shape drift. Skipped by default (network +
 * slow); run with `gradlew testDebugUnitTest -PliveApiTests=1`.
 */
class LiveApiTest {
    private val fetcher = JobFetcher()
    private val terms = listOf("machine learning")
    private val india = listOf("india", "bengaluru", "bangalore")

    private fun fetch(source: SourceType, id: String) = runBlocking {
        fetcher.fetch(Company("t", "Test", source, id), terms, india) { true }
    }

    /** Every automatic-source company in the bundled seed list must fetch without error. */
    @Test
    fun seededCompanies_allFetch() {
        assumeTrue(System.getProperty("liveApiTests") == "1")
        val arr = org.json.JSONArray(java.io.File("src/main/assets/default_companies.json").readText())
        val companies = (0 until arr.length())
            .map { com.vignesh.jobmatcher.data.JsonCodec.companyFromJson(arr.getJSONObject(it)) }
            .filter { it.source.automatic }
        val failures = mutableListOf<String>()
        companies.forEach { c ->
            val result = runCatching { runBlocking { fetcher.fetch(c, terms, india) { true } } }
            result.onSuccess { postings ->
                val inIndia = postings.count { p -> india.any { p.location.contains(it, ignoreCase = true) } }
                println("${c.name} [${c.source.label}]: ${postings.size} postings, $inIndia in India; e.g. ${postings.firstOrNull()?.title}")
            }.onFailure {
                println("${c.name} [${c.source.label}]: FAILED ${it.message}")
                failures += c.name
            }
        }
        assertTrue("Failed: $failures", failures.isEmpty())
    }

    /** Real job links of each kind (open on 2026-10-08; replace any that expire). */
    @Test
    fun manualLinks_import() {
        assumeTrue(System.getProperty("liveApiTests") == "1")
        val importer = com.vignesh.jobmatcher.sources.ManualJobImporter()
        val links = listOf(
            "https://www.linkedin.com/jobs/view/4474679765",
            "https://job-boards.greenhouse.io/anthropic/jobs/4461450008",
            "https://apply.careers.microsoft.com/careers/job/1970393556991771",
            "https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/job/US-CA-Santa-Clara/Machine-Learning-Engineer_JR2025036",
            "https://jobs.smartrecruiters.com/Freshworks/744000154351019-staff-engineer-full-stack",
            "https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001/job/210767444"
        )
        val failures = mutableListOf<String>()
        links.forEach { url ->
            runCatching { runBlocking { importer.import(url) } }
                .onSuccess {
                    println("OK  ${it.via}: ${it.posting.title} @ ${it.company} | ${it.posting.location} | ${it.posting.description.length} chars | complete=${it.complete}")
                    if (!it.complete) failures += url
                }
                .onFailure { println("ERR $url: ${it.message}"); failures += url }
        }
        assertTrue("Not fully read: $failures", failures.isEmpty())
    }

    @Test
    fun liveSources_returnParseablePostings() {
        assumeTrue(System.getProperty("liveApiTests") == "1")
        val results = mapOf(
            "greenhouse" to fetch(SourceType.GREENHOUSE, "anthropic"),
            "lever" to fetch(SourceType.LEVER, "palantir"),
            "ashby" to fetch(SourceType.ASHBY, "openai"),
            "workday" to fetch(SourceType.WORKDAY, "https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite"),
            "amazon" to fetch(SourceType.AMAZON, "")
        )
        results.forEach { (name, postings) ->
            println("$name: ${postings.size} postings; first = ${postings.firstOrNull()?.let { "${it.title} | ${it.location} | ${it.description.length} chars" }}")
            assertTrue("$name returned nothing", postings.isNotEmpty())
            assertTrue("$name has blank titles", postings.all { it.title.isNotBlank() && it.url.startsWith("http") })
        }
        // Search-based sources filter server-side, so (nearly) everything should be in India.
        listOf("workday", "amazon").forEach { name ->
            val inIndia = results.getValue(name).count { p -> india.any { p.location.contains(it, ignoreCase = true) } }
            println("$name in India: $inIndia / ${results.getValue(name).size}")
            assertTrue("$name returned no India postings", inIndia > 0)
        }
    }
}
