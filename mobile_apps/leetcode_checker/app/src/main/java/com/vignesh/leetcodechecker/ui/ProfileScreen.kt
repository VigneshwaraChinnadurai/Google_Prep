package com.vignesh.leetcodechecker.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vignesh.leetcodechecker.data.LeetCodeBadge
import com.vignesh.leetcodechecker.data.LeetCodeProfileSummary
import com.vignesh.leetcodechecker.data.LeetCodeRepository
import com.vignesh.leetcodechecker.data.TagProblemCount
import com.vignesh.leetcodechecker.prooffiling.ProofFilingUIState
import com.vignesh.leetcodechecker.prooffiling.ProofFilingViewModel
import com.vignesh.leetcodechecker.viewmodel.GitHubProfileViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ProfileScreen - Unified profile view with expandable sections
 * 
 * Contains:
 * - ProofFiling summary card (this week's progress)
 * - GitHub dropdown (contribution graph, stats)
 * - LeetCode dropdown (solved breakdown, streaks, heatmap, topic mastery, badges)
 * - Credly dropdown (badges/certifications)
 * - LinkedIn dropdown (profile preview)
 * - Medium dropdown (blog articles)
 */
@Composable
fun ProfileScreen(
    gitHubViewModel: GitHubProfileViewModel,
    proofFilingViewModel: ProofFilingViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val gitHubState by gitHubViewModel.state.collectAsState()
    val proofFilingState by proofFilingViewModel.state.collectAsState()
    
    // Section expansion states
    var githubExpanded by remember { mutableStateOf(true) }
    var leetcodeExpanded by remember { mutableStateOf(true) }
    var credlyExpanded by remember { mutableStateOf(false) }
    var linkedinExpanded by remember { mutableStateOf(false) }
    var mediumExpanded by remember { mutableStateOf(false) }

    // Refresh trigger to force reload
    var refreshTrigger by remember { mutableStateOf(0) }

    // Medium articles state
    var mediumArticles by remember { mutableStateOf<List<MediumArticle>>(emptyList()) }
    var mediumLoading by remember { mutableStateOf(false) }
    var mediumError by remember { mutableStateOf<String?>(null) }

    // Credly badges state
    var credlyBadges by remember { mutableStateOf<List<CredlyBadge>>(emptyList()) }
    var credlyLoading by remember { mutableStateOf(false) }
    var credlyError by remember { mutableStateOf<String?>(null) }

    // LeetCode profile summary state
    var leetcodeSummary by remember { mutableStateOf<LeetCodeProfileSummary?>(null) }
    var leetcodeLoading by remember { mutableStateOf(false) }
    var leetcodeError by remember { mutableStateOf<String?>(null) }
    
    // Load GitHub profile on first composition
    LaunchedEffect(Unit) {
        if (gitHubState.user == null && !gitHubState.isLoading) {
            gitHubViewModel.initializeWithDefault(context)
        }
    }
    
    // Load Medium articles when expanded, or unconditionally on refresh (even collapsed)
    LaunchedEffect(mediumExpanded, refreshTrigger) {
        if ((mediumExpanded || refreshTrigger > 0) && !mediumLoading) {
            mediumLoading = true
            mediumError = null
            try {
                val articles = fetchMediumArticles("rockingstarvic")
                mediumArticles = articles
            } catch (e: Exception) {
                mediumError = e.message ?: "Failed to load articles"
            }
            mediumLoading = false
        }
    }
    
    // Load Credly badges when expanded, or unconditionally on refresh (even collapsed)
    LaunchedEffect(credlyExpanded, refreshTrigger) {
        if ((credlyExpanded || refreshTrigger > 0) && !credlyLoading) {
            credlyLoading = true
            credlyError = null
            try {
                val badges = fetchCredlyBadges("vigneshwarachinnadurai")
                credlyBadges = badges
            } catch (e: Exception) {
                credlyError = e.message ?: "Failed to load badges"
            }
            credlyLoading = false
        }
    }

    // Load LeetCode profile summary when expanded, or unconditionally on refresh (even collapsed)
    LaunchedEffect(leetcodeExpanded, refreshTrigger) {
        if ((leetcodeExpanded || refreshTrigger > 0) && !leetcodeLoading) {
            leetcodeLoading = true
            leetcodeError = null
            try {
                leetcodeSummary = LeetCodeRepository(context).fetchLeetCodeProfileSummary().getOrThrow()
            } catch (e: Exception) {
                leetcodeError = e.message ?: "Failed to load LeetCode profile"
            }
            leetcodeLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                color = Color(0xFFE6EDF3),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { 
                    gitHubViewModel.refresh(context)
                    // Trigger reload of other data
                    refreshTrigger++
                },
                enabled = !gitHubState.isLoading
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF58A6FF)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ProofFiling Summary Card
        ProofFilingSummaryCard(state = proofFilingState)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // GitHub Section
        ProfileDropdownSection(
            title = "GitHub",
            subtitle = "@VigneshwaraChinnadurai",
            icon = { 
                // GitHub icon (using code icon as proxy)
                Icon(Icons.Filled.Build, contentDescription = null, tint = Color.White)
            },
            iconBackground = Color(0xFF24292E),
            expanded = githubExpanded,
            onToggle = { githubExpanded = !githubExpanded }
        ) {
            GitHubContent(
                state = gitHubState,
                onRetry = { gitHubViewModel.refresh(context) }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // LeetCode Section
        ProfileDropdownSection(
            title = "LeetCode",
            subtitle = "Solved Problems, Streaks & Badges",
            icon = {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White)
            },
            iconBackground = Color(0xFFFFA116),
            expanded = leetcodeExpanded,
            onToggle = { leetcodeExpanded = !leetcodeExpanded }
        ) {
            LeetCodeProfileContent(
                summary = leetcodeSummary,
                isLoading = leetcodeLoading,
                error = leetcodeError,
                onOpenProfile = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://leetcode.com/u/rockingstarvic/"))
                    context.startActivity(intent)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Credly Section
        ProfileDropdownSection(
            title = "Credly",
            subtitle = "Certifications & Badges",
            icon = {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White)
            },
            iconBackground = Color(0xFFFF6B00),
            expanded = credlyExpanded,
            onToggle = { credlyExpanded = !credlyExpanded }
        ) {
            CredlyContent(
                badges = credlyBadges,
                isLoading = credlyLoading,
                error = credlyError,
                onOpenProfile = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.credly.com/users/vigneshwarachinnadurai/badges"))
                    context.startActivity(intent)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // LinkedIn Section
        ProfileDropdownSection(
            title = "LinkedIn",
            subtitle = "Professional Network",
            icon = {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
            },
            iconBackground = Color(0xFF0A66C2),
            expanded = linkedinExpanded,
            onToggle = { linkedinExpanded = !linkedinExpanded }
        ) {
            LinkedInContent(
                onOpenProfile = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/vigneshwarac/"))
                    context.startActivity(intent)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Medium Section
        ProfileDropdownSection(
            title = "Medium",
            subtitle = "Blog Articles",
            icon = {
                Icon(Icons.Filled.Create, contentDescription = null, tint = Color.White)
            },
            iconBackground = Color(0xFF000000),
            expanded = mediumExpanded,
            onToggle = { mediumExpanded = !mediumExpanded }
        ) {
            MediumContent(
                articles = mediumArticles,
                isLoading = mediumLoading,
                error = mediumError,
                onOpenArticle = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                },
                onOpenProfile = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://medium.com/@rockingstarvic"))
                    context.startActivity(intent)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for nav bar
    }
}

@Composable
private fun ProfileDropdownSection(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    iconBackground: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column {
            // Header (clickable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Title and subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(0xFFE6EDF3),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp
                    )
                }
                
                // Expand/collapse icon
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color(0xFF8B949E),
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            }
            
            // Content (animated)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF30363D), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun GitHubContent(
    state: com.vignesh.leetcodechecker.viewmodel.GitHubProfileState,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    
    when {
        state.isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF58A6FF), modifier = Modifier.size(24.dp))
            }
        }
        
        state.error != null -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚠️ ${state.error}",
                    color = Color(0xFFF85149),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                ) {
                    Text("Retry")
                }
            }
        }
        
        state.user != null -> {
            val user = state.user!!
            
            // Profile info row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                if (state.avatarUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(state.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFF30363D), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF30363D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF8B949E))
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = user.name ?: user.login ?: "Unknown",
                        color = Color(0xFFE6EDF3),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (user.bio != null) {
                        Text(
                            text = user.bio,
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GitHubStatItem(user.followers?.totalCount ?: 0, "Followers")
                GitHubStatItem(user.following?.totalCount ?: 0, "Following")
                GitHubStatItem(user.repositories?.totalCount ?: 0, "Repos")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Contribution stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ContributionChip(
                    value = user.contributionsCollection?.totalCommitContributions ?: 0,
                    label = "Commits",
                    color = Color(0xFF39D353)
                )
                ContributionChip(
                    value = user.contributionsCollection?.totalPullRequestContributions ?: 0,
                    label = "PRs",
                    color = Color(0xFF58A6FF)
                )
                ContributionChip(
                    value = user.contributionsCollection?.totalIssueContributions ?: 0,
                    label = "Issues",
                    color = Color(0xFFA371F7)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Contribution heatmap
            ContributionHeatmap(
                contributionDays = state.contributionDays,
                totalContributions = state.totalContributions,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GitHubStatItem(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            color = Color(0xFFE6EDF3),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color(0xFF8B949E),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ContributionChip(value: Int, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = value.toString(),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = color.copy(alpha = 0.8f),
            fontSize = 10.sp
        )
    }
}

// ============== LeetCode Section ==============

private val LeetCodeOrange = Color(0xFFFFA116)

@Composable
private fun LeetCodeProfileContent(
    summary: LeetCodeProfileSummary?,
    isLoading: Boolean,
    error: String?,
    onOpenProfile: () -> Unit
) {
    when {
        isLoading && summary == null -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LeetCodeOrange, modifier = Modifier.size(24.dp))
            }
        }

        error != null && summary == null -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Unable to load LeetCode stats",
                    color = Color(0xFF8B949E),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenProfile,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LeetCodeOrange)
                ) {
                    Text("View on LeetCode")
                }
            }
        }

        summary != null -> {
            Column {
                // Solved breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LeetCodeStatItem(summary.easySolved, "Easy", Color(0xFF00B8A3))
                    LeetCodeStatItem(summary.mediumSolved, "Medium", Color(0xFFFFC01E))
                    LeetCodeStatItem(summary.hardSolved, "Hard", Color(0xFFFF375F))
                    LeetCodeStatItem(summary.totalSolved, "Total", Color(0xFFE6EDF3))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Streaks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ContributionChip(value = summary.currentStreak, label = "Current Streak", color = Color(0xFFF0883E))
                    ContributionChip(value = summary.longestStreak, label = "Longest Streak", color = LeetCodeOrange)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real per-day submission heatmap -- same data source as the Features tab
                LeetCodeHeatmap()

                // Topic mastery
                if (summary.topTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Topic Mastery",
                        color = Color(0xFFE6EDF3),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val maxSolved = summary.topTags.first().problemsSolved ?: 1
                    summary.topTags.forEach { tag ->
                        TagMasteryRow(tag = tag, maxSolved = maxSolved)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Badges
                if (summary.badges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Badges (${summary.badges.size})",
                        color = Color(0xFFE6EDF3),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        summary.badges.forEach { badge -> LeetCodeBadgeItem(badge) }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenProfile,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LeetCodeOrange)
                ) {
                    Text("View Full Profile on LeetCode")
                }
            }
        }

        else -> {
            Text(
                text = "No LeetCode data available",
                color = Color(0xFF8B949E),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun LeetCodeStatItem(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color(0xFF8B949E),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TagMasteryRow(tag: TagProblemCount, maxSolved: Int) {
    val solved = tag.problemsSolved ?: 0
    val fraction = if (maxSolved > 0) solved.toFloat() / maxSolved else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = tag.tagName ?: "Unknown", color = Color(0xFFE6EDF3), fontSize = 12.sp)
            Text(text = "$solved", color = Color(0xFF8B949E), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = LeetCodeOrange,
            trackColor = Color(0xFF30363D)
        )
    }
}

@Composable
private fun LeetCodeBadgeItem(badge: LeetCodeBadge) {
    val context = LocalContext.current
    var imageLoadFailed by remember { mutableStateOf(false) }
    val iconUrl = badge.icon?.let { if (it.startsWith("http")) it else "https://leetcode.com$it" }

    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (imageLoadFailed || iconUrl == null) LeetCodeOrange.copy(alpha = 0.2f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (iconUrl != null && !imageLoadFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(iconUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = badge.displayName ?: badge.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                    onError = { imageLoadFailed = true }
                )
            } else {
                Icon(Icons.Filled.Star, contentDescription = null, tint = LeetCodeOrange)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = badge.displayName ?: badge.name ?: "Badge",
            color = Color(0xFF8B949E),
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ============== Credly Section ==============

data class CredlyBadge(
    val name: String,
    val issuer: String,
    val imageUrl: String,
    val issuerAbbrev: String,
    val issuerColor: Long,
    val badgeUrl: String,
    val dateInfo: String,
    val isExpired: Boolean = false
)

/**
 * Fetches real badges from Credly's public badges.json endpoint -- the same
 * unauthenticated JSON the credly.com profile page itself loads client-side.
 */
private suspend fun fetchCredlyBadges(username: String): List<CredlyBadge> = withContext(Dispatchers.IO) {
    try {
        val url = "https://www.credly.com/users/$username/badges.json" +
            "?page=1&page_size=48&sort=-state_updated_at&filter=state%3A%3Aaccepted"
        val connection = URL(url).openConnection()
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        val json = connection.getInputStream().bufferedReader().readText()

        val data = JSONObject(json).optJSONArray("data") ?: JSONArray()
        (0 until data.length()).mapNotNull { i ->
            val badge = data.getJSONObject(i)
            val template = badge.optJSONObject("badge_template") ?: return@mapNotNull null
            val name = template.optString("name").ifBlank { return@mapNotNull null }
            val issuerSummary = badge.optJSONObject("issuer")?.optString("summary").orEmpty()
            val issuerName = issuerSummary.removePrefix("issued by ").trim().ifBlank { "Unknown issuer" }
            // optString() on a JSON null value returns the literal string "null", not
            // blank -- badges with no expiry (a common, valid case) were rendering
            // "Expires null" instead of falling back to the issued-date line.
            val expiresDate = badge.optString("expires_at_date").takeIf { it.isNotBlank() && it != "null" }
            val issuedDate = badge.optString("issued_at_date").takeIf { it.isNotBlank() && it != "null" }
            val isExpired = expiresDate?.let { isPastCredlyDate(it) } ?: false

            CredlyBadge(
                name = name,
                issuer = issuerName,
                imageUrl = template.optString("image_url"),
                issuerAbbrev = credlyIssuerAbbreviation(issuerName),
                issuerColor = credlyIssuerColor(issuerName),
                badgeUrl = template.optString("url").ifBlank { "https://www.credly.com/users/$username/badges" },
                dateInfo = when {
                    isExpired && expiresDate != null -> "Expired ${formatCredlyDate(expiresDate)}"
                    expiresDate != null -> "Expires ${formatCredlyDate(expiresDate)}"
                    issuedDate != null -> "Issued ${formatCredlyDate(issuedDate)}"
                    else -> ""
                },
                isExpired = isExpired
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun isPastCredlyDate(isoDate: String): Boolean = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.before(Date()) ?: false
}.getOrDefault(false)

private fun formatCredlyDate(isoDate: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)
    parsed?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(it) } ?: isoDate
}.getOrDefault(isoDate)

private val CREDLY_ISSUER_COLORS = mapOf(
    "google cloud" to 0xFF4285F4L,
    "servicenow" to 0xFF62D84EL,
    "amazon web services" to 0xFFFF9900L,
    "aws" to 0xFFFF9900L,
    "deloitte certified us" to 0xFF86BC25L,
    "microsoft" to 0xFF00A4EFL,
    "ibm" to 0xFF0F62FEL
)
private val CREDLY_COLOR_PALETTE = listOf(0xFF4285F4L, 0xFF62D84EL, 0xFFFF9900L, 0xFF86BC25L, 0xFFAB47BCL, 0xFF26A69AL, 0xFFEF5350L)

private fun credlyIssuerColor(issuer: String): Long {
    val key = issuer.lowercase(Locale.US)
    return CREDLY_ISSUER_COLORS[key] ?: CREDLY_COLOR_PALETTE[(key.hashCode() and 0x7fffffff) % CREDLY_COLOR_PALETTE.size]
}

private fun credlyIssuerAbbreviation(issuer: String): String {
    val words = issuer.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase(Locale.US)
        words.size == 1 -> words[0].take(2).uppercase(Locale.US)
        else -> "?"
    }
}

@Composable
private fun CredlyContent(
    badges: List<CredlyBadge>,
    isLoading: Boolean,
    error: String?,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
    
    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFF6B00), modifier = Modifier.size(24.dp))
            }
        }
        
        error != null -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Unable to load badges",
                    color = Color(0xFF8B949E),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenProfile,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B00))
                ) {
                    Text("View on Credly")
                }
            }
        }
        
        badges.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No badges found",
                    color = Color(0xFF8B949E),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenProfile,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B00))
                ) {
                    Text("View on Credly")
                }
            }
        }
        
        else -> {
            Column {
                badges.forEach { badge ->
                    CredlyBadgeItem(
                        badge = badge,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(badge.badgeUrl))
                            context.startActivity(intent)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // View all button
                OutlinedButton(
                    onClick = onOpenProfile,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B00))
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View All Badges on Credly")
                }
            }
        }
    }
}

