package com.vc.jobfinder.data.remote.contests

import com.vc.jobfinder.data.remote.UA
import com.vc.jobfinder.data.remote.jsonClient
import com.vc.jobfinder.domain.Competition
import com.vc.jobfinder.domain.CompanyMatcher
import com.vc.jobfinder.domain.Platform
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class KaggleListBody(
    val page: Int = 1,
    val group: String = "active",
    val category: String = "all",
    val sortBy: String = "recentlyCreated",
)

@Serializable
private data class KaggleResp(val competitions: List<KaggleComp> = emptyList())

@Serializable
private data class KaggleComp(
    val id: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val competitionUrl: String? = null,
    val slug: String? = null,
    val category: String? = null,
    val organizationName: String? = null,
    val rewardDisplay: String? = null,
    val enabledDate: String? = null,
    val deadline: String? = null,
)

@Singleton
class KaggleAdapter @Inject constructor(
    private val matcher: CompanyMatcher,
) : ContestAdapter {

    override suspend fun fetch(): List<Competition> {
        val url =
            "https://www.kaggle.com/api/i/competitions.CompetitionService/ListCompetitions"
        val client = jsonClient()
        return try {
            val resp: KaggleResp = client.post(url) {
                contentType(ContentType.Application.Json)
                headers {
                    append("User-Agent", UA)
                    append("Accept", "application/json")
                }
                setBody(KaggleListBody())
            }.body()

            resp.competitions.map { c ->
                val category = (c.category ?: "").lowercase()
                val sponsor = c.organizationName
                val canonical = matcher.match(sponsor)
                val slug = c.competitionUrl ?: c.slug ?: ""
                val absUrl = if (slug.startsWith("/")) "https://www.kaggle.com$slug" else slug

                Competition(
                    id = "kaggle-${c.id ?: slug.trimEnd('/').substringAfterLast('/')}",
                    platform = Platform.KAGGLE,
                    title = (c.title ?: "").trim(),
                    sponsorCompany = sponsor,
                    matchedCompany = canonical,
                    isTrackedCompany = canonical != null,
                    isHiring = "recruitment" in category,
                    startsAt = c.enabledDate,
                    endsAt = c.deadline,
                    registrationUrl = absUrl.ifBlank { "https://www.kaggle.com/competitions" },
                    prizePool = c.rewardDisplay,
                    descriptionSnippet = (c.description ?: "").take(280),
                )
            }
        } catch (t: Throwable) {
            android.util.Log.w("Kaggle", "fetch failed: ${t.message}")
            emptyList()
        } finally {
            client.close()
        }
    }
}
