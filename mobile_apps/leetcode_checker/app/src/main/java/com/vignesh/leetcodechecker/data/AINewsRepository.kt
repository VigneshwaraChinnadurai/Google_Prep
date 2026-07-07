package com.vignesh.leetcodechecker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Repository for fetching AI/ML/Quantum Computing news.
 * Pulls real items from public RSS feeds -- no API key required.
 */
class AINewsRepository(private val context: Context) {

    private data class FeedSource(val url: String, val sourceName: String, val categoryTags: List<String>)

    private val feeds = listOf(
        FeedSource("https://export.arxiv.org/rss/cs.AI", "arXiv cs.AI", listOf("AI", "Research")),
        FeedSource("https://export.arxiv.org/rss/quant-ph", "arXiv Quantum Physics", listOf("Quantum", "Research")),
        FeedSource("https://huggingface.co/blog/feed.xml", "Hugging Face Blog", listOf("LLM", "Industry", "Open Source")),
        FeedSource("https://openai.com/news/rss.xml", "OpenAI News", listOf("AI", "LLM", "Industry"))
    )

    /**
     * Fetch AI news from all configured feeds.
     * Returns cached data if available and fresh, otherwise fetches new data.
     */
    suspend fun fetchAINews(forceRefresh: Boolean = false): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        try {
            if (!forceRefresh && !AINewsStorage.isCacheStale(context)) {
                val cached = AINewsStorage.getCachedNews(context)
                if (cached.isNotEmpty()) {
                    return@withContext Result.success(cached)
                }
            }

            // Each feed fails independently so one dead source doesn't blank the whole screen.
            val combinedNews = feeds
                .flatMap { feed -> runCatching { fetchRssFeed(feed) }.getOrElse { emptyList() } }
                .distinctBy { it.title }
                .sortedByDescending { parseRssDate(it.pubDate) }

            if (combinedNews.isEmpty()) {
                val cached = AINewsStorage.getCachedNews(context)
                return@withContext if (cached.isNotEmpty()) {
                    Result.success(cached)
                } else {
                    Result.failure(Exception("Couldn't reach any news source. Check your connection and try again."))
                }
            }

            AINewsStorage.cacheNews(context, combinedNews)
            Result.success(combinedNews)
        } catch (e: Exception) {
            val cached = AINewsStorage.getCachedNews(context)
            if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e)
        }
    }

    private fun fetchRssFeed(feed: FeedSource): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()

        val connection = URL(feed.url).openConnection()
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        val xmlContent = connection.getInputStream().bufferedReader().readText()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))

        var eventType = parser.eventType
        var inItem = false
        var title = ""
        var link = ""
        var description = ""
        var pubDate = ""
        val categories = mutableListOf<String>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = ""; link = ""; description = ""; pubDate = ""
                        categories.clear()
                    }
                    "title" -> if (inItem) title = parser.nextText()
                    "link" -> if (inItem) link = parser.nextText()
                    "description" -> if (inItem) description = parser.nextText()
                    "pubDate" -> if (inItem) pubDate = parser.nextText()
                    "category" -> if (inItem) categories.add(parser.nextText())
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        if (title.isNotBlank() && link.isNotBlank()) {
                            val cleanDescription = description.trim().take(400)
                            articles.add(
                                NewsArticle(
                                    title = title.trim().take(180),
                                    description = cleanDescription.ifBlank { null },
                                    content = description.trim(),
                                    url = link.trim(),
                                    urlToImage = null,
                                    image_url = null,
                                    source_id = feed.sourceName.lowercase(Locale.US).replace(" ", "_"),
                                    source = NewsSource(feed.sourceName, feed.sourceName),
                                    pubDate = pubDate,
                                    publishedAt = pubDate,
                                    author = feed.sourceName,
                                    creator = listOf(feed.sourceName),
                                    category = (feed.categoryTags + categories).distinct(),
                                    keywords = categories
                                )
                            )
                        }
                        inItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return articles.take(15)
    }

    private fun parseRssDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(dateStr)?.time
        }.getOrNull() ?: 0L
    }

    /**
     * Get news by category
     */
    suspend fun getNewsByCategory(category: NewsCategory): List<NewsArticle> {
        val allNews = fetchAINews().getOrDefault(emptyList())
        return allNews.filter { article ->
            val content = "${article.title} ${article.description}".lowercase()
            category.keywords.any { keyword -> content.contains(keyword.lowercase()) }
        }
    }

    /**
     * Search news by query
     */
    suspend fun searchNews(query: String): List<NewsArticle> {
        val allNews = fetchAINews().getOrDefault(emptyList())
        val queryLower = query.lowercase()
        return allNews.filter { article ->
            val content = "${article.title} ${article.description} ${article.content}".lowercase()
            content.contains(queryLower)
        }
    }
}
