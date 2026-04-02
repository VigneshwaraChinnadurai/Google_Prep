package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.vignesh.leetcodechecker.ConsistencyStorage
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/**
 * Offline Mode Screen - View persistently saved problems and solutions
 * Uses permanent storage that survives app restarts and doesn't get cleared automatically
 */
@Composable
fun OfflineModeScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var savedProblems by remember { mutableStateOf(ConsistencyStorage.loadSavedProblemsHistory(context)) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF58A6FF)
                    )
                }
                Text(text = "💾", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Saved Problems",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3)
                    )
                    Text(
                        text = "Persists across app restarts",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            }
            
            // Problem Count Badge
            if (savedProblems.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF238636)
                ) {
                    Text(
                        text = "${savedProblems.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Status Summary Card
        StatusSummaryCard(savedProblems = savedProblems)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (savedProblems.isEmpty()) {
            EmptyStorageCard()
        } else {
            Text(
                text = "📚 Your Saved Problems",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Saved Problems List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(savedProblems, key = { it.id }) { problem ->
                    SavedProblemCard(
                        problem = problem,
                        onDelete = { showDeleteConfirm = problem.titleSlug }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = Color(0xFF161B22),
            title = {
                Text(
                    text = "Delete Problem?",
                    color = Color(0xFFE6EDF3),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently remove this saved problem.",
                    color = Color(0xFF8B949E)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm?.let { slug ->
                            ConsistencyStorage.deleteSavedProblem(context, slug)
                            savedProblems = ConsistencyStorage.loadSavedProblemsHistory(context)
                        }
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF85149))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            }
        )
    }
}

@Composable
private fun StatusSummaryCard(savedProblems: List<ConsistencyStorage.SavedProblem>) {
    val withSolutions = savedProblems.count { it.solution != null }
    val easyCount = savedProblems.count { it.difficulty.lowercase() == "easy" }
    val mediumCount = savedProblems.count { it.difficulty.lowercase() == "medium" }
    val hardCount = savedProblems.count { it.difficulty.lowercase() == "hard" }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = "📄",
                    value = "${savedProblems.size}",
                    label = "Total",
                    color = Color(0xFF58A6FF)
                )
                
                StatItem(
                    icon = "💡",
                    value = "$withSolutions",
                    label = "With Solutions",
                    color = Color(0xFF39D353)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF30363D))
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DifficultyCount("Easy", easyCount, Color(0xFF00B8A3))
                DifficultyCount("Medium", mediumCount, Color(0xFFFFC01E))
                DifficultyCount("Hard", hardCount, Color(0xFFFF375F))
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: String,
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF8B949E)
        )
    }
}

@Composable
private fun DifficultyCount(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label: $count",
            fontSize = 12.sp,
            color = Color(0xFF8B949E)
        )
    }
}

@Composable
private fun SavedProblemCard(
    problem: ConsistencyStorage.SavedProblem,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val difficultyColor = when (problem.difficulty.lowercase()) {
        "easy" -> Color(0xFF00B8A3)
        "medium" -> Color(0xFFFFC01E)
        "hard" -> Color(0xFFFF375F)
        else -> Color(0xFF8B949E)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Difficulty Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = difficultyColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = problem.difficulty,
                            fontSize = 11.sp,
                            color = difficultyColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Source Badge
                    Text(
                        text = problem.source,
                        fontSize = 10.sp,
                        color = if (problem.source == "Gemini") Color(0xFF00B8A3) else Color(0xFF58A6FF)
                    )
                }
                
                Row {
                    if (problem.solution != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF238636).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "✓ Solution",
                                fontSize = 10.sp,
                                color = Color(0xFF39D353),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFF85149),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "#${problem.questionId}. ${problem.title}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            
            Text(
                text = "Saved: ${problem.date}",
                fontSize = 11.sp,
                color = Color(0xFF6E7681)
            )
            
            // Tags
            if (problem.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    problem.tags.take(4).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF30363D)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 9.sp,
                                color = Color(0xFF8B949E),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
            // Expand/Collapse
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (expanded) "Less" else "More Details",
                    color = Color(0xFF58A6FF),
                    fontSize = 12.sp
                )
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF30363D))
                Spacer(modifier = Modifier.height(8.dp))
                
                if (problem.descriptionPreview.isNotBlank()) {
                    Text(
                        text = "📝 Problem:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE6EDF3)
                    )
                    Text(
                        text = problem.descriptionPreview.take(500) + if (problem.descriptionPreview.length > 500) "..." else "",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                if (problem.solution != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "💡 Solution:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF39D353)
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0D1117)
                    ) {
                        Text(
                            text = problem.solution.take(800) + if (problem.solution.length > 800) "\n..." else "",
                            fontSize = 11.sp,
                            color = Color(0xFFE6EDF3),
                            modifier = Modifier.padding(8.dp),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStorageCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "📭", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Saved Problems",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Problems are automatically saved when you generate solutions in the LeetCode or Ollama tabs",
                fontSize = 14.sp,
                color = Color(0xFF8B949E),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "💡 How to save problems:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE6EDF3)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Go to LeetCode or Ollama tab\n2. Fetch a daily challenge\n3. Generate AI solution\n4. Problem is auto-saved here!",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
