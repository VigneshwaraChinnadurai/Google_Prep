package com.vignesh.jobmatcher.sources

import com.vignesh.jobmatcher.data.JsonCodec.str
import com.vignesh.jobmatcher.model.Job
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

/** What a pasted job link points at; ATS links are read through their JSON APIs, not HTML. */
sealed class JobLink(open val url: String) {
    data class Greenhouse(override val url: String, val token: String, val id: String) : JobLink(url)
    data class Lever(override val url: String, val slug: String, val id: String) : JobLink(url)
    data class Ashby(override val url: String, val org: String, val id: String) : JobLink(url)
    data class Workday(override val url: String, val target: WorkdayTarget, val externalPath: String) : JobLink(url)
    data class Eightfold(override val url: String, val target: EightfoldTarget, val id: String) : JobLink(url)
    data class SmartRecruiters(override val url: String, val company: String, val id: String) : JobLink(url)
    data class Oracle(override val url: String, val target: OracleTarget, val id: String) : JobLink(url)
    data class LinkedIn(override val url: String, val id: String) : JobLink(url)
    data class Generic(override val url: String) : JobLink(url)

    val sourceLabel: String
        get() = when (this) {
            is Greenhouse -> "Greenhouse API"
            is Lever -> "Lever API"
            is Ashby -> "Ashby API"
            is Workday -> "Workday API"
            is Eightfold -> "Eightfold API"
            is SmartRecruiters -> "SmartRecruiters API"
            is Oracle -> "Oracle HCM API"
            is LinkedIn -> "LinkedIn public page"
            is Generic -> "web page"
        }

    companion object {
        private val URL_IN_TEXT = Regex("""https?://[^\s"'<>]+""")

        /** Pulls the first URL out of shared text ("Check out this job at X: https://…"). */
        fun extractUrl(text: String): String? =
            URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')

        fun detect(url: String): JobLink {
            val uri = runCatching { URI(url) }.getOrNull() ?: return Generic(url)
            val host = uri.host?.lowercase() ?: return Generic(url)
            val seg = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
            val query = uri.rawQuery.orEmpty().split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }

            return when {
                host == "boards.greenhouse.io" || host == "job-boards.greenhouse.io" ->
                    if (seg.size >= 3 && seg[1] == "jobs") Greenhouse(url, seg[0], seg[2]) else Generic(url)
                host == "jobs.lever.co" ->
                    if (seg.size >= 2) Lever(url, seg[0], seg[1]) else Generic(url)
                host == "jobs.ashbyhq.com" ->
                    if (seg.size >= 2) Ashby(url, seg[0], seg[1]) else Generic(url)
                host.endsWith("myworkdayjobs.com") -> {
                    val target = WorkdayTarget.parse(url)
                    val i = uri.path.indexOf("/job/")
                    // Links copied mid-application end in /apply or /apply/autofillWithResume.
                    val path = if (i >= 0) uri.path.substring(i).replace(Regex("/apply(/.*)?$"), "") else ""
                    if (target != null && i >= 0) Workday(url, target, path) else Generic(url)
                }
                host == "jobs.smartrecruiters.com" ->
                    seg.getOrNull(1)?.substringBefore('-')?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
                        ?.let { SmartRecruiters(url, seg[0], it) } ?: Generic(url)
                host.endsWith("oraclecloud.com") && seg.contains("job") -> {
                    val target = OracleTarget.parse(url)
                    val id = seg.getOrNull(seg.indexOf("job") + 1)
                    if (target != null && id != null) Oracle(url, target, id) else Generic(url)
                }
                host.contains("linkedin.com") -> {
                    val id = seg.getOrNull(seg.indexOf("view") + 1)?.takeIf { seg.contains("view") }
                        ?.substringAfterLast('-')
                        ?: query["currentJobId"]
                    if (id != null && id.all(Char::isDigit)) LinkedIn("https://www.linkedin.com/jobs/view/$id", id) else Generic(url)
                }
                (seg.size >= 3 && seg[seg.size - 3] == "careers" && seg[seg.size - 2] == "job") || query.containsKey("pid") -> {
                    // Eightfold careers sites: /careers/job/<id> or /careers?pid=<id>
                    val id = query["pid"]?.takeIf { it.isNotBlank() } ?: seg.last()
                    val target = EightfoldTarget.parse(url)
                    if (target != null && id.all(Char::isDigit)) Eightfold(url, target, id) else Generic(url)
                }
                else -> Generic(url)
            }
        }
    }
}

