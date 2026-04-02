package com.vignesh.leetcodechecker.data

import retrofit2.http.Body
import retrofit2.http.POST

data class GraphQLRequest(
    val query: String,
    val variables: Map<String, String>? = null
)

data class DailyChallengeResponse(
    val data: DailyChallengeData?
)

data class DailyChallengeData(
    val activeDailyCodingChallengeQuestion: DailyChallenge?
)

data class DailyChallenge(
    val date: String,
    val link: String,
    val question: DailyQuestion
)

data class DailyQuestion(
    val title: String,
    val titleSlug: String,
    val difficulty: String,
    val questionFrontendId: String,
    val topicTags: List<TopicTag>
)

data class TopicTag(
    val name: String,
    val slug: String
)

data class QuestionDetailsResponse(
    val data: QuestionDetailsData?
)

data class QuestionDetailsData(
    val question: QuestionDetails?
)

data class QuestionDetails(
    val content: String?,
    val exampleTestcases: String?,
    val codeSnippets: List<CodeSnippet>?
)

data class CodeSnippet(
    val lang: String,
    val langSlug: String,
    val code: String
)

// Global Ranking Response
data class GlobalRankingResponse(
    val data: GlobalRankingData?
)

data class GlobalRankingData(
    val globalRanking: GlobalRanking?
)

data class GlobalRanking(
    val totalUsers: Int?,
    val rankingNodes: List<RankingNode>?
)

data class RankingNode(
    val ranking: Int,
    val currentRating: Int?,
    val currentGlobalRanking: Int?,
    val dataRegion: String?,
    val user: RankingUser?
)

data class RankingUser(
    val username: String?,
    val profile: RankingProfile?
)

data class RankingProfile(
    val userAvatar: String?,
    val countryName: String?
)

// User Profile Response (for getting user's own rank)
data class UserProfileResponse(
    val data: UserProfileData?
)

data class UserProfileData(
    val matchedUser: MatchedUser?
)

data class MatchedUser(
    val username: String?,
    val profile: UserProfile?,
    val submitStats: SubmitStats?
)

data class UserProfile(
    val ranking: Int?,
    val userAvatar: String?,
    val realName: String?,
    val countryName: String?
)

data class SubmitStats(
    val acSubmissionNum: List<AcSubmission>?
)

data class AcSubmission(
    val difficulty: String?,
    val count: Int?
)

interface LeetCodeApi {
    @POST("graphql")
    suspend fun postQuery(@Body request: GraphQLRequest): DailyChallengeResponse

    @POST("graphql")
    suspend fun postQuestionDetails(@Body request: GraphQLRequest): QuestionDetailsResponse
    
    @POST("graphql")
    suspend fun getGlobalRanking(@Body request: GraphQLRequest): GlobalRankingResponse
    
    @POST("graphql")
    suspend fun getUserProfile(@Body request: GraphQLRequest): UserProfileResponse
}
