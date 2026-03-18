package com.vignesh.leetcodechecker.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class GitHubContentFileResponse(
    val sha: String?
)

data class GitHubUpsertRequest(
    val message: String,
    val content: String,
    val branch: String,
    val sha: String? = null
)

interface GitHubApi {
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFile(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String
    ): GitHubContentFileResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun upsertFile(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body request: GitHubUpsertRequest
    ): Any
}