/** A job read from a link; [complete] is false when the page yielded too little description. */
data class ImportedJob(
    val posting: RawPosting,
    val company: String,
    val via: String
) {
    val complete: Boolean get() = posting.description.length >= Job.MIN_DESCRIPTION_CHARS
}

/**
 * Reads a pasted/shared job link. Known careers systems go through their JSON APIs (the
 * same parsers the automatic sources use); LinkedIn's public job page and any page with
 * schema.org JobPosting data are parsed from HTML; anything else falls back to page meta
 * tags and is usually incomplete -- the app then offers "Let Claude read it" or pasting
 * the description by hand.
 */
class ManualJobImporter(private val api: CareersApi = CareersApi.create()) {

    suspend fun import(url: String): ImportedJob {
        val link = JobLink.detect(url)
        // API failures (moved board, expired posting) fall through to reading the HTML page.
        val posting: RawPosting? = runCatching { readViaApi(link) }.getOrNull()
        if (posting != null && posting.title.isNotBlank()) {
            return ImportedJob(posting.copy(url = url), companyFromLink(link), link.sourceLabel)
        }
        val html = api.getHtml(link.url)
        val parsed = parseJobHtml(html, link.url) ?: error("Couldn't find a job posting on that page.")
        return if (link is JobLink.LinkedIn || link is JobLink.Generic) parsed else parsed.copy(via = "web page")
    }

    private suspend fun readViaApi(link: JobLink): RawPosting? = when (link) {
            is JobLink.Greenhouse -> {
                val json = api.getJson("https://boards-api.greenhouse.io/v1/boards/${enc(link.token)}/jobs/${enc(link.id)}")
                JobParsers.parseGreenhouse("""{"jobs":[$json]}""").firstOrNull()
            }
            is JobLink.Lever -> {
                val json = api.getJson("https://api.lever.co/v0/postings/${enc(link.slug)}/${enc(link.id)}")
                JobParsers.parseLever("[$json]").firstOrNull()
            }
            is JobLink.Ashby -> JobParsers.parseAshby(
                api.getJson("https://api.ashbyhq.com/posting-api/job-board/${enc(link.org)}?includeCompensation=false")
            ).firstOrNull { it.externalId == link.id }
            is JobLink.Workday -> JobParsers.parseWorkdayDetail(
                api.getJson(link.target.detailUrl(link.externalPath)), link.target.publicUrl(link.externalPath)
            )
            is JobLink.Eightfold -> JobParsers.parseEightfoldDetail(api.getJson(link.target.detailUrl(link.id)), link.target.origin)
            is JobLink.SmartRecruiters -> JobParsers.parseSmartRecruitersDetail(
                api.getJson("https://api.smartrecruiters.com/v1/companies/${enc(link.company)}/postings/${link.id}")
            )
            is JobLink.Oracle -> JobParsers.parseOracleDetail(api.getJson(link.target.detailUrl(link.id)), link.target.publicUrl(link.id))
            is JobLink.LinkedIn, is JobLink.Generic -> null
        }

    /** Company name for API-read links, from the board slug (refined later by company matching). */
    private fun companyFromLink(link: JobLink): String = when (link) {
        is JobLink.Greenhouse -> link.token
        is JobLink.Lever -> link.slug
        is JobLink.Ashby -> link.org
        is JobLink.Workday -> link.target.tenant
        is JobLink.Eightfold -> link.target.domain.substringBefore('.')
        is JobLink.SmartRecruiters -> link.company
        is JobLink.Oracle -> URI(link.url).host.substringBefore('.')
        else -> ""
    }.replaceFirstChar { it.uppercase() }

