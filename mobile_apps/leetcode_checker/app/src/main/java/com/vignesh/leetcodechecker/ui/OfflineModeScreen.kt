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
import com.vignesh.leetcodechecker.data.DailyChallengeUiModel

/**
 * Offline Mode Screen - View cached problems and solutions
 */
@Composable
fun OfflineModeScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cachedChallenge = remember { ConsistencyStorage.loadChallenge(context) }
    val cachedAi = remember { ConsistencyStorage.loadAi(context) }
    val ollamaChallenge = remember { ConsistencyStorage.loadOllamaChallenge(context) }
    val ollamaAi = remember { ConsistencyStorage.loadOllamaAi(context) }
    
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF58A6FF)
                )
            }
            Text(text = "📱", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Offline Mode",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    text = "Access cached problems without internet",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Status Card
        StatusCard(
            hasGeminiChallenge = cachedChallenge != null,
            hasGeminiSolution = cachedAi != null,
            hasOllamaChallenge = ollamaChallenge != null,
            hasOllamaSolution = ollamaAi != null
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Cached Challenges
        if (cachedChallenge != null || ollamaChallenge != null) {
            Text(
                text = "📋 Cached Problems",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            cachedChallenge?.let { challenge ->
                CachedProblemCard(
                    challenge = challenge,
                    source = "Gemini",
                    hasSolution = cachedAi != null
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            ollamaChallenge?.let { challenge ->
                CachedProblemCard(
                    challenge = challenge,
                    source = "Ollama",
                    hasSolution = ollamaAi != null
                )
            }
        } else {
            EmptyOfflineCard()
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Tips Card
        OfflineTipsCard()
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun StatusCard(
    hasGeminiChallenge: Boolean,
    hasGeminiSolution: Boolean,
    hasOllamaChallenge: Boolean,
    hasOllamaSolution: Boolean
) {
    val totalCached = listOf(hasGeminiChallenge, hasOllamaChallenge).count { it }
    val totalSolutions = listOf(hasGeminiSolution, hasOllamaSolution).count { it }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatusItem(
                icon = "📄",
                value = "$totalCached",
                label = "Problems",
                color = Color(0xFF58A6FF)
            )
            
            Divider(
                modifier = Modifier
                    .height(50.dp)
                    .width(1.dp),
                color = Color(0xFF30363D)
            )
            
            StatusItem(
                icon = "💡",
                value = "$totalSolutions",
                label = "Solutions",
                color = Color(0xFF39D353)
            )
            
            Divider(
                modifier = Modifier
                    .height(50.dp)
                    .width(1.dp),
                color = Color(0xFF30363D)
            )
            
            StatusItem(
                icon = if (totalCached > 0) "✅" else "⚠️",
                value = if (totalCached > 0) "Ready" else "Empty",
                label = "Status",
                color = if (totalCached > 0) Color(0xFF39D353) else Color(0xFFF0883E)
            )
        }
    }
}

@Composable
private fun StatusItem(
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
            fontSize = 18.sp,
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
private fun CachedProblemCard(
    challenge: DailyChallengeUiModel,
    source: String,
    hasSolution: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DifficultyBadge(difficulty = challenge.difficulty)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = source,
                        fontSize = 11.sp,
                        color = Color(0xFF6E7681)
                    )
                }
                
                if (hasSolution) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF238636).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "✓ Has Solution",
                            fontSize = 11.sp,
                            color = Color(0xFF39D353),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = challenge.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            
            Text(
                text = "Date: ${challenge.date}",
                fontSize = 12.sp,
                color = Color(0xFF8B949E)
            )
            
            if (challenge.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    challenge.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF30363D)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.sp,
                                color = Color(0xFF8B949E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            
            // Expandable description
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(
                onClick = { expanded = !expanded }
            ) {
                Text(
                    text = if (expanded) "Hide Description" else "Show Description",
                    color = Color(0xFF58A6FF),
                    fontSize = 12.sp
                )
            }
            
            if (expanded && challenge.descriptionPreview.isNotBlank()) {
                Text(
                    text = challenge.descriptionPreview,
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: String) {
    val color = when (difficulty.lowercase()) {
        "easy" -> Color(0xFF00B8A3)
        "medium" -> Color(0xFFFFC01E)
        "hard" -> Color(0xFFFF375F)
        else -> Color(0xFF8B949E)
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = difficulty,
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyOfflineCard() {
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
            Text(text = "📭", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Cached Problems",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            Text(
                text = "Fetch a challenge while online to cache it",
                fontSize = 14.sp,
                color = Color(0xFF8B949E),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OfflineTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "💡 Offline Tips",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val tips = listOf(
                "• Problems are cached when you fetch daily challenges",
                "• AI solutions are saved after generation",
                "• Use 'Fetch Challenge' while online to prepare",
                "• Flashcards work offline for review",
                "• Focus mode timer works without internet"
            )
            
            tips.forEach { tip ->
                Text(
                    text = tip,
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
