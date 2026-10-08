package com.vignesh.jobmatcher.sources

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
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

    companion object {
        /** The only place in the app that builds a Retrofit client. */
        fun create(): CareersApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android) JobMatcher/1.0")
                            .header("Accept", "application/json")
                            .build()
                    )
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
    }
}
