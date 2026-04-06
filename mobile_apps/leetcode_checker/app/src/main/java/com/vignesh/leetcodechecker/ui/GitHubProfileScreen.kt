package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.leetcodechecker.viewmodel.GitHubProfileViewModel

/**
 * GitHub Profile Screen
 * 
 * Displays:
 * - User profile information
 * - Contribution heatmap (GitHub-style)
 * - Stats and metrics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubProfileScreen(
    viewModel: GitHubProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    
    // Automatically load profile with default username on first composition
    LaunchedEffect(Unit) {
        if (state.user == null && !state.isLoading) {
            viewModel.initializeWithDefault(context)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header with refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GitHub Profile",
                color = Color(0xFFE6EDF3),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { viewModel.refresh(context) },
                enabled = !state.isLoading
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF58A6FF)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF58A6FF))
                }
            }
            
            state.error != null -> {
                ErrorCard(
                    error = state.error!!,
                    onRetry = { viewModel.refresh(context) }
                )
            }
            
            state.user != null -> {
                val user = state.user!!
                
                // Profile Card
                ProfileCard(
                    name = user.name ?: user.login ?: "Unknown",
                    username = user.login ?: "",
                    bio = user.bio,
                    location = user.location,
                    company = user.company,
                    followers = user.followers?.totalCount ?: 0,
                    following = user.following?.totalCount ?: 0,
                    repos = user.repositories?.totalCount ?: 0
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Contribution Stats
                ContributionStatsCard(
                    commits = user.contributionsCollection?.totalCommitContributions ?: 0,
                    pullRequests = user.contributionsCollection?.totalPullRequestContributions ?: 0,
                    issues = user.contributionsCollection?.totalIssueContributions ?: 0,
                    repos = user.contributionsCollection?.totalRepositoryContributions ?: 0
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Contribution Heatmap
                ContributionHeatmap(
                    contributionDays = state.contributionDays,
                    totalContributions = state.totalContributions,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for nav bar
            }
        }
    }
}

@Composable
private fun ProfileCard(
    name: String,
    username: String,
    bio: String?,
    location: String?,
    company: String?,
    followers: Int,
    following: Int,
    repos: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar placeholder
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF30363D)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "Avatar",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = name,
                        color = Color(0xFFE6EDF3),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@$username",
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp
                    )
                }
            }
            
            if (bio != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = bio,
                    color = Color(0xFFE6EDF3),
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (location != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = "Location",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = location,
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(value = followers, label = "Followers")
                StatItem(value = following, label = "Following")
                StatItem(value = repos, label = "Repos")
            }
        }
    }
}

@Composable
private fun StatItem(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            color = Color(0xFFE6EDF3),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color(0xFF8B949E),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ContributionStatsCard(
    commits: Int,
    pullRequests: Int,
    issues: Int,
    repos: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Contribution Activity",
                color = Color(0xFFE6EDF3),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ContributionStatItem(value = commits, label = "Commits", color = Color(0xFF39D353))
                ContributionStatItem(value = pullRequests, label = "PRs", color = Color(0xFF58A6FF))
                ContributionStatItem(value = issues, label = "Issues", color = Color(0xFFA371F7))
                ContributionStatItem(value = repos, label = "Repos", color = Color(0xFFF0883E))
            }
        }
    }
}

@Composable
private fun ContributionStatItem(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value.toString(),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color(0xFF8B949E),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️ Error",
                color = Color(0xFFF85149),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = Color(0xFF8B949E),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
            ) {
                Text("Retry")
            }
        }
    }
}
