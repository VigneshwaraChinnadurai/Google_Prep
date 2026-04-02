package com.vignesh.leetcodechecker.data

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * AI/ML News API - Uses NewsData.io or similar free news API
 * Fetches news related to AI, Machine Learning, and Quantum Computing
 */

// Data models for news articles
data class NewsResponse(
    val status: String?,
    val totalResults: Int?,
    val results: List<NewsArticle>?,
    val articles: List<NewsArticle>?  // For compatibility with different API formats
)

data class NewsArticle(
    val title: String?,
    val description: String?,
    val content: String?,
    val url: String?,
    val urlToImage: String?,
    val image_url: String?,  // For newsdata.io format
    val source_id: String?,
    val source: NewsSource?,
    val pubDate: String?,
    val publishedAt: String?,
    val author: String?,
    val creator: List<String>?,
    val category: List<String>?,
    val keywords: List<String>?
)

data class NewsSource(
    val id: String?,
    val name: String?
)

// Data models for tracking people announcements
data class TrackedPerson(
    val name: String,
    val twitterHandle: String? = null,
    val keywords: List<String> = emptyList(),
    val isEnabled: Boolean = true
)

// Pre-defined AI thought leaders
val DEFAULT_AI_PEOPLE = listOf(
    TrackedPerson("Sam Altman", "@sama", listOf("OpenAI", "ChatGPT", "AGI")),
    TrackedPerson("Elon Musk", "@elonmusk", listOf("xAI", "Grok", "Tesla AI")),
    TrackedPerson("Sundar Pichai", "@sundarpichai", listOf("Google AI", "Gemini", "DeepMind")),
    TrackedPerson("Jensen Huang", null, listOf("NVIDIA", "CUDA", "GPU", "AI chips")),
    TrackedPerson("Satya Nadella", "@sataborman", listOf("Microsoft", "Copilot", "Azure AI")),
    TrackedPerson("Yann LeCun", "@ylecun", listOf("Meta AI", "Deep Learning")),
    TrackedPerson("Andrew Ng", "@AndrewYNg", listOf("AI Fund", "Coursera AI")),
    TrackedPerson("Demis Hassabis", null, listOf("DeepMind", "AlphaGo", "Gemini")),
    TrackedPerson("Ilya Sutskever", null, listOf("SSI", "Safe Superintelligence")),
    TrackedPerson("Dario Amodei", null, listOf("Anthropic", "Claude", "AI Safety"))
)

// Categories for news filtering
enum class NewsCategory(val displayName: String, val keywords: List<String>) {
    AI_GENERAL("🤖 AI General", listOf("artificial intelligence", "AI", "machine learning", "ML")),
    QUANTUM("⚛️ Quantum Computing", listOf("quantum computing", "quantum", "qubits", "IBM Quantum", "Google Quantum")),
    LLM("💬 LLMs & ChatBots", listOf("LLM", "GPT", "Claude", "Gemini", "ChatGPT", "language model")),
    RESEARCH("📚 Research & Papers", listOf("AI research", "deep learning", "neural network", "transformer")),
    INDUSTRY("🏢 Industry News", listOf("OpenAI", "Google DeepMind", "Anthropic", "Meta AI", "Microsoft AI")),
    PEOPLE("👤 People & Announcements", listOf("announces", "launched", "revealed", "unveils"))
}

interface NewsApi {
    // Using NewsAPI.org free tier
    @GET("v2/everything")
    suspend fun getAINews(
        @Query("q") query: String = "(artificial intelligence OR machine learning OR AI OR quantum computing)",
        @Query("language") language: String = "en",
        @Query("sortBy") sortBy: String = "publishedAt",
        @Query("pageSize") pageSize: Int = 20,
        @Query("apiKey") apiKey: String
    ): NewsResponse
    
    @GET("v2/top-headlines")
    suspend fun getTechHeadlines(
        @Query("category") category: String = "technology",
        @Query("language") language: String = "en",
        @Query("pageSize") pageSize: Int = 10,
        @Query("apiKey") apiKey: String
    ): NewsResponse
}
