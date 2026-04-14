package com.vignesh.leetcodechecker.pipeline

import android.util.Log
import com.vignesh.leetcodechecker.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DeepAnalysisPipeline — Truly agentic pipeline with dynamic step selection.
 *
 * Ported from Python orchestrator.py with AGENTIC enhancements.
 * Uses LLM to dynamically decide which analysis steps to run based on
 * the query context and available data.
 *
 * Pipeline steps (dynamically selected):
 *   0. Query Planning + Agentic Router (decide which steps to run)
 *   1. Plan (LLM decomposes into analysis steps) - ALWAYS
 *   2. Fetch & Index (news, SEC EDGAR, grounded search) - if needsExternalData
 *   3. Financial retrieval + extraction - if needsFinancials
 *   4. Market share delta (pure math) - if needsFinancials AND multipleCompanies
 *   5. Qualitative signals retrieval - if needsQualitative
 *   6. Threat/Competitive analysis (LLM) - if needsCompetitiveAnalysis
 *   7. Critique (quality gate) - ALWAYS when analysis done
 *   7b. Refinement loop - if score < 7
 *   8. Strategy generation - if needsStrategies
 *   9. Memory extraction - ALWAYS
 *  10. Executive summary - ALWAYS
 */

/**
 * Status update emitted by the pipeline during execution.
 */
data class PipelineStatus(
    val step: String,
    val message: String,
    val isComplete: Boolean = false,
    val fullReport: String? = null
)

/**
 * Agentic routing decisions for dynamic pipeline execution.
 */
data class AgenticRoute(
    val needsExternalData: Boolean = true,
    val needsSecFilings: Boolean = false,
    val needsNewsData: Boolean = true,
    val needsFinancials: Boolean = true,
    val needsQualitative: Boolean = true,
    val needsCompetitiveAnalysis: Boolean = true,
    val needsStrategies: Boolean = true,
    val reasoning: String = ""
)

