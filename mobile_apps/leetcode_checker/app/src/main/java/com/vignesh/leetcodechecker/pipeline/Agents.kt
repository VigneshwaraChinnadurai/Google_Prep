package com.vignesh.leetcodechecker.pipeline

import android.util.Log
import com.vignesh.leetcodechecker.data.*
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Agent classes for the Deep Analysis pipeline — ported from Python agents.py
 * and query_planner.py.
 *
 * Includes:
 * - QueryPlanner — generates SearchPlan from user's question
 * - AnalystAgent — plan, extract, analyse, strategize, summarize
 * - CritiqueModule — LLM-powered quality gate
 */

// ════════════════════════════════════════════════════════════════════════
// Query Planner — generates SearchPlan from any strategic question
// ════════════════════════════════════════════════════════════════════════

class QueryPlanner(
    private val geminiApi: GeminiApi,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "QueryPlanner"

        private const val PLANNER_SYSTEM = """You are a strategic research planner. Given ANY strategic question or analysis request, generate a comprehensive search plan.

Your job is to decompose the question into:
1. Domain identification (e.g., "cloud computing", "semiconductors", "retail")
2. Key companies involved (with stock tickers if public)
3. Specific web search queries to gather data
4. News search queries for recent developments
5. SEC filing search queries (if US public companies are involved)

Return ONLY valid JSON matching the provided schema.

Guidelines:
- Generate 4-8 grounding queries covering financial data, competitive dynamics, and trends
- Generate 2-4 news queries for recent headlines
- For public US companies, include SEC search queries and tickers
- Set perspective to the appropriate analyst role
- Include 2-4 memory seed edges capturing key relationships"""
    }

    /**
     * Generate a SearchPlan from the user's strategic question.
     */
    suspend fun generateSearchPlan(prompt: String): PipelineSearchPlan {
        return try {
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = PLANNER_SYSTEM))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1,
                    maxOutputTokens = 2048,
                    responseMimeType = "application/json",
                    responseSchema = PlannerSchemas.SEARCH_PLAN,
                    thinkingConfig = GeminiThinkingConfig(thinkingBudget = 1024)
                )
            )

            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: ""

            parsePlan(text, prompt)
        } catch (e: Exception) {
            Log.w(TAG, "Plan generation failed: ${e.message}")
            fallbackPlan(prompt)
        }
    }

    private fun parsePlan(jsonText: String, originalPrompt: String): PipelineSearchPlan {
        val cleaned = cleanJsonText(jsonText)
        val json = JSONObject(cleaned)

        val companies = mutableListOf<PipelineCompanyInfo>()
        val companiesArr = json.optJSONArray("companies") ?: JSONArray()
        for (i in 0 until companiesArr.length()) {
            val c = companiesArr.getJSONObject(i)
            companies.add(
                PipelineCompanyInfo(
                    name = c.optString("name", ""),
                    ticker = c.optString("ticker", ""),
                    role = c.optString("role", "primary")
                )
            )
        }

        val groundingQueries = jsonArrayToStringList(json.optJSONArray("grounding_queries"))
        val newsQueries = jsonArrayToStringList(json.optJSONArray("news_queries"))
        val secFulltextQueries = jsonArrayToStringList(json.optJSONArray("sec_fulltext_queries"))

        // Resolve CIKs
        val tickerToCik = mutableMapOf<String, String>()
        for (company in companies) {
            if (company.ticker.isNotBlank()) {
                val cik = CikLookup.lookupCik(company.ticker, httpClient)
                if (cik != null) {
                    tickerToCik[company.ticker] = cik
                }
            }
        }

        // Build extraction companies map
        val extractionCompanies = mutableMapOf<String, String>()
        for (company in companies) {
            if (company.ticker.isNotBlank()) {
                extractionCompanies[company.ticker] = company.name
            }
        }

        // Build company patterns for metadata enrichment
        val companyPatterns = companies.map { c ->
            val pattern = "${Regex.escape(c.name)}|${Regex.escape(c.ticker)}"
            pattern to c.name
        }

        // Parse memory seeds
        val memorySeedEdges = mutableListOf<Triple<String, String, String>>()
        val seedsArr = json.optJSONArray("memory_seeds") ?: JSONArray()
        for (i in 0 until seedsArr.length()) {
            val s = seedsArr.getJSONObject(i)
            memorySeedEdges.add(
                Triple(
                    s.optString("source", ""),
                    s.optString("relation", ""),
                    s.optString("target", "")
                )
            )
        }

        return PipelineSearchPlan(
            domain = json.optString("domain", "general"),
            focusTopic = json.optString("focus_topic", ""),
            perspective = json.optString("perspective", "an industry analyst"),
            primaryQuestion = json.optString("primary_question", originalPrompt),
            companies = companies,
            groundingQueries = groundingQueries,
            newsQueries = newsQueries,
            secFulltextQueries = secFulltextQueries,
            memorySeedEdges = memorySeedEdges,
            financialRetrievalQuery = json.optString("financial_retrieval_query", ""),
            qualitativeRetrievalQuery = json.optString("qualitative_retrieval_query", ""),
            companyPatterns = companyPatterns,
            tickerToCik = tickerToCik,
            extractionCompanies = extractionCompanies
        )
    }

    private fun fallbackPlan(prompt: String): PipelineSearchPlan {
        return PipelineSearchPlan(
            domain = "general",
            focusTopic = prompt.take(80),
            perspective = "an industry analyst",
            primaryQuestion = prompt,
            groundingQueries = listOf(
                prompt,
                "$prompt market analysis",
                "$prompt competitive landscape"
            ),
            newsQueries = listOf(prompt.take(60)),
            financialRetrievalQuery = "$prompt quarterly revenue growth",
            qualitativeRetrievalQuery = "$prompt competitive signals trends"
        )
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun cleanJsonText(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            val lines = cleaned.split("\n").toMutableList()
            lines.removeFirst()
            if (lines.isNotEmpty() && lines.last().trim() == "```") lines.removeLast()
            cleaned = lines.joinToString("\n").trim()
        }
        // Fix floating-point precision explosions
        cleaned = cleaned.replace(Regex("""(\d+\.\d{2})\d{10,}"""), "$1")
        return cleaned
    }
}

