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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.data.LeetCodeActivityStorage
import com.vignesh.leetcodechecker.data.LeetCodeProfileSummary
import com.vignesh.leetcodechecker.data.LeetCodeRepository

/**
 * Features Hub Screen - Central navigation for all app features
 */
@Composable
fun FeaturesHubScreen(
    onNavigate: (FeatureDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stats = remember { LeetCodeActivityStorage.loadProblemStats(context) }
    val scrollState = rememberScrollState()

    // Solved/streak/hard here used to come from local, app-only completion history --
    // which disagreed with the real LeetCode totals shown in the Profile tab (same
    // pattern as the streak-mismatch bug). Prefer the real API totals once fetched,
    // falling back to the local count only until that first fetch resolves.
    var realSummary by remember { mutableStateOf<LeetCodeProfileSummary?>(null) }
    LaunchedEffect(Unit) {
        LeetCodeRepository(context).fetchLeetCodeProfileSummary().onSuccess { realSummary = it }
    }
    val displayedTotalSolved = realSummary?.totalSolved ?: stats.totalSolved
    val displayedStreak = realSummary?.currentStreak ?: stats.currentStreak
    val displayedHardSolved = realSummary?.hardSolved ?: stats.hardSolved
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header with streak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Features Hub",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    text = "Explore & enhance your practice",
                    fontSize = 14.sp,
                    color = Color(0xFF8B949E)
                )
            }
            
            // Streak badge
            if (displayedStreak > 0) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF0883E).copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔥", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$displayedStreak",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF0883E)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        // Feature Grid
        Text(
            text = "Practice Tools",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE6EDF3)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        val features = listOf(
            FeatureItem("LeetCode", "🏠", Color(0xFF1F6FEB), FeatureDestination.LEETCODE),
            FeatureItem("Ollama", "🔧", Color(0xFF6E7681), FeatureDestination.OLLAMA),
            FeatureItem("Global Settings", "⚙", Color(0xFFA3A3A3), FeatureDestination.GLOBAL_SETTINGS),
            FeatureItem("AI Hub", "🧠", Color(0xFFFF6B6B), FeatureDestination.AI_LEARNING_HUB),
            FeatureItem("Chatbot", "💬", Color(0xFF00D4AA), FeatureDestination.STRATEGIC_CHATBOT),
            FeatureItem("Analytics", "📊", Color(0xFF58A6FF), FeatureDestination.ANALYTICS),
            FeatureItem("Achievements", "🏆", Color(0xFFFFD700), FeatureDestination.ACHIEVEMENTS),
            FeatureItem("Goals", "🎯", Color(0xFF39D353), FeatureDestination.GOALS),
            FeatureItem("Flashcards", "📚", Color(0xFFA371F7), FeatureDestination.FLASHCARDS),
            FeatureItem("Focus Mode", "🎧", Color(0xFFF0883E), FeatureDestination.FOCUS),
            FeatureItem("Interview", "🎤", Color(0xFF00B8A3), FeatureDestination.INTERVIEW),
            FeatureItem("Leaderboard", "📈", Color(0xFFC0C0C0), FeatureDestination.LEADERBOARD),
            FeatureItem("Offline", "📱", Color(0xFF6E7681), FeatureDestination.OFFLINE),
            FeatureItem("AI/ML News", "🤖", Color(0xFF9C27B0), FeatureDestination.AI_NEWS),
            FeatureItem("Protection", "🔒", Color(0xFFF85149), FeatureDestination.PROTECTION),
            FeatureItem("What's New", "✨", Color(0xFFFFB347), FeatureDestination.WHATS_NEW)
        )
        
        // Check for unseen updates
        val unseenCount = remember { getUnseenUpdateCount(context) }

        // Non-lazy, content-sized grid: the item count is small and fixed, so there's
        // no need for LazyVerticalGrid's own scroll viewport (which forced a clipped
        // fixed height here). Rows just wrap to their content inside the outer
        // scrollable Column instead.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            features.chunked(4).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { feature ->
                        Box(modifier = Modifier.weight(1f)) {
                            FeatureGridItem(
                                feature = feature,
                                onClick = { onNavigate(feature.destination) },
                                badgeCount = if (feature.destination == FeatureDestination.WHATS_NEW) unseenCount else 0
                            )
                        }
                    }
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Stats Card
        QuickStatsCard(totalSolved = displayedTotalSolved, streak = displayedStreak, hardSolved = displayedHardSolved)

        Spacer(modifier = Modifier.height(16.dp))

        // LeetCode Heatmap
        LeetCodeHeatmap()
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun FeatureGridItem(
    feature: FeatureItem,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = feature.emoji,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = feature.name,
                    fontSize = 11.sp,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            
            // Badge for unseen updates
            if (badgeCount > 0) {
                Badge(
                    containerColor = Color(0xFFF85149),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                ) {
                    Text(
                        text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStatsCard(totalSolved: Int, streak: Int, hardSolved: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickStatItem(
                value = "$totalSolved",
                label = "Solved",
                color = Color(0xFF39D353)
            )
            QuickStatItem(
                value = "$streak",
                label = "Streak",
                color = Color(0xFFF0883E)
            )
            QuickStatItem(
                value = "$hardSolved",
                label = "Hard",
                color = Color(0xFFFF375F)
            )
        }
    }
}

@Composable
private fun QuickStatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

data class FeatureItem(
    val name: String,
    val emoji: String,
    val color: Color,
    val destination: FeatureDestination
)

enum class FeatureDestination {
    LEETCODE,
    ANALYTICS,
    GOALS,
    ACHIEVEMENTS,
    FLASHCARDS,
    FOCUS,
    INTERVIEW,
    LEADERBOARD,
    OFFLINE,
    OLLAMA,
    CHATBOT,
    AI_NEWS,
    AI_NEWS_SETTINGS,
    GLOBAL_SETTINGS,
    PROFILE,
    GITHUB_PROFILE,
    PROTECTION,
    AI_LEARNING_HUB,
    STRATEGIC_CHATBOT,
    WHATS_NEW,
    BOOK_READER
}
