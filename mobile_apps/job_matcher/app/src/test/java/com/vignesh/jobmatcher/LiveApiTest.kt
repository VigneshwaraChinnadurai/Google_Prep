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