// ════════════════════════════════════════════════════════════════════════
// Critique Module — LLM quality gate
// ════════════════════════════════════════════════════════════════════════

class CritiqueModule(
    private val geminiApi: GeminiApi,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash"
) {
    companion object {
        private const val TAG = "CritiqueModule"

        private const val CRITIQUE_SYSTEM = """You are a senior strategy review board member. Your job is to evaluate whether a strategic analysis conclusion is *deep enough* for a C-suite audience.

Score the conclusion on these four criteria (each 0-10):
  1. **Depth** — Does it explain WHY, not just WHAT?
  2. **Evidence** — Does it cite specific data or signals?
  3. **Causality** — Does it identify root drivers, not symptoms?
  4. **Actionability** — Could a decision-maker act on this?

Return ONLY valid JSON:
{
  "overall_score": <float 0-10>,
  "depth": <int>,
  "evidence": <int>,
  "causality": <int>,
  "actionability": <int>,
  "verdict": "PASS" | "NEEDS_REFINEMENT",
  "feedback": "<specific guidance on what to improve>"
}

If overall_score >= 7, verdict = PASS. Otherwise NEEDS_REFINEMENT."""
    }

    /**
     * Critique an analysis conclusion. Returns score + verdict.
     */
    suspend fun critique(conclusion: String, supportingContext: String): Map<String, Any> {
        val prompt = "CONCLUSION TO REVIEW:\n$conclusion\n\n" +
                "SUPPORTING CONTEXT (summary):\n${supportingContext.take(1500)}"

        return try {
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = CRITIQUE_SYSTEM))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.0,
                    maxOutputTokens = 2048,
                    responseMimeType = "application/json",
                    responseSchema = PipelineSchemas.CRITIQUE,
                    thinkingConfig = GeminiThinkingConfig(thinkingBudget = 1024)
                )
            )

            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: ""

            val json = JSONObject(cleanJsonText(text))
            val score = json.optDouble("overall_score", 0.0)
            val verdict = if (score >= 7) "PASS" else "NEEDS_REFINEMENT"

            mapOf(
                "overall_score" to score,
                "depth" to json.optInt("depth", 0),
                "evidence" to json.optInt("evidence", 0),
                "causality" to json.optInt("causality", 0),
                "actionability" to json.optInt("actionability", 0),
                "verdict" to verdict,
                "feedback" to json.optString("feedback", "")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Critique failed: ${e.message}")
            mapOf(
                "overall_score" to 7.0,
                "verdict" to "PASS",
                "feedback" to "(critique parse error — accepted as-is)"
            )
        }
    }

    private fun cleanJsonText(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            val lines = cleaned.split("\n").toMutableList()
            lines.removeFirst()
            if (lines.isNotEmpty() && lines.last().trim() == "```") lines.removeLast()
            cleaned = lines.joinToString("\n").trim()
        }
        return cleaned
    }
}

