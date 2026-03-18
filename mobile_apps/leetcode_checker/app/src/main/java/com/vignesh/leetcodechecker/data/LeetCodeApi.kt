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
    val exampleTestcases: String?
)

interface LeetCodeApi {
    @POST("graphql")
    suspend fun postQuery(@Body request: GraphQLRequest): DailyChallengeResponse

    @POST("graphql")
    suspend fun postQuestionDetails(@Body request: GraphQLRequest): QuestionDetailsResponse
}