@Composable
private fun CredlyBadgeItem(
    badge: CredlyBadge,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var imageLoadFailed by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1117))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge image with fallback to issuer icon
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (imageLoadFailed) {
                        if (badge.isExpired) Color(badge.issuerColor).copy(alpha = 0.4f)
                        else Color(badge.issuerColor)
                    } else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageLoadFailed) {
                // Fallback: Show issuer abbreviation
                Text(
                    text = badge.issuerAbbrev,
                    color = Color.White,
                    fontSize = if (badge.issuerAbbrev.length > 2) 14.sp else 18.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                // Try to load badge image
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(badge.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = badge.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (badge.isExpired) Modifier.background(Color.Black.copy(alpha = 0.3f))
                            else Modifier
                        ),
                    contentScale = ContentScale.Fit,
                    onError = { imageLoadFailed = true }
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = badge.name,
                color = if (badge.isExpired) Color(0xFF8B949E) else Color(0xFFE6EDF3),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = badge.issuer,
                color = Color(0xFF8B949E),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = badge.dateInfo,
                color = if (badge.isExpired) Color(0xFFF85149) else Color(0xFF8B949E),
                fontSize = 11.sp
            )
        }
        
        Icon(
            Icons.Filled.ArrowForward,
            contentDescription = "Open",
            tint = Color(0xFF8B949E),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ============== LinkedIn Section ==============

@Composable
private fun LinkedInContent(
    onOpenProfile: () -> Unit
) {
    Column {
        // Profile preview card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0A66C2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VC",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = "Vigneshwara Chinnadurai",
                            color = Color(0xFFE6EDF3),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Data Scientist & ML Engineer",
                            color = Color(0xFF8B949E),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Deloitte • India",
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Skills/highlights
                Text(
                    text = "7+ years of experience in Data Science, Machine Learning, and AI solutions across automotive, finance, and IT consulting sectors.",
                    color = Color(0xFFE6EDF3),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Skills chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("Python", "Machine Learning", "AWS", "NLP", "Computer Vision", "Spark").forEach { skill ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0A66C2).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = skill,
                                color = Color(0xFF58A6FF),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Open LinkedIn button
        Button(
            onClick = onOpenProfile,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A66C2))
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("View Full Profile on LinkedIn")
        }
    }
}

