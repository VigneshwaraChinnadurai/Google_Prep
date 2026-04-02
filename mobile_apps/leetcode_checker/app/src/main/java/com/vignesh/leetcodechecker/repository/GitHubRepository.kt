package com.vignesh.leetcodechecker.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leetcodechecker.BuildConfig
import com.vignesh.leetcodechecker.api.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Repository for fetching GitHub profile and contribution data
 */
class GitHubRepository {
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
    
    private val api = retrofit.create(GitHubGraphQLApi::class.java)
    
    /**
     * Fetch user profile with contributions
     */
    suspend fun getUserProfile(username: String = BuildConfig.GITHUB_OWNER): Result<GitHubUser> {
        return try {
            val token = BuildConfig.GITHUB_TOKEN
            if (token.isBlank()) {
                return Result.failure(Exception("GitHub token not configured"))
            }
            
            val query = GitHubQueries.userContributions(username)
            val request = GraphQLRequest(query = query)
            val response = api.query("Bearer $token", request)
            
            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(Exception(response.errors.first().message))
            } else if (response.data?.user != null) {
                Result.success(response.data.user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
