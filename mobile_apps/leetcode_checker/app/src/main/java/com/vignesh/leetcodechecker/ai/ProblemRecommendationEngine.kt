package com.vignesh.leetcodechecker.ai

import android.content.Context
import android.util.Log
import com.vignesh.leetcodechecker.data.GeminiApi
import com.vignesh.leetcodechecker.data.GeminiContent
import com.vignesh.leetcodechecker.data.GeminiGenerateRequest
import com.vignesh.leetcodechecker.data.GeminiGenerationConfig
import com.vignesh.leetcodechecker.data.GeminiPart
import com.vignesh.leetcodechecker.data.LeetCodeActivityStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln

/**
 * Problem Recommendation Engine - AI-powered personalized problem suggestions.
 * 
 * Uses multiple signals:
 * 1. Topic weakness detection (from solve history)
 * 2. Difficulty progression (gradually increase)
 * 3. Time-decay for spaced repetition
 * 4. Pattern-based recommendations (related problems)
 * 
 * The engine learns from:
 * - Problems solved and time taken
 * - Hints used vs. solved independently
 * - Topics practiced vs. avoided
 * - Streak patterns and consistency
 */
class ProblemRecommendationEngine(
    private val context: Context,
    private val geminiApi: GeminiApi? = null,
    private val apiKey: String? = null,
    private val model: String = "gemini-2.5-flash"
) {
    companion object {
        private const val TAG = "RecommendEngine"
        private const val PREFS = "recommendation_prefs"
        private const val KEY_PROBLEM_DB = "curated_problems_json"
        
        // Topic categories for LeetCode problems
        val TOPICS = listOf(
            "Array", "String", "Hash Table", "Dynamic Programming", "Math",
            "Sorting", "Greedy", "Depth-First Search", "Binary Search", "Tree",
            "Breadth-First Search", "Two Pointers", "Stack", "Heap (Priority Queue)",
            "Graph", "Linked List", "Recursion", "Sliding Window", "Backtracking",
            "Union Find", "Trie", "Divide and Conquer", "Bit Manipulation"
        )
        
        // Curated foundational problems by topic
        private val FOUNDATIONAL_PROBLEMS = mapOf(
            "Array" to listOf(
                ProblemInfo("1", "Two Sum", "Easy", listOf("Array", "Hash Table")),
                ProblemInfo("121", "Best Time to Buy and Sell Stock", "Easy", listOf("Array", "Dynamic Programming")),
                ProblemInfo("53", "Maximum Subarray", "Medium", listOf("Array", "Divide and Conquer", "Dynamic Programming")),
                ProblemInfo("238", "Product of Array Except Self", "Medium", listOf("Array", "Prefix Sum"))
            ),
            "String" to listOf(
                ProblemInfo("125", "Valid Palindrome", "Easy", listOf("Two Pointers", "String")),
                ProblemInfo("20", "Valid Parentheses", "Easy", listOf("String", "Stack")),
                ProblemInfo("3", "Longest Substring Without Repeating Characters", "Medium", listOf("Hash Table", "String", "Sliding Window")),
                ProblemInfo("5", "Longest Palindromic Substring", "Medium", listOf("String", "Dynamic Programming"))
            ),
            "Linked List" to listOf(
                ProblemInfo("206", "Reverse Linked List", "Easy", listOf("Linked List", "Recursion")),
                ProblemInfo("21", "Merge Two Sorted Lists", "Easy", listOf("Linked List", "Recursion")),
                ProblemInfo("141", "Linked List Cycle", "Easy", listOf("Hash Table", "Linked List", "Two Pointers")),
                ProblemInfo("19", "Remove Nth Node From End of List", "Medium", listOf("Linked List", "Two Pointers"))
            ),
            "Tree" to listOf(
                ProblemInfo("104", "Maximum Depth of Binary Tree", "Easy", listOf("Tree", "DFS", "BFS")),
                ProblemInfo("226", "Invert Binary Tree", "Easy", listOf("Tree", "DFS", "BFS")),
                ProblemInfo("100", "Same Tree", "Easy", listOf("Tree", "DFS", "BFS")),
                ProblemInfo("102", "Binary Tree Level Order Traversal", "Medium", listOf("Tree", "BFS"))
            ),
            "Dynamic Programming" to listOf(
                ProblemInfo("70", "Climbing Stairs", "Easy", listOf("Math", "Dynamic Programming", "Memoization")),
                ProblemInfo("198", "House Robber", "Medium", listOf("Array", "Dynamic Programming")),
                ProblemInfo("322", "Coin Change", "Medium", listOf("Array", "Dynamic Programming", "BFS")),
                ProblemInfo("300", "Longest Increasing Subsequence", "Medium", listOf("Array", "Binary Search", "Dynamic Programming"))
            ),
            "Graph" to listOf(
                ProblemInfo("200", "Number of Islands", "Medium", listOf("Array", "DFS", "BFS", "Union Find")),
                ProblemInfo("133", "Clone Graph", "Medium", listOf("Hash Table", "DFS", "BFS", "Graph")),
                ProblemInfo("207", "Course Schedule", "Medium", listOf("DFS", "BFS", "Graph", "Topological Sort")),
                ProblemInfo("417", "Pacific Atlantic Water Flow", "Medium", listOf("Array", "DFS", "BFS"))
            ),
            "Binary Search" to listOf(
                ProblemInfo("704", "Binary Search", "Easy", listOf("Array", "Binary Search")),
                ProblemInfo("33", "Search in Rotated Sorted Array", "Medium", listOf("Array", "Binary Search")),
                ProblemInfo("153", "Find Minimum in Rotated Sorted Array", "Medium", listOf("Array", "Binary Search")),
                ProblemInfo("4", "Median of Two Sorted Arrays", "Hard", listOf("Array", "Binary Search", "Divide and Conquer"))
            ),
            "Two Pointers" to listOf(
                ProblemInfo("167", "Two Sum II", "Medium", listOf("Array", "Two Pointers", "Binary Search")),
                ProblemInfo("15", "3Sum", "Medium", listOf("Array", "Two Pointers", "Sorting")),
                ProblemInfo("11", "Container With Most Water", "Medium", listOf("Array", "Two Pointers", "Greedy")),
                ProblemInfo("42", "Trapping Rain Water", "Hard", listOf("Array", "Two Pointers", "Dynamic Programming", "Stack"))
            ),
            "Sliding Window" to listOf(
                ProblemInfo("121", "Best Time to Buy and Sell Stock", "Easy", listOf("Array", "Dynamic Programming")),
                ProblemInfo("3", "Longest Substring Without Repeating Characters", "Medium", listOf("Hash Table", "String", "Sliding Window")),
                ProblemInfo("424", "Longest Repeating Character Replacement", "Medium", listOf("Hash Table", "String", "Sliding Window")),
                ProblemInfo("76", "Minimum Window Substring", "Hard", listOf("Hash Table", "String", "Sliding Window"))
            ),
            "Stack" to listOf(
                ProblemInfo("20", "Valid Parentheses", "Easy", listOf("String", "Stack")),
                ProblemInfo("155", "Min Stack", "Medium", listOf("Stack", "Design")),
                ProblemInfo("739", "Daily Temperatures", "Medium", listOf("Array", "Stack", "Monotonic Stack")),
                ProblemInfo("84", "Largest Rectangle in Histogram", "Hard", listOf("Array", "Stack", "Monotonic Stack"))
            ),
            "Heap (Priority Queue)" to listOf(
                ProblemInfo("703", "Kth Largest Element in a Stream", "Easy", listOf("Tree", "Design", "Heap")),
                ProblemInfo("215", "Kth Largest Element in an Array", "Medium", listOf("Array", "Divide and Conquer", "Sorting", "Heap")),
                ProblemInfo("347", "Top K Frequent Elements", "Medium", listOf("Array", "Hash Table", "Divide and Conquer", "Sorting", "Heap")),
                ProblemInfo("295", "Find Median from Data Stream", "Hard", listOf("Two Pointers", "Design", "Sorting", "Heap"))
            ),
            "Backtracking" to listOf(
                ProblemInfo("78", "Subsets", "Medium", listOf("Array", "Backtracking", "Bit Manipulation")),
                ProblemInfo("46", "Permutations", "Medium", listOf("Array", "Backtracking")),
                ProblemInfo("39", "Combination Sum", "Medium", listOf("Array", "Backtracking")),
                ProblemInfo("79", "Word Search", "Medium", listOf("Array", "Backtracking", "Matrix"))
            )
        )
    }

    data class ProblemInfo(
        val id: String,
        val title: String,
        val difficulty: String,
        val topics: List<String>,
        val url: String = "https://leetcode.com/problems/${title.lowercase().replace(" ", "-")}/",
        val prerequisites: List<String> = emptyList()
    )

    data class TopicAnalysis(
        val topic: String,
        val solvedCount: Int,
        val totalAttempted: Int,
        val averageTime: Int,
        val usedHintsCount: Int,
        val proficiencyScore: Double // 0.0 - 1.0
    )

    data class Recommendation(
        val problem: ProblemInfo,
        val reason: String,
        val priority: Double, // Higher = more recommended
        val category: RecommendationCategory
    )

    enum class RecommendationCategory {
        WEAKNESS_IMPROVEMENT,    // Problems in weak topics
        SKILL_BUILDING,          // Foundational problems
        CHALLENGE,               // Slightly harder than current level
        REVIEW,                  // Problems to revisit for retention
        DAILY_GOAL               // Part of daily goal achievement
    }

    /**
     * Get personalized recommendations based on user's history.
     * 
     * @param count Number of recommendations to return
     * @param focusTopic Optional topic to focus on
     * @param difficultyPreference Optional difficulty level
     */
    suspend fun getRecommendations(
        count: Int = 5,
        focusTopic: String? = null,
        difficultyPreference: String? = null
    ): List<Recommendation> = withContext(Dispatchers.IO) {
        val history = LeetCodeActivityStorage.loadCompletionHistory(context)
        val topicAnalysis = analyzeTopics(history)
        val solvedIds = history.map { it.problemId }.toSet()
        
        val recommendations = mutableListOf<Recommendation>()
        
        // 1. Find weak topics
        val weakTopics = topicAnalysis
            .filter { it.proficiencyScore < 0.5 }
            .sortedBy { it.proficiencyScore }
            .take(3)
            .map { it.topic }
        
        // 2. Determine current skill level
        val avgDifficulty = calculateAverageDifficulty(history)
        val targetDifficulty = difficultyPreference ?: getNextDifficulty(avgDifficulty)
        
        // 3. Get problems from weak topics (not already solved)
        for (topic in weakTopics) {
            val topicProblems = FOUNDATIONAL_PROBLEMS[topic] ?: continue
            val unsolved = topicProblems.filter { it.id !in solvedIds }
            
            for (problem in unsolved.take(2)) {
                if (matchesDifficulty(problem.difficulty, targetDifficulty)) {
                    recommendations.add(Recommendation(
                        problem = problem,
                        reason = "Strengthen your $topic skills",
                        priority = 1.0 - (topicAnalysis.find { it.topic == topic }?.proficiencyScore ?: 0.5),
                        category = RecommendationCategory.WEAKNESS_IMPROVEMENT
                    ))
                }
            }
        }
        
        // 4. Add foundational problems if user is beginner
        if (history.size < 20) {
            val foundational = FOUNDATIONAL_PROBLEMS.values.flatten()
                .filter { it.id !in solvedIds && it.difficulty == "Easy" }
                .take(3)
            
            for (problem in foundational) {
                recommendations.add(Recommendation(
                    problem = problem,
                    reason = "Build foundation in ${problem.topics.first()}",
                    priority = 0.8,
                    category = RecommendationCategory.SKILL_BUILDING
                ))
            }
        }
        
        // 5. Add challenge problems
        val challengeProblems = FOUNDATIONAL_PROBLEMS.values.flatten()
            .filter { it.id !in solvedIds && isChallenge(it.difficulty, avgDifficulty) }
            .take(2)
        
        for (problem in challengeProblems) {
            recommendations.add(Recommendation(
                problem = problem,
                reason = "Challenge yourself with ${problem.difficulty} level",
                priority = 0.7,
                category = RecommendationCategory.CHALLENGE
            ))
        }
        
        // 6. Add review problems (spaced repetition)
        val reviewCandidates = getReviewCandidates(history)
        for (entry in reviewCandidates.take(2)) {
            val problem = FOUNDATIONAL_PROBLEMS.values.flatten()
                .find { it.id == entry.problemId } ?: continue
            recommendations.add(Recommendation(
                problem = problem,
                reason = "Review for long-term retention (solved ${daysSince(entry.date)} days ago)",
                priority = calculateReviewPriority(entry),
                category = RecommendationCategory.REVIEW
            ))
        }
        
        // Filter by focus topic if specified
        val filtered = if (focusTopic != null) {
            recommendations.filter { rec -> 
                rec.problem.topics.any { it.equals(focusTopic, ignoreCase = true) }
            }
        } else {
            recommendations
        }
        
        // Sort by priority and return top N
        filtered
            .distinctBy { it.problem.id }
            .sortedByDescending { it.priority }
            .take(count)
    }

    /**
     * Analyze user's proficiency in each topic.
     */
    fun analyzeTopics(
        history: List<LeetCodeActivityStorage.CompletionEntry>
    ): List<TopicAnalysis> {
        val topicStats = mutableMapOf<String, MutableList<LeetCodeActivityStorage.CompletionEntry>>()
        
        for (entry in history) {
            for (topic in entry.topics) {
                topicStats.getOrPut(topic) { mutableListOf() }.add(entry)
            }
        }
        
        return TOPICS.map { topic ->
            val entries = topicStats[topic] ?: emptyList()
            val solvedCount = entries.size
            val avgTime = if (entries.isNotEmpty()) {
                entries.map { it.timeTakenMinutes }.average().toInt()
            } else 0
            val hintCount = entries.count { it.usedHint }
            
            // Calculate proficiency score
            val proficiency = when {
                solvedCount == 0 -> 0.0
                solvedCount < 3 -> 0.3
                solvedCount < 5 -> 0.5
                else -> {
                    val baseScore = 0.5
                    val experienceBonus = minOf(solvedCount * 0.05, 0.3)
                    val hintPenalty = if (solvedCount > 0) (hintCount.toDouble() / solvedCount) * 0.2 else 0.0
                    val timeFactor = when {
                        avgTime < 15 -> 0.1
                        avgTime < 30 -> 0.05
                        else -> 0.0
                    }
                    minOf(baseScore + experienceBonus - hintPenalty + timeFactor, 1.0)
                }
            }
            
            TopicAnalysis(
                topic = topic,
                solvedCount = solvedCount,
                totalAttempted = solvedCount, // Could track attempts separately
                averageTime = avgTime,
                usedHintsCount = hintCount,
                proficiencyScore = proficiency
            )
        }
    }

    /**
     * Get AI-powered recommendations using Gemini.
     * More personalized but requires API call.
     */
    suspend fun getAIRecommendations(
        userGoal: String = "general improvement",
        timeAvailable: Int = 30 // minutes
    ): List<Recommendation> = withContext(Dispatchers.IO) {
        if (geminiApi == null || apiKey.isNullOrBlank()) {
            return@withContext getRecommendations()
        }
        
        try {
            val history = LeetCodeActivityStorage.loadCompletionHistory(context)
            val topicAnalysis = analyzeTopics(history)
            
            val prompt = buildString {
                appendLine("# LeetCode Problem Recommendation Request")
                appendLine()
                appendLine("## User Profile:")
                appendLine("- Problems solved: ${history.size}")
                appendLine("- Goal: $userGoal")
                appendLine("- Available time: $timeAvailable minutes")
                appendLine()
                appendLine("## Topic Proficiency:")
                topicAnalysis.filter { it.solvedCount > 0 }.forEach { topic ->
                    appendLine("- ${topic.topic}: ${(topic.proficiencyScore * 100).toInt()}% (${topic.solvedCount} solved)")
                }
                appendLine()
                appendLine("## Weak Areas (need improvement):")
                topicAnalysis.filter { it.proficiencyScore < 0.5 }.forEach { topic ->
                    appendLine("- ${topic.topic}")
                }
                appendLine()
                appendLine("## Recently Solved:")
                history.takeLast(5).forEach { entry ->
                    appendLine("- ${entry.problemTitle} (${entry.difficulty})")
                }
                appendLine()
                appendLine("Based on this profile, recommend 5 specific LeetCode problems with:")
                appendLine("1. Problem number and title")
                appendLine("2. Why it's recommended for this user")
                appendLine("3. Expected time to solve")
                appendLine("4. Main topic/pattern")
                appendLine()
                appendLine("Return JSON array format.")
            }

            val request = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.4,
                    maxOutputTokens = 1024,
                    responseMimeType = "application/json"
                )
            )

            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: ""
            
            parseAIRecommendations(text)
        } catch (e: Exception) {
            Log.e(TAG, "AI recommendation failed, using local", e)
            getRecommendations()
        }
    }

    private fun parseAIRecommendations(json: String): List<Recommendation> {
        return try {
            val arr = JSONArray(json.trim().removePrefix("```json").removeSuffix("```"))
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val problem = ProblemInfo(
                    id = obj.optString("number", obj.optString("id", "${i + 1}")),
                    title = obj.getString("title"),
                    difficulty = obj.optString("difficulty", "Medium"),
                    topics = listOf(obj.optString("topic", "General"))
                )
                Recommendation(
                    problem = problem,
                    reason = obj.getString("reason"),
                    priority = 0.9 - (i * 0.1),
                    category = RecommendationCategory.SKILL_BUILDING
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI recommendations", e)
            emptyList()
        }
    }

    // Helper functions
    private fun calculateAverageDifficulty(history: List<LeetCodeActivityStorage.CompletionEntry>): Double {
        if (history.isEmpty()) return 1.0
        return history.map { 
            when (it.difficulty) {
                "Easy" -> 1.0
                "Medium" -> 2.0
                "Hard" -> 3.0
                else -> 2.0
            }
        }.average()
    }

    private fun getNextDifficulty(avg: Double): String {
        return when {
            avg < 1.3 -> "Easy"
            avg < 1.8 -> "Medium"
            avg < 2.3 -> "Medium"
            else -> "Hard"
        }
    }

    private fun matchesDifficulty(difficulty: String, target: String): Boolean {
        val order = mapOf("Easy" to 1, "Medium" to 2, "Hard" to 3)
        val diff = order[difficulty] ?: 2
        val tgt = order[target] ?: 2
        return kotlin.math.abs(diff - tgt) <= 1
    }

    private fun isChallenge(difficulty: String, avgDifficulty: Double): Boolean {
        val diffValue = when (difficulty) {
            "Easy" -> 1.0
            "Medium" -> 2.0
            "Hard" -> 3.0
            else -> 2.0
        }
        return diffValue > avgDifficulty + 0.5
    }

    private fun getReviewCandidates(
        history: List<LeetCodeActivityStorage.CompletionEntry>
    ): List<LeetCodeActivityStorage.CompletionEntry> {
        val now = System.currentTimeMillis()
        return history.filter { entry ->
            val daysSinceCompletion = daysSince(entry.date)
            // Spaced repetition intervals: 1, 3, 7, 14, 30 days
            daysSinceCompletion in listOf(1, 3, 7, 14, 30, 60, 90)
        }
    }

    private fun daysSince(dateString: String): Int {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val date = sdf.parse(dateString)
            val diff = System.currentTimeMillis() - (date?.time ?: 0)
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun calculateReviewPriority(entry: LeetCodeActivityStorage.CompletionEntry): Double {
        val days = daysSince(entry.date)
        // Forgetting curve - Ebbinghaus formula approximation
        val retention = exp(-days.toDouble() / 30.0)
        return 1.0 - retention // Higher priority when retention is lower
    }
}
