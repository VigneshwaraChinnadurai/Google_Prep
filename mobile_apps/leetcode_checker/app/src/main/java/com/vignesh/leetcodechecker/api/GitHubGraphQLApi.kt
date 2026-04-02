package com.vignesh.leetcodechecker.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * GitHub GraphQL API for fetching contribution data
 * Uses GitHub's GraphQL API v4
 */
interface GitHubGraphQLApi {
    @POST("graphql")
    suspend fun query(
        @Header("Authorization") auth: String,
        @Body request: GraphQLRequest
    ): GraphQLResponse
}

@JsonClass(generateAdapter = true)
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class GraphQLResponse(
    val data: GitHubData?,
    val errors: List<GraphQLError>? = null
)

@JsonClass(generateAdapter = true)
data class GraphQLError(
    val message: String,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubData(
    val user: GitHubUser?
)

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String?,
    val name: String?,
    val avatarUrl: String?,
    val bio: String?,
    val company: String?,
    val location: String?,
    val followers: FollowerConnection?,
    val following: FollowingConnection?,
    val repositories: RepositoryConnection?,
    val contributionsCollection: ContributionsCollection?
)

@JsonClass(generateAdapter = true)
data class FollowerConnection(
    val totalCount: Int?
)

@JsonClass(generateAdapter = true)
data class FollowingConnection(
    val totalCount: Int?
)

@JsonClass(generateAdapter = true)
data class RepositoryConnection(
    val totalCount: Int?
)

@JsonClass(generateAdapter = true)
data class ContributionsCollection(
    val totalCommitContributions: Int?,
    val totalPullRequestContributions: Int?,
    val totalIssueContributions: Int?,
    val totalRepositoryContributions: Int?,
    val contributionCalendar: ContributionCalendar?
)

@JsonClass(generateAdapter = true)
data class ContributionCalendar(
    val totalContributions: Int?,
    val weeks: List<ContributionWeek>?
)

@JsonClass(generateAdapter = true)
data class ContributionWeek(
    val contributionDays: List<ContributionDay>?
)

@JsonClass(generateAdapter = true)
data class ContributionDay(
    val contributionCount: Int?,
    val date: String?,
    val color: String?,
    val weekday: Int?
)

/**
 * GraphQL Query to fetch user contributions and profile
 */
object GitHubQueries {
    fun userContributions(username: String): String = """
        query {
          user(login: "$username") {
            login
            name
            avatarUrl
            bio
            company
            location
            followers { totalCount }
            following { totalCount }
            repositories(privacy: PUBLIC) { totalCount }
            contributionsCollection {
              totalCommitContributions
              totalPullRequestContributions
              totalIssueContributions
              totalRepositoryContributions
              contributionCalendar {
                totalContributions
                weeks {
                  contributionDays {
                    contributionCount
                    date
                    color
                    weekday
                  }
                }
              }
            }
          }
        }
    """.trimIndent()
}
