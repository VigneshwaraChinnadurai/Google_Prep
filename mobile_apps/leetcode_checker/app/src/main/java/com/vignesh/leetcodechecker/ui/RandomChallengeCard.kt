package com.vignesh.leetcodechecker.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.data.LeetCodeActivityStorage
import kotlin.random.Random

/**
 * Problem topics available for challenges
 * Each entry: display name, emoji, LeetCode tag slug
 */
private data class ProblemTopic(val name: String, val emoji: String, val slug: String)

private val PROBLEM_TOPICS = listOf(
    ProblemTopic("Array", "📊", "array"),
    ProblemTopic("String", "📝", "string"),
    ProblemTopic("Dynamic Programming", "🧠", "dynamic-programming"),
    ProblemTopic("Tree", "🌳", "tree"),
    ProblemTopic("Graph", "🔗", "graph"),
    ProblemTopic("Hash Table", "#️⃣", "hash-table"),
    ProblemTopic("Binary Search", "🔍", "binary-search"),
    ProblemTopic("Linked List", "🔗", "linked-list"),
    ProblemTopic("Stack", "📚", "stack"),
    ProblemTopic("Queue", "📤", "queue"),
    ProblemTopic("Heap", "⛰️", "heap-priority-queue"),
    ProblemTopic("Greedy", "💰", "greedy"),
    ProblemTopic("Backtracking", "↩️", "backtracking"),
    ProblemTopic("Sorting", "📈", "sorting"),
    ProblemTopic("Math", "🔢", "math"),
    ProblemTopic("Bit Manipulation", "💻", "bit-manipulation"),
    ProblemTopic("Two Pointers", "👆👆", "two-pointers"),
    ProblemTopic("Sliding Window", "🪟", "sliding-window"),
    ProblemTopic("Recursion", "🔄", "recursion"),
    ProblemTopic("Matrix", "🔲", "matrix")
)

/**
 * Random Challenge Card Component - Select topic first, then difficulty
 */
