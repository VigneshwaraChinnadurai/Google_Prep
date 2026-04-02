package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.vignesh.leetcodechecker.data.LeetCodeActivityStorage

/**
 * Leaderboard Screen - Compare with other users
 * Note: Currently uses mock data for demonstration. Can be connected to a backend or GitHub leaderboard.
 */
@Composable
fun LeaderboardScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val leaderboard = remember { LeetCodeActivityStorage.getLeaderboard(context) }
    val userProfile = remember { LeetCodeActivityStorage.loadUserProfile(context) }
    val stats = remember { LeetCodeActivityStorage.loadProblemStats(context) }
    
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
                text = "🏆 Leaderboard",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Top 3 Podium
        TopThreePodium(leaderboard = leaderboard.take(3))
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Your Rank Card
        val userEntry = leaderboard.find { it.isCurrentUser }
        if (userEntry != null) {
            YourRankCard(entry = userEntry, stats = stats)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Full Leaderboard
        Text(
            text = "Full Rankings",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE6EDF3)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(leaderboard) { index, entry ->
                LeaderboardRow(entry = entry, rank = index + 1)
            }
        }
    }
}

@Composable
private fun TopThreePodium(leaderboard: List<LeetCodeActivityStorage.LeaderboardEntry>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        // 2nd place
        if (leaderboard.size > 1) {
            PodiumItem(
                entry = leaderboard[1],
                rank = 2,
                height = 100.dp,
                color = Color(0xFFC0C0C0)
            )
        }
        
        // 1st place
        if (leaderboard.isNotEmpty()) {
            PodiumItem(
                entry = leaderboard[0],
                rank = 1,
                height = 130.dp,
                color = Color(0xFFFFD700)
            )
        }
        
        // 3rd place
        if (leaderboard.size > 2) {
            PodiumItem(
                entry = leaderboard[2],
                rank = 3,
                height = 80.dp,
                color = Color(0xFFCD7F32)
            )
        }
    }
}

@Composable
private fun PodiumItem(
    entry: LeetCodeActivityStorage.LeaderboardEntry,
    rank: Int,
    height: androidx.compose.ui.unit.Dp,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f))
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.username.first().uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = entry.username,
            fontSize = 12.sp,
            color = Color(0xFFE6EDF3),
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = "${entry.score} pts",
            fontSize = 11.sp,
            color = Color(0xFF8B949E)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Podium base
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(height)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(color, color.copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = when (rank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "$rank"
                },
                fontSize = 28.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun YourRankCard(
    entry: LeetCodeActivityStorage.LeaderboardEntry,
    stats: LeetCodeActivityStorage.ProblemStats
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161B22)
        ),
        border = BorderStroke(1.dp, Color(0xFF58A6FF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF58A6FF).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#${entry.rank}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF58A6FF)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your Rank",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E)
                )
                Text(
                    text = entry.username,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${entry.score}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF58A6FF)
                )
                Text(
                    text = "points",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E)
                )
            }
        }
        
        Divider(color = Color(0xFF30363D))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MiniStat(value = "${stats.totalSolved}", label = "Solved")
            MiniStat(value = "${stats.currentStreak}🔥", label = "Streak")
            MiniStat(value = "${stats.hardSolved}", label = "Hard")
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE6EDF3)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF8B949E)
        )
    }
}

@Composable
private fun LeaderboardRow(
    entry: LeetCodeActivityStorage.LeaderboardEntry,
    rank: Int
) {
    val backgroundColor = when {
        entry.isCurrentUser -> Color(0xFF0D2818)
        rank <= 3 -> Color(0xFF161B22)
        else -> Color(0xFF161B22)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (entry.isCurrentUser) BorderStroke(1.dp, Color(0xFF238636)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center
            ) {
                when (rank) {
                    1 -> Text("🥇", fontSize = 20.sp)
                    2 -> Text("🥈", fontSize = 20.sp)
                    3 -> Text("🥉", fontSize = 20.sp)
                    else -> Text(
                        text = "#$rank",
                        fontSize = 14.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF30363D)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.username.first().uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Name
            Text(
                text = entry.username + if (entry.isCurrentUser) " (You)" else "",
                fontSize = 14.sp,
                color = Color(0xFFE6EDF3),
                fontWeight = if (entry.isCurrentUser) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            
            // Score
            Text(
                text = "${entry.score} pts",
                fontSize = 14.sp,
                color = when (rank) {
                    1 -> Color(0xFFFFD700)
                    2 -> Color(0xFFC0C0C0)
                    3 -> Color(0xFFCD7F32)
                    else -> Color(0xFF8B949E)
                },
                fontWeight = FontWeight.Medium
            )
        }
    }
}
