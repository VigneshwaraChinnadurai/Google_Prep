package com.vc.jobfinder.data.remote.contests

import com.vc.jobfinder.data.remote.UA
import com.vc.jobfinder.data.remote.jsonClient
import com.vc.jobfinder.domain.Competition
import com.vc.jobfinder.domain.CompanyMatcher
import com.vc.jobfinder.domain.Platform
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnstopAdapter @Inject constructor(
    private val matcher: CompanyMatcher,
) : ContestAdapter {

    override suspend fun fetch(): List<Competition> {
        val out = mutableListOf<Competition>()
        for (oppType in listOf("hackathons", "competitions")) {
            out += fetchType(oppType)
        }
        return out
    }

    private suspend fun fetchType(oppType: String): List<Competition> {
        val client = jsonClient()
        return try {
            // Unstop responses are deeply nested and have inconsistent field
            // names — parse loosely as JsonElement and pull what we need.
            val raw: JsonElement = client.get("https://unstop.com/api/public/opportunity/search-result") {
                headers {
                    append("User-Agent", UA)
                    append("Accept", "application/json")
                }
                parameter("opportunity", oppType)
                parameter("per_page", 50)
                parameter("oppstatus", "open")
            }.body()

            val items = raw.jsonObject["data"]?.jsonObject?.get("data")?.jsonArray
                ?: return emptyList()

            items.mapNotNull { el ->
                runCatching {
                    val o = el.jsonObject
                    val id = o.string("id") ?: o.string("uuid") ?: return@runCatching null
                    val title = o.string("title") ?: ""
                    val sponsor = o["organisation"]
                        ?.let { runCatching { it.jsonObject.string("name") }.getOrNull() }
                        ?: o.string("organisation_name")
                    val canonical = matcher.match(sponsor)

                    var publicUrl = o.string("public_url") ?: o.string("seo_url") ?: ""
                    if (publicUrl.isNotBlank() && !publicUrl.startsWith("http")) {
                        publicUrl = "https://unstop.com/${publicUrl.trimStart('/')}"
                    }

                    val tags = o["filters"]?.jsonArray
                        ?.mapNotNull { runCatching { it.jsonObject.string("name") }.getOrNull() }
                        .orEmpty()
                    val typeStr = o.string("type") ?: ""
                    val isHiring = "hiring" in typeStr.lowercase() ||
                        (o["is_hiring"]?.jsonPrimitive?.content?.lowercase() == "true")

                    Competition(
                        id = "unstop-$id",
                        platform = Platform.UNSTOP,
                        title = title,
                        sponsorCompany = sponsor,
                        matchedCompany = canonical,
                        isTrackedCompany = canonical != null,
                        isHiring = isHiring,
                        startsAt = o.string("start_date") ?: o.string("registration_start_date"),
                        endsAt = o.string("end_date") ?: o.string("registration_end_date"),
                        registrationUrl = publicUrl.ifBlank { "https://unstop.com/" },
                        prizePool = o.string("prize_money"),
                        descriptionSnippet = (o.string("subtitle") ?: "").take(280),
                    )
                }.getOrNull()
            }
        } catch (t: Throwable) {
            android.util.Log.w("Unstop", "fetch failed for $oppType: ${t.message}")
            emptyList()
        } finally {
            client.close()
        }
    }

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" } }
            .getOrNull()
}
