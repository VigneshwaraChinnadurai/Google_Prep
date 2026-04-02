package com.vignesh.leetcodechecker.ui

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.data.LeetCodeActivityStorage
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.delay

/**
 * Flashcard Review Screen with Spaced Repetition
 */
@Composable
fun FlashcardScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var flashcards by remember { mutableStateOf(LeetCodeActivityStorage.getDueFlashcards(context)) }
    var currentIndex by remember { mutableStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    
    // Import from History Dialog
    if (showImportDialog) {
        ImportFromHistoryDialog(
            onDismiss = { showImportDialog = false },
            onImport = { count ->
                importFlashcardsFromHistory(context, count)
                flashcards = LeetCodeActivityStorage.getDueFlashcards(context)
                showImportDialog = false
            }
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF58A6FF)
                    )
                }
                Text(
                    text = "📚 Flashcards",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showImportDialog = true }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Import from History",
                        tint = Color(0xFF58A6FF)
                    )
                }
                Text(
                    text = "${currentIndex + 1} / ${flashcards.size}",
                    fontSize = 16.sp,
                    color = Color(0xFF8B949E)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        if (flashcards.isEmpty()) {
            EmptyFlashcardsPlaceholder(
                onImportClick = { showImportDialog = true }
            )
        } else if (showCompleted) {
            CompletedReviewPlaceholder(
                onRestart = {
                    flashcards = LeetCodeActivityStorage.getDueFlashcards(context)
                    currentIndex = 0
                    showCompleted = false
                }
            )
        } else {
            val currentCard = flashcards[currentIndex]
            
            // Progress bar
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / flashcards.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF58A6FF),
                trackColor = Color(0xFF30363D)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Flashcard
            FlashcardView(
                card = currentCard,
                isFlipped = isFlipped,
                onFlip = { isFlipped = !isFlipped },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Rating buttons (shown after flip)
            if (isFlipped) {
                Text(
                    text = "How well did you remember?",
                    fontSize = 14.sp,
                    color = Color(0xFF8B949E),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RatingButton(
                        text = "Again",
                        color = Color(0xFFF85149),
                        onClick = {
                            LeetCodeActivityStorage.updateFlashcardAfterReview(context, currentCard.id, 0)
                            moveToNext(flashcards.size, currentIndex) { newIndex, completed ->
                                currentIndex = newIndex
                                showCompleted = completed
                                isFlipped = false
                            }
                        }
                    )
                    RatingButton(
                        text = "Hard",
                        color = Color(0xFFF0883E),
                        onClick = {
                            LeetCodeActivityStorage.updateFlashcardAfterReview(context, currentCard.id, 2)
                            moveToNext(flashcards.size, currentIndex) { newIndex, completed ->
                                currentIndex = newIndex
                                showCompleted = completed
                                isFlipped = false
                            }
                        }
                    )
                    RatingButton(
                        text = "Good",
                        color = Color(0xFF39D353),
                        onClick = {
                            LeetCodeActivityStorage.updateFlashcardAfterReview(context, currentCard.id, 4)
                            moveToNext(flashcards.size, currentIndex) { newIndex, completed ->
                                currentIndex = newIndex
                                showCompleted = completed
                                isFlipped = false
                            }
                        }
                    )
                    RatingButton(
                        text = "Easy",
                        color = Color(0xFF58A6FF),
                        onClick = {
                            LeetCodeActivityStorage.updateFlashcardAfterReview(context, currentCard.id, 5)
                            moveToNext(flashcards.size, currentIndex) { newIndex, completed ->
                                currentIndex = newIndex
                                showCompleted = completed
                                isFlipped = false
                            }
                        }
                    )
                }
            } else {
                Button(
                    onClick = { isFlipped = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                ) {
                    Text("Show Answer")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun FlashcardView(
    card: LeetCodeActivityStorage.Flashcard,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(400),
        label = "flip"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable { onFlip() }
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (rotation <= 90f) Color(0xFF161B22) else Color(0xFF1C2128)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Flip text when card is flipped
                        rotationY = if (rotation > 90f) 180f else 0f
                    }
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (rotation <= 90f) {
                    // Front (Question)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "❓",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = card.problemTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6EDF3),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = card.question,
                            fontSize = 16.sp,
                            color = Color(0xFF8B949E),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DifficultyChip(difficulty = card.difficulty)
                            card.topics.take(2).forEach { topic ->
                                TopicChip(topic = topic)
                            }
                        }
                    }
                } else {
                    // Back (Answer)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "💡",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Answer",
                            fontSize = 14.sp,
                            color = Color(0xFF8B949E)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = card.answer,
                            fontSize = 16.sp,
                            color = Color(0xFFE6EDF3),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DifficultyChip(difficulty: String) {
    val color = when (difficulty.lowercase()) {
        "easy" -> Color(0xFF00B8A3)
        "medium" -> Color(0xFFFFC01E)
        "hard" -> Color(0xFFFF375F)
        else -> Color(0xFF8B949E)
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = difficulty,
            fontSize = 11.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TopicChip(topic: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF30363D)
    ) {
        Text(
            text = topic.take(12),
            fontSize = 11.sp,
            color = Color(0xFF8B949E),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyFlashcardsPlaceholder(
    onImportClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val historyCount = remember {
        LeetCodeActivityStorage.loadCompletionHistory(context).size
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🎉", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "All caught up!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE6EDF3)
        )
        Text(
            text = "No flashcards due for review",
            fontSize = 14.sp,
            color = Color(0xFF8B949E)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Flashcards are auto-created when you solve problems",
            fontSize = 12.sp,
            color = Color(0xFF6E7681),
            textAlign = TextAlign.Center
        )
        
        if (historyCount > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📖 Import from History",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE6EDF3)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You have $historyCount solved problems in your history",
                        fontSize = 13.sp,
                        color = Color(0xFF8B949E),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onImportClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Flashcards")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedReviewPlaceholder(onRestart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "✅", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Review Complete!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF39D353)
        )
        Text(
            text = "Great job reviewing your flashcards",
            fontSize = 14.sp,
            color = Color(0xFF8B949E)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Review Again")
        }
    }
}

private fun moveToNext(total: Int, current: Int, callback: (Int, Boolean) -> Unit) {
    val nextIndex = current + 1
    if (nextIndex >= total) {
        callback(0, true)
    } else {
        callback(nextIndex, false)
    }
}

@Composable
private fun ImportFromHistoryDialog(
    onDismiss: () -> Unit,
    onImport: (Int) -> Unit
) {
    val context = LocalContext.current
    val history = remember { LeetCodeActivityStorage.loadCompletionHistory(context) }
    val existingFlashcards = remember { LeetCodeActivityStorage.loadFlashcards(context) }
    val existingIds = existingFlashcards.map { it.problemId }.toSet()
    val newProblems = history.filter { it.problemId !in existingIds }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161B22),
        title = {
            Text(
                text = "📖 Import from History",
                color = Color(0xFFE6EDF3),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Create flashcards from your solved problems",
                    color = Color(0xFF8B949E),
                    fontSize = 14.sp
                )
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total solved:",
                                color = Color(0xFF8B949E),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${history.size}",
                                color = Color(0xFFE6EDF3),
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Already have cards:",
                                color = Color(0xFF8B949E),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${existingFlashcards.size}",
                                color = Color(0xFFE6EDF3),
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "New cards to create:",
                                color = Color(0xFF8B949E),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${newProblems.size}",
                                color = Color(0xFF39D353),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                
                if (newProblems.isEmpty()) {
                    Text(
                        text = "All your solved problems already have flashcards!",
                        color = Color(0xFF58A6FF),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(newProblems.size) },
                enabled = newProblems.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF238636),
                    disabledContainerColor = Color(0xFF30363D)
                )
            ) {
                Text("Import ${newProblems.size} Cards")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8B949E))
            }
        }
    )
}

