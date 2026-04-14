package com.vignesh.leetcodechecker.pipeline

import android.util.Log
import com.vignesh.leetcodechecker.data.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Data fetcher — fetches real-time data from 4 sources:
 *
 * 1. Gemini Grounded Search (Google Search tool)
 * 2. SEC EDGAR XBRL API (financial facts)
 * 3. SEC EDGAR Full-Text Search
 * 4. Google News RSS Feed
 *
 * Ported from Python web_fetcher.py.
 */
class DataFetcher(
    private val geminiApi: GeminiApi,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash"
) {
    companion object {
        private const val TAG = "DataFetcher"
        private const val SEC_UA = "StrategicAnalysisBot/1.0 (contact: autonomous-analysis@example.com)"

        private val FACT_KEYS = listOf(
            "Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax",
            "SalesRevenueNet", "NetIncomeLoss", "OperatingIncomeLoss",
            "GrossProfit", "CostOfGoodsAndServicesSold", "ResearchAndDevelopmentExpense",
            "EarningsPerShareBasic", "EarningsPerShareDiluted"
        )

        private val GROUNDED_SYSTEM = """You are a financial research assistant with access to Google Search.
For the given query, provide a detailed factual summary including specific numbers,
dates, and sources. Focus on the most recent and relevant data available."""
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch data from all sources based on the search plan.
     * Supports selective fetching based on agentic routing decisions.
     * Returns list of PipelineDocuments.
     */
    suspend fun fetchAll(
        plan: PipelineSearchPlan,
        fetchSec: Boolean = true,
        fetchNews: Boolean = true
    ): List<PipelineDocument> {
        val docs = mutableListOf<PipelineDocument>()
        var docIdx = 0

        // 1. Gemini Grounded Search (always enabled if queries exist)
        for (query in plan.groundingQueries) {
            try {
                val doc = fetchGrounded(query, "grounded_${docIdx++}")
                if (doc != null) docs.add(doc)
            } catch (e: Exception) {
                Log.w(TAG, "Grounded search failed for '$query': ${e.message}")
            }
        }

        // 2. SEC EDGAR XBRL (optional based on agentic routing)
        if (fetchSec) {
            for ((ticker, cik) in plan.tickerToCik) {
                try {
                    val doc = fetchSecEdgar(ticker, cik, "sec_xbrl_${docIdx++}")
                    if (doc != null) docs.add(doc)
                } catch (e: Exception) {
                    Log.w(TAG, "SEC EDGAR failed for $ticker: ${e.message}")
                }
            }

            // 3. SEC Full-Text Search
            for (query in plan.secFulltextQueries) {
                try {
                    val doc = fetchSecFulltext(query, "sec_ft_${docIdx++}")
                    if (doc != null) docs.add(doc)
                } catch (e: Exception) {
                    Log.w(TAG, "SEC fulltext failed for '$query': ${e.message}")
                }
            }
        } else {
            Log.i(TAG, "Skipping SEC data fetch (agentic router decision)")
        }

        // 4. Google News RSS (optional based on agentic routing)
        if (fetchNews) {
            for (query in plan.newsQueries) {
                try {
                    val newDocs = fetchNewsRss(query, docIdx)
                    docIdx += newDocs.size
                    docs.addAll(newDocs)
                } catch (e: Exception) {
                    Log.w(TAG, "News RSS failed for '$query': ${e.message}")
                }
            }
        } else {
            Log.i(TAG, "Skipping News data fetch (agentic router decision)")
        }

        Log.i(TAG, "Fetched ${docs.size} documents total (SEC=${fetchSec}, News=${fetchNews})")
        return docs
    }

    // ════════════════════════════════════════════════════════════════
    // 1. Gemini Grounded Search
    // ════════════════════════════════════════════════════════════════

    private suspend fun fetchGrounded(query: String, docId: String): PipelineDocument? {
        val request = GeminiGenerateRequest(
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = GROUNDED_SYSTEM))
            ),
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = query)))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.2,
                maxOutputTokens = 4096,
                responseMimeType = "text/plain"
            ),
            tools = listOf(GeminiTool(googleSearch = emptyMap()))
        )

        val response = geminiApi.generateContent(model, apiKey, request)
        val text = response.candidates?.firstOrNull()?.content?.parts
            ?.mapNotNull { it.text }?.joinToString("") ?: return null

        if (text.isBlank()) return null

        // Collect grounding sources
        val sources = response.candidates?.firstOrNull()?.groundingMetadata
            ?.groundingChunks?.mapNotNull { it.web?.uri } ?: emptyList()

        return PipelineDocument(
            content = text,
            docId = docId,
            metadata = mutableMapOf(
                "source" to "gemini_grounded",
                "query" to query,
                "urls" to sources.take(5).joinToString(", ")
            )
        )
    }

    // ════════════════════════════════════════════════════════════════
    // 2. SEC EDGAR XBRL API
    // ════════════════════════════════════════════════════════════════

    private fun fetchSecEdgar(ticker: String, cik: String, docId: String): PipelineDocument? {
        val paddedCik = cik.padStart(10, '0')
        val url = "https://data.sec.gov/api/xbrl/companyfacts/CIK$paddedCik.json"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", SEC_UA)
            .header("Accept", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "SEC EDGAR HTTP ${response.code} for $ticker")
            return null
        }

        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        val facts = json.optJSONObject("facts")
        val usGaap = facts?.optJSONObject("us-gaap") ?: return null

        val lines = mutableListOf<String>()
        lines.add("SEC EDGAR XBRL Data for $ticker (CIK: $cik)")
        lines.add("=" .repeat(60))

        for (factKey in FACT_KEYS) {
            val factObj = usGaap.optJSONObject(factKey) ?: continue
            val units = factObj.optJSONObject("units") ?: continue

            // Try USD first, then USD/shares
            val unitKey = when {
                units.has("USD") -> "USD"
                units.has("USD/shares") -> "USD/shares"
                else -> continue
            }
            val dataArr = units.optJSONArray(unitKey) ?: continue

            // Get last 8 entries (most recent)
            val start = maxOf(0, dataArr.length() - 8)
            lines.add("\n$factKey:")
            for (i in start until dataArr.length()) {
                val entry = dataArr.getJSONObject(i)
                val period = entry.optString("end", "?")
                val form = entry.optString("form", "?")
                val value = entry.optDouble("val", 0.0)
                val valueStr = if (unitKey == "USD") {
                    "\$${String.format("%.2f", value / 1_000_000_000)}B"
                } else {
                    "\$${String.format("%.2f", value)}"
                }
                lines.add("  $period ($form): $valueStr")
            }
        }

        val content = lines.joinToString("\n")
        if (content.lines().size < 5) return null

        return PipelineDocument(
            content = content,
            docId = docId,
            metadata = mutableMapOf(
                "source" to "sec_edgar",
                "ticker" to ticker,
                "cik" to cik
            )
        )
    }

    // ════════════════════════════════════════════════════════════════
    // 3. SEC EDGAR Full-Text Search
    // ════════════════════════════════════════════════════════════════

    private fun fetchSecFulltext(query: String, docId: String): PipelineDocument? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://efts.sec.gov/LATEST/search-index?q=$encoded&dateRange=custom" +
                "&startdt=2024-01-01&forms=10-K,10-Q,8-K"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", SEC_UA)
            .header("Accept", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        val hits = json.optJSONObject("hits")?.optJSONArray("hits") ?: return null

        val lines = mutableListOf<String>()
        lines.add("SEC Full-Text Search: '$query'")
        lines.add("=" .repeat(60))

        for (i in 0 until minOf(hits.length(), 5)) {
            val hit = hits.getJSONObject(i)
            val source = hit.optJSONObject("_source") ?: continue
            val name = source.optString("entity_name", "?")
            val form = source.optString("form_type", "?")
            val date = source.optString("file_date", "?")
            lines.add("\n$name — $form ($date)")

            // Include highlight snippets if available
            val highlights = hit.optJSONObject("highlight")
            if (highlights != null) {
                val keys = highlights.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val frags = highlights.optJSONArray(key)
                    if (frags != null) {
                        for (j in 0 until minOf(frags.length(), 2)) {
                            lines.add("  ${frags.getString(j).replace(Regex("<[^>]+>"), "")}")
                        }
                    }
                }
            }
        }

        val content = lines.joinToString("\n")
        return PipelineDocument(
            content = content,
            docId = docId,
            metadata = mutableMapOf(
                "source" to "sec_fulltext",
                "query" to query
            )
        )
    }

    // ════════════════════════════════════════════════════════════════
    // 4. Google News RSS
    // ════════════════════════════════════════════════════════════════

    private fun fetchNewsRss(query: String, startIdx: Int): List<PipelineDocument> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://news.google.com/rss/search?q=$encoded&hl=en-US&gl=US&ceid=US:en"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val xml = response.body?.string() ?: return emptyList()
        return parseRssXml(xml, query, startIdx)
    }

    private fun parseRssXml(xml: String, query: String, startIdx: Int): List<PipelineDocument> {
        val docs = mutableListOf<PipelineDocument>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var inItem = false
            var title = ""
            var description = ""
            var pubDate = ""
            var link = ""
            var currentTag = ""
            var idx = startIdx

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag == "item") {
                            inItem = true
                            title = ""; description = ""; pubDate = ""; link = ""
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inItem) {
                            when (currentTag) {
                                "title" -> title = parser.text ?: ""
                                "description" -> description = parser.text ?: ""
                                "pubDate" -> pubDate = parser.text ?: ""
                                "link" -> link = parser.text ?: ""
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item" && inItem) {
                            inItem = false
                            if (title.isNotBlank()) {
                                val content = buildString {
                                    appendLine(title)
                                    if (description.isNotBlank()) appendLine(description)
                                    if (pubDate.isNotBlank()) appendLine("Published: $pubDate")
                                }
                                docs.add(
                                    PipelineDocument(
                                        content = content,
                                        docId = "news_${idx++}",
                                        metadata = mutableMapOf(
                                            "source" to "google_news",
                                            "query" to query,
                                            "url" to link,
                                            "date" to pubDate
                                        )
                                    )
                                )
                            }
                            if (docs.size - startIdx >= 10) break // Max 10 per query
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "RSS XML parse error: ${e.message}")
        }
        return docs
    }
}

// ════════════════════════════════════════════════════════════════════════
// CIK Lookup — resolve ticker to SEC CIK number
// ════════════════════════════════════════════════════════════════════════

object CikLookup {
    private const val TAG = "CikLookup"
    private const val TICKERS_URL = "https://www.sec.gov/files/company_tickers.json"

    private var tickerMap: Map<String, String>? = null

    fun lookupCik(ticker: String, httpClient: OkHttpClient): String? {
        if (tickerMap == null) {
            try {
                val request = Request.Builder()
                    .url(TICKERS_URL)
                    .header("User-Agent", "StrategicAnalysisBot/1.0 (contact: autonomous-analysis@example.com)")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    val map = mutableMapOf<String, String>()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = json.getJSONObject(key)
                        val t = obj.optString("ticker", "").uppercase()
                        val cik = obj.optString("cik_str", "")
                        if (t.isNotBlank() && cik.isNotBlank()) {
                            map[t] = cik
                        }
                    }
                    tickerMap = map
                    Log.i(TAG, "Loaded ${map.size} ticker→CIK mappings")
                }
            } catch (e: Exception) {
                Log.w(TAG, "CIK lookup failed: ${e.message}")
            }
        }
        return tickerMap?.get(ticker.uppercase())
    }
}
