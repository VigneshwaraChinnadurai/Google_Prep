package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.data.LeetCodeActivityStorage

/**
 * Analytics Dashboard showing comprehensive LeetCode statistics
 * Data is tracked from:
 * 1. Local completions (when you mark problems complete in LeetCode/Ollama tabs)
 * 2. Can be synced with GitHub revision history (problems pushed to GitHub)
 */
@Composable
fun AnalyticsDashboard(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(LeetCodeActivityStorage.loadProblemStats(context)) }
    val achievements = remember { LeetCodeActivityStorage.loadAchievements(context) }
    val profile = remember { LeetCodeActivityStorage.loadUserProfile(context) }
    
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header with back button
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
                    text = "📊 Analytics Dashboard",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
            }
            IconButton(onClick = { 
                stats = LeetCodeActivityStorage.loadProblemStats(context)
            }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF58A6FF)
                )
            }
        }
        
        // Data source info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Data tracked from problems marked complete in LeetCode & Ollama tabs",
                    fontSize = 11.sp,
                    color = Color(0xFF8B949E)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // User Level Card
        UserLevelCard(profile = profile, stats = stats)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Problem Stats Grid
        ProblemStatsGrid(stats = stats)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Streak Card
        StreakCard(currentStreak = stats.currentStreak, longestStreak = stats.longestStreak)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Difficulty Distribution
        DifficultyDistributionCard(
            easy = stats.easySolved,
            medium = stats.mediumSolved,
            hard = stats.hardSolved
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Topic Mastery
        if (stats.topicDistribution.isNotEmpty()) {
            TopicMasteryCard(topicDistribution = stats.topicDistribution)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // LeetCode Heatmap
        LeetCodeHeatmap()
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Recent Achievements
        RecentAchievementsCard(achievements = achievements)
        
        Spacer(modifier = Modifier.height(80.dp)) // Bottom padding
    }
}

@Composable
private fun UserLevelCard(
    profile: LeetCodeActivityStorage.UserProfile,
    stats: LeetCodeActivityStorage.ProblemStats
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level Circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            profile.level >= 30 -> Color(0xFFFFD700)
                            profile.level >= 20 -> Color(0xFFC0C0C0)
                            profile.level >= 10 -> Color(0xFFCD7F32)
                            else -> Color(0xFF58A6FF)
                        }.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${profile.level}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        profile.level >= 30 -> Color(0xFFFFD700)
                        profile.level >= 20 -> Color(0xFFC0C0C0)
                        profile.level >= 10 -> Color(0xFFCD7F32)
                        else -> Color(0xFF58A6FF)
                    }
                )
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Column {
                Text(
                    text = profile.username,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    text = profile.title,
                    fontSize = 14.sp,
                    color = Color(0xFF8B949E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // XP Progress
                val xpInLevel = profile.xp % 100
                LinearProgressIndicator(
                    progress = { xpInLevel / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF58A6FF),
                    trackColor = Color(0xFF30363D)
                )
                
                Text(
                    text = "$xpInLevel / 100 XP to next level",
                    fontSize = 11.sp,
                    color = Color(0xFF8B949E),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ProblemStatsGrid(stats: LeetCodeActivityStorage.ProblemStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            value = stats.totalSolved.toString(),
            label = "Solved",
            icon = "✅",
            color = Color(0xFF39D353)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = "${stats.weeklyAverage.toInt()}",
            label = "Weekly Avg",
            icon = "📈",
            color = Color(0xFF58A6FF)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = "${stats.averageTimeMinutes}m",
            label = "Avg Time",
            icon = "⏱️",
            color = Color(0xFFF0883E)
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF8B949E)
            )
        }
    }
}

@Composable
private fun StreakCard(currentStreak: Int, longestStreak: Int) {
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🔥", fontSize = 32.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$currentStreak",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF0883E)
                )
                Text(
                    text = "Current Streak",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E)
                )
            }
            
            Divider(
                modifier = Modifier
                    .height(80.dp)
                    .width(1.dp),
                color = Color(0xFF30363D)
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🏆", fontSize = 32.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$longestStreak",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )
                Text(
                    text = "Longest Streak",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E)
                )
            }
        }
    }
}

@Composable
private fun DifficultyDistributionCard(easy: Int, medium: Int, hard: Int) {
    val total = easy + medium + hard
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Difficulty Distribution",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DifficultyCircle(
                    count = easy,
                    total = total,
                    label = "Easy",
                    color = Color(0xFF00B8A3)
                )
                DifficultyCircle(
                    count = medium,
                    total = total,
                    label = "Medium",
                    color = Color(0xFFFFC01E)
                )
                DifficultyCircle(
                    count = hard,
                    total = total,
                    label = "Hard",
                    color = Color(0xFFFF375F)
                )
            }
        }
    }
}

@Composable
private fun DifficultyCircle(count: Int, total: Int, label: String, color: Color) {
    val progress = if (total > 0) count.toFloat() / total else 0f
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(70.dp)) {
                // Background circle
                drawArc(
                    color = Color(0xFF30363D),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
                // Progress arc
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = "$count",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = color
        )
    }
}

@Composable
private fun TopicMasteryCard(topicDistribution: Map<String, Int>) {
    val sortedTopics = topicDistribution.entries.sortedByDescending { it.value }.take(8)
    val maxCount = sortedTopics.maxOfOrNull { it.value } ?: 1
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Topic Mastery",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            sortedTopics.forEach { (topic, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = topic.take(15),
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E),
                        modifier = Modifier.width(100.dp)
                    )
                    
                    LinearProgressIndicator(
                        progress = { count.toFloat() / maxCount },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF58A6FF),
                        trackColor = Color(0xFF30363D)
                    )
                    
                    Text(
                        text = "$count",
                        fontSize = 12.sp,
                        color = Color(0xFFE6EDF3),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentAchievementsCard(achievements: List<LeetCodeActivityStorage.Achievement>) {
    val unlocked = achievements.filter { it.unlockedAt != null }.sortedByDescending { it.unlockedAt }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Achievements",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    text = "${unlocked.size}/${achievements.size}",
                    fontSize = 14.sp,
                    color = Color(0xFF8B949E)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(achievements.take(10)) { achievement ->
                    AchievementBadge(achievement = achievement)
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(achievement: LeetCodeActivityStorage.Achievement) {
    val isUnlocked = achievement.unlockedAt != null
    
    Column(
        modifier = Modifier.width(70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    if (isUnlocked) Color(0xFF238636).copy(alpha = 0.2f)
                    else Color(0xFF30363D)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = achievement.icon,
                fontSize = 24.sp,
                color = if (isUnlocked) Color.Unspecified else Color(0xFF484F58)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = achievement.name,
            fontSize = 10.sp,
            color = if (isUnlocked) Color(0xFFE6EDF3) else Color(0xFF484F58),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
