package com.vignesh.jobmatcher.sources

import com.vignesh.jobmatcher.data.JsonCodec.str
import com.vignesh.jobmatcher.data.JsonCodec.strings
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One posting as returned by a careers API, before scoring/merging into a Job. */
data class RawPosting(
    val externalId: String,
    val title: String,
    val location: String,
    val url: String,
    val description: String,
    val postedAt: String = ""
)

/** Workday list-endpoint row; the detail call supplies location and description. */
data class WorkdayListing(val externalPath: String, val title: String, val locationsText: String)

data class WorkdayFacetChoice(val parameter: String, val ids: List<String>)

/**
 * Pure JSON -> RawPosting parsers, one per ATS. Kept free of Android/network types so
 * they're covered by JVM unit tests against real response shapes.
 */
object JobParsers {
    const val MAX_DESCRIPTION_CHARS = 12_000

    private fun String.capped() = if (length > MAX_DESCRIPTION_CHARS) take(MAX_DESCRIPTION_CHARS) + "…" else this

    private fun joinLocations(vararg parts: String?): String =
        parts.filterNotNull().map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("; ")

    private fun dateOnly(iso: String): String = iso.take(10)

    private fun epochMillisToDate(ms: Long): String =
        if (ms <= 0) "" else DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC))

    /** GET boards-api.greenhouse.io/v1/boards/{token}/jobs?content=true */
    fun parseGreenhouse(json: String): List<RawPosting> {
        val jobs = JSONObject(json).optJSONArray("jobs") ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val o = jobs.getJSONObject(i)
            val offices = o.optJSONArray("offices")
            val officeNames = if (offices == null) emptyList() else
                (0 until offices.length()).mapNotNull { offices.optJSONObject(it)?.str("name") }
            RawPosting(
                externalId = o.optLong("id").toString(),
                title = o.str("title"),
                location = joinLocations(o.optJSONObject("location")?.str("name"), *officeNames.toTypedArray()),
                url = o.str("absolute_url"),
                description = HtmlText.toText(o.str("content"), alreadyEscaped = true).capped(),
                postedAt = dateOnly(o.str("updated_at"))
            )
        }
    }

    /** GET api.lever.co/v0/postings/{slug}?mode=json */
    fun parseLever(json: String): List<RawPosting> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val cats = o.optJSONObject("categories") ?: JSONObject()
            val country = o.str("country").let { if (it.equals("IN", true)) "India" else it }
            val lists = o.optJSONArray("lists")
            val listText = if (lists == null) "" else (0 until lists.length()).joinToString("\n\n") { li ->
                val l = lists.getJSONObject(li)
                l.str("text") + "\n" + HtmlText.toText("<ul>" + l.str("content") + "</ul>")
            }
            val description = listOf(o.str("descriptionPlain"), listText, o.str("additionalPlain"))
                .filter { it.isNotBlank() }.joinToString("\n\n")
            RawPosting(
                externalId = o.str("id"),
                title = o.str("text"),
                location = joinLocations(
                    cats.str("location"),
                    *cats.optJSONArray("allLocations").strings().toTypedArray(),
                    country,
                    o.str("workplaceType").takeIf { it.equals("remote", true) }?.let { "Remote" }
                ),
                url = o.str("hostedUrl"),
                description = description.capped(),
                postedAt = epochMillisToDate(o.optLong("createdAt"))
            )
        }
    }

    /** GET api.ashbyhq.com/posting-api/job-board/{org} */
    fun parseAshby(json: String): List<RawPosting> {
        val jobs = JSONObject(json).optJSONArray("jobs") ?: return emptyList()
        return (0 until jobs.length()).mapNotNull { i ->
            val o = jobs.getJSONObject(i)
            if (!o.optBoolean("isListed", true)) return@mapNotNull null
            val secondary = o.optJSONArray("secondaryLocations")
            val secondaryNames = if (secondary == null) emptyList() else
                (0 until secondary.length()).mapNotNull { secondary.optJSONObject(it)?.str("location") }
            val country = o.optJSONObject("address")?.optJSONObject("postalAddress")?.str("addressCountry")
            RawPosting(
                externalId = o.str("id"),
                title = o.str("title"),
                location = joinLocations(
                    o.str("location"), *secondaryNames.toTypedArray(), country,
                    if (o.optBoolean("isRemote")) "Remote" else null
                ),
                url = o.str("jobUrl"),
                description = o.str("descriptionPlain").ifBlank { HtmlText.toText(o.str("descriptionHtml")) }.capped(),
                postedAt = dateOnly(o.str("publishedAt"))
            )
        }
    }

    /** POST {host}/wday/cxs/{tenant}/{site}/jobs -- returns listings and the reported total. */
    fun parseWorkdayList(json: String): Pair<List<WorkdayListing>, Int> {
        val o = JSONObject(json)
        val arr = o.optJSONArray("jobPostings") ?: return emptyList<WorkdayListing>() to 0
        val listings = (0 until arr.length()).mapNotNull { i ->
            val p = arr.getJSONObject(i)
            val path = p.str("externalPath")
            if (path.isBlank()) null else WorkdayListing(path, p.str("title"), p.str("locationsText"))
        }
        return listings to o.optInt("total", listings.size)
    }

    /**
     * Picks the Workday location facet to filter on, from a list response's "facets".
     * Tenants name these differently (locationCountry, locationHierarchy1, locations, ...),
     * so: prefer a facet whose value *equals* a keyword (country level, e.g. "India"),
     * else the facet with the most whole-word matches (city level, e.g. "India, Bengaluru").
     * Returns null if the tenant exposes no location facets at all (caller then fetches
     * unfiltered); an empty id list means "has location facets, none match -> no jobs here".
     */
    fun parseWorkdayLocationFacet(json: String, keywords: List<String>): WorkdayFacetChoice? {
        val values = mutableListOf<Triple<String, String, String>>() // (parameter, descriptor, id)
        fun walk(facets: JSONArray?) {
            if (facets == null) return
            for (i in 0 until facets.length()) {
                val f = facets.optJSONObject(i) ?: continue
                val param = f.str("facetParameter")
                val vals = f.optJSONArray("values") ?: continue
                for (j in 0 until vals.length()) {
                    val v = vals.optJSONObject(j) ?: continue
                    if (v.has("facetParameter")) walk(JSONArray().put(v))
                    else if (param.contains("location", true) || param.contains("country", true)) {
                        values += Triple(param, v.str("descriptor"), v.str("id"))
                    }
                }
            }
        }
        walk(JSONObject(json).optJSONArray("facets"))
        if (values.isEmpty()) return null
        val words = keywords.map { Regex("(?<![A-Za-z])" + Regex.escape(it) + "(?![A-Za-z])", RegexOption.IGNORE_CASE) }
        val exact = values.filter { (_, d, _) -> keywords.any { it.equals(d.trim(), true) } }
        if (exact.isNotEmpty()) {
            val param = exact.groupBy { it.first }.maxBy { it.value.size }.key
            return WorkdayFacetChoice(param, exact.filter { it.first == param }.map { it.third })
        }
        val partial = values.filter { (_, d, _) -> words.any { it.containsMatchIn(d) } }
        if (partial.isEmpty()) return WorkdayFacetChoice("", emptyList())
        val param = partial.groupBy { it.first }.maxBy { it.value.size }.key
        return WorkdayFacetChoice(param, partial.filter { it.first == param }.map { it.third })
    }

    /** GET {host}/wday/cxs/{tenant}/{site}{externalPath} */
    fun parseWorkdayDetail(json: String, fallbackUrl: String): RawPosting? {
        val info = JSONObject(json).optJSONObject("jobPostingInfo") ?: return null
        return RawPosting(
            externalId = info.str("jobReqId").ifBlank { info.str("id") },
            title = info.str("title"),
            location = joinLocations(
                info.str("location"),
                *info.optJSONArray("additionalLocations").strings().toTypedArray(),
                info.optJSONObject("country")?.str("descriptor")
            ),
            url = info.str("externalUrl").ifBlank { fallbackUrl },
            description = HtmlText.toText(info.str("jobDescription")).capped(),
            postedAt = info.str("startDate").ifBlank { info.str("postedOn") }
        )
    }

    /** GET www.amazon.jobs/en/search.json?base_query=...&loc_query=... */
    fun parseAmazon(json: String): List<RawPosting> {
        val jobs = JSONObject(json).optJSONArray("jobs") ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val o = jobs.getJSONObject(i)
            val description = buildString {
                append(HtmlText.toText(o.str("description")))
                o.str("basic_qualifications").takeIf { it.isNotBlank() }?.let {
                    append("\n\nBasic qualifications:\n").append(HtmlText.toText(it))
                }
                o.str("preferred_qualifications").takeIf { it.isNotBlank() }?.let {
                    append("\n\nPreferred qualifications:\n").append(HtmlText.toText(it))
                }
            }
            RawPosting(
                externalId = o.str("id_icims").ifBlank { o.str("id") },
                title = o.str("title"),
                location = joinLocations(o.str("normalized_location"), o.str("location")),
                url = "https://www.amazon.jobs" + o.str("job_path"),
                description = description.capped(),
                postedAt = o.str("posted_date")
            )
        }
    }
}
