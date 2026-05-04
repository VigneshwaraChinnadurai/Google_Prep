package com.vc.jobfinder.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

data class ApiConfig(val baseUrl: String, val apiKey: String)

fun buildClient(config: ApiConfig) = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(WebSockets)
    install(Logging) { level = LogLevel.INFO }
    defaultRequest {
        url(config.baseUrl)
        headers.append("X-API-Key", config.apiKey)
        headers.append(HttpHeaders.Accept, "application/json")
    }
}