@Composable
fun RandomChallengeCard(
    onStartChallenge: (String, String) -> Unit,  // (topic, difficulty)
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stats = remember { LeetCodeActivityStorage.loadProblemStats(context) }
    
    // State for selection flow
    var selectedTopic by remember { mutableStateOf<String?>(null) }
    var showTopicSelector by remember { mutableStateOf(false) }
    var showDifficultySelector by remember { mutableStateOf(false) }
    var showChallengeDialog by remember { mutableStateOf(false) }
    var selectedDifficulty by remember { mutableStateOf<String?>(null) }
    
    // Identify weak areas (least solved topics)
    val weakTopics = stats.topicDistribution.entries
        .sortedBy { it.value }
        .take(5)
        .map { it.key }
        .ifEmpty { listOf("Array", "String", "Dynamic Programming") }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161B22)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🎲", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Random Challenge",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3)
                    )
                    Text(
                        text = "Challenge yourself with a surprise problem",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Select Topic Button (Primary action)
            Button(
                onClick = { showTopicSelector = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("🎯 Select Problem Type", fontWeight = FontWeight.Medium)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Quick random challenge button
            OutlinedButton(
                onClick = {
                    val topic = weakTopics.random()
                    val difficulty = listOf("Easy", "Medium", "Hard").random()
                    selectedTopic = topic
                    selectedDifficulty = difficulty
                    showChallengeDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF58A6FF)),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("🎲 Quick Random Challenge")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Weak areas hint
            if (weakTopics.isNotEmpty()) {
                Text(
                    text = "💡 Focus areas: ${weakTopics.take(3).joinToString(", ")}",
                    fontSize = 12.sp,
                    color = Color(0xFF58A6FF)
                )
            }
        }
    }
    
    // Topic Selection Dialog
    if (showTopicSelector) {
        AlertDialog(
            onDismissRequest = { showTopicSelector = false },
            containerColor = Color(0xFF161B22),
            title = {
                Text(
                    text = "Select Problem Type",
                    color = Color(0xFFE6EDF3),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    PROBLEM_TOPICS.forEach { topic ->
                        val isWeak = weakTopics.contains(topic.name)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedTopic = topic.name
                                    showTopicSelector = false
                                    showDifficultySelector = true
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isWeak) Color(0xFF58A6FF).copy(alpha = 0.1f) else Color(0xFF21262D)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = topic.emoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = topic.name,
                                        color = Color(0xFFE6EDF3),
                                        fontSize = 14.sp
                                    )
                                }
                                if (isWeak) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF58A6FF).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Focus",
                                            fontSize = 10.sp,
                                            color = Color(0xFF58A6FF),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTopicSelector = false }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            }
        )
    }
    
    // Difficulty Selection Dialog
    if (showDifficultySelector && selectedTopic != null) {
        AlertDialog(
            onDismissRequest = { 
                showDifficultySelector = false
                selectedTopic = null
            },
            containerColor = Color(0xFF161B22),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = selectedTopic!!,
                        color = Color(0xFFE6EDF3),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select Difficulty",
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DifficultyOptionButton(
                        text = "Easy",
                        color = Color(0xFF00B8A3),
                        onClick = {
                            selectedDifficulty = "Easy"
                            showDifficultySelector = false
                            showChallengeDialog = true
                        }
                    )
                    DifficultyOptionButton(
                        text = "Medium",
                        color = Color(0xFFFFC01E),
                        onClick = {
                            selectedDifficulty = "Medium"
                            showDifficultySelector = false
                            showChallengeDialog = true
                        }
                    )
                    DifficultyOptionButton(
                        text = "Hard",
                        color = Color(0xFFFF375F),
                        onClick = {
                            selectedDifficulty = "Hard"
                            showDifficultySelector = false
                            showChallengeDialog = true
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { 
                    showDifficultySelector = false
                    selectedTopic = null
                }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            }
        )
    }
    
    // Final Challenge Dialog
    if (showChallengeDialog && selectedTopic != null && selectedDifficulty != null) {
        AlertDialog(
            onDismissRequest = { 
                showChallengeDialog = false
                selectedTopic = null
                selectedDifficulty = null
            },
            containerColor = Color(0xFF161B22),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🎯", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your Challenge",
                        color = Color(0xFFE6EDF3),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selectedTopic!!,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = when (selectedDifficulty) {
                            "Easy" -> Color(0xFF00B8A3)
                            "Medium" -> Color(0xFFFFC01E)
                            else -> Color(0xFFFF375F)
                        }.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = selectedDifficulty!!,
                            fontSize = 14.sp,
                            color = when (selectedDifficulty) {
                                "Easy" -> Color(0xFF00B8A3)
                                "Medium" -> Color(0xFFFFC01E)
                                else -> Color(0xFFFF375F)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Go to LeetCode and find a ${selectedDifficulty} ${selectedTopic} problem to solve!",
                        fontSize = 14.sp,
                        color = Color(0xFF8B949E),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Clicking 'Accept' opens LeetCode with filters applied",
                        fontSize = 12.sp,
                        color = Color(0xFF58A6FF),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                val context = LocalContext.current
                Button(
                    onClick = {
                        // Open LeetCode problemset with filters
                        // Look up the proper slug from the topic name
                        val topicSlug = PROBLEM_TOPICS.find { it.name == selectedTopic }?.slug 
                            ?: selectedTopic!!.lowercase().replace(" ", "-")
                        val difficultyParam = selectedDifficulty!!.uppercase()
                        val url = "https://leetcode.com/problemset/?topicSlugs=$topicSlug&difficulty=$difficultyParam"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                        
                        onStartChallenge(selectedTopic!!, selectedDifficulty!!)
                        showChallengeDialog = false
                        selectedTopic = null
                        selectedDifficulty = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open LeetCode")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    // Pick another random but keep topic
                    val newTopic = PROBLEM_TOPICS.random().name
                    val newDifficulty = listOf("Easy", "Medium", "Hard").random()
                    selectedTopic = newTopic
                    selectedDifficulty = newDifficulty
                }) {
                    Text("🔄 Another", color = Color(0xFF58A6FF))
                }
            }
        )
    }
}

@Composable
private fun DifficultyOptionButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
