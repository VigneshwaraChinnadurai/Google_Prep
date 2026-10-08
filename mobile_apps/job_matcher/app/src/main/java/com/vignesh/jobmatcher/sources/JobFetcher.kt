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

/** Eightfold careers site (e.g. Microsoft's apply.careers.microsoft.com) -> its PCSX endpoints. */
data class EightfoldTarget(val origin: String, val domain: String) {
    fun searchUrl(query: String, location: String, start: Int) =
        "$origin/api/pcsx/search?domain=$domain&query=${enc(query)}&location=${enc(location)}&start=$start&num=10"
    fun detailUrl(id: String) = "$origin/api/pcsx/position_details?position_id=$id&domain=$domain&hl=en"

    companion object {
        /** "https://apply.careers.microsoft.com?domain=microsoft.com"; domain defaults from the host. */
        fun parse(url: String): EightfoldTarget? = runCatching {
            val uri = URI(url.trim())
            val host = uri.host ?: return null
            val explicit = uri.query.orEmpty().split('&')
                .firstOrNull { it.startsWith("domain=") }?.substringAfter('=')
            val labels = host.split('.')
            val derived = if (host.endsWith(".eightfold.ai")) labels.first() + ".com" else labels.takeLast(2).joinToString(".")
            EightfoldTarget("${uri.scheme ?: "https"}://$host", explicit?.takeIf { it.isNotBlank() } ?: derived)
        }.getOrNull()
    }
}

/** Oracle HCM Candidate Experience site (e.g. JPMorgan) -> its recruiting REST endpoints. */
data class OracleTarget(val origin: String, val site: String) {
    private val api get() = "$origin/hcmRestApi/resources/latest"

    // The finder's ; , = separators must stay literal; only values are encoded.
    fun listUrl(keyword: String, location: String, offset: Int) =
        "$api/recruitingCEJobRequisitions?onlyData=true&expand=requisitionList.secondaryLocations" +
            "&finder=findReqs;siteNumber=$site,keyword=%22${enc(keyword)}%22,location=${enc(location)},limit=25,offset=$offset"
    fun detailUrl(id: String) =
        "$api/recruitingCEJobRequisitionDetails?expand=all&onlyData=true&finder=ById;Id=%22$id%22,siteNumber=$site"
    fun publicUrl(id: String) = "$origin/hcmUI/CandidateExperience/en/sites/$site/job/$id"

    companion object {
        /** "https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001[/...]" */
        fun parse(url: String): OracleTarget? = runCatching {
            val uri = URI(url.trim())
            val host = uri.host ?: return null
            val segments = uri.path.split('/').filter { it.isNotBlank() }
            val i = segments.indexOf("sites")
            val site = if (i == -1) null else segments.getOrNull(i + 1)
            if (site == null) return null
            OracleTarget("${uri.scheme ?: "https"}://$host", site)
        }.getOrNull()
    }
}

internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

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
                JobParsers.parseGreenhouse(getJson("https://boards-api.greenhouse.io/v1/boards/${enc(requireId(id))}/jobs?content=true"))
            SourceType.LEVER ->
                JobParsers.parseLever(getJson("https://api.lever.co/v0/postings/${enc(requireId(id))}?mode=json"))
            SourceType.ASHBY ->
                JobParsers.parseAshby(getJson("https://api.ashbyhq.com/posting-api/job-board/${enc(requireId(id))}?includeCompensation=false"))
            SourceType.WORKDAY -> fetchWorkday(id, searchTerms, locationKeywords, titleAllowed)
            SourceType.AMAZON -> fetchAmazon(searchTerms, locationKeywords)
            SourceType.EIGHTFOLD -> fetchEightfold(id, searchTerms, locationKeywords, titleAllowed)
            SourceType.ORACLE_HCM -> fetchOracle(id, searchTerms, locationKeywords, titleAllowed)
            SourceType.SMARTRECRUITERS -> fetchSmartRecruiters(requireId(id), locationKeywords, titleAllowed)
            SourceType.CLAUDE_SEARCH, SourceType.MANUAL_LINK -> emptyList()
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
                JobParsers.parseWorkdayDetail(getJson(target.detailUrl(row.externalPath)), target.publicUrl(row.externalPath))
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
                val rows = JobParsers.parseAmazon(getJson(url))
                rows.forEach { results.putIfAbsent(it.externalId, it) }
                if (rows.size < AMAZON_LIMIT) break
            }
        }
        return results.values.toList()
    }

    /** Search APIs take one location string; "India" when it's in the keywords, else the first keyword. */
    private fun primaryLocation(locationKeywords: List<String>): String =
        if (locationKeywords.any { it.equals("india", true) }) "India" else locationKeywords.firstOrNull().orEmpty()

    private suspend fun getJson(url: String): String = api.getJson(url)

    private suspend fun fetchEightfold(
        url: String,
        searchTerms: List<String>,
        locationKeywords: List<String>,
        titleAllowed: (String) -> Boolean
    ): List<RawPosting> {
        val target = EightfoldTarget.parse(url)
            ?: error("Not an Eightfold careers URL: '$url' (expected e.g. https://apply.careers.microsoft.com?domain=microsoft.com)")
        val location = primaryLocation(locationKeywords)
        val ids = LinkedHashMap<String, String>()
        for (term in searchTerms.ifEmpty { listOf("") }) {
            var start = 0
            for (page in 0 until EIGHTFOLD_PAGES_PER_TERM) {
                val (rows, total) = JobParsers.parseEightfoldSearch(getJson(target.searchUrl(term, location, start)))
                rows.forEach { (id, title) -> ids.putIfAbsent(id, title) }
                start += rows.size
                if (rows.isEmpty() || start >= total) break
            }
        }
        return ids.filter { titleAllowed(it.value) }.keys.take(MAX_DETAILS).mapNotNull { id ->
            runCatching { JobParsers.parseEightfoldDetail(getJson(target.detailUrl(id)), target.origin) }.getOrNull()
        }
    }

    private suspend fun fetchOracle(
        url: String,
        searchTerms: List<String>,
        locationKeywords: List<String>,
        titleAllowed: (String) -> Boolean
    ): List<RawPosting> {
        val target = OracleTarget.parse(url)
            ?: error("Not an Oracle HCM careers URL: '$url' (expected .../hcmUI/CandidateExperience/en/sites/<SITE>)")
        val location = primaryLocation(locationKeywords)
        val ids = LinkedHashMap<String, String>()
        for (term in searchTerms.ifEmpty { listOf("") }) {
            var offset = 0
            for (page in 0 until ORACLE_PAGES_PER_TERM) {
                val (rows, total) = JobParsers.parseOracleList(getJson(target.listUrl(term, location, offset)))
                rows.forEach { (id, title) -> ids.putIfAbsent(id, title) }
                offset += rows.size
                if (rows.isEmpty() || offset >= total) break
            }
        }
        return ids.filter { titleAllowed(it.value) }.keys.take(MAX_DETAILS).mapNotNull { id ->
            runCatching { JobParsers.parseOracleDetail(getJson(target.detailUrl(id)), target.publicUrl(id)) }.getOrNull()
        }
    }

    /** SmartRecruiters' keyword search is weak; list everything in-country, then fetch details. */
    private suspend fun fetchSmartRecruiters(
        companyId: String,
        locationKeywords: List<String>,
        titleAllowed: (String) -> Boolean
    ): List<RawPosting> {
        val country = if (locationKeywords.any { it.equals("india", true) }) "&country=in" else ""
        val base = "https://api.smartrecruiters.com/v1/companies/${enc(companyId)}/postings"
        val ids = LinkedHashMap<String, String>()
        var offset = 0
        for (page in 0 until SMARTRECRUITERS_MAX_PAGES) {
            val (rows, total) = JobParsers.parseSmartRecruitersList(getJson("$base?limit=100&offset=$offset$country"))
            rows.forEach { (id, title) -> ids.putIfAbsent(id, title) }
            offset += rows.size
            if (rows.isEmpty() || offset >= total) break
        }
        return ids.filter { titleAllowed(it.value) }.keys.take(MAX_DETAILS).mapNotNull { id ->
            runCatching { JobParsers.parseSmartRecruitersDetail(getJson("$base/$id")) }.getOrNull()
        }
    }

    companion object {
        private const val WORKDAY_PAGE_SIZE = 20
        private const val WORKDAY_PAGES_PER_TERM = 3
        private const val WORKDAY_MAX_DETAILS = 60
        private const val AMAZON_LIMIT = 100
        private const val AMAZON_PAGES_PER_TERM = 3
        private const val EIGHTFOLD_PAGES_PER_TERM = 4
        private const val ORACLE_PAGES_PER_TERM = 3
        private const val SMARTRECRUITERS_MAX_PAGES = 5
        private const val MAX_DETAILS = 60
    }
}
