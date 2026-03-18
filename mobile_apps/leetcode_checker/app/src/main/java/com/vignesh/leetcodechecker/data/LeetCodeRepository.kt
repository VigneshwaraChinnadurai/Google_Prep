package com.vignesh.leetcodechecker.data

import com.squareup.moshi.Moshi
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

data class DailyChallengeUiModel(
    val date: String,
    val title: String,
    val difficulty: String,
    val questionId: String,
    val tags: List<String>,
    val url: String,
    val descriptionPreview: String
)

class LeetCodeRepository {
    private val api: LeetCodeApi by lazy {
        val moshi = Moshi.Builder().build()
        Retrofit.Builder()
            .baseUrl("https://leetcode.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LeetCodeApi::class.java)
    }

    suspend fun fetchDailyChallenge(): Result<DailyChallengeUiModel> {
        return runCatching {
            val dailyQuery = """
                query questionOfToday {
                  activeDailyCodingChallengeQuestion {
                    date
                    link
                    question {
                      title
                      titleSlug
                      difficulty
                      questionFrontendId
                      topicTags {
                        name
                        slug
                      }
                    }
                  }
                }
            """.trimIndent()

            val dailyResponse = api.postQuery(GraphQLRequest(query = dailyQuery))
            val daily = dailyResponse.data?.activeDailyCodingChallengeQuestion
                ?: error("Daily challenge data not available")

            val detailQuery = """
                query questionContent(
                  ${'$'}titleSlug: String!
                ) {
                  question(
                    titleSlug: ${'$'}titleSlug
                  ) {
                    content
                    exampleTestcases
                  }
                }
            """.trimIndent()

            val detailsResponse = api.postQuestionDetails(
                GraphQLRequest(
                    query = detailQuery,
                    variables = mapOf("titleSlug" to daily.question.titleSlug)
                )
            )

            val content = detailsResponse.data?.question?.content
                ?.replace(Regex("<[^>]*>"), " ")
                ?.replace("&nbsp;", " ")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                .orEmpty()

            DailyChallengeUiModel(
                date = daily.date,
                title = daily.question.title,
                difficulty = daily.question.difficulty,
                questionId = daily.question.questionFrontendId,
                tags = daily.question.topicTags.map { it.name },
                url = "https://leetcode.com${daily.link}",
                descriptionPreview = content.take(280)
            )
        }
    }
}
