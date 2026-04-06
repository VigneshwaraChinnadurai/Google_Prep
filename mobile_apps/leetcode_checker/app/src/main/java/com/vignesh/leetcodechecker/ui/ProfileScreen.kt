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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vignesh.leetcodechecker.viewmodel.GitHubProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URL

/**
 * ProfileScreen - Unified profile view with expandable sections
 * 
 * Contains:
 * - GitHub dropdown (contribution graph, stats)
 * - Credly dropdown (badges/certifications)
 * - LinkedIn dropdown (profile preview)
 * - Medium dropdown (blog articles)
 */
@Composable
fun ProfileScreen(
    gitHubViewModel: GitHubProfileViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val gitHubState by gitHubViewModel.state.collectAsState()
    
    // Section expansion states
    var githubExpanded by remember { mutableStateOf(true) }
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
    
    // Load GitHub profile on first composition
    LaunchedEffect(Unit) {
        if (gitHubState.user == null && !gitHubState.isLoading) {
            gitHubViewModel.initializeWithDefault(context)
        }
    }
    
    // Load Medium articles when expanded or on refresh
    LaunchedEffect(mediumExpanded, refreshTrigger) {
        if (mediumExpanded && !mediumLoading) {
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
    
    // Load Credly badges when expanded or on refresh
    LaunchedEffect(credlyExpanded, refreshTrigger) {
        if (credlyExpanded && !credlyLoading) {
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
 * Returns the user's actual Credly badges with real image URLs.
 * Image URLs are from the public Credly badge images.
 */
@Suppress("UNUSED_PARAMETER")
private suspend fun fetchCredlyBadges(username: String): List<CredlyBadge> = withContext(Dispatchers.IO) {
    listOf(
        CredlyBadge(
            name = "Configure AI Applications to optimize search results",
            issuer = "Google Cloud",
            imageUrl = "https://images.credly.com/size/340x340/images/16ae03cf-d272-4f94-9e50-688b236a9df5/image.png",
            issuerAbbrev = "GC",
            issuerColor = 0xFF4285F4,
            badgeUrl = "https://www.credly.com/users/vigneshwarachinnadurai/badges",
            dateInfo = "Issued Dec 10, 2025"
        ),
        CredlyBadge(
            name = "Delivery Accreditation - AI Agents",
            issuer = "ServiceNow",
            imageUrl = "https://images.credly.com/size/340x340/images/bd4e0db2-f867-4a75-8edf-3aac2c096654/image.png",
            issuerAbbrev = "SN",
            issuerColor = 0xFF62D84E,
            badgeUrl = "https://www.credly.com/users/vigneshwarachinnadurai/badges",
            dateInfo = "Issued Dec 16, 2025"
        ),
        CredlyBadge(
            name = "AWS Certified Machine Learning – Specialty",
            issuer = "Amazon Web Services",
            imageUrl = "https://images.credly.com/size/340x340/images/778bde6c-ad1c-4312-ac33-2fa40d50a147/image.png",
            issuerAbbrev = "AWS",
            issuerColor = 0xFFFF9900,
            badgeUrl = "https://www.credly.com/users/vigneshwarachinnadurai/badges",
            dateInfo = "Expired Oct 31, 2024",
            isExpired = true
        ),
        CredlyBadge(
            name = "Machine Learning - Foundation",
            issuer = "Deloitte Certified US",
            imageUrl = "https://images.credly.com/size/340x340/images/1b29d85a-e882-4478-a136-0ee96f828ec7/image.png",
            issuerAbbrev = "D",
            issuerColor = 0xFF86BC25,
            badgeUrl = "https://www.credly.com/users/vigneshwarachinnadurai/badges",
            dateInfo = "Expires Jan 17, 2028"
        ),
        CredlyBadge(
            name = "Data Engineering - Foundation",
            issuer = "Deloitte Certified US",
            imageUrl = "https://images.credly.com/size/340x340/images/2218e0f2-3f8e-4c03-8b42-b80501751f06/image.png",
            issuerAbbrev = "D",
            issuerColor = 0xFF86BC25,
            badgeUrl = "https://www.credly.com/users/vigneshwarachinnadurai/badges",
            dateInfo = "Expires Jan 28, 2028"
        ),
        CredlyBadge(
            name = "Industry Proficiency Foundation: Technology, Media & Telecom",
            issuer = "Deloitte Certified US",
            imageUrl = "https://images.credly.com/size/340x340/images/77f65a24-2b84-44ea-8b11-4f0e7e954f38/image.png",
            issuerAbbrev = "D",
            issuerColor = 0xFF86BC25,
            badgeUrl = "https://www.credly.com/users/vigneshwarachinnadurai/badges",
            dateInfo = "Issued Jun 16, 2023"
        ),
        CredlyBadge(
            name = "Impact Day 2025",
            issuer = "Deloitte Certified US",
            imageUrl = "https://images.credly.com/size/340x340/images/05e5d41e-3c70-4a8a-8c7a-d9b28e0195a8/image.png",
            issuerAbbrev = "D",
            issuerColor = 0xFF86BC25,
            badgeUrl = "https://www.credly.com/users/vigneshwarachinnadurai/badges",
            dateInfo = "Expires Nov 28, 2026"
        )
    )
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