private fun importFlashcardsFromHistory(context: android.content.Context, count: Int) {
    val history = LeetCodeActivityStorage.loadCompletionHistory(context)
    val existingFlashcards = LeetCodeActivityStorage.loadFlashcards(context)
    val existingIds = existingFlashcards.map { it.problemId }.toSet()
    
    // Filter out problems that already have flashcards
    val newProblems = history.filter { it.problemId !in existingIds }
    
    // Create flashcards for each new problem
    newProblems.forEach { entry ->
        val flashcard = LeetCodeActivityStorage.Flashcard(
            problemId = entry.problemId,
            problemTitle = entry.problemTitle,
            question = generateQuestionForProblem(entry),
            answer = generateAnswerForProblem(entry),
            difficulty = entry.difficulty,
            topics = entry.topics,
            lastReviewed = null,
            nextReviewDate = null,
            repetitions = 0,
            easeFactor = 2.5f
        )
        LeetCodeActivityStorage.saveFlashcard(context, flashcard)
    }
}

private fun generateQuestionForProblem(entry: LeetCodeActivityStorage.CompletionEntry): String {
    val topicHint = if (entry.topics.isNotEmpty()) {
        " (Topics: ${entry.topics.take(2).joinToString(", ")})"
    } else ""
    
    return when (entry.difficulty.lowercase()) {
        "easy" -> "What is the key insight to solve \"${entry.problemTitle}\"?$topicHint"
        "medium" -> "Explain your approach to \"${entry.problemTitle}\" and its time complexity.$topicHint"
        "hard" -> "Describe the optimal algorithm for \"${entry.problemTitle}\" including edge cases.$topicHint"
        else -> "How would you solve \"${entry.problemTitle}\"?$topicHint"
    }
}

private fun generateAnswerForProblem(entry: LeetCodeActivityStorage.CompletionEntry): String {
    val difficultyTip = when (entry.difficulty.lowercase()) {
        "easy" -> "Focus on the simplest working solution first."
        "medium" -> "Consider trade-offs between time and space complexity."
        "hard" -> "Look for patterns like DP, graphs, or advanced data structures."
        else -> "Think step by step."
    }
    
    val topicTips = entry.topics.take(3).mapNotNull { topic ->
        when (topic.lowercase()) {
            "array" -> "Consider two-pointer or sliding window techniques."
            "string" -> "Think about character frequency or pattern matching."
            "dynamic programming", "dp" -> "Define subproblems and recurrence relations."
            "tree" -> "Consider DFS/BFS traversal patterns."
            "graph" -> "Think about graph traversal or shortest path algorithms."
            "hash table", "hashmap" -> "Use hash maps for O(1) lookups."
            "binary search" -> "Look for sorted order or search space reduction."
            "stack" -> "Consider monotonic stack for next greater/smaller problems."
            "queue" -> "Use BFS for level-order processing."
            "linked list" -> "Use dummy nodes and two-pointer techniques."
            "recursion" -> "Identify the base case and recursive relation."
            "backtracking" -> "Explore all possibilities with pruning."
            "greedy" -> "Make locally optimal choices."
            "sorting" -> "Consider if sorting simplifies the problem."
            "heap" -> "Use for top-k or streaming median problems."
            else -> null
        }
    }
    
    val tips = listOf(difficultyTip) + topicTips
    return "Review your solution for \"${entry.problemTitle}\":\n\n" +
           tips.joinToString("\n• ", prefix = "• ") +
           "\n\nPractice explaining your approach out loud!"
}