// ════════════════════════════════════════════════════════════════════════
// Analyst Agent — main orchestrator for analysis steps
// ════════════════════════════════════════════════════════════════════════

class AnalystAgent(
    private val geminiApi: GeminiApi,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
    val memory: GraphMemory,
    private val critiqueMod: CritiqueModule
) {
    companion object {
        private const val TAG = "AnalystAgent"

        private const val PLANNER_SYSTEM = """You are an elite strategy analyst AI. Given a strategic prompt, decompose it into a numbered plan of 5-8 concrete execution steps.

Return ONLY valid JSON:
{"steps": ["step 1 text", "step 2 text", ...]}

Keep each step actionable and specific. Include data acquisition, analysis, critique, and synthesis steps."""

        private const val MEMORY_EXTRACTION_SYSTEM = """You are a knowledge graph curator. Given analysis text, extract strategic relationships as (source, relation, target) triples.

Return ONLY valid JSON:
{"edges": [{"source": "...", "relation": "...", "target": "..."}]}

Focus on competitive relationships, growth drivers, and strategic linkages. Extract 3-6 edges."""
    }

    private var plan: PipelineSearchPlan? = null
    private val trace = mutableListOf<StepResult>()

    fun setSearchPlan(searchPlan: PipelineSearchPlan) {
        plan = searchPlan
    }

    private fun logStep(name: String, detail: String) {
        trace.add(StepResult(name = name, details = detail))
        Log.i(TAG, "STEP [$name]: ${detail.take(120)}")
    }

    // ── 1. Build Plan ──────────────────────────────────────────────

    suspend fun buildPlan(prompt: String): List<String> {
        return try {
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = PLANNER_SYSTEM))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1,
                    maxOutputTokens = 1024,
                    responseMimeType = "application/json",
                    responseSchema = PipelineSchemas.PLAN,
                    thinkingConfig = null
                )
            )
            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: "[]"
            val json = JSONObject(cleanJsonText(text))
            val steps = jsonArrayToStringList(json.optJSONArray("steps"))
            logStep("Plan", "${steps.size} steps generated")
            steps
        } catch (e: Exception) {
            Log.w(TAG, "Plan generation failed: ${e.message}")
            val fallback = listOf(
                "Fetch latest data for the companies in the analysis.",
                "Extract relevant financial metrics.",
                "Compute comparative metrics.",
                "Analyse qualitative signals and trends.",
                "Perform strategic analysis.",
                "Generate actionable recommendations.",
                "Critique and refine.",
                "Synthesise final report."
            )
            logStep("Plan", "${fallback.size} fallback steps")
            fallback
        }
    }

    // ── 2. Extract Financials ──────────────────────────────────────

    suspend fun extractFinancials(context: String): JSONObject {
        val p = plan ?: throw RuntimeException("SearchPlan not set")
        val extractionSystem = buildExtractionSystem(p)
        val extractionSchema = PipelineSchemas.buildExtractionSchema(p)

        return try {
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = extractionSystem))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = "RAW DATA:\n${context.take(6000)}")))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.0,
                    maxOutputTokens = 8192,
                    responseMimeType = "application/json",
                    responseSchema = extractionSchema,
                    thinkingConfig = null
                )
            )
            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: "{}"
            val json = JSONObject(cleanJsonText(text))
            logStep("Extract", "Parsed financials")
            json
        } catch (e: Exception) {
            Log.w(TAG, "Financial extraction failed: ${e.message}")
            JSONObject().put("raw", "extraction failed")
        }
    }

    // ── 3. Threat Analysis ────────────────────────────────────────

    suspend fun analyseThreat(
        financials: JSONObject,
        signalsContext: String,
        memoryContext: String
    ): String {
        val p = plan ?: throw RuntimeException("SearchPlan not set")
        val threatSystem = buildThreatSystem(p)
        val prompt = buildString {
            appendLine("FINANCIAL DATA:")
            appendLine(financials.toString(2).take(3000))
            appendLine("\nQUALITATIVE SIGNALS:")
            appendLine(signalsContext.take(2000))
            appendLine("\nSTRATEGIC MEMORY:")
            appendLine(memoryContext)
        }

        return try {
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = threatSystem))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.2,
                    maxOutputTokens = 4096,
                    responseMimeType = "text/plain"
                )
            )
            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: ""
            logStep("Analysis", text.take(120))
            text
        } catch (e: Exception) {
            Log.w(TAG, "Threat analysis failed: ${e.message}")
            "Analysis could not be completed: ${e.message}"
        }
    }

    // ── 4. Market Share Delta ─────────────────────────────────────

    fun computeShareDelta(financials: JSONObject): JSONObject {
        val companies = financials.optJSONObject("companies") ?: return JSONObject().put("note", "no data")
        val previous = mutableMapOf<String, Double>()
        val current = mutableMapOf<String, Double>()

        val keys = companies.keys()
        while (keys.hasNext()) {
            val ticker = keys.next()
            val data = companies.getJSONObject(ticker)
            val name = data.optString("name", ticker)
            val prevRev = data.optDouble("previous_revenue_b", 0.0)
            val currRev = data.optDouble("current_revenue_b", 0.0)
            if (prevRev > 0) previous[name] = prevRev
            if (currRev > 0) current[name] = currRev
        }

        if (current.size < 2) {
            logStep("ShareDelta", "Insufficient data")
            return JSONObject().put("note", "insufficient revenue data for delta")
        }

        val prevTotal = previous.values.sum().let { if (it == 0.0) 1.0 else it }
        val currTotal = current.values.sum().let { if (it == 0.0) 1.0 else it }

        val result = JSONObject()
        val allNames = (previous.keys + current.keys).distinct()
        for (name in allNames) {
            val prev = previous[name] ?: 0.0
            val curr = current[name] ?: 0.0
            val prevShare = (prev / prevTotal * 100).round(2)
            val currShare = (curr / currTotal * 100).round(2)
            result.put(name, JSONObject().apply {
                put("share_previous_pct", prevShare)
                put("share_current_pct", currShare)
                put("delta_pp", (currShare - prevShare).round(2))
            })
        }

        logStep("ShareDelta", "Computed: $result")
        return result
    }

    // ── 5. Generate Strategies ────────────────────────────────────

    suspend fun generateStrategies(threat: String, context: String): List<Map<String, Any>> {
        val p = plan ?: throw RuntimeException("SearchPlan not set")
        val strategySystem = buildStrategySystem(p)
        val prompt = "ANALYSIS:\n$threat\n\nMARKET CONTEXT:\n${context.take(2000)}"

        return try {
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = strategySystem))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.3,
                    maxOutputTokens = 4096,
                    responseMimeType = "application/json",
                    responseSchema = PipelineSchemas.STRATEGY,
                    thinkingConfig = GeminiThinkingConfig(thinkingBudget = 2048)
                )
            )
            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: "[]"
            val json = JSONObject(cleanJsonText(text))
            val arr = json.optJSONArray("strategies") ?: JSONArray()
            val strategies = (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                val actions = (0 until (s.optJSONArray("actions")?.length() ?: 0)).map { j ->
                    s.getJSONArray("actions").getString(j)
                }
                mapOf<String, Any>(
                    "name" to s.optString("name", ""),
                    "actions" to actions,
                    "cost" to s.optString("cost", ""),
                    "expected_outcome" to s.optString("expected_outcome", "")
                )
            }
            logStep("Strategies", "${strategies.size} generated")
            strategies
        } catch (e: Exception) {
            Log.w(TAG, "Strategy generation failed: ${e.message}")
            emptyList()
        }
    }

    // ── 6. Memory Extraction ──────────────────────────────────────

    suspend fun extractMemory(analysisText: String) {
        try {
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = MEMORY_EXTRACTION_SYSTEM))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = "ANALYSIS TEXT:\n${analysisText.take(3000)}")))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.0,
                    maxOutputTokens = 1024,
                    responseMimeType = "application/json",
                    responseSchema = PipelineSchemas.MEMORY,
                    thinkingConfig = null
                )
            )
            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: "{}"
            val json = JSONObject(cleanJsonText(text))
            val edges = json.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until edges.length()) {
                val e = edges.getJSONObject(i)
                memory.addEdge(
                    e.optString("source", ""),
                    e.optString("relation", ""),
                    e.optString("target", "")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Memory extraction failed: ${e.message}")
        }
    }

    // ── 7. Executive Summary ──────────────────────────────────────

    suspend fun synthesiseSummary(threat: String, strategies: List<Map<String, Any>>): String {
        val p = plan ?: throw RuntimeException("SearchPlan not set")
        val synthesisSystem = buildSynthesisSystem(p)
        val strategiesJson = JSONArray(strategies.map { JSONObject(it) })
        val prompt = "ANALYSIS:\n$threat\n\nSTRATEGIES:\n${strategiesJson.toString(2).take(2000)}"

        return try {
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = synthesisSystem))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.2,
                    maxOutputTokens = 4096,
                    responseMimeType = "text/plain"
                )
            )
            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: ""
            logStep("Synthesis", "Executive summary generated")
            text
        } catch (e: Exception) {
            Log.w(TAG, "Summary synthesis failed: ${e.message}")
            "Summary could not be generated: ${e.message}"
        }
    }

    // ── Refinement query generation ──────────────────────────────

    suspend fun generateRefinementQueries(feedback: String): List<String> {
        return try {
            val system = """You are a research coordinator. Given critique feedback on a strategic analysis, generate 1-2 specific search queries that would fetch the missing evidence.

Return ONLY JSON: {"queries": ["query 1", "query 2"]}"""

            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = system))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = "CRITIQUE FEEDBACK:\n$feedback")))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.0,
                    maxOutputTokens = 512,
                    responseMimeType = "application/json",
                    responseSchema = PipelineSchemas.REFINEMENT,
                    thinkingConfig = null
                )
            )
            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: "{}"
            val json = JSONObject(cleanJsonText(text))
            jsonArrayToStringList(json.optJSONArray("queries"))
        } catch (e: Exception) {
            Log.w(TAG, "Refinement query generation failed: ${e.message}")
            emptyList()
        }
    }

    // ── Dynamic prompt builders (from Python agents.py) ──────────

    private fun buildExtractionSystem(p: PipelineSearchPlan): String {
        val companiesJson = JSONObject()
        for ((ticker, name) in p.extractionCompanies) {
            companiesJson.put(ticker, JSONObject().apply {
                put("name", name)
                put("current_revenue_b", "<float or 0 if unknown>")
                put("previous_revenue_b", "<float or 0 if unknown>")
                put("yoy_growth_pct", "<float or 0 if unknown>")
                put("operating_margin_pct", "<float or 0 if unknown>")
            })
        }
        if (companiesJson.length() == 0) {
            companiesJson.put("COMPANY", JSONObject().apply {
                put("name", "<company name>")
                put("current_revenue_b", "<float or 0 if unknown>")
                put("previous_revenue_b", "<float or 0 if unknown>")
                put("yoy_growth_pct", "<float or 0 if unknown>")
                put("operating_margin_pct", "<float or 0 if unknown>")
            })
        }
        val schema = JSONObject().apply {
            put("companies", companiesJson)
            put("quarter", "<e.g. FY2025-Q4>")
        }
        return "You are a financial data extraction specialist. Given raw text from " +
                "earnings reports, SEC filings, and news about the ${p.domain} industry, " +
                "extract financial data for the relevant companies.\n\n" +
                "Return ONLY valid JSON matching this structure:\n${schema.toString(2)}\n\n" +
                "Extract ONLY numbers you find in the text. Use 0 for missing/unknown values.\n" +
                "Do NOT invent numbers. Round all numeric values to at most 2 decimal places."
    }

    private fun buildThreatSystem(p: PipelineSearchPlan): String {
        return "You are a competitive intelligence analyst specializing in the " +
                "${p.domain} industry. Given financial data and qualitative signals, " +
                "answer the following strategic question:\n\n" +
                "**${p.primaryQuestion}**\n\n" +
                "Be specific: name the product lines, market segments, and dynamics.\n" +
                "Provide a comprehensive, detailed analysis with specific data points and evidence-backed reasoning.\n" +
                "Structure your analysis with clear sections and cite specific numbers where available."
    }

    private fun buildStrategySystem(p: PipelineSearchPlan): String {
        return "You are a McKinsey-level strategy consultant providing analysis for " +
                "${p.perspective}.\n" +
                "Given the analysis and market context about ${p.domain}, generate " +
                "exactly 3 actionable strategic recommendations.\n\n" +
                "Return ONLY valid JSON:\n" +
                "{\n  \"strategies\": [\n    {\n" +
                "      \"name\": \"<strategy name>\",\n" +
                "      \"actions\": [\"action 1\", \"action 2\", \"action 3\"],\n" +
                "      \"cost\": \"<estimated cost level and type>\",\n" +
                "      \"expected_outcome\": \"<expected business impact>\"\n" +
                "    }\n  ]\n}\n\n" +
                "Each strategy must be distinct, specific, and actionable."
    }

    private fun buildSynthesisSystem(p: PipelineSearchPlan): String {
        return "You are an executive report writer. Given all the analysis components " +
                "about the ${p.domain} industry, synthesise a thorough executive " +
                "summary suitable for ${p.perspective}.\n\n" +
                "Cover: the key findings with specific data points, why they matter " +
                "for strategic positioning, market dynamics, and recommended actions " +
                "with expected impact. Be detailed and evidence-backed."
    }

    private fun cleanJsonText(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            val lines = cleaned.split("\n").toMutableList()
            lines.removeFirst()
            if (lines.isNotEmpty() && lines.last().trim() == "```") lines.removeLast()
            cleaned = lines.joinToString("\n").trim()
        }
        cleaned = cleaned.replace(Regex("""(\d+\.\d{2})\d{10,}"""), "$1")
        return cleaned
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
