package com.vc.jobfinder.data.remote.ats

import com.vc.jobfinder.data.remote.UA
import com.vc.jobfinder.data.remote.jsonClient
import com.vc.jobfinder.domain.Company
import com.vc.jobfinder.domain.Job
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GhResponse(val jobs: List<GhJob> = emptyList())

@Serializable
private data class GhJob(
    val id: Long,
    val title: String,
    val absolute_url: String,
    val location: GhLocation? = null,
    val content: String = "",
    val updated_at: String? = null,
)

@Serializable
private data class GhLocation(val name: String? = null)

@Singleton
class GreenhouseAdapter @Inject constructor() : AtsAdapter {
    override val key = "greenhouse"

    override suspend fun fetch(company: Company): List<Job> {
        val url = "https://boards-api.greenhouse.io/v1/boards/${company.slug}/jobs?content=true"
        val client = jsonClient()
        return try {
            val resp: GhResponse = client.get(url) {
                headers { append("User-Agent", UA) }
            }.body()

            resp.jobs.map { j ->
                Job(
                    id = "gh-${company.slug}-${j.id}",
                    title = j.title,
                    company = company.name,
                    location = j.location?.name,
                    applyUrl = j.absolute_url,
                    description = stripHtml(j.content).take(6_000),
                    postedAt = j.updated_at,
                )
            }.applyLocationFilter(company.locationFilter)
        } catch (t: Throwable) {
            android.util.Log.w("Greenhouse", "fetch failed for ${company.slug}: ${t.message}")
            emptyList()
        } finally {
            client.close()
        }
    }
}

internal fun stripHtml(s: String): String =
    s.replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun List<Job>.applyLocationFilter(filter: String?): List<Job> {
    if (filter.isNullOrBlank()) return this
    val lf = filter.lowercase()
    return filter { lf in (it.location ?: "").lowercase() }
}