// ============== Medium Section ==============

data class MediumArticle(
    val title: String,
    val link: String,
    val pubDate: String,
    val categories: List<String>
)

private suspend fun fetchMediumArticles(username: String): List<MediumArticle> = withContext(Dispatchers.IO) {
    val articles = mutableListOf<MediumArticle>()
    try {
        val feedUrl = "https://medium.com/feed/@$username"
        val connection = URL(feedUrl).openConnection()
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val xmlContent = connection.getInputStream().bufferedReader().readText()
        
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))
        
        var eventType = parser.eventType
        var currentTitle = ""
        var currentLink = ""
        var currentPubDate = ""
        val currentCategories = mutableListOf<String>()
        var inItem = false
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "item" -> {
                            inItem = true
                            currentTitle = ""
                            currentLink = ""
                            currentPubDate = ""
                            currentCategories.clear()
                        }
                        "title" -> if (inItem) currentTitle = parser.nextText()
                        "link" -> if (inItem) currentLink = parser.nextText()
                        "pubDate" -> if (inItem) currentPubDate = parser.nextText()
                        "category" -> if (inItem) currentCategories.add(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        if (currentTitle.isNotBlank() && currentLink.isNotBlank()) {
                            articles.add(MediumArticle(
                                title = currentTitle,
                                link = currentLink,
                                pubDate = formatMediumDate(currentPubDate),
                                categories = currentCategories.toList()
                            ))
                        }
                        inItem = false
                    }
                }
            }
            eventType = parser.next()
        }
    } catch (e: Exception) {
        // Return empty list on error
    }
    articles.take(10) // Limit to 10 articles
}

