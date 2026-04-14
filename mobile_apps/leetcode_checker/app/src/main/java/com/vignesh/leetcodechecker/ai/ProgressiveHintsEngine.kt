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
 * Progressive Hints Engine - Provides 5-level hints for LeetCode problems.
 * 
 * Level 1: Conceptual nudge (e.g., "Think about what data structure offers O(1) lookup")
 * Level 2: Algorithm category (e.g., "This is a classic two-pointer problem")
 * Level 3: Specific approach (e.g., "Use a hashmap to store indices as you iterate")
 * Level 4: Pseudocode outline (e.g., "For each element, check if complement exists...")
 * Level 5: Full explanation with edge cases
 * 
 * Usage:
 *   val engine = ProgressiveHintsEngine(geminiApi, apiKey)
 *   val hints = engine.generateHints(problemTitle, problemDescription, difficulty)
 *   // Show hints[0] first, then hints[1] when user asks for more, etc.
 */
class ProgressiveHintsEngine(
    private val geminiApi: GeminiApi,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash"
) {
    companion object {
        private const val TAG = "ProgressiveHints"
        
        private const val HINTS_SYSTEM_PROMPT = """You are a coding interview tutor specializing in progressive hints.
Your goal is to help students learn problem-solving WITHOUT giving away the answer immediately.

Generate exactly 5 hints, each progressively more revealing:

LEVEL 1 - CONCEPTUAL NUDGE (most vague):
- Ask a guiding question about the problem
- Mention the general category or pattern without naming a specific algorithm
- Example: "What property of the input could you exploit to avoid checking every pair?"

LEVEL 2 - ALGORITHM CATEGORY:
- Name the general technique or data structure category
- Don't explain how to apply it yet
- Example: "This problem is well-suited for a hash-based approach."

LEVEL 3 - SPECIFIC APPROACH:
- Describe the specific technique to use
- Explain the core insight without giving code
- Example: "Use a hashmap to store each number's index. For each element, check if its complement exists in the map."

LEVEL 4 - PSEUDOCODE OUTLINE:
- Provide step-by-step pseudocode or algorithm outline
- Include time/space complexity
- Example: "1. Create empty hashmap\n2. For each element x at index i:\n   - Calculate complement = target - x\n   - If complement in hashmap, return [hashmap[complement], i]\n   - Else store x:i in hashmap"

LEVEL 5 - FULL EXPLANATION:
- Complete explanation with edge cases
- Common pitfalls to avoid
- Why this approach is optimal

Return JSON ONLY in this exact format:
{
  "hints": [
    {"level": 1, "title": "Conceptual Nudge", "content": "..."},
    {"level": 2, "title": "Algorithm Category", "content": "..."},
    {"level": 3, "title": "Specific Approach", "content": "..."},
    {"level": 4, "title": "Pseudocode", "content": "..."},
    {"level": 5, "title": "Full Solution", "content": "..."}
  ],
  "estimated_difficulty": "Easy|Medium|Hard",
  "key_concepts": ["concept1", "concept2"]
}"""
    }

    data class HintLevel(
        val level: Int,
        val title: String,
        val content: String
    )

    data class HintsResult(
        val hints: List<HintLevel>,
        val estimatedDifficulty: String,
        val keyConcepts: List<String>
    )

    /**
     * Generate 5-level progressive hints for a problem.
     * 
     * @param problemTitle Title of the LeetCode problem
     * @param problemDescription The full problem statement
     * @param difficulty Easy/Medium/Hard
     * @param userCode Optional: user's current attempt (for targeted hints)
     */
    suspend fun generateHints(
        problemTitle: String,
        problemDescription: String,
        difficulty: String,
        userCode: String? = null
    ): HintsResult = withContext(Dispatchers.IO) {
        try {
            val userPrompt = buildString {
                appendLine("# Problem: $problemTitle")
                appendLine("## Difficulty: $difficulty")
                appendLine()
                appendLine("## Description:")
                appendLine(problemDescription.take(2000))
                
                if (!userCode.isNullOrBlank()) {
                    appendLine()
                    appendLine("## User's Current Attempt:")
                    appendLine("```")
                    appendLine(userCode.take(1000))
                    appendLine("```")
                    appendLine("(Tailor hints to help user improve their current approach)")
                }
            }

            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = HINTS_SYSTEM_PROMPT))
                ),
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.3,
                    maxOutputTokens = 2048,
                    responseMimeType = "application/json"
                )
            )

            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: ""

            parseHintsResponse(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate hints", e)
            // Return fallback hints
            HintsResult(
                hints = listOf(
                    HintLevel(1, "Conceptual Nudge", "Think about what data structures could help solve this efficiently."),
                    HintLevel(2, "Algorithm Category", "Consider common algorithmic patterns like two-pointers, sliding window, or hash maps."),
                    HintLevel(3, "Specific Approach", "Break the problem into smaller subproblems and solve each one."),
                    HintLevel(4, "Pseudocode", "1. Parse the input\n2. Process using your chosen algorithm\n3. Return the result"),
                    HintLevel(5, "Full Solution", "Unable to generate detailed hints. Try refreshing or check your API key.")
                ),
                estimatedDifficulty = difficulty,
                keyConcepts = listOf("Problem Solving", "Algorithm Design")
            )
        }
    }

    private fun parseHintsResponse(json: String): HintsResult {
        return try {
            val obj = JSONObject(json.trim().removePrefix("```json").removeSuffix("```"))
            
            val hintsArray = obj.getJSONArray("hints")
            val hints = (0 until hintsArray.length()).map { i ->
                val hint = hintsArray.getJSONObject(i)
                HintLevel(
                    level = hint.getInt("level"),
                    title = hint.getString("title"),
                    content = hint.getString("content")
                )
            }
            
            val conceptsArray = obj.optJSONArray("key_concepts")
            val concepts = if (conceptsArray != null) {
                (0 until conceptsArray.length()).map { conceptsArray.getString(it) }
            } else emptyList()
            
            HintsResult(
                hints = hints,
                estimatedDifficulty = obj.optString("estimated_difficulty", "Medium"),
                keyConcepts = concepts
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse hints JSON: $json", e)
            throw e
        }
    }

    /**
     * Generate a single targeted hint based on user's current code.
     * Useful for real-time assistance while coding.
     */
    suspend fun generateTargetedHint(
        problemTitle: String,
        problemDescription: String,
        userCode: String,
        errorMessage: String? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                appendLine("Problem: $problemTitle")
                appendLine("Description: ${problemDescription.take(1000)}")
                appendLine()
                appendLine("User's code:")
                appendLine("```")
                appendLine(userCode.take(1500))
                appendLine("```")
                if (!errorMessage.isNullOrBlank()) {
                    appendLine()
                    appendLine("Error: $errorMessage")
                }
                appendLine()
                appendLine("Provide ONE concise hint to help the user progress. Don't give the solution.")
            }

            val request = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.4,
                    maxOutputTokens = 300
                )
            )

            val response = geminiApi.generateContent(model, apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") 
                ?: "Consider the edge cases in your solution."
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate targeted hint", e)
            "Check your algorithm's time complexity and edge case handling."
        }
    }
}
