package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.vignesh.leetcodechecker.data.LeetCodeActivityStorage

/**
 * Achievements Screen - Full achievement gallery
 */
@Composable
fun AchievementsScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val achievements = remember { LeetCodeActivityStorage.loadAchievements(context) }
    val unlocked = achievements.filter { it.unlockedAt != null }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
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
            Text(
                text = "🏆 Achievements",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress circle
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${unlocked.size}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = "${unlocked.size} of ${achievements.size} Unlocked",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE6EDF3)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    LinearProgressIndicator(
                        progress = { unlocked.size.toFloat() / achievements.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFFFD700),
                        trackColor = Color(0xFF30363D)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Achievement categories
        val categories = LeetCodeActivityStorage.AchievementCategory.values()
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            categories.forEach { category ->
                val categoryAchievements = achievements.filter { it.category == category }
                if (categoryAchievements.isNotEmpty()) {
                    item {
                        Text(
                            text = getCategoryTitle(category),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF8B949E)
                        )
                    }
                    
                    items(categoryAchievements) { achievement ->
                        AchievementCard(achievement = achievement)
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun AchievementCard(achievement: LeetCodeActivityStorage.Achievement) {
    val isUnlocked = achievement.unlockedAt != null
    val progress = (achievement.progress.toFloat() / achievement.requirement).coerceIn(0f, 1f)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) Color(0xFF0D2818) else Color(0xFF161B22)
        ),
        border = if (isUnlocked) BorderStroke(1.dp, Color(0xFF238636)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked) Color(0xFFFFD700).copy(alpha = 0.2f)
                        else Color(0xFF30363D)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isUnlocked) achievement.icon else "🔒",
                    fontSize = 28.sp,
                    color = if (isUnlocked) Color.Unspecified else Color(0xFF6E7681)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isUnlocked) Color(0xFFE6EDF3) else Color(0xFF8B949E)
                )
                
                Text(
                    text = achievement.description,
                    fontSize = 12.sp,
                    color = Color(0xFF6E7681)
                )
                
                if (!isUnlocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF58A6FF),
                            trackColor = Color(0xFF30363D)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${achievement.progress}/${achievement.requirement}",
                            fontSize = 11.sp,
                            color = Color(0xFF8B949E)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Unlocked ${achievement.unlockedAt}",
                        fontSize = 11.sp,
                        color = Color(0xFF238636)
                    )
                }
            }
        }
    }
}

private fun getCategoryTitle(category: LeetCodeActivityStorage.AchievementCategory): String {
    return when (category) {
        LeetCodeActivityStorage.AchievementCategory.STREAK -> "🔥 Streak Achievements"
        LeetCodeActivityStorage.AchievementCategory.PROBLEMS_SOLVED -> "✅ Problems Solved"
        LeetCodeActivityStorage.AchievementCategory.DIFFICULTY -> "💪 Difficulty Challenges"
        LeetCodeActivityStorage.AchievementCategory.SPEED -> "⚡ Speed Achievements"
        LeetCodeActivityStorage.AchievementCategory.CONSISTENCY -> "📅 Consistency"
        LeetCodeActivityStorage.AchievementCategory.MASTERY -> "🎯 Mastery"
    }
}