private fun formatMediumDate(dateStr: String): String {
    return try {
        // Format: "Sat, 15 Mar 2025 10:30:00 GMT"
        val parts = dateStr.split(" ")
        if (parts.size >= 4) "${parts[2]} ${parts[1]}, ${parts[3]}" else dateStr
    } catch (e: Exception) {
        dateStr
    }
}

@Composable
private fun MediumContent(
    articles: List<MediumArticle>,
    isLoading: Boolean,
    error: String?,
    onOpenArticle: (String) -> Unit,
    onOpenProfile: () -> Unit
) {
    Column {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFE6EDF3), modifier = Modifier.size(24.dp))
                }
            }
            
            error != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⚠️ $error",
                            color = Color(0xFFF85149),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            articles.isEmpty() -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No articles found",
                            color = Color(0xFF8B949E),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            else -> {
                articles.forEachIndexed { index, article ->
                    MediumArticleItem(
                        article = article,
                        onClick = { onOpenArticle(article.link) }
                    )
                    if (index < articles.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // View all button
        OutlinedButton(
            onClick = onOpenProfile,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE6EDF3))
        ) {
            Icon(Icons.Filled.Create, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("View All on Medium")
        }
    }
}

@Composable
private fun MediumArticleItem(
    article: MediumArticle,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1117))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Article icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Create,
                contentDescription = null,
                tint = Color(0xFF8B949E),
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = article.title,
                color = Color(0xFFE6EDF3),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = article.pubDate,
                color = Color(0xFF8B949E),
                fontSize = 11.sp
            )
            if (article.categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    article.categories.take(3).forEach { tag ->
                        Text(
                            text = "#$tag",
                            color = Color(0xFF58A6FF),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
        
        Icon(
            Icons.Filled.ArrowForward,
            contentDescription = "Open",
            tint = Color(0xFF8B949E),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ProofFiling Summary Card
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ProofFilingSummaryCard(state: ProofFilingUIState) {
    val entry = state.currentEntry
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF238636)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = "📊 This Week's Progress",
                            color = Color(0xFFE6EDF3),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (entry != null) {
                            Text(
                                text = "${entry.weekStartDate} → ${entry.weekEndDate}",
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                
                if (entry?.isPushedToGitHub == true) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Pushed",
                        tint = Color(0xFF3FB950),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            if (entry != null) {
                Spacer(modifier = Modifier.height(12.dp))
                
                HorizontalDivider(color = Color(0xFF30363D))
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProofFilingStatItem(
                        value = entry.wins.size.toString(),
                        label = "Wins",
                        color = Color(0xFF3FB950)
                    )
                    ProofFilingStatItem(
                        value = entry.learnings.size.toString(),
                        label = "Learnings",
                        color = Color(0xFF58A6FF)
                    )
                    ProofFilingStatItem(
                        value = entry.evidence.size.toString(),
                        label = "Evidence",
                        color = Color(0xFFA371F7)
                    )
                    
                    // GitHub stats if available
                    entry.githubStats?.let { stats ->
                        ProofFilingStatItem(
                            value = stats.totalCommits.toString(),
                            label = "Commits",
                            color = Color(0xFFF0883E)
                        )
                    }
                }
                
                if (entry.wins.isEmpty() && entry.learnings.isEmpty() && entry.evidence.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Go to ProofFile tab to track your progress!",
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Start tracking your weekly progress in the ProofFile tab",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ProofFilingStatItem(
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
            fontSize = 10.sp,
            color = Color(0xFF8B949E)
        )
    }
}
