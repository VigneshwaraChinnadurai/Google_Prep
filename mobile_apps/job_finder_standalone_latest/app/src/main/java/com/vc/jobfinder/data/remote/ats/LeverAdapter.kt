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
private data class LeverPosting(
    val id: String,
    val text: String,
    val hostedUrl: String? = null,
    val applyUrl: String? = null,
    val descriptionPlain: String? = null,
    val description: String? = null,
    val categories: LeverCategories? = null,
)

@Serializable
private data class LeverCategories(
    val location: String? = null,
    val commitment: String? = null,
)

@Singleton
class LeverAdapter @Inject constructor() : AtsAdapter {
    override val key = "lever"

    override suspend fun fetch(company: Company): List<Job> {
        val url = "https://api.lever.co/v0/postings/${company.slug}?mode=json"
        val client = jsonClient()
        return try {
            val list: List<LeverPosting> = client.get(url) {
                headers { append("User-Agent", UA) }
            }.body()

            list.map { p ->
                val desc = p.descriptionPlain?.takeIf { it.isNotBlank() }
                    ?: p.description?.let { stripHtml(it) }
                    ?: ""
                Job(
                    id = "lever-${company.slug}-${p.id}",
                    title = p.text,
                    company = company.name,
                    location = p.categories?.location,
                    applyUrl = p.hostedUrl ?: p.applyUrl ?: "https://jobs.lever.co/${company.slug}",
                    description = desc.take(6_000),
                )
            }.applyLocationFilter(company.locationFilter)
        } catch (t: Throwable) {
            android.util.Log.w("Lever", "fetch failed for ${company.slug}: ${t.message}")
            emptyList()
        } finally {
            client.close()
        }
    }
}
