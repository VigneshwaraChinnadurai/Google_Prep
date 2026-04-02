package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.data.LeetCodeActivityStorage

/**
 * Features Hub Screen - Central navigation for all app features
 */
@Composable
fun FeaturesHubScreen(
    onNavigate: (FeatureDestination) -> Unit,
    onOpenLeaderboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stats = remember { LeetCodeActivityStorage.loadProblemStats(context) }
    val achievements = remember { LeetCodeActivityStorage.loadAchievements(context) }
    val scrollState = rememberScrollState()
    
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
            if (stats.currentStreak > 0) {
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
                            text = "${stats.currentStreak}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF0883E)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Random Challenge Card
        RandomChallengeCard(
            onStartChallenge = { topic, difficulty ->
                // Could open LeetCode with filters
            }
        )
        
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
            FeatureItem("Analytics", "📊", Color(0xFF58A6FF), FeatureDestination.ANALYTICS),
            FeatureItem("Goals", "🎯", Color(0xFF39D353), FeatureDestination.GOALS),
            FeatureItem("Achievements", "🏆", Color(0xFFFFD700), FeatureDestination.ACHIEVEMENTS),
            FeatureItem("Flashcards", "📚", Color(0xFFA371F7), FeatureDestination.FLASHCARDS),
            FeatureItem("Focus Mode", "🎧", Color(0xFFF0883E), FeatureDestination.FOCUS),
            FeatureItem("Interview", "🎤", Color(0xFF00B8A3), FeatureDestination.INTERVIEW),
            FeatureItem("Leaderboard", "📈", Color(0xFFC0C0C0), FeatureDestination.LEADERBOARD),
            FeatureItem("Offline", "📱", Color(0xFF6E7681), FeatureDestination.OFFLINE),
            FeatureItem("AI/ML News", "🤖", Color(0xFF9C27B0), FeatureDestination.AI_NEWS),
            FeatureItem("Protection", "🔒", Color(0xFFF85149), FeatureDestination.PROTECTION)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(features) { feature ->
                FeatureGridItem(
                    feature = feature,
                    onClick = { onNavigate(feature.destination) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Quick Stats Card
        QuickStatsCard(stats = stats)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Recent Achievements Preview
        AchievementsPreview(achievements = achievements)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // LeetCode Heatmap
        LeetCodeHeatmap()
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun FeatureGridItem(
    feature: FeatureItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
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
    }
}

@Composable
private fun QuickStatsCard(stats: LeetCodeActivityStorage.ProblemStats) {
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
                value = "${stats.totalSolved}",
                label = "Solved",
                color = Color(0xFF39D353)
            )
            QuickStatItem(
                value = "${stats.currentStreak}",
                label = "Streak",
                color = Color(0xFFF0883E)
            )
            QuickStatItem(
                value = "${stats.hardSolved}",
                label = "Hard",
                color = Color(0xFFFF375F)
            )
            QuickStatItem(
                value = "${stats.averageTimeMinutes}m",
                label = "Avg Time",
                color = Color(0xFF58A6FF)
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

@Composable
private fun AchievementsPreview(achievements: List<LeetCodeActivityStorage.Achievement>) {
    val unlocked = achievements.filter { it.unlockedAt != null }
    val progress = unlocked.size.toFloat() / achievements.size
    
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
                Text(
                    text = "🏆 Achievements",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    text = "${unlocked.size}/${achievements.size}",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFFFFD700),
                trackColor = Color(0xFF30363D)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Show recent unlocked achievements
                unlocked.sortedByDescending { it.unlockedAt }.take(5).forEach { achievement ->
                    Text(
                        text = achievement.icon,
                        fontSize = 24.sp
                    )
                }
                
                // Show locked placeholders
                repeat((5 - unlocked.size).coerceAtLeast(0)) {
                    Text(
                        text = "🔒",
                        fontSize = 24.sp,
                        color = Color(0xFF30363D)
                    )
                }
            }
        }
    }
}

data class FeatureItem(
    val name: String,
    val emoji: String,
    val color: Color,
    val destination: FeatureDestination
)

enum class FeatureDestination {
    ANALYTICS,
    GOALS,
    ACHIEVEMENTS,
    FLASHCARDS,
    FOCUS,
    INTERVIEW,
    LEADERBOARD,
    OFFLINE,
    PROTECTION,
    AI_NEWS,
    AI_NEWS_SETTINGS
}
