package com.vignesh.jobmatcher.sources

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Every careers API is called through absolute @Url requests, so one Retrofit instance
 * covers all ATS hosts. Responses come back as raw strings and are parsed by JobParsers.
 */
interface CareersApi {
    @GET
    suspend fun get(@Url url: String): String

    @POST
    suspend fun post(@Url url: String, @Body body: RequestBody): String

    /** Job pages added from links (LinkedIn etc.) -- served to a browser-like client. */
    @GET
    suspend fun getHtml(
        @Url url: String,
        @Header("Accept") accept: String = "text/html,application/xhtml+xml",
        @Header("User-Agent") userAgent: String = BROWSER_UA
    ): String

    companion object {
        /** The only place in the app that builds a Retrofit client. */
        fun create(): CareersApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    // Defaults for API calls; getHtml supplies its own browser-like headers.
                    val req = chain.request()
                    val builder = req.newBuilder()
                    if (req.header("User-Agent") == null) builder.header("User-Agent", "Mozilla/5.0 (Linux; Android) JobMatcher/1.0")
                    if (req.header("Accept") == null) builder.header("Accept", "application/json")
                    chain.proceed(builder.build())
                }
                .build()
            return Retrofit.Builder()
                .baseUrl("https://boards-api.greenhouse.io/")
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
                .create(CareersApi::class.java)
        }

        fun jsonBody(json: String): RequestBody = json.toRequestBody("application/json".toMediaType())

        const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"
    }
}

/**
 * GET that insists on a JSON body. Some careers sites (Eightfold) intermittently serve
 * their HTML app shell instead of JSON when throttling, so non-JSON is retried with backoff.
 */
suspend fun CareersApi.getJson(url: String): String {
    var lastError: Throwable? = null
    repeat(3) { attempt ->
        val body = runCatching { get(url) }.onFailure { lastError = it }.getOrNull()
        val head = body?.trimStart()
        if (head != null && (head.startsWith("{") || head.startsWith("["))) return body
        delay(1500L * (attempt + 1))
    }
    throw lastError ?: IllegalStateException("Careers site returned a non-JSON page (throttled?) -- try again later.")
}
