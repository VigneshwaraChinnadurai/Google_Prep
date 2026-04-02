package com.vignesh.leetcodechecker.ui

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
 * Random Challenge Card Component - Pick a random problem from weak areas
 */
@Composable
fun RandomChallengeCard(
    onStartChallenge: (String, String) -> Unit,  // (topic, difficulty)
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stats = remember { LeetCodeActivityStorage.loadProblemStats(context) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedChallenge by remember { mutableStateOf<Pair<String, String>?>(null) }
    
    // Identify weak areas (least solved topics)
    val weakTopics = stats.topicDistribution.entries
        .sortedBy { it.value }
        .take(5)
        .map { it.key }
        .ifEmpty { listOf("Arrays", "Strings", "Dynamic Programming", "Trees", "Graphs") }
    
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
            
            // Quick challenge options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChallengeButton(
                    text = "Easy",
                    color = Color(0xFF00B8A3),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val topic = weakTopics.random()
                        selectedChallenge = Pair(topic, "Easy")
                        showDialog = true
                    }
                )
                ChallengeButton(
                    text = "Medium",
                    color = Color(0xFFFFC01E),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val topic = weakTopics.random()
                        selectedChallenge = Pair(topic, "Medium")
                        showDialog = true
                    }
                )
                ChallengeButton(
                    text = "Hard",
                    color = Color(0xFFFF375F),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val topic = weakTopics.random()
                        selectedChallenge = Pair(topic, "Hard")
                        showDialog = true
                    }
                )
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
    
    // Challenge Dialog
    if (showDialog && selectedChallenge != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
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
                    val (topic, difficulty) = selectedChallenge!!
                    
                    Text(
                        text = topic,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = when (difficulty) {
                            "Easy" -> Color(0xFF00B8A3)
                            "Medium" -> Color(0xFFFFC01E)
                            else -> Color(0xFFFF375F)
                        }.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = difficulty,
                            fontSize = 14.sp,
                            color = when (difficulty) {
                                "Easy" -> Color(0xFF00B8A3)
                                "Medium" -> Color(0xFFFFC01E)
                                else -> Color(0xFFFF375F)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Go to LeetCode and find a $difficulty $topic problem to solve!",
                        fontSize = 14.sp,
                        color = Color(0xFF8B949E),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStartChallenge(selectedChallenge!!.first, selectedChallenge!!.second)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                ) {
                    Text("Accept Challenge")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    // Pick another random challenge
                    val topic = weakTopics.random()
                    val difficulties = listOf("Easy", "Medium", "Hard")
                    selectedChallenge = Pair(topic, difficulties.random())
                }) {
                    Text("🔄 Another", color = Color(0xFF58A6FF))
                }
            }
        )
    }
}

@Composable
private fun ChallengeButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