    companion object {
        /**
         * Extracts a posting from page HTML: LinkedIn's public job page layout, then any
         * schema.org JobPosting JSON-LD, then OpenGraph/meta tags (usually incomplete).
         */
        fun parseJobHtml(html: String, url: String): ImportedJob? {
            val doc = Jsoup.parse(html, url)
            return parseLinkedIn(doc, url) ?: parseJsonLd(doc, url) ?: parseMeta(doc, url)
        }

        private fun parseLinkedIn(doc: Document, url: String): ImportedJob? {
            val title = doc.selectFirst(".top-card-layout__title, .topcard__title")?.text()?.trim().orEmpty()
            val body = doc.selectFirst(".show-more-less-html__markup, .description__text")
            if (title.isBlank() || body == null) return null
            val criteria = doc.select(".description__job-criteria-item").joinToString("\n") { item ->
                "${item.selectFirst(".description__job-criteria-subheader")?.text().orEmpty()}: " +
                    item.selectFirst(".description__job-criteria-text")?.text().orEmpty()
            }
            return ImportedJob(
                RawPosting(
                    externalId = url.substringAfterLast('/'),
                    title = title,
                    location = doc.selectFirst(".topcard__flavor--bullet")?.text()?.trim().orEmpty(),
                    url = url,
                    description = (HtmlText.toText(body.html()) + if (criteria.isNotBlank()) "\n\n$criteria" else "").take(JobParsers.MAX_DESCRIPTION_CHARS),
                    postedAt = doc.selectFirst(".posted-time-ago__text")?.text()?.trim().orEmpty()
                ),
                company = doc.selectFirst(".topcard__org-name-link, .topcard__flavor a")?.text()?.trim().orEmpty(),
                via = "LinkedIn public page"
            )
        }

        private fun parseJsonLd(doc: Document, url: String): ImportedJob? {
            for (script in doc.select("script[type=application/ld+json]")) {
                val posting = runCatching { findJobPosting(script.data().trim()) }.getOrNull() ?: continue
                val org = posting.opt("hiringOrganization")
                val company = (org as? JSONObject)?.str("name") ?: (org as? String).orEmpty()
                return ImportedJob(
                    RawPosting(
                        externalId = posting.optJSONObject("identifier")?.str("value").orEmpty(),
                        title = posting.str("title"),
                        location = jsonLdLocations(posting.opt("jobLocation")) +
                            if (posting.str("jobLocationType").contains("TELECOMMUTE", true)) "; Remote" else "",
                        url = url,
                        description = HtmlText.toText(posting.str("description")).take(JobParsers.MAX_DESCRIPTION_CHARS),
                        postedAt = posting.str("datePosted").take(10)
                    ),
                    company = company,
                    via = "page JobPosting data"
                )
            }
            return null
        }

        private fun findJobPosting(json: String): JSONObject? {
            fun search(node: Any?): JSONObject? = when (node) {
                is JSONObject -> {
                    val type = node.opt("@type")
                    val isJob = type == "JobPosting" || (type is JSONArray && (0 until type.length()).any { type.optString(it) == "JobPosting" })
                    if (isJob) node else search(node.opt("@graph"))
                }
                is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { search(node.opt(it)) }
                else -> null
            }
            return search(if (json.startsWith("[")) JSONArray(json) else JSONObject(json))
        }

        private fun jsonLdLocations(node: Any?): String {
            val places = when (node) {
                is JSONArray -> (0 until node.length()).mapNotNull { node.optJSONObject(it) }
                is JSONObject -> listOf(node)
                else -> emptyList()
            }
            return places.mapNotNull { place ->
                val a = place.optJSONObject("address") ?: return@mapNotNull place.str("name").ifBlank { null }
                val country = a.opt("addressCountry").let { (it as? JSONObject)?.str("name") ?: (it as? String) }
                listOf(a.str("addressLocality"), a.str("addressRegion"), country.orEmpty())
                    .filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
            }.distinct().joinToString("; ")
        }

        private fun parseMeta(doc: Document, url: String): ImportedJob? {
            fun meta(key: String) = doc.selectFirst("meta[property=$key], meta[name=$key]")?.attr("content")?.trim().orEmpty()
            val title = meta("og:title").ifBlank { doc.title().trim() }
            if (title.isBlank()) return null
            return ImportedJob(
                RawPosting(
                    externalId = "",
                    title = title,
                    location = "",
                    url = url,
                    description = meta("og:description").ifBlank { meta("description") }
                ),
                company = meta("og:site_name"),
                via = "page title/description only"
            )
        }
    }
}
