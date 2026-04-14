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
import org.json.JSONObject

/**
 * Problem Knowledge Graph - Maps relationships between LeetCode problems.
 * 
 * Features:
 * 1. Prerequisites mapping (learn X before Y)
 * 2. Pattern relationships (same technique, different application)
 * 3. Difficulty progression paths
 * 4. Topic clusters for learning paths
 * 
 * Example relationships:
 * - "Two Sum" → prerequisite for → "3Sum" → prerequisite for → "4Sum"
 * - "Binary Search" → same pattern as → "Search in Rotated Sorted Array"
 * - "DFS on Tree" → same technique as → "DFS on Graph"
 */
class ProblemKnowledgeGraph(
    private val context: Context,
    private val geminiApi: GeminiApi? = null,
    private val apiKey: String? = null
) {
    companion object {
        private const val TAG = "KnowledgeGraph"
        private const val PREFS = "knowledge_graph_prefs"
        private const val KEY_CUSTOM_EDGES = "custom_edges_json"
        
        // Edge types in the knowledge graph
        const val EDGE_PREREQUISITE = "prerequisite_for"
        const val EDGE_SAME_PATTERN = "same_pattern"
        const val EDGE_BUILDS_ON = "builds_on"
        const val EDGE_SIMILAR_TO = "similar_to"
        const val EDGE_HARDER_VERSION = "harder_version_of"
    }

    /**
     * A node in the knowledge graph representing a problem.
     */
    data class ProblemNode(
        val id: String,
        val title: String,
        val difficulty: String,
        val topics: List<String>,
        val pattern: String, // e.g., "Two Pointers", "Sliding Window"
        val solved: Boolean = false,
        val proficiency: Float = 0f // 0-1, how well user knows this
    )

    /**
     * An edge in the knowledge graph.
     */
    data class Edge(
        val fromId: String,
        val toId: String,
        val type: String,
        val description: String = ""
    )

    /**
     * A learning path through the graph.
     */
    data class LearningPath(
        val name: String,
        val description: String,
        val problems: List<ProblemNode>,
        val estimatedHours: Int,
        val targetSkill: String
    )

    // Built-in knowledge graph edges
    private val edges = mutableListOf<Edge>()
    
    // Problem nodes
    private val nodes = mutableMapOf<String, ProblemNode>()
    
    init {
        initializeGraph()
    }

    private fun initializeGraph() {
        // ═══════════════════════════════════════════════════════════════
        // TWO POINTERS LEARNING PATH
        // ═══════════════════════════════════════════════════════════════
        addNode(ProblemNode("125", "Valid Palindrome", "Easy", listOf("Two Pointers", "String"), "Two Pointers"))
        addNode(ProblemNode("167", "Two Sum II", "Medium", listOf("Array", "Two Pointers"), "Two Pointers"))
        addNode(ProblemNode("15", "3Sum", "Medium", listOf("Array", "Two Pointers", "Sorting"), "Two Pointers"))
        addNode(ProblemNode("11", "Container With Most Water", "Medium", listOf("Array", "Two Pointers", "Greedy"), "Two Pointers"))
        addNode(ProblemNode("42", "Trapping Rain Water", "Hard", listOf("Array", "Two Pointers", "Stack", "DP"), "Two Pointers"))
        
        addEdge("125", "167", EDGE_PREREQUISITE, "Master basic two-pointer traversal")
        addEdge("167", "15", EDGE_BUILDS_ON, "Extends two-pointer to three elements")
        addEdge("11", "42", EDGE_HARDER_VERSION, "Same concept, more complex constraint")
        addEdge("15", "11", EDGE_SAME_PATTERN, "Both use shrinking window technique")
        
        // ═══════════════════════════════════════════════════════════════
        // SLIDING WINDOW LEARNING PATH
        // ═══════════════════════════════════════════════════════════════
        addNode(ProblemNode("121", "Best Time to Buy and Sell Stock", "Easy", listOf("Array", "DP"), "Sliding Window"))
        addNode(ProblemNode("3", "Longest Substring Without Repeating Characters", "Medium", listOf("Hash Table", "String", "Sliding Window"), "Sliding Window"))
        addNode(ProblemNode("424", "Longest Repeating Character Replacement", "Medium", listOf("Hash Table", "String", "Sliding Window"), "Sliding Window"))
        addNode(ProblemNode("567", "Permutation in String", "Medium", listOf("Hash Table", "Two Pointers", "String", "Sliding Window"), "Sliding Window"))
        addNode(ProblemNode("76", "Minimum Window Substring", "Hard", listOf("Hash Table", "String", "Sliding Window"), "Sliding Window"))
        
        addEdge("121", "3", EDGE_PREREQUISITE, "Understand tracking max in a pass")
        addEdge("3", "424", EDGE_BUILDS_ON, "Similar sliding window, different constraint")
        addEdge("424", "567", EDGE_SIMILAR_TO, "Both handle character frequency")
        addEdge("567", "76", EDGE_HARDER_VERSION, "More complex window shrinking")
        
        // ═══════════════════════════════════════════════════════════════
        // BINARY SEARCH LEARNING PATH
        // ═══════════════════════════════════════════════════════════════
        addNode(ProblemNode("704", "Binary Search", "Easy", listOf("Array", "Binary Search"), "Binary Search"))
        addNode(ProblemNode("35", "Search Insert Position", "Easy", listOf("Array", "Binary Search"), "Binary Search"))
        addNode(ProblemNode("74", "Search a 2D Matrix", "Medium", listOf("Array", "Binary Search", "Matrix"), "Binary Search"))
        addNode(ProblemNode("33", "Search in Rotated Sorted Array", "Medium", listOf("Array", "Binary Search"), "Binary Search"))
        addNode(ProblemNode("153", "Find Minimum in Rotated Sorted Array", "Medium", listOf("Array", "Binary Search"), "Binary Search"))
        addNode(ProblemNode("4", "Median of Two Sorted Arrays", "Hard", listOf("Array", "Binary Search", "Divide and Conquer"), "Binary Search"))
        
        addEdge("704", "35", EDGE_BUILDS_ON, "Same technique, find insertion point")
        addEdge("35", "74", EDGE_BUILDS_ON, "Binary search on 2D array")
        addEdge("704", "33", EDGE_PREREQUISITE, "Need binary search basics first")
        addEdge("33", "153", EDGE_SAME_PATTERN, "Both handle rotation")
        addEdge("153", "4", EDGE_HARDER_VERSION, "Advanced binary search concepts")
        
        // ═══════════════════════════════════════════════════════════════
        // LINKED LIST LEARNING PATH
        // ═══════════════════════════════════════════════════════════════
        addNode(ProblemNode("206", "Reverse Linked List", "Easy", listOf("Linked List", "Recursion"), "Linked List"))
        addNode(ProblemNode("21", "Merge Two Sorted Lists", "Easy", listOf("Linked List", "Recursion"), "Linked List"))
        addNode(ProblemNode("141", "Linked List Cycle", "Easy", listOf("Hash Table", "Linked List", "Two Pointers"), "Linked List"))
        addNode(ProblemNode("142", "Linked List Cycle II", "Medium", listOf("Hash Table", "Linked List", "Two Pointers"), "Linked List"))
        addNode(ProblemNode("19", "Remove Nth Node From End of List", "Medium", listOf("Linked List", "Two Pointers"), "Linked List"))
        addNode(ProblemNode("23", "Merge k Sorted Lists", "Hard", listOf("Linked List", "Divide and Conquer", "Heap"), "Linked List"))
        
        addEdge("206", "21", EDGE_PREREQUISITE, "Master pointer manipulation")
        addEdge("141", "142", EDGE_BUILDS_ON, "Find cycle, then find start")
        addEdge("21", "23", EDGE_HARDER_VERSION, "Merge 2 → Merge k")
        addEdge("19", "23", EDGE_PREREQUISITE, "Two-pointer technique helps here")
        
        // ═══════════════════════════════════════════════════════════════
        // TREE/BFS/DFS LEARNING PATH
        // ═══════════════════════════════════════════════════════════════
        addNode(ProblemNode("104", "Maximum Depth of Binary Tree", "Easy", listOf("Tree", "DFS", "BFS"), "Tree Traversal"))
        addNode(ProblemNode("226", "Invert Binary Tree", "Easy", listOf("Tree", "DFS", "BFS"), "Tree Traversal"))
        addNode(ProblemNode("100", "Same Tree", "Easy", listOf("Tree", "DFS", "BFS"), "Tree Traversal"))
        addNode(ProblemNode("102", "Binary Tree Level Order Traversal", "Medium", listOf("Tree", "BFS"), "Tree Traversal"))
        addNode(ProblemNode("199", "Binary Tree Right Side View", "Medium", listOf("Tree", "DFS", "BFS"), "Tree Traversal"))
        addNode(ProblemNode("230", "Kth Smallest Element in a BST", "Medium", listOf("Tree", "DFS", "BST"), "Tree Traversal"))
        addNode(ProblemNode("98", "Validate Binary Search Tree", "Medium", listOf("Tree", "DFS", "BST"), "Tree Traversal"))
        addNode(ProblemNode("124", "Binary Tree Maximum Path Sum", "Hard", listOf("Tree", "DFS", "DP"), "Tree Traversal"))
        
        addEdge("104", "226", EDGE_SAME_PATTERN, "Both use recursive DFS")
        addEdge("104", "100", EDGE_SAME_PATTERN, "Compare trees using same logic")
        addEdge("104", "102", EDGE_PREREQUISITE, "Understand tree basics first")
        addEdge("102", "199", EDGE_BUILDS_ON, "Level order with modification")
        addEdge("230", "98", EDGE_SAME_PATTERN, "Both use BST properties")
        addEdge("104", "124", EDGE_PREREQUISITE, "Path concepts build on depth")
        
        // ═══════════════════════════════════════════════════════════════
        // DYNAMIC PROGRAMMING LEARNING PATH
        // ═══════════════════════════════════════════════════════════════
        addNode(ProblemNode("70", "Climbing Stairs", "Easy", listOf("Math", "DP", "Memoization"), "Dynamic Programming"))
        addNode(ProblemNode("198", "House Robber", "Medium", listOf("Array", "DP"), "Dynamic Programming"))
        addNode(ProblemNode("213", "House Robber II", "Medium", listOf("Array", "DP"), "Dynamic Programming"))
        addNode(ProblemNode("322", "Coin Change", "Medium", listOf("Array", "DP", "BFS"), "Dynamic Programming"))
        addNode(ProblemNode("300", "Longest Increasing Subsequence", "Medium", listOf("Array", "Binary Search", "DP"), "Dynamic Programming"))
        addNode(ProblemNode("1143", "Longest Common Subsequence", "Medium", listOf("String", "DP"), "Dynamic Programming"))
        addNode(ProblemNode("72", "Edit Distance", "Medium", listOf("String", "DP"), "Dynamic Programming"))
        addNode(ProblemNode("139", "Word Break", "Medium", listOf("Hash Table", "String", "DP"), "Dynamic Programming"))
        
        addEdge("70", "198", EDGE_PREREQUISITE, "Same recurrence pattern")
        addEdge("198", "213", EDGE_BUILDS_ON, "Circular array version")
        addEdge("70", "322", EDGE_PREREQUISITE, "Understand DP recurrence")
        addEdge("300", "1143", EDGE_SAME_PATTERN, "Both are subsequence problems")
        addEdge("1143", "72", EDGE_BUILDS_ON, "LCS to Edit Distance")
        addEdge("322", "139", EDGE_SAME_PATTERN, "Both use DP with target")
        
        // ═══════════════════════════════════════════════════════════════
        // GRAPH LEARNING PATH
        // ═══════════════════════════════════════════════════════════════
        addNode(ProblemNode("200", "Number of Islands", "Medium", listOf("Array", "DFS", "BFS", "Union Find"), "Graph"))
        addNode(ProblemNode("133", "Clone Graph", "Medium", listOf("Hash Table", "DFS", "BFS", "Graph"), "Graph"))
        addNode(ProblemNode("207", "Course Schedule", "Medium", listOf("DFS", "BFS", "Graph", "Topological Sort"), "Graph"))
        addNode(ProblemNode("210", "Course Schedule II", "Medium", listOf("DFS", "BFS", "Graph", "Topological Sort"), "Graph"))
        addNode(ProblemNode("417", "Pacific Atlantic Water Flow", "Medium", listOf("Array", "DFS", "BFS"), "Graph"))
        addNode(ProblemNode("269", "Alien Dictionary", "Hard", listOf("Array", "String", "DFS", "BFS", "Graph", "Topological Sort"), "Graph"))
        
        addEdge("200", "417", EDGE_SAME_PATTERN, "Both are grid DFS/BFS")
        addEdge("133", "200", EDGE_PREREQUISITE, "Graph traversal basics")
        addEdge("207", "210", EDGE_BUILDS_ON, "Detect cycle → Return order")
        addEdge("210", "269", EDGE_BUILDS_ON, "Topological sort application")
        
        // ═══════════════════════════════════════════════════════════════
        // BACKTRACKING LEARNING PATH
        // ═══════════════════════════════════════════════════════════════
        addNode(ProblemNode("78", "Subsets", "Medium", listOf("Array", "Backtracking", "Bit Manipulation"), "Backtracking"))
        addNode(ProblemNode("90", "Subsets II", "Medium", listOf("Array", "Backtracking", "Bit Manipulation"), "Backtracking"))
        addNode(ProblemNode("46", "Permutations", "Medium", listOf("Array", "Backtracking"), "Backtracking"))
        addNode(ProblemNode("47", "Permutations II", "Medium", listOf("Array", "Backtracking"), "Backtracking"))
        addNode(ProblemNode("39", "Combination Sum", "Medium", listOf("Array", "Backtracking"), "Backtracking"))
        addNode(ProblemNode("40", "Combination Sum II", "Medium", listOf("Array", "Backtracking"), "Backtracking"))
        addNode(ProblemNode("79", "Word Search", "Medium", listOf("Array", "Backtracking", "Matrix"), "Backtracking"))
        addNode(ProblemNode("51", "N-Queens", "Hard", listOf("Array", "Backtracking"), "Backtracking"))
        
        addEdge("78", "90", EDGE_BUILDS_ON, "Handle duplicates")
        addEdge("46", "47", EDGE_BUILDS_ON, "Handle duplicates in permutations")
        addEdge("39", "40", EDGE_BUILDS_ON, "No reuse → Handle duplicates")
        addEdge("78", "46", EDGE_SAME_PATTERN, "Same backtrack template")
        addEdge("78", "39", EDGE_SAME_PATTERN, "Same backtrack template")
        addEdge("79", "51", EDGE_HARDER_VERSION, "More complex constraint checking")
        
        // ═══════════════════════════════════════════════════════════════
        // HEAP / PRIORITY QUEUE LEARNING PATH
        // ═══════════════════════════════════════════════════════════════
        addNode(ProblemNode("703", "Kth Largest Element in a Stream", "Easy", listOf("Tree", "Design", "Heap"), "Heap"))
        addNode(ProblemNode("215", "Kth Largest Element in an Array", "Medium", listOf("Array", "Divide and Conquer", "Sorting", "Heap"), "Heap"))
        addNode(ProblemNode("347", "Top K Frequent Elements", "Medium", listOf("Array", "Hash Table", "Sorting", "Heap"), "Heap"))
        addNode(ProblemNode("295", "Find Median from Data Stream", "Hard", listOf("Two Pointers", "Design", "Sorting", "Heap"), "Heap"))
        addNode(ProblemNode("23", "Merge k Sorted Lists", "Hard", listOf("Linked List", "Divide and Conquer", "Heap"), "Heap"))
        
        addEdge("703", "215", EDGE_SAME_PATTERN, "Same kth largest concept")
        addEdge("215", "347", EDGE_SAME_PATTERN, "Top K pattern")
        addEdge("347", "295", EDGE_BUILDS_ON, "Two heaps technique")
        addEdge("215", "23", EDGE_BUILDS_ON, "Heap for efficient selection")
    }

    private fun addNode(node: ProblemNode) {
        nodes[node.id] = node
    }

    private fun addEdge(fromId: String, toId: String, type: String, description: String) {
        edges.add(Edge(fromId, toId, type, description))
    }

    /**
     * Get all nodes in the graph.
     */
    fun getAllNodes(): List<ProblemNode> = nodes.values.toList()

    /**
     * Get all edges in the graph.
     */
    fun getAllEdges(): List<Edge> = edges.toList()

    /**
     * Get prerequisites for a problem.
     */
    fun getPrerequisites(problemId: String): List<ProblemNode> {
        val prereqIds = edges
            .filter { it.toId == problemId && it.type in listOf(EDGE_PREREQUISITE, EDGE_BUILDS_ON) }
            .map { it.fromId }
        return prereqIds.mapNotNull { nodes[it] }
    }

    /**
     * Get problems that build on a given problem.
     */
    fun getNextProblems(problemId: String): List<ProblemNode> {
        val nextIds = edges
            .filter { it.fromId == problemId }
            .map { it.toId }
        return nextIds.mapNotNull { nodes[it] }
    }

    /**
     * Get similar problems (same pattern).
     */
    fun getSimilarProblems(problemId: String): List<ProblemNode> {
        val similarIds = edges
            .filter { 
                (it.fromId == problemId || it.toId == problemId) && 
                it.type in listOf(EDGE_SAME_PATTERN, EDGE_SIMILAR_TO)
            }
            .flatMap { listOf(it.fromId, it.toId) }
            .filter { it != problemId }
            .distinct()
        return similarIds.mapNotNull { nodes[it] }
    }

    /**
     * Get a learning path for a target skill.
     */
    fun getLearningPath(targetPattern: String): LearningPath {
        val patternNodes = nodes.values
            .filter { it.pattern == targetPattern }
            .sortedBy { 
                when (it.difficulty) {
                    "Easy" -> 0
                    "Medium" -> 1
                    "Hard" -> 2
                    else -> 1
                }
            }
        
        // Use topological sort based on prerequisite edges
        val sorted = topologicalSort(patternNodes)
        
        return LearningPath(
            name = "$targetPattern Mastery",
            description = "Complete learning path for $targetPattern pattern",
            problems = sorted,
            estimatedHours = sorted.size * 1, // ~1 hour per problem on average
            targetSkill = targetPattern
        )
    }

    /**
     * Get all available learning paths.
     */
    fun getAvailableLearningPaths(): List<String> {
        return nodes.values
            .map { it.pattern }
            .distinct()
            .sorted()
    }

    /**
     * Topological sort of nodes based on prerequisites.
     */
    private fun topologicalSort(nodeList: List<ProblemNode>): List<ProblemNode> {
        val nodeIds = nodeList.map { it.id }.toSet()
        val inDegree = mutableMapOf<String, Int>()
        val adjacency = mutableMapOf<String, MutableList<String>>()
        
        for (node in nodeList) {
            inDegree[node.id] = 0
            adjacency[node.id] = mutableListOf()
        }
        
        for (edge in edges) {
            if (edge.fromId in nodeIds && edge.toId in nodeIds && 
                edge.type in listOf(EDGE_PREREQUISITE, EDGE_BUILDS_ON)) {
                adjacency[edge.fromId]?.add(edge.toId)
                inDegree[edge.toId] = (inDegree[edge.toId] ?: 0) + 1
            }
        }
        
        val queue = ArrayDeque<String>()
        for ((id, degree) in inDegree) {
            if (degree == 0) queue.add(id)
        }
        
        val result = mutableListOf<ProblemNode>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            nodes[id]?.let { result.add(it) }
            
            for (nextId in adjacency[id] ?: emptyList()) {
                inDegree[nextId] = (inDegree[nextId] ?: 1) - 1
                if (inDegree[nextId] == 0) {
                    queue.add(nextId)
                }
            }
        }
        
        // Add any remaining nodes (cycles or disconnected)
        for (node in nodeList) {
            if (result.none { it.id == node.id }) {
                result.add(node)
            }
        }
        
        return result
    }

    /**
     * Update user's proficiency for a node.
     */
    fun updateProficiency(problemId: String, solved: Boolean, hintsUsed: Boolean) {
        val node = nodes[problemId] ?: return
        val newProficiency = when {
            !solved -> node.proficiency // No change if not solved
            hintsUsed -> minOf(node.proficiency + 0.3f, 0.7f)
            else -> minOf(node.proficiency + 0.5f, 1.0f)
        }
        nodes[problemId] = node.copy(solved = solved, proficiency = newProficiency)
    }

    /**
     * Sync solved status with activity storage.
     */
    fun syncWithHistory(context: Context) {
        val history = LeetCodeActivityStorage.loadCompletionHistory(context)
        val solvedIds = history.map { it.problemId }.toSet()
        
        for ((id, node) in nodes) {
            if (id in solvedIds) {
                val entry = history.find { it.problemId == id }
                val hintsUsed = entry?.usedHint ?: false
                updateProficiency(id, true, hintsUsed)
            }
        }
    }

    /**
     * Generate Mermaid diagram for visualization.
     */
    fun toMermaidDiagram(): String {
        val sb = StringBuilder()
        sb.appendLine("graph TD")
        
        // Add nodes with styling
        for (node in nodes.values.take(30)) { // Limit for readability
            val shape = when (node.difficulty) {
                "Easy" -> "[${node.title}]"
                "Medium" -> "[[${node.title}]]"
                "Hard" -> "{${node.title}}"
                else -> "[${node.title}]"
            }
            sb.appendLine("    ${node.id}$shape")
        }
        
        // Add edges
        for (edge in edges.take(40)) {
            val style = when (edge.type) {
                EDGE_PREREQUISITE -> "==>"
                EDGE_BUILDS_ON -> "-->"
                EDGE_SAME_PATTERN -> "-.->"
                else -> "-->"
            }
            sb.appendLine("    ${edge.fromId} $style ${edge.toId}")
        }
        
        // Add styling
        sb.appendLine()
        sb.appendLine("    classDef easy fill:#4ade80,stroke:#16a34a")
        sb.appendLine("    classDef medium fill:#facc15,stroke:#ca8a04")
        sb.appendLine("    classDef hard fill:#f87171,stroke:#dc2626")
        
        return sb.toString()
    }
}
