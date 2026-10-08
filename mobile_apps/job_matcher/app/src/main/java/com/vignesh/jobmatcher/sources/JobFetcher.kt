package com.vignesh.jobmatcher.sources

import com.vignesh.jobmatcher.model.Company
import com.vignesh.jobmatcher.model.SourceType
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/** Parsed Workday careers URL -> its CXS JSON endpoints. */
data class WorkdayTarget(val origin: String, val tenant: String, val site: String) {
    val listUrl get() = "$origin/wday/cxs/$tenant/$site/jobs"
    fun detailUrl(externalPath: String) = "$origin/wday/cxs/$tenant/$site$externalPath"
    fun publicUrl(externalPath: String) = "$origin/$site$externalPath"

    companion object {
        private val LOCALE = Regex("^[a-z]{2}-[A-Z]{2}$")

        /** Accepts e.g. https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/job/... */
        fun parse(url: String): WorkdayTarget? = runCatching {
            val uri = URI(url.trim())
            val host = uri.host ?: return null
            if (!host.contains("myworkdayjobs.com")) return null
            val site = uri.path.split('/').filter { it.isNotBlank() }.firstOrNull { !LOCALE.matches(it) }
                ?: return null
            WorkdayTarget("${uri.scheme ?: "https"}://$host", host.substringBefore('.'), site)
        }.getOrNull()
    }
}

/**
 * Fetches current postings for one company from its ATS. Search-based sources (Workday,
 * Amazon) are queried once per profile search term; Workday additionally needs one
 * detail call per posting, so it pre-filters on title/location first and caps the count.
 */
class JobFetcher(private val api: CareersApi = CareersApi.create()) {

    suspend fun fetch(
        company: Company,
        searchTerms: List<String>,
        locationKeywords: List<String>,
        titleAllowed: (String) -> Boolean
    ): List<RawPosting> {
        val id = company.identifier.trim()
        return when (company.source) {
            SourceType.GREENHOUSE ->
                JobParsers.parseGreenhouse(api.get("https://boards-api.greenhouse.io/v1/boards/${enc(requireId(id))}/jobs?content=true"))
            SourceType.LEVER ->
                JobParsers.parseLever(api.get("https://api.lever.co/v0/postings/${enc(requireId(id))}?mode=json"))
            SourceType.ASHBY ->
                JobParsers.parseAshby(api.get("https://api.ashbyhq.com/posting-api/job-board/${enc(requireId(id))}?includeCompensation=false"))
            SourceType.WORKDAY -> fetchWorkday(id, searchTerms, locationKeywords, titleAllowed)
            SourceType.AMAZON -> fetchAmazon(searchTerms, locationKeywords)
            SourceType.CLAUDE_SEARCH -> emptyList()
        }
    }

    private fun requireId(id: String): String {
        require(id.isNotBlank()) { "Missing board token / slug for this company." }
        return id
    }

    private suspend fun fetchWorkday(
        url: String,
        searchTerms: List<String>,
        locationKeywords: List<String>,
        titleAllowed: (String) -> Boolean
    ): List<RawPosting> {
        val target = WorkdayTarget.parse(url)
            ?: error("Not a Workday careers URL: '$url' (expected https://<tenant>.wdN.myworkdayjobs.com/<site>)")
        val listings = LinkedHashMap<String, WorkdayListing>()
        suspend fun page(term: String, offset: Int, facets: JSONObject): String {
            val body = JSONObject()
                .put("appliedFacets", facets)
                .put("limit", WORKDAY_PAGE_SIZE)
                .put("offset", offset)
                .put("searchText", term)
                .toString()
            return api.post(target.listUrl, CareersApi.jsonBody(body))
        }
        for (term in searchTerms.ifEmpty { listOf("") }) {
            // Unfiltered results are global (mostly US); narrow to the location facet when the tenant has one.
            val first = page(term, 0, JSONObject())
            val choice = JobParsers.parseWorkdayLocationFacet(first, locationKeywords)
            if (choice != null && choice.ids.isEmpty()) continue // no postings in your locations for this term
            val facets = if (choice == null) JSONObject()
            else JSONObject().put(choice.parameter, org.json.JSONArray(choice.ids))
            var offset = 0
            for (p in 0 until WORKDAY_PAGES_PER_TERM) {
                val json = if (p == 0 && choice == null) first else page(term, offset, facets)
                val (rows, total) = JobParsers.parseWorkdayList(json)
                rows.forEach { listings.putIfAbsent(it.externalPath, it) }
                offset += WORKDAY_PAGE_SIZE
                if (rows.isEmpty() || offset >= total) break
            }
        }
        // List rows only say e.g. "3 Locations" for multi-site postings, so those must be
        // fetched in detail too; single-location rows can be location-filtered right here.
        val multi = Regex("^\\d+ Locations?$", RegexOption.IGNORE_CASE)
        val candidates = listings.values
            .filter { titleAllowed(it.title) }
            .filter { row -> multi.matches(row.locationsText) || locationKeywords.any { row.locationsText.contains(it, ignoreCase = true) } }
            .take(WORKDAY_MAX_DETAILS)
        return candidates.mapNotNull { row ->
            runCatching {
                JobParsers.parseWorkdayDetail(api.get(target.detailUrl(row.externalPath)), target.publicUrl(row.externalPath))
            }.getOrNull()
        }
    }

    private suspend fun fetchAmazon(searchTerms: List<String>, locationKeywords: List<String>): List<RawPosting> {
        // loc_query is only a relevance hint (~13% India hits); the country-code facet is a real filter.
        val locFilter = if (locationKeywords.any { it.equals("india", true) }) "normalized_country_code%5B%5D=IND"
        else "loc_query=${enc(locationKeywords.firstOrNull().orEmpty())}"
        val results = LinkedHashMap<String, RawPosting>()
        for (term in searchTerms.ifEmpty { listOf("machine learning") }) {
            for (page in 0 until AMAZON_PAGES_PER_TERM) {
                val url = "https://www.amazon.jobs/en/search.json?base_query=${enc(term)}&$locFilter" +
                    "&result_limit=$AMAZON_LIMIT&offset=${page * AMAZON_LIMIT}&sort=recent"
                val rows = JobParsers.parseAmazon(api.get(url))
                rows.forEach { results.putIfAbsent(it.externalId, it) }
                if (rows.size < AMAZON_LIMIT) break
            }
        }
        return results.values.toList()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        private const val WORKDAY_PAGE_SIZE = 20
        private const val WORKDAY_PAGES_PER_TERM = 3
        private const val WORKDAY_MAX_DETAILS = 60
        private const val AMAZON_LIMIT = 100
        private const val AMAZON_PAGES_PER_TERM = 3
    }
}
