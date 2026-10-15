package com.vc.jobfinder.data.remote.contests

import com.vc.jobfinder.data.remote.UA
import com.vc.jobfinder.domain.Competition
import com.vc.jobfinder.domain.CompanyMatcher
import com.vc.jobfinder.domain.Platform
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HackerEarthAdapter @Inject constructor(
    private val matcher: CompanyMatcher,
) : ContestAdapter {

    override suspend fun fetch(): List<Competition> {
        val url = "https://www.hackerearth.com/challenges/hiring/"
        val client = HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
            }
        }
        return try {
            val html = client.get(url) {
                headers { append("User-Agent", UA) }
            }.bodyAsText()

            val doc = Jsoup.parse(html, "https://www.hackerearth.com/")
            doc.select("div.challenge-card-modern").mapNotNull { card ->
                runCatching {
                    val link = card.selectFirst("a.challenge-card-wrapper")
                        ?: card.selectFirst("a")
                        ?: return@runCatching null
                    val href = link.attr("abs:href").ifBlank { return@runCatching null }
                    val title = (card.selectFirst(".challenge-list-title") ?: card)
                        .text().take(200)
                    val sponsor = card.selectFirst(".challenge-list-company")?.text()
                    val canonical = matcher.match(sponsor)
                    val cid = href.trimEnd('/').substringAfterLast('/')
                        .ifBlank { title.lowercase().replace(' ', '-').take(50) }

                    Competition(
                        id = "hackerearth-$cid",
                        platform = Platform.HACKEREARTH,
                        title = title,
                        sponsorCompany = sponsor,
                        matchedCompany = canonical,
                        isTrackedCompany = canonical != null,
                        isHiring = true,  // listing page is /hiring/
                        startsAt = null,
                        endsAt = null,
                        registrationUrl = href,
                        prizePool = null,
                        descriptionSnippet = title,
                    )
                }.getOrNull()
            }
        } catch (t: Throwable) {
            android.util.Log.w("HackerEarth", "fetch failed: ${t.message}")
            emptyList()
        } finally {
            client.close()
        }
    }
}
