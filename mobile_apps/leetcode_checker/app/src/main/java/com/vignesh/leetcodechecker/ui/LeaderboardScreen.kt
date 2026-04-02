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
import com.vignesh.leetcodechecker.data.LeetCodeApi
import com.vignesh.leetcodechecker.data.GraphQLRequest
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Leaderboard Screen - Shows LeetCode Global Ranking
 * Fetches real data from LeetCode GraphQL API
 */
@Composable
fun LeaderboardScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var globalRanking by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var userRanking by remember { mutableStateOf<UserRankingInfo?>(null) }
    var leetcodeUsername by remember { mutableStateOf("") }
    var showUsernameDialog by remember { mutableStateOf(false) }
    
    // Load saved username
    val savedPrefs = remember { context.getSharedPreferences("leetcode_leaderboard", android.content.Context.MODE_PRIVATE) }
    
    LaunchedEffect(Unit) {
        leetcodeUsername = savedPrefs.getString("leetcode_username", "") ?: ""
        fetchLeaderboardData(
            username = leetcodeUsername,
            onSuccess = { ranking, userInfo ->
                globalRanking = ranking
                userRanking = userInfo
                isLoading = false
            },
            onError = { error ->
                errorMessage = error
                isLoading = false
            }
        )
    }
    
    // Username Input Dialog
    if (showUsernameDialog) {
        var inputUsername by remember { mutableStateOf(leetcodeUsername) }
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            containerColor = Color(0xFF161B22),
            title = {
                Text(
                    text = "🔗 LeetCode Username",
                    color = Color(0xFFE6EDF3),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your LeetCode username to see your global ranking",
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputUsername,
                        onValueChange = { inputUsername = it },
                        placeholder = { Text("e.g., tourist", color = Color(0xFF6E7681)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        leetcodeUsername = inputUsername
                        savedPrefs.edit().putString("leetcode_username", inputUsername).apply()
                        showUsernameDialog = false
                        isLoading = true
                        scope.launch {
                            fetchLeaderboardData(
                                username = inputUsername,
                                onSuccess = { ranking, userInfo ->
                                    globalRanking = ranking
                                    userRanking = userInfo
                                    isLoading = false
                                    errorMessage = null
                                },
                                onError = { error ->
                                    errorMessage = error
                                    isLoading = false
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                ) {
                    Text("Save & Refresh")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
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
                Text(
                    text = "🏆 Global Ranking",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
            }
            
            Row {
                IconButton(onClick = { showUsernameDialog = true }) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "Set Username",
                        tint = Color(0xFF58A6FF)
                    )
                }
                IconButton(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            fetchLeaderboardData(
                                username = leetcodeUsername,
                                onSuccess = { ranking, userInfo ->
                                    globalRanking = ranking
                                    userRanking = userInfo
                                    isLoading = false
                                },
                                onError = { error ->
                                    errorMessage = error
                                    isLoading = false
                                }
                            )
                        }
                    }
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF58A6FF)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Data Source Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "📊", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "LeetCode Global Contest Ranking",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE6EDF3)
                    )
                    Text(
                        text = "Updated from leetcode.com",
                        fontSize = 11.sp,
                        color = Color(0xFF58A6FF)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF58A6FF))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Fetching rankings from LeetCode...",
                        color = Color(0xFF8B949E)
                    )
                }
            }
        } else if (errorMessage != null) {
            ErrorCard(
                message = errorMessage!!,
                onRetry = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        fetchLeaderboardData(
                            username = leetcodeUsername,
                            onSuccess = { ranking, userInfo ->
                                globalRanking = ranking
                                userRanking = userInfo
                                isLoading = false
                            },
                            onError = { error ->
                                errorMessage = error
                                isLoading = false
                            }
                        )
                    }
                }
            )
        } else {
            // Your Rank Card (if username is set)
            if (userRanking != null) {
                YourRankCard(userRanking!!)
                Spacer(modifier = Modifier.height(16.dp))
            } else if (leetcodeUsername.isBlank()) {
                SetUsernamePrompt(onClick = { showUsernameDialog = true })
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Top 3 Podium
            if (globalRanking.size >= 3) {
                TopThreePodium(globalRanking.take(3))
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            Text(
                text = "Top Global Players",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Full Leaderboard
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(globalRanking) { index, entry ->
                    LeaderboardRow(entry = entry, rank = index + 1)
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

// Data classes for leaderboard
data class LeaderboardEntry(
    val username: String,
    val rating: Int,
    val globalRank: Int,
    val country: String?,
    val isCurrentUser: Boolean = false
)

data class UserRankingInfo(
    val username: String,
    val globalRank: Int?,
    val totalSolved: Int,
    val easySolved: Int,
    val mediumSolved: Int,
    val hardSolved: Int
)

private suspend fun fetchLeaderboardData(
    username: String,
    onSuccess: (List<LeaderboardEntry>, UserRankingInfo?) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val api = Retrofit.Builder()
            .baseUrl("https://leetcode.com/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(LeetCodeApi::class.java)
        
        // Fetch global ranking (top players)
        val globalQuery = """
            query globalRanking(${"$"}page: Int!) {
                globalRanking(page: ${"$"}page) {
                    totalUsers
                    rankingNodes {
                        ranking
                        currentRating
                        currentGlobalRanking
                        dataRegion
                        user {
                            username
                            profile {
                                userAvatar
                                countryName
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        
        val globalResponse = api.getGlobalRanking(
            GraphQLRequest(
                query = globalQuery,
                variables = mapOf("page" to 1)
            )
        )
        
        val rankings = globalResponse.data?.globalRanking?.rankingNodes?.mapIndexed { index, node ->
            LeaderboardEntry(
                username = node.user?.username ?: "Unknown",
                rating = node.currentRating ?: 0,
                globalRank = node.currentGlobalRanking ?: (index + 1),
                country = node.user?.profile?.countryName,
                isCurrentUser = node.user?.username?.equals(username, ignoreCase = true) == true
            )
        } ?: emptyList()
        
        // Fetch user profile if username is provided
        var userInfo: UserRankingInfo? = null
        if (username.isNotBlank()) {
            try {
                val userQuery = """
                    query userPublicProfile(${"$"}username: String!) {
                        matchedUser(username: ${"$"}username) {
                            username
                            profile {
                                ranking
                                userAvatar
                                realName
                                countryName
                            }
                            submitStats {
                                acSubmissionNum {
                                    difficulty
                                    count
                                }
                            }
                        }
                    }
                """.trimIndent()
                
                val userResponse = api.getUserProfile(
                    GraphQLRequest(
                        query = userQuery,
                        variables = mapOf("username" to username)
                    )
                )
                
                userResponse.data?.matchedUser?.let { user ->
                    val stats = user.submitStats?.acSubmissionNum ?: emptyList()
                    userInfo = UserRankingInfo(
                        username = user.username ?: username,
                        globalRank = user.profile?.ranking,
                        totalSolved = stats.sumOf { it.count ?: 0 },
                        easySolved = stats.find { it.difficulty == "Easy" }?.count ?: 0,
                        mediumSolved = stats.find { it.difficulty == "Medium" }?.count ?: 0,
                        hardSolved = stats.find { it.difficulty == "Hard" }?.count ?: 0
                    )
                }
            } catch (e: Exception) {
                // User profile fetch failed, continue without it
            }
        }
        
        onSuccess(rankings, userInfo)
    } catch (e: Exception) {
        onError("Failed to fetch rankings: ${e.message}")
    }
}

@Composable
private fun TopThreePodium(leaderboard: List<LeaderboardEntry>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        // 2nd place
        PodiumItem(
            entry = leaderboard[1],
            rank = 2,
            height = 100.dp,
            color = Color(0xFFC0C0C0)
        )
        
        // 1st place
        PodiumItem(
            entry = leaderboard[0],
            rank = 1,
            height = 130.dp,
            color = Color(0xFFFFD700)
        )
        
        // 3rd place
        PodiumItem(
            entry = leaderboard[2],
            rank = 3,
            height = 80.dp,
            color = Color(0xFFCD7F32)
        )
    }
}

@Composable
private fun PodiumItem(
    entry: LeaderboardEntry,
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
                text = entry.username.firstOrNull()?.uppercase() ?: "?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = entry.username.take(10),
            fontSize = 11.sp,
            color = Color(0xFFE6EDF3),
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = "${entry.rating}",
            fontSize = 10.sp,
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
private fun YourRankCard(userInfo: UserRankingInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = BorderStroke(1.dp, Color(0xFF58A6FF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        text = if (userInfo.globalRank != null) "#${userInfo.globalRank}" else "N/A",
                        fontSize = if (userInfo.globalRank != null && userInfo.globalRank > 99999) 11.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF58A6FF)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your Global Rank",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )
                    Text(
                        text = userInfo.username,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3)
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${userInfo.totalSolved}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF39D353)
                    )
                    Text(
                        text = "solved",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF30363D))
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniStat(value = "${userInfo.easySolved}", label = "Easy", color = Color(0xFF00B8A3))
                MiniStat(value = "${userInfo.mediumSolved}", label = "Medium", color = Color(0xFFFFC01E))
                MiniStat(value = "${userInfo.hardSolved}", label = "Hard", color = Color(0xFFFF375F))
            }
        }
    }
}

@Composable
private fun SetUsernamePrompt(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Color(0xFF58A6FF),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Set Your LeetCode Username",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    text = "See your global ranking position",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E)
                )
            }
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF8B949E)
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "⚠️", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Failed to Load Rankings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )
            Text(
                text = message,
                fontSize = 13.sp,
                color = Color(0xFF8B949E),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, color: Color = Color(0xFFE6EDF3)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
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
private fun LeaderboardRow(
    entry: LeaderboardEntry,
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
                    text = entry.username.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Name and country
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.username + if (entry.isCurrentUser) " (You)" else "",
                    fontSize = 14.sp,
                    color = Color(0xFFE6EDF3),
                    fontWeight = if (entry.isCurrentUser) FontWeight.Bold else FontWeight.Normal
                )
                if (entry.country != null) {
                    Text(
                        text = entry.country,
                        fontSize = 11.sp,
                        color = Color(0xFF6E7681)
                    )
                }
            }
            
            // Rating
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${entry.rating}",
                    fontSize = 14.sp,
                    color = when (rank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> Color(0xFF58A6FF)
                    },
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "rating",
                    fontSize = 10.sp,
                    color = Color(0xFF6E7681)
                )
            }
        }
    }
}