class DeepAnalysisPipeline(
    private val geminiApi: GeminiApi,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash"
) {
    companion object {
        private const val TAG = "DeepAnalysisPipeline"
        private const val MAX_CRITIQUE_LOOPS = 2
        
        private const val AGENTIC_ROUTER_SYSTEM = """You are an agentic orchestrator that decides which analysis steps to run based on the user's query.

Analyze the query and decide which pipeline steps are needed. Return JSON with boolean flags.

Guidelines:
- needsExternalData: TRUE for any analysis needing current market data, recent news, or external information
- needsSecFilings: TRUE only for US public companies when financial/regulatory data from SEC is needed
- needsNewsData: TRUE for queries about recent events, trends, or competitive moves
- needsFinancials: TRUE for queries about revenue, growth, margins, valuation, or financial metrics
- needsQualitative: TRUE for queries about strategy, market position, competitive dynamics, or trends
- needsCompetitiveAnalysis: TRUE for competitive queries, threat assessments, or market positioning
- needsStrategies: TRUE when the user asks for recommendations, strategic advice, or action items

Be selective! Not every query needs all steps. A simple "What is X's market position?" doesn't need SEC filings.
For conversational questions like "Explain cloud computing" or "What is AI?", use Quick Chat mode instead.

Return ONLY valid JSON with this structure:
{
  "needsExternalData": true/false,
  "needsSecFilings": true/false,
  "needsNewsData": true/false,
  "needsFinancials": true/false,
  "needsQualitative": true/false,
  "needsCompetitiveAnalysis": true/false,
  "needsStrategies": true/false,
  "reasoning": "<brief explanation of why these steps were selected>"
}"""
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Persistent state (reused for follow-up)
    private var memory = GraphMemory()
    private var retrievalPipeline: RetrievalPipeline? = null
    private var indexBuilt = false
    private var analysisResult: Map<String, Any>? = null
    private var costGuard = PipelineCostGuard(budgetUsd = 0.50)

    val isIndexBuilt get() = indexBuilt
    val analysisAvailable get() = analysisResult != null
    val totalCost get() = costGuard.totalCostUsd
    val apiCallCount get() = costGuard.apiCalls
    val memoryEdgeCount get() = memory.allEdges().size

    /** Wire cost guard to the LoggingGeminiApi wrapper (if present) */
    private fun wireCostGuard() {
        (geminiApi as? LoggingGeminiApi)?.costGuard = costGuard
    }

    /**
     * Agentic router — LLM decides which pipeline steps to run.
     * This makes the pipeline truly adaptive to the query.
     */
    private suspend fun determineAgenticRoute(prompt: String, searchPlan: PipelineSearchPlan): AgenticRoute {
        return try {
            Log.d(TAG, "Agentic router analyzing query: ${prompt.take(100)}")
            
            val contextInfo = buildString {
                appendLine("User Query: $prompt")
                appendLine("Detected Domain: ${searchPlan.domain}")
                appendLine("Companies Identified: ${searchPlan.companies.map { it.name }}")
                appendLine("Has SEC Tickers: ${searchPlan.tickerToCik.isNotEmpty()}")
            }
            
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = AGENTIC_ROUTER_SYSTEM))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = contextInfo)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1,
                    maxOutputTokens = 512,
                    responseMimeType = "application/json",
                    thinkingConfig = null
                )
            )
            
            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: ""
            
            val json = JSONObject(text.trim().removePrefix("```json").removeSuffix("```").trim())
            
            AgenticRoute(
                needsExternalData = json.optBoolean("needsExternalData", true),
                needsSecFilings = json.optBoolean("needsSecFilings", false),
                needsNewsData = json.optBoolean("needsNewsData", true),
                needsFinancials = json.optBoolean("needsFinancials", true),
                needsQualitative = json.optBoolean("needsQualitative", true),
                needsCompetitiveAnalysis = json.optBoolean("needsCompetitiveAnalysis", true),
                needsStrategies = json.optBoolean("needsStrategies", true),
                reasoning = json.optString("reasoning", "Default full analysis")
            ).also {
                Log.d(TAG, "Agentic route: $it")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Agentic router failed, using full pipeline: ${e.message}")
            AgenticRoute(reasoning = "Fallback to full analysis due to router error")
        }
    }

    /**
     * Run the agentic deep analysis pipeline.
     * Uses LLM to dynamically decide which steps to run.
     * Emits PipelineStatus updates as a Flow.
     */
    fun runDeepAnalysis(prompt: String): Flow<PipelineStatus> = flow {
        var accumulated = ""
        var currentStep = 0
        var totalSteps = 10  // Will be adjusted by agentic router

        fun emit(text: String): PipelineStatus {
            accumulated += text + "\n"
            return PipelineStatus(step = "", message = accumulated)
        }

        // Wire cost guard so every LLM call auto-records token usage
        wireCostGuard()

        try {
            // ── Step 0: Query Planning + Agentic Routing ──────────
            emit("🧠 **Step 0:** Understanding your question & planning approach...").also { emit(it) }
            val planner = QueryPlanner(geminiApi, apiKey, model, httpClient)
            val searchPlan = planner.generateSearchPlan(prompt)
            
            val companiesSummary = searchPlan.companies.joinToString(", ") { 
                it.ticker.ifBlank { it.name } 
            }
            emit(
                "   ✅ Domain: **${searchPlan.domain}** | " +
                "Companies: $companiesSummary | " +
                "Queries planned: ${searchPlan.groundingQueries.size}\n"
            ).also { emit(it) }
            
            // ── Agentic Router — decides what steps to run ──────
            emit("🤖 **Agentic Router:** Analyzing query to optimize pipeline...").also { emit(it) }
            val route = determineAgenticRoute(prompt, searchPlan)
            
            // Calculate dynamic step count based on selected steps
            val selectedSteps = mutableListOf("Planning", "Summary", "Memory")
            if (route.needsExternalData) selectedSteps.add("Data Fetch")
            if (route.needsFinancials) selectedSteps.add("Financials")
            if (route.needsQualitative) selectedSteps.add("Qualitative")
            if (route.needsCompetitiveAnalysis) selectedSteps.add("Analysis")
            if (route.needsStrategies) selectedSteps.add("Strategies")
            selectedSteps.add("Critique")
            totalSteps = selectedSteps.size
            
            emit(
                "   ✅ Selected ${selectedSteps.size} steps: ${selectedSteps.joinToString(" → ")}\n" +
                "   📝 Reasoning: ${route.reasoning}\n"
            ).also { emit(it) }

            // Seed memory
            if (memory.allEdges().isEmpty()) {
                for ((source, relation, target) in searchPlan.memorySeedEdges) {
                    memory.addEdge(source, relation, target)
                }
            }

            // ── Build Agents ────────────────────────────────────
            val dataFetcher = DataFetcher(geminiApi, apiKey, model)
            val critiqueMod = CritiqueModule(geminiApi, apiKey, model)
            val analyst = AnalystAgent(geminiApi, apiKey, model, memory, critiqueMod)
            analyst.setSearchPlan(searchPlan)
            
            currentStep = 1

            // ── Step 1: Planning (ALWAYS) ───────────────────────
            emit("🔍 **Step $currentStep/$totalSteps:** Planning analysis steps...").also { emit(it) }
            val plan = analyst.buildPlan(prompt)
            emit("   ✅ Generated ${plan.size} steps\n").also { emit(it) }
            currentStep++
            
            // Variables for analysis context
            var finContext = ""
            var signalsContext = ""
            var financials: JSONObject? = null
            var shareDelta: JSONObject? = null
            var documents: List<PipelineDocument> = emptyList()

            // ── Step 2: Fetch & Index (if needsExternalData) ────
            if (route.needsExternalData) {
                val fetchTypes = mutableListOf<String>()
                if (route.needsNewsData) fetchTypes.add("news")
                if (route.needsSecFilings && searchPlan.tickerToCik.isNotEmpty()) fetchTypes.add("SEC")
                fetchTypes.add("web")
                
                emit(
                    "📡 **Step $currentStep/$totalSteps:** Fetching real-time data " +
                    "(${fetchTypes.joinToString(", ")})..."
                ).also { emit(it) }

                documents = dataFetcher.fetchAll(searchPlan, 
                    fetchSec = route.needsSecFilings,
                    fetchNews = route.needsNewsData
                )
                emit("   ✅ Fetched ${documents.size} documents").also { emit(it) }

                // Build retrieval pipeline
                emit("   ⏳ Indexing: chunking + embedding...").also { emit(it) }
                retrievalPipeline = RetrievalPipeline(
                    geminiApi = geminiApi,
                    apiKey = apiKey,
                    model = model,
                    companyPatterns = searchPlan.companyPatterns
                )
                val chunkCount = retrievalPipeline!!.ingest(documents)
                indexBuilt = true
                emit("   ✅ Indexed $chunkCount chunks from ${documents.size} documents\n").also { emit(it) }
                currentStep++
            } else {
                emit("⏭️ **Skipping data fetch** — Using cached context or not needed\n").also { emit(it) }
            }

            // ── Step 3: Financial retrieval + extraction (if needsFinancials) ────
            if (route.needsFinancials && retrievalPipeline != null) {
                emit(
                    "📊 **Step $currentStep/$totalSteps:** Retrieving financial context..."
                ).also { emit(it) }
                val finQuery = searchPlan.financialRetrievalQuery.ifBlank {
                    "${searchPlan.domain} quarterly revenue growth operating income"
                }
                finContext = retrievalPipeline!!.query(finQuery)
                emit("   ✅ Financial context retrieved").also { emit(it) }
                emit("   ⏳ Extracting structured metrics via LLM...").also { emit(it) }
                financials = analyst.extractFinancials(finContext)
                emit("   ✅ Financial data extracted\n").also { emit(it) }
                currentStep++
                
                // Market share delta only if multiple companies
                if (searchPlan.companies.size >= 2 && financials != null) {
                    emit("🧮 **Step $currentStep/$totalSteps:** Computing market share deltas...").also { emit(it) }
                    shareDelta = analyst.computeShareDelta(financials)
                    emit("   ✅ ${shareDelta.toString().take(200)}\n").also { emit(it) }
                    currentStep++
                }
            } else if (route.needsFinancials) {
                emit("⏭️ **Skipping financials** — No index built yet\n").also { emit(it) }
            }

            // ── Step 4: Qualitative signals (if needsQualitative) ────
            if (route.needsQualitative && retrievalPipeline != null) {
                emit("🔎 **Step $currentStep/$totalSteps:** Retrieving competitive signals...").also { emit(it) }
                val qualQuery = searchPlan.qualitativeRetrievalQuery.ifBlank {
                    "${searchPlan.domain} ${searchPlan.focusTopic} competitive signals trends"
                }
                signalsContext = retrievalPipeline!!.query(qualQuery)
                emit("   ✅ Qualitative signals retrieved\n").also { emit(it) }
                currentStep++
            }
            
            val memoryCtx = memory.format()
            var threat = ""

            // ── Step 5: Competitive/Threat analysis (if needsCompetitiveAnalysis) ────
            if (route.needsCompetitiveAnalysis) {
                emit("⚡ **Step $currentStep/$totalSteps:** Synthesising strategic analysis...").also { emit(it) }
                threat = analyst.analyseThreat(
                    financials ?: JSONObject(), 
                    signalsContext.ifEmpty { finContext }, 
                    memoryCtx
                )
                emit("   ✅ Strategic analysis complete\n").also { emit(it) }
                currentStep++
            } else {
                emit("⏭️ **Skipping competitive analysis** — Not needed for this query\n").also { emit(it) }
            }

            // ── Step 6: Critique (quality gate - ALWAYS) ────────
            emit("📝 **Step $currentStep/$totalSteps:** Self-critique (quality gate)...").also { emit(it) }
            var conclusion = buildString {
                if (shareDelta != null) appendLine("Market share delta: $shareDelta")
                appendLine("Analysis: $threat")
            }
            var critique = critiqueMod.critique(conclusion, signalsContext.ifEmpty { finContext })
            var score = (critique["overall_score"] as? Number)?.toDouble() ?: 7.0
            var verdict = critique["verdict"]?.toString() ?: "PASS"
            val icon7 = if (verdict == "PASS") "✅" else "⚠️"
            emit("   $icon7 Score: ${String.format("%.1f", score)}/10 — $verdict\n").also { emit(it) }
            currentStep++

            // ── Step 7: Refinement loop (if needed) ─────────────
            var loop = 0
            while (verdict == "NEEDS_REFINEMENT" && loop < MAX_CRITIQUE_LOOPS && retrievalPipeline != null) {
                loop++
                emit(
                    "🔄 **Refinement $loop/$MAX_CRITIQUE_LOOPS:** " +
                    "Generating deeper evidence queries..."
                ).also { emit(it) }

                val feedback = critique["feedback"]?.toString() ?: ""
                val queries = analyst.generateRefinementQueries(feedback)

                val extraParts = mutableListOf<String>()
                for (q in queries.take(2)) {
                    extraParts.add(retrievalPipeline!!.query(q))
                }

                if (extraParts.isNotEmpty()) {
                    val enriched = signalsContext + "\n\n" + extraParts.joinToString("\n\n")
                    threat = analyst.analyseThreat(financials ?: JSONObject(), enriched, memoryCtx)
                    conclusion = buildString {
                        if (shareDelta != null) appendLine("Market share delta: $shareDelta")
                        appendLine("Analysis: $threat")
                    }
                }

                critique = critiqueMod.critique(conclusion, signalsContext.ifEmpty { finContext })
                score = (critique["overall_score"] as? Number)?.toDouble() ?: 7.0
                verdict = critique["verdict"]?.toString() ?: "PASS"
                val iconR = if (verdict == "PASS") "✅" else "⚠️"
                emit("   $iconR Refined score: ${String.format("%.1f", score)}/10 — $verdict\n").also { emit(it) }
            }

            // ── Step 8: Strategy generation (if needsStrategies) ─
            var strategies: List<Map<String, Any>> = emptyList()
            if (route.needsStrategies) {
                emit("💡 **Step $currentStep/$totalSteps:** Generating strategic recommendations...").also { emit(it) }
                strategies = analyst.generateStrategies(threat, signalsContext.ifEmpty { finContext })
                emit("   ✅ ${strategies.size} strategies generated\n").also { emit(it) }
                currentStep++
            }

            // ── Step 9: Memory extraction (ALWAYS) ──────────────
            emit("🧠 **Step $currentStep/$totalSteps:** Extracting knowledge graph edges...").also { emit(it) }
            val edgesBefore = memory.allEdges().size
            val strategiesText = strategies.joinToString("\n") { s ->
                "${s["name"]}: ${(s["actions"] as? List<*>)?.joinToString(", ") ?: ""}"
            }
            analyst.extractMemory(threat + "\n" + strategiesText.take(1500))
            val edgeCount = memory.allEdges().size
            emit("   ✅ $edgeCount total edges in memory (+${edgeCount - edgesBefore} new)\n").also { emit(it) }
            currentStep++

            // ── Step 10: Executive summary (ALWAYS) ─────────────
            emit("📋 **Step $currentStep/$totalSteps:** Writing executive summary...").also { emit(it) }
            val summary = analyst.synthesiseSummary(threat, strategies)
            emit("   ✅ Complete!\n").also { emit(it) }

            // ── Store result for follow-up ──────────────────────
            analysisResult = mapOf(
                "plan" to plan,
                "financials" to (financials?.toString() ?: ""),
                "share_delta" to (shareDelta?.toString() ?: ""),
                "threat_statement" to threat,
                "strategies" to strategies,
                "critique" to critique,
                "executive_summary" to summary,
                "memory_edges" to memory.allEdges(),
                "agentic_route" to route.toString()
            )

            // ── Format final report ─────────────────────────────
            accumulated += "\n---\n\n"
            accumulated += "## 📄 Executive Summary\n\n$summary\n\n"
            accumulated += "## ⚡ Key Analysis\n\n$threat\n\n"
            accumulated += "## 💡 Strategies\n\n"
            if (strategies.isEmpty()) {
                accumulated += "*No structured strategies could be generated. " +
                        "See the Key Analysis section above for strategic insights.*\n\n"
            } else {
                for ((i, s) in strategies.withIndex()) {
                    accumulated += "### ${i + 1}. ${s["name"] ?: "?"}\n"
                    val actions = s["actions"] as? List<*>
                    actions?.forEach { a -> accumulated += "- $a\n" }
                    accumulated += "\n**Cost:** ${s["cost"] ?: "?"} | " +
                            "**Outcome:** ${s["expected_outcome"] ?: "?"}\n\n"
                }
            }
            accumulated += "\n---\n*💰 Total cost: \$${String.format("%.4f", costGuard.totalCostUsd)} " +
                    "(${costGuard.apiCalls} API calls) | " +
                    "${costGuard.totalInputTokens} input tokens, " +
                    "${costGuard.totalOutputTokens} output tokens*"

            emit(PipelineStatus(
                step = "complete",
                message = accumulated,
                isComplete = true,
                fullReport = accumulated
            ))

        } catch (e: BudgetExceededException) {
            Log.w(TAG, "Budget exceeded: ${e.message}")
            accumulated += "\n⚠️ **Budget Exceeded:** ${e.message}\n\n" +
                    "💰 Spent: \$${String.format("%.4f", costGuard.totalCostUsd)} / " +
                    "\$${String.format("%.2f", costGuard.budgetUsd)}"
            emit(PipelineStatus(step = "error", message = accumulated, isComplete = true))
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error", e)
            accumulated += "\n❌ **Error:** ${e.javaClass.simpleName}: ${e.message}"
            emit(PipelineStatus(step = "error", message = accumulated, isComplete = true))
        }
    }

    /**
     * Follow-up query on already-built index.
     * Returns a Flow with the answer.
     */
    fun runFollowUp(question: String, conversationContext: String = ""): Flow<PipelineStatus> = flow {
        var accumulated = ""

        fun emit(text: String): PipelineStatus {
            accumulated += text + "\n"
            return PipelineStatus(step = "", message = accumulated)
        }

        // Wire cost guard for follow-up calls
        wireCostGuard()

        if (!indexBuilt || retrievalPipeline == null) {
            emit(PipelineStatus(
                step = "error",
                message = "ℹ️ No analysis index available yet. Run a **Deep Analysis** first.",
                isComplete = true
            ))
            return@flow
        }

        emit("🔎 Searching existing index...").also { emit(it) }
        val context = retrievalPipeline!!.query(question)
        emit("✅ Found relevant chunks. Generating answer...\n").also { emit(it) }

        // Build rich prompt
        val systemPrompt = buildString {
            appendLine("You are a strategic analysis assistant. Answer the user's " +
                    "follow-up question using the provided context and prior analysis. " +
                    "Be specific, cite data where available.")
            appendLine()
            appendLine("Known relationships:\n${memory.format()}")
            appendLine()
            val prevSummary = analysisResult?.get("executive_summary")?.toString()
            if (!prevSummary.isNullOrBlank()) {
                appendLine("Previous analysis summary:\n$prevSummary")
            }
        }

        val prompt = "RETRIEVED CONTEXT:\n${context.take(3000)}\n\nQUESTION: $question"

        try {
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.3,
                    maxOutputTokens = 2048,
                    responseMimeType = "text/plain"
                )
            )
            val response = geminiApi.generateContent(model, apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: "No response."

            accumulated += "\n$responseText\n"
            accumulated += "\n---\n*💰 Cost: \$${String.format("%.4f", costGuard.totalCostUsd)} " +
                    "(${costGuard.apiCalls} API calls)*"

            emit(PipelineStatus(
                step = "complete",
                message = accumulated,
                isComplete = true
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Follow-up error", e)
            accumulated += "\n❌ **Error:** ${e.message}"
            emit(PipelineStatus(step = "error", message = accumulated, isComplete = true))
        }
    }

    /**
     * Reset all pipeline state.
     */
    fun reset() {
        memory = GraphMemory()
        retrievalPipeline = null
        indexBuilt = false
        analysisResult = null
        costGuard = PipelineCostGuard(budgetUsd = 0.50)
    }
}
