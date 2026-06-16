package com.vignesh.leetcodechecker.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * WhatsNewScreen - Shows app update highlights and tracks version history
 * 
 * Features:
 * - Current version's new features with visual cards
 * - Version history dropdown showing past updates
 * - Auto-shows on first app launch after update
 * - Persists seen state per version
 */

// Color scheme
private val WhatsNewColors = object {
    val background = Color(0xFF0D1117)
    val surface = Color(0xFF161B22)
    val surfaceVariant = Color(0xFF21262D)
    val primary = Color(0xFFFFB347) // Orange/amber for "new"
    val secondary = Color(0xFF58A6FF)
    val tertiary = Color(0xFF3FB950)
    val onSurface = Color(0xFFE6EDF3)
    val onSurfaceVariant = Color(0xFF8B949E)
    val border = Color(0xFF30363D)
}

// Data classes
data class WhatsNewEntry(
    val version: String,
    val versionCode: Int,
    val releaseDate: String,
    val title: String,
    val features: List<FeatureHighlight>,
    val improvements: List<String> = emptyList(),
    val bugFixes: List<String> = emptyList()
)

data class FeatureHighlight(
    val emoji: String,
    val title: String,
    val description: String,
    val isNew: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showHistory by remember { mutableStateOf(false) }
    
    // Get all versions and current version
    val allVersions = remember { getWhatsNewHistory() }
    val currentVersion = allVersions.firstOrNull()
    val previousVersions = allVersions.drop(1)
    
    // Mark current version as seen
    LaunchedEffect(currentVersion) {
        currentVersion?.let {
            markVersionAsSeen(context, it.version)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WhatsNewColors.background)
    ) {
        // Top bar
        TopAppBar(
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨ ", fontSize = 20.sp)
                    Text("What's New", color = WhatsNewColors.onSurface)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = WhatsNewColors.onSurface
                    )
                }
            },
            actions = {
                if (previousVersions.isNotEmpty()) {
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(
                            if (showHistory) Icons.Default.KeyboardArrowUp else Icons.Default.DateRange,
                            contentDescription = "History",
                            tint = WhatsNewColors.secondary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = WhatsNewColors.surface
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Current Version
            currentVersion?.let { version ->
                CurrentVersionCard(version)
            }
            
            // History toggle
            AnimatedVisibility(
                visible = showHistory && previousVersions.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "📜 Previous Updates",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WhatsNewColors.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    previousVersions.forEach { version ->
                        HistoryVersionCard(version)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun CurrentVersionCard(version: WhatsNewEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WhatsNewColors.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Version header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Version ${version.version}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = WhatsNewColors.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = WhatsNewColors.tertiary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "LATEST",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WhatsNewColors.tertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = version.releaseDate,
                        fontSize = 12.sp,
                        color = WhatsNewColors.onSurfaceVariant
                    )
                }
            }
            
            if (version.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = version.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = WhatsNewColors.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = WhatsNewColors.border)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Features
            version.features.forEach { feature ->
                FeatureHighlightRow(feature)
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Improvements
            if (version.improvements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚡ Improvements",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WhatsNewColors.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                version.improvements.forEach { improvement ->
                    BulletPoint(text = improvement, color = WhatsNewColors.secondary)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            // Bug fixes
            if (version.bugFixes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🐛 Bug Fixes",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WhatsNewColors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                version.bugFixes.forEach { fix ->
                    BulletPoint(text = fix, color = WhatsNewColors.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlightRow(feature: FeatureHighlight) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WhatsNewColors.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WhatsNewColors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(feature.emoji, fontSize = 20.sp)
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = feature.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WhatsNewColors.onSurface
                )
                if (feature.isNew) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = WhatsNewColors.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "NEW",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = WhatsNewColors.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = feature.description,
                fontSize = 12.sp,
                color = WhatsNewColors.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun BulletPoint(text: String, color: Color) {
    Row(
        modifier = Modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = color,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun HistoryVersionCard(version: WhatsNewEntry) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = WhatsNewColors.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Version ${version.version}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WhatsNewColors.onSurface
                    )
                    Text(
                        text = version.releaseDate,
                        fontSize = 11.sp,
                        color = WhatsNewColors.onSurfaceVariant
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${version.features.size} features",
                        fontSize = 11.sp,
                        color = WhatsNewColors.onSurfaceVariant
                    )
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = WhatsNewColors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = WhatsNewColors.border)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    version.features.forEach { feature ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(feature.emoji, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feature.title,
                                fontSize = 12.sp,
                                color = WhatsNewColors.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Version History Data
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Returns all "What's New" entries for the app.
 * Add new versions at the TOP of this list.
 */
private fun getWhatsNewHistory(): List<WhatsNewEntry> {
    return listOf(
        // ═══════════════════════════════════════════════════════════════════
        // LATEST VERSION - Add new versions here at the top
        // ═══════════════════════════════════════════════════════════════════
        WhatsNewEntry(
            version = "2.5.0",
            versionCode = 25,
            releaseDate = "June 16, 2026",
            title = "ProofFiling & Career Tracking",
            features = listOf(
                FeatureHighlight(
                    emoji = "📊",
                    title = "ProofFiling",
                    description = "Track your weekly wins, learnings, and evidence. Auto-fetch GitHub & LeetCode stats, generate AI summaries, and push to your proof_journal repo.",
                    isNew = true
                ),
                FeatureHighlight(
                    emoji = "🤖",
                    title = "AI-Powered Weekly Summaries",
                    description = "Gemini LLM analyzes your commits and activity to generate professional weekly summaries for career documentation.",
                    isNew = true
                ),
                FeatureHighlight(
                    emoji = "⏰",
                    title = "Weekly Reminder",
                    description = "Configurable notifications remind you to update your ProofFiling every Friday at 6 PM.",
                    isNew = true
                ),
                FeatureHighlight(
                    emoji = "✨",
                    title = "What's New Screen",
                    description = "This screen! Track app updates and see what's new with each version.",
                    isNew = true
                )
            ),
            improvements = listOf(
                "Profile screen now shows ProofFiling summary card",
                "Reorganized bottom navigation for better flow",
                "Strategic Chatbot moved to Features Hub"
            ),
            bugFixes = listOf(
                "Fixed Credly badges not showing after refresh",
                "Fixed badge images not loading with fallback icons"
            )
        ),
        
        // ═══════════════════════════════════════════════════════════════════
        // Previous versions
        // ═══════════════════════════════════════════════════════════════════
        WhatsNewEntry(
            version = "2.4.0",
            versionCode = 24,
            releaseDate = "June 2026",
            title = "Profile & Credentials",
            features = listOf(
                FeatureHighlight(
                    emoji = "👤",
                    title = "Unified Profile Screen",
                    description = "GitHub, Credly, LinkedIn, and Medium all in one place with expandable sections.",
                    isNew = false
                ),
                FeatureHighlight(
                    emoji = "🏅",
                    title = "Credly Badges",
                    description = "View your AWS, Google Cloud, and other certifications directly in the app.",
                    isNew = false
                ),
                FeatureHighlight(
                    emoji = "📝",
                    title = "Medium Articles",
                    description = "Your latest blog posts pulled from Medium RSS feed.",
                    isNew = false
                )
            )
        ),
        
        WhatsNewEntry(
            version = "2.3.0",
            versionCode = 23,
            releaseDate = "May 2026",
            title = "AI Features Expansion",
            features = listOf(
                FeatureHighlight(
                    emoji = "🧠",
                    title = "AI Learning Hub",
                    description = "Pattern recognition, code optimization, and problem hints powered by Gemini.",
                    isNew = false
                ),
                FeatureHighlight(
                    emoji = "💬",
                    title = "Strategic Chatbot",
                    description = "AI-powered career advisor for interview prep and technical questions.",
                    isNew = false
                ),
                FeatureHighlight(
                    emoji = "📰",
                    title = "AI/ML News",
                    description = "Stay updated with the latest AI and ML news from top sources.",
                    isNew = false
                )
            )
        ),
        
        WhatsNewEntry(
            version = "2.2.0",
            versionCode = 22,
            releaseDate = "April 2026",
            title = "Practice Features",
            features = listOf(
                FeatureHighlight(
                    emoji = "🐍",
                    title = "Python Playground",
                    description = "Write and execute Python code directly in the app.",
                    isNew = false
                ),
                FeatureHighlight(
                    emoji = "📚",
                    title = "Flashcards",
                    description = "DSA concept flashcards for quick review and memorization.",
                    isNew = false
                ),
                FeatureHighlight(
                    emoji = "🎯",
                    title = "Goal Tracking",
                    description = "Set and track your LeetCode solving goals.",
                    isNew = false
                )
            )
        ),
        
        WhatsNewEntry(
            version = "2.1.0",
            versionCode = 21,
            releaseDate = "March 2026",
            title = "Analytics & Achievements",
            features = listOf(
                FeatureHighlight(
                    emoji = "📊",
                    title = "Analytics Dashboard",
                    description = "Visualize your solving patterns, difficulty distribution, and progress over time.",
                    isNew = false
                ),
                FeatureHighlight(
                    emoji = "🏆",
                    title = "Achievements System",
                    description = "Unlock badges for milestones like solving streaks and difficulty completions.",
                    isNew = false
                ),
                FeatureHighlight(
                    emoji = "🔔",
                    title = "Daily Challenge Notifications",
                    description = "Get reminded of the daily LeetCode challenge.",
                    isNew = false
                )
            )
        ),
        
        WhatsNewEntry(
            version = "2.0.0",
            versionCode = 20,
            releaseDate = "February 2026",
            title = "Major Redesign",
            features = listOf(
                FeatureHighlight(
                    emoji = "🎨",
                    title = "New Dark Theme",
                    description = "GitHub-inspired dark theme for comfortable viewing.",
                    isNew = false
                ),
                FeatureHighlight(
                    emoji = "🤖",
                    title = "Ollama Integration",
                    description = "Local LLM support via Ollama for code analysis.",
                    isNew = false
                ),
                FeatureHighlight(
                    emoji = "⚡",
                    title = "Features Hub",
                    description = "Central navigation for all app features.",
                    isNew = false
                )
            )
        )
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// Persistence
// ═══════════════════════════════════════════════════════════════════════════

private const val PREFS_NAME = "whats_new_prefs"
private const val KEY_SEEN_VERSIONS = "seen_versions"

private fun markVersionAsSeen(context: Context, version: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val seenSet = prefs.getStringSet(KEY_SEEN_VERSIONS, mutableSetOf()) ?: mutableSetOf()
    val updatedSet = seenSet.toMutableSet().apply { add(version) }
    prefs.edit().putStringSet(KEY_SEEN_VERSIONS, updatedSet).apply()
}

fun hasSeenVersion(context: Context, version: String): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val seenSet = prefs.getStringSet(KEY_SEEN_VERSIONS, emptySet()) ?: emptySet()
    return version in seenSet
}

fun getCurrentAppVersion(): String {
    return getWhatsNewHistory().firstOrNull()?.version ?: "1.0.0"
}

fun shouldShowWhatsNew(context: Context): Boolean {
    val currentVersion = getCurrentAppVersion()
    return !hasSeenVersion(context, currentVersion)
}

/**
 * Get the number of unseen new features for badge display
 */
fun getUnseenUpdateCount(context: Context): Int {
    val currentVersion = getCurrentAppVersion()
    return if (!hasSeenVersion(context, currentVersion)) {
        getWhatsNewHistory().firstOrNull()?.features?.size ?: 0
    } else {
        0
    }
}
