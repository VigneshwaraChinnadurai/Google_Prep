package com.vc.jobfinder.data.repository

import com.vc.jobfinder.data.remote.*
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobFinderRepository @Inject constructor(
    private val holder: ConfigHolder,
) {
    private val client get() = holder.client
    private val config get() = holder.config

    // ---- Resume ----
    suspend fun uploadResume(bytes: ByteArray, filename: String): ResumeDto =
        client.post("api/v1/resume/upload") {
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, when {
                        filename.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                        else -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    })
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                })
            }))
        }.body()

    // ---- Pipeline ----
    suspend fun startScrape(): String =
        client.post("api/v1/pipeline/scrape").body<RunStartedDto>().run_id

    suspend fun startMatch(): String =
        client.post("api/v1/pipeline/match").body<RunStartedDto>().run_id

    fun runEvents(runId: String): Flow<RunEventDto> = streamEvents(
        path = "api/v1/pipeline/$runId/events"
    )

    // ---- Matches ----
    suspend fun listMatches(minScore: Double = 0.0): List<MatchResultDto> =
        client.get("api/v1/matches") { parameter("min_score", minScore) }.body()

    suspend fun applyPacket(matchId: String): ApplyPacketDto =
        client.get("api/v1/apply/$matchId/packet").body()

    suspend fun approve(matchId: String) {
        client.post("api/v1/matches/$matchId/approve")
    }

    suspend fun skip(matchId: String) {
        client.post("api/v1/matches/$matchId/skip")
    }

    // ---- Competitions ----
    suspend fun listCompetitions(
        trackedOnly: Boolean = false,
        hiringOnly: Boolean = false,
        platforms: List<String>? = null,
    ): List<CompetitionDto> = client.get("api/v1/competitions") {
        parameter("tracked_only", trackedOnly)
        parameter("hiring_only", hiringOnly)
        platforms?.forEach { parameter("platform", it) }
    }.body()

    suspend fun refreshCompetitions(): String =
        client.post("api/v1/competitions/refresh").body<RunStartedDto>().run_id

    fun competitionEvents(runId: String): Flow<RunEventDto> = streamEvents(
        path = "api/v1/competitions/$runId/events"
    )

    // ---- Internal ----
    private fun streamEvents(path: String): Flow<RunEventDto> = flow {
        val wsUrl = config.baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/')
        client.webSocket("$wsUrl/$path?api_key=${config.apiKey}") {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    emit(Json.decodeFromString<RunEventDto>(frame.readText()))
                }
            }
        }
    }
}
