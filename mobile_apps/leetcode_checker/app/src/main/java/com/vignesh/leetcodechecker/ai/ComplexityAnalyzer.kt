package com.vignesh.leetcodechecker.ai

import android.util.Log
import com.vignesh.leetcodechecker.data.GeminiApi
import com.vignesh.leetcodechecker.data.GeminiContent
import com.vignesh.leetcodechecker.data.GeminiGenerateRequest
import com.vignesh.leetcodechecker.data.GeminiGenerationConfig
import com.vignesh.leetcodechecker.data.GeminiPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Complexity Analyzer - Analyzes code to determine time and space complexity.
 * 
 * Features:
 * 1. Static analysis for common patterns (loops, recursion)
 * 2. AI-powered detailed analysis
 * 3. Comparison with optimal complexity
 * 4. Suggestions for optimization
 * 
 * Supported languages: Python, Java, Kotlin, JavaScript, C++
 */
class ComplexityAnalyzer(
    private val geminiApi: GeminiApi? = null,
    private val apiKey: String? = null,
    private val model: String = "gemini-2.5-flash"
) {
    companion object {
        private const val TAG = "ComplexityAnalyzer"
        
        private const val ANALYSIS_SYSTEM_PROMPT = """You are an expert algorithm complexity analyzer.
Analyze the given code and provide detailed complexity analysis.

Your analysis must include:
1. Time Complexity (Big O notation)
2. Space Complexity (Big O notation)
3. Explanation of how you derived each complexity
4. Any assumptions about input size (n)
5. Best/Average/Worst case if they differ
6. Suggestions for optimization if applicable

Common patterns to look for:
- Single loop over n elements: O(n)
- Nested loops: O(n²), O(n³), etc.
- Binary search / divide and conquer: O(log n)
- Recursive calls: Analyze recurrence relation
- Hash table operations: O(1) average
- Sorting (comparison-based): O(n log n)
- BFS/DFS on graph: O(V + E)

Return JSON only:
{
  "timeComplexity": "O(n)",
  "spaceComplexity": "O(1)",
  "timeExplanation": "Single pass through array...",
  "spaceExplanation": "Only using constant extra space...",
  "bestCase": "O(n)",
  "worstCase": "O(n)",
  "averageCase": "O(n)",
  "bottlenecks": ["The nested loop at line 5 dominates..."],
  "optimizations": ["Use a hash map to reduce to O(n)..."],
  "isOptimal": true,
  "optimalComplexity": "O(n)"
}"""
    }

    /**
     * Complexity analysis result.
     */
    data class ComplexityResult(
        val timeComplexity: String,
        val spaceComplexity: String,
        val timeExplanation: String,
        val spaceExplanation: String,
        val bestCase: String? = null,
        val worstCase: String? = null,
        val averageCase: String? = null,
        val bottlenecks: List<String> = emptyList(),
        val optimizations: List<String> = emptyList(),
        val isOptimal: Boolean = false,
        val optimalComplexity: String? = null,
        val confidence: Float = 0.8f
    )

    /**
     * Quick static analysis result (no AI).
     */
    data class QuickAnalysis(
        val estimatedTime: String,
        val estimatedSpace: String,
        val patterns: List<String>,
        val warnings: List<String>
    )

    /**
     * Perform quick static analysis without AI (fast, works offline).
     */
    fun quickAnalyze(code: String, language: String = "python"): QuickAnalysis {
        val patterns = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var loopDepth = 0
        var maxLoopDepth = 0
        var hasRecursion = false
        var hasBinarySearch = false
        var hasSorting = false
        var hasHashMap = false
        var hasHeap = false
        
        val lines = code.lines()
        val funcName = extractFunctionName(code, language)
        
        for (line in lines) {
            val trimmed = line.trim().lowercase()
            
            // Count loop nesting
            if (isLoopStart(trimmed, language)) {
                loopDepth++
                maxLoopDepth = maxOf(maxLoopDepth, loopDepth)
            }
            if (isBlockEnd(trimmed, language) && loopDepth > 0) {
                loopDepth--
            }
            
            // Detect patterns
            if (containsRecursiveCall(trimmed, funcName)) {
                hasRecursion = true
                patterns.add("Recursion detected")
            }
            
            if (containsBinarySearch(trimmed)) {
                hasBinarySearch = true
                patterns.add("Binary search pattern")
            }
            
            if (containsSorting(trimmed, language)) {
                hasSorting = true
                patterns.add("Sorting operation")
            }
            
            if (containsHashMap(trimmed, language)) {
                hasHashMap = true
                patterns.add("Hash table usage")
            }
            
            if (containsHeap(trimmed, language)) {
                hasHeap = true
                patterns.add("Heap/Priority queue")
            }
        }
        
        // Estimate time complexity based on patterns
        val timeComplexity = estimateTimeComplexity(
            maxLoopDepth, hasRecursion, hasBinarySearch, hasSorting
        )
        
        // Estimate space complexity
        val spaceComplexity = estimateSpaceComplexity(
            code, hasRecursion, hasHashMap
        )
        
        // Add warnings
        if (maxLoopDepth >= 3) {
            warnings.add("Triple nested loops detected - consider optimization")
        }
        if (hasRecursion && !containsMemoization(code)) {
            warnings.add("Recursion without memoization may cause TLE")
        }
        
        return QuickAnalysis(
            estimatedTime = timeComplexity,
            estimatedSpace = spaceComplexity,
            patterns = patterns.distinct(),
            warnings = warnings
        )
    }

    /**
     * Perform detailed AI-powered analysis.
     */
    suspend fun analyzeWithAI(
        code: String,
        language: String = "python",
        problemContext: String? = null,
        optimalComplexity: String? = null
    ): ComplexityResult = withContext(Dispatchers.IO) {
        if (geminiApi == null || apiKey.isNullOrBlank()) {
            // Fall back to static analysis
            val quick = quickAnalyze(code, language)
            return@withContext ComplexityResult(
                timeComplexity = quick.estimatedTime,
                spaceComplexity = quick.estimatedSpace,
                timeExplanation = "Static analysis: ${quick.patterns.joinToString(", ")}",
                spaceExplanation = "Based on code structure analysis",
                bottlenecks = quick.warnings,
                confidence = 0.6f
            )
        }
        
        try {
            val prompt = buildString {
                appendLine("Analyze the complexity of this code:")
                appendLine()
                appendLine("```$language")
                appendLine(code.take(3000))
                appendLine("```")
                
                if (!problemContext.isNullOrBlank()) {
                    appendLine()
                    appendLine("Problem context: $problemContext")
                }
                
                if (!optimalComplexity.isNullOrBlank()) {
                    appendLine()
                    appendLine("Known optimal complexity: $optimalComplexity")
                }
            }

            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = ANALYSIS_SYSTEM_PROMPT))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.2,
                    maxOutputTokens = 1024,
                    responseMimeType = "application/json"
                )
            )

            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: ""

            parseAnalysisResult(text)
        } catch (e: Exception) {
            Log.e(TAG, "AI analysis failed", e)
            val quick = quickAnalyze(code, language)
            ComplexityResult(
                timeComplexity = quick.estimatedTime,
                spaceComplexity = quick.estimatedSpace,
                timeExplanation = "Fallback to static analysis: ${quick.patterns.joinToString(", ")}",
                spaceExplanation = "Based on code structure",
                bottlenecks = quick.warnings + listOf("AI analysis unavailable: ${e.message}"),
                confidence = 0.5f
            )
        }
    }

    private fun parseAnalysisResult(json: String): ComplexityResult {
        return try {
            val obj = JSONObject(json.trim().removePrefix("```json").removeSuffix("```"))
            
            ComplexityResult(
                timeComplexity = obj.getString("timeComplexity"),
                spaceComplexity = obj.getString("spaceComplexity"),
                timeExplanation = obj.getString("timeExplanation"),
                spaceExplanation = obj.getString("spaceExplanation"),
                bestCase = obj.optString("bestCase").takeIf { it.isNotBlank() },
                worstCase = obj.optString("worstCase").takeIf { it.isNotBlank() },
                averageCase = obj.optString("averageCase").takeIf { it.isNotBlank() },
                bottlenecks = obj.optJSONArray("bottlenecks")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                optimizations = obj.optJSONArray("optimizations")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                isOptimal = obj.optBoolean("isOptimal", false),
                optimalComplexity = obj.optString("optimalComplexity").takeIf { it.isNotBlank() },
                confidence = 0.9f
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse analysis JSON: $json", e)
            throw e
        }
    }

    /**
     * Compare user's solution complexity with optimal.
     */
    fun compareWithOptimal(
        userComplexity: String,
        optimalComplexity: String
    ): ComparisonResult {
        val userOrder = parseComplexityOrder(userComplexity)
        val optimalOrder = parseComplexityOrder(optimalComplexity)
        
        return when {
            userOrder == optimalOrder -> ComparisonResult.OPTIMAL
            userOrder < optimalOrder -> ComparisonResult.BETTER_THAN_OPTIMAL
            userOrder <= optimalOrder + 1 -> ComparisonResult.ACCEPTABLE
            userOrder <= optimalOrder + 2 -> ComparisonResult.SUBOPTIMAL
            else -> ComparisonResult.NEEDS_OPTIMIZATION
        }
    }

    enum class ComparisonResult(val message: String, val color: String) {
        BETTER_THAN_OPTIMAL("🏆 Better than expected!", "#4ade80"),
        OPTIMAL("✅ Optimal solution!", "#4ade80"),
        ACCEPTABLE("👍 Good, close to optimal", "#facc15"),
        SUBOPTIMAL("⚠️ Room for improvement", "#fb923c"),
        NEEDS_OPTIMIZATION("🔴 Consider optimization", "#f87171")
    }

    // Helper functions for static analysis
    private fun extractFunctionName(code: String, language: String): String {
        val pattern = when (language.lowercase()) {
            "python" -> Regex("""def\s+(\w+)\s*\(""")
            "java", "kotlin" -> Regex("""fun\s+(\w+)\s*\(|void\s+(\w+)\s*\(|int\s+(\w+)\s*\(""")
            "javascript", "typescript" -> Regex("""function\s+(\w+)\s*\(|const\s+(\w+)\s*=\s*\(""")
            else -> Regex("""(\w+)\s*\(""")
        }
        return pattern.find(code)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() } ?: ""
    }

    private fun isLoopStart(line: String, language: String): Boolean {
        return when (language.lowercase()) {
            "python" -> line.startsWith("for ") || line.startsWith("while ")
            else -> line.contains("for ") || line.contains("for(") || 
                    line.contains("while ") || line.contains("while(")
        }
    }

    private fun isBlockEnd(line: String, language: String): Boolean {
        return when (language.lowercase()) {
            "python" -> false // Python uses indentation
            else -> line == "}" || line.endsWith("}")
        }
    }

    private fun containsRecursiveCall(line: String, funcName: String): Boolean {
        return funcName.isNotEmpty() && line.contains("$funcName(")
    }

    private fun containsBinarySearch(line: String): Boolean {
        return line.contains("mid") && (line.contains("left") || line.contains("right") || 
               line.contains("low") || line.contains("high")) ||
               line.contains("bisect") || line.contains("binarysearch")
    }

    private fun containsSorting(line: String, language: String): Boolean {
        return line.contains(".sort(") || line.contains("sorted(") ||
               line.contains("arrays.sort") || line.contains("collections.sort")
    }

    private fun containsHashMap(line: String, language: String): Boolean {
        return line.contains("dict(") || line.contains("{}") ||
               line.contains("hashmap") || line.contains("hashset") ||
               line.contains("set(") || line.contains("mutablemapof") ||
               line.contains("new map") || line.contains("new set")
    }

    private fun containsHeap(line: String, language: String): Boolean {
        return line.contains("heapq") || line.contains("priorityqueue") ||
               line.contains("heap") || line.contains("minheap") || line.contains("maxheap")
    }

    private fun containsMemoization(code: String): Boolean {
        return code.contains("@cache") || code.contains("@lru_cache") ||
               code.contains("memo") || code.contains("dp[") || code.contains("cache[")
    }

    private fun estimateTimeComplexity(
        loopDepth: Int,
        hasRecursion: Boolean,
        hasBinarySearch: Boolean,
        hasSorting: Boolean
    ): String {
        return when {
            hasBinarySearch && loopDepth <= 1 -> "O(log n)"
            hasSorting -> "O(n log n)"
            hasRecursion && loopDepth == 0 -> "O(2^n) or O(n!)"
            loopDepth == 0 -> "O(1)"
            loopDepth == 1 -> "O(n)"
            loopDepth == 2 -> "O(n²)"
            loopDepth == 3 -> "O(n³)"
            else -> "O(n^$loopDepth)"
        }
    }

    private fun estimateSpaceComplexity(
        code: String,
        hasRecursion: Boolean,
        hasHashMap: Boolean
    ): String {
        val hasArray = code.contains("[") && (code.contains("*") || code.contains("range"))
        
        return when {
            hasRecursion -> "O(n)" // Stack space
            hasHashMap || hasArray -> "O(n)"
            else -> "O(1)"
        }
    }

    private fun parseComplexityOrder(complexity: String): Int {
        val c = complexity.uppercase()
        return when {
            c.contains("1)") -> 0
            c.contains("LOG N)") -> 1
            c.contains("N)") && !c.contains("LOG") && !c.contains("²") && !c.contains("^2") -> 2
            c.contains("N LOG N)") || c.contains("N*LOG") -> 3
            c.contains("N²)") || c.contains("N^2)") -> 4
            c.contains("N³)") || c.contains("N^3)") -> 5
            c.contains("2^N") || c.contains("2**N") -> 6
            c.contains("N!)") -> 7
            else -> 3 // Default to n log n
        }
    }

    /**
     * Get complexity emoji and color for display.
     */
    fun getComplexityDisplay(complexity: String): Pair<String, String> {
        val c = complexity.uppercase()
        return when {
            c.contains("1)") -> "⚡" to "#4ade80" // Constant - excellent
            c.contains("LOG N)") -> "🚀" to "#4ade80" // Logarithmic - excellent
            c.contains("N)") && !c.contains("LOG") -> "✅" to "#4ade80" // Linear - good
            c.contains("N LOG N)") -> "👍" to "#a3e635" // Linearithmic - good
            c.contains("N²)") || c.contains("N^2)") -> "⚠️" to "#facc15" // Quadratic - watch out
            c.contains("N³)") || c.contains("N^3)") -> "⚠️" to "#fb923c" // Cubic - suboptimal
            c.contains("2^N") -> "🔴" to "#f87171" // Exponential - bad
            c.contains("N!)") -> "💀" to "#ef4444" // Factorial - very bad
            else -> "❓" to "#94a3b8" // Unknown
        }
    }
}
