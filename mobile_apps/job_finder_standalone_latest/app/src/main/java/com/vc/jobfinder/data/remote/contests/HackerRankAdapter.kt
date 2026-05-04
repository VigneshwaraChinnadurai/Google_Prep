package com.vc.jobfinder.data.remote.contests

import com.vc.jobfinder.data.remote.UA
import com.vc.jobfinder.data.remote.jsonClient
import com.vc.jobfinder.domain.Competition
import com.vc.jobfinder.domain.CompanyMatcher
import com.vc.jobfinder.domain.Platform
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class HrModel(
    val slug: String,
    val name: String,
    val description: String? = null,
    val company_name: String? = null,
    val organization: String? = null,
    val starts_at: String? = null,
    val ends_at: String? = null,
)

@Serializable
private data class HrEnvelope(val models: List<HrModel> = emptyList())

@Singleton
class HackerRankAdapter @Inject constructor(
    private val matcher: CompanyMatcher,
) : ContestAdapter {
    override suspend fun fetch(): List<Competition> {
        val url = "https://www.hackerrank.com/rest/contests/upcoming?offset=0&limit=50&contest_slug=active"
        val client = jsonClient()
        return try {
            val resp: HrEnvelope = client.get(url) {
                headers {
                    append("User-Agent", UA)
                    append("Accept", "application/json")
                }
            }.body()

            resp.models.map { c ->
                val sponsor = (c.company_name ?: c.organization)?.takeIf { it.isNotBlank() }
                val canonical = matcher.match(sponsor)
                Competition(
                    id = "hackerrank-${c.slug}",
                    platform = Platform.HACKERRANK,
                    title = c.name,
                    sponsorCompany = sponsor,
                    matchedCompany = canonical,
                    isTrackedCompany = canonical != null,
                    isHiring = sponsor != null,
                    startsAt = c.starts_at,
                    endsAt = c.ends_at,
                    registrationUrl = "https://www.hackerrank.com/contests/${c.slug}",
                    prizePool = null,
                    descriptionSnippet = (c.description ?: "").take(280),
                )
            }
        } catch (t: Throwable) {
            android.util.Log.w("HackerRank", "fetch failed: ${t.message}")
            emptyList()
        } finally {
            client.close()
        }
    }
}
