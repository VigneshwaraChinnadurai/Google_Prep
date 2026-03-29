package com.vignesh.leetcodechecker.pipeline

import android.util.Log
import com.vignesh.leetcodechecker.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DeepAnalysisPipeline — Full 10-step agentic pipeline running on-device.
 *
 * Ported from Python orchestrator.py deep_analysis() generator.
 * All computation is HTTP calls to public APIs + pure text/math.
 * No Python backend needed.
 *
 * Pipeline steps:
 *   0. Query Planning (generate SearchPlan from user prompt)
 *   1. Plan (LLM decomposes into analysis steps)
 *   2. Fetch & Index (news, SEC EDGAR, grounded search → chunk → embed)
 *   3. Financial retrieval + extraction
 *   4. Market share delta (pure math)
 *   5. Qualitative signals retrieval
 *   6. Threat analysis (LLM)
 *   7. Critique (quality gate)
 *   7b. Refinement loop (0-2 iterations)
 *   8. Strategy generation
 *   9. Memory extraction
 *  10. Executive summary
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

class DeepAnalysisPipeline(
    private val geminiApi: GeminiApi,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash"
) {
    companion object {
        private const val TAG = "DeepAnalysisPipeline"
        private const val MAX_CRITIQUE_LOOPS = 2
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

    /**
     * Run the full deep analysis pipeline.
     * Emits PipelineStatus updates as a Flow.
     */
    fun runDeepAnalysis(prompt: String): Flow<PipelineStatus> = flow {
        var accumulated = ""

        fun emit(text: String): PipelineStatus {
            accumulated += text + "\n"
            return PipelineStatus(step = "", message = accumulated)
        }

        try {
            // ── Step 0: Query Planning ──────────────────────────
            emit("🧠 **Step 0:** Understanding your question...").also { emit(it) }
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

            // ── Step 1: Planning ────────────────────────────────
            emit("🔍 **Step 1/10:** Planning analysis steps...").also { emit(it) }
            val plan = analyst.buildPlan(prompt)
            emit("   ✅ Generated ${plan.size} steps\n").also { emit(it) }

            // ── Step 2: Fetch & Index ───────────────────────────
            emit(
                "📡 **Step 2/10:** Fetching real-time data " +
                "(${searchPlan.groundingQueries.size} web searches, " +
                "${searchPlan.tickerToCik.size} SEC filings, " +
                "${searchPlan.newsQueries.size} news feeds)..."
            ).also { emit(it) }

            val documents = dataFetcher.fetchAll(searchPlan)
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

            // ── Step 3: Financial retrieval + extraction ────────
            emit(
                "📊 **Step 3/10:** Retrieving financial context " +
                "(hybrid search → rerank → fuse)..."
            ).also { emit(it) }
            val finQuery = searchPlan.financialRetrievalQuery.ifBlank {
                "${searchPlan.domain} quarterly revenue growth operating income"
            }
            val finContext = retrievalPipeline!!.query(finQuery)
            emit("   ✅ Financial context retrieved").also { emit(it) }
            emit("   ⏳ Extracting structured metrics via LLM...").also { emit(it) }
            val financials = analyst.extractFinancials(finContext)
            emit("   ✅ Financial data extracted\n").also { emit(it) }

            // ── Step 4: Market share delta ──────────────────────
            emit("🧮 **Step 4/10:** Computing market share deltas...").also { emit(it) }
            val shareDelta = analyst.computeShareDelta(financials)
            emit("   ✅ ${shareDelta.toString().take(200)}\n").also { emit(it) }

            // ── Step 5: Qualitative signals ─────────────────────
            emit("🔎 **Step 5/10:** Retrieving competitive signals...").also { emit(it) }
            val qualQuery = searchPlan.qualitativeRetrievalQuery.ifBlank {
                "${searchPlan.domain} ${searchPlan.focusTopic} competitive signals trends"
            }
            val signalsContext = retrievalPipeline!!.query(qualQuery)
            val memoryCtx = memory.format()
            emit("   ✅ Qualitative signals retrieved\n").also { emit(it) }

            // ── Step 6: Threat analysis ─────────────────────────
            emit("⚡ **Step 6/10:** Synthesising strategic analysis...").also { emit(it) }
            var threat = analyst.analyseThreat(financials, signalsContext, memoryCtx)
            emit("   ✅ Strategic analysis complete\n").also { emit(it) }

            // ── Step 7: Critique (quality gate) ─────────────────
            emit("📝 **Step 7/10:** Self-critique (quality gate)...").also { emit(it) }
            var conclusion = "Market share delta: $shareDelta\n\nThreat: $threat"
            var critique = critiqueMod.critique(conclusion, signalsContext)
            var score = (critique["overall_score"] as? Number)?.toDouble() ?: 7.0
            var verdict = critique["verdict"]?.toString() ?: "PASS"
            val icon7 = if (verdict == "PASS") "✅" else "⚠️"
            emit("   $icon7 Score: ${String.format("%.1f", score)}/10 — $verdict\n").also { emit(it) }

            // ── Step 7b: Refinement loop ────────────────────────
            var loop = 0
            while (verdict == "NEEDS_REFINEMENT" && loop < MAX_CRITIQUE_LOOPS) {
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
                    threat = analyst.analyseThreat(financials, enriched, memoryCtx)
                    conclusion = "Market share delta: $shareDelta\n\nThreat: $threat"
                }

                critique = critiqueMod.critique(conclusion, signalsContext)
                score = (critique["overall_score"] as? Number)?.toDouble() ?: 7.0
                verdict = critique["verdict"]?.toString() ?: "PASS"
                val iconR = if (verdict == "PASS") "✅" else "⚠️"
                emit("   $iconR Refined score: ${String.format("%.1f", score)}/10 — $verdict\n").also { emit(it) }
            }

            // ── Step 8: Strategy generation ─────────────────────
            emit("💡 **Step 8/10:** Generating strategic recommendations...").also { emit(it) }
            val strategies = analyst.generateStrategies(threat, signalsContext)
            emit("   ✅ ${strategies.size} strategies generated\n").also { emit(it) }

            // ── Step 9: Memory extraction ───────────────────────
            emit("🧠 **Step 9/10:** Extracting knowledge graph edges...").also { emit(it) }
            val edgesBefore = memory.allEdges().size
            val strategiesText = strategies.joinToString("\n") { s ->
                "${s["name"]}: ${(s["actions"] as? List<*>)?.joinToString(", ") ?: ""}"
            }
            analyst.extractMemory(threat + "\n" + strategiesText.take(1500))
            val edgeCount = memory.allEdges().size
            emit("   ✅ $edgeCount total edges in memory (+${edgeCount - edgesBefore} new)\n").also { emit(it) }

            // ── Step 10: Executive summary ──────────────────────
            emit("📋 **Step 10/10:** Writing executive summary...").also { emit(it) }
            val summary = analyst.synthesiseSummary(threat, strategies)
            emit("   ✅ Complete!\n").also { emit(it) }

            // ── Store result for follow-up ──────────────────────
            analysisResult = mapOf(
                "plan" to plan,
                "financials" to financials.toString(),
                "share_delta" to shareDelta.toString(),
                "threat_statement" to threat,
                "strategies" to strategies,
                "critique" to critique,
                "executive_summary" to summary,
                "memory_edges" to memory.allEdges()
            )

            // ── Format final report ─────────────────────────────
            accumulated += "\n---\n\n"
            accumulated += "## 📄 Executive Summary\n\n$summary\n\n"
            accumulated += "## ⚡ Key Analysis\n\n$threat\n\n"
            accumulated += "## 💡 Strategies\n\n"
            for ((i, s) in strategies.withIndex()) {
                accumulated += "### ${i + 1}. ${s["name"] ?: "?"}\n"
                val actions = s["actions"] as? List<*>
                actions?.forEach { a -> accumulated += "- $a\n" }
                accumulated += "\n**Cost:** ${s["cost"] ?: "?"} | " +
                        "**Outcome:** ${s["expected_outcome"] ?: "?"}\n\n"
            }
            accumulated += "\n---\n*💰 Total cost: \$${String.format("%.4f", costGuard.totalCostUsd)} " +
                    "(${costGuard.apiCalls} API calls)*"

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
