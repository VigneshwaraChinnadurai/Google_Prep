package com.vignesh.leetcodechecker.prooffiling

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

// Color scheme for ProofFiling (matching app theme)
private val ProofFilingColors = object {
    val background = Color(0xFF0D1117)
    val surface = Color(0xFF161B22)
    val surfaceVariant = Color(0xFF21262D)
    val primary = Color(0xFF58A6FF)
    val secondary = Color(0xFF238636)
    val tertiary = Color(0xFFF0883E)
    val error = Color(0xFFF85149)
    val onSurface = Color(0xFFE6EDF3)
    val onSurfaceVariant = Color(0xFF8B949E)
    val border = Color(0xFF30363D)
    val winsColor = Color(0xFF3FB950)
    val learningsColor = Color(0xFF58A6FF)
    val evidenceColor = Color(0xFFA371F7)
}

/**
 * Main ProofFiling Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofFilingScreen(
    viewModel: ProofFilingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val suggestedLearnings by viewModel.suggestedLearnings.collectAsState()
    
    var currentScreen by remember { mutableStateOf(ProofFilingSubScreen.MAIN) }
    var showAddDialog by remember { mutableStateOf<AddDialogType?>(null) }
    
    // Show snackbar for messages
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            delay(2000)
            viewModel.clearSuccessMessage()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ProofFilingColors.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
                },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    ProofFilingSubScreen.MAIN -> {
                        MainContent(
                            state = state,
                            suggestedLearnings = suggestedLearnings,
                            onAddWin = { showAddDialog = AddDialogType.WIN },
                            onAddLearning = { showAddDialog = AddDialogType.LEARNING },
                            onAddEvidence = { showAddDialog = AddDialogType.EVIDENCE },
                            onRemoveWin = viewModel::removeWin,
                            onRemoveLearning = viewModel::removeLearning,
                            onRemoveEvidence = viewModel::removeEvidence,
                            onAcceptSuggestion = viewModel::acceptSuggestedLearning,
                            onDismissSuggestion = viewModel::dismissSuggestedLearning,
                            onFetchStats = viewModel::fetchAllStats,
                            onFetchLeetCode = { viewModel.fetchLeetCodeStats() },
                            onGenerateSummary = viewModel::generateWeeklySummary,
                            onPreview = { currentScreen = ProofFilingSubScreen.PREVIEW },
                            onHistory = { currentScreen = ProofFilingSubScreen.HISTORY },
                            onSettings = { currentScreen = ProofFilingSubScreen.SETTINGS },
                            onRefresh = viewModel::refresh
                        )
                    }
                    
                    ProofFilingSubScreen.PREVIEW -> {
                        PreviewContent(
                            state = state,
                            onBack = { currentScreen = ProofFilingSubScreen.MAIN },
                            onPush = viewModel::pushToGitHub
                        )
                    }
                    
                    ProofFilingSubScreen.HISTORY -> {
                        HistoryContent(
                            entries = state.historyEntries,
                            onBack = { currentScreen = ProofFilingSubScreen.MAIN },
                            onSelectEntry = { entryId ->
                                viewModel.loadHistoryEntry(entryId)
                                currentScreen = ProofFilingSubScreen.MAIN
                            }
                        )
                    }
                    
                    ProofFilingSubScreen.SETTINGS -> {
                        SettingsContent(
                            config = state.config,
                            onBack = { currentScreen = ProofFilingSubScreen.MAIN },
                            onUpdateReminderTime = viewModel::updateReminderTime,
                            onUpdateReminderDay = viewModel::updateReminderDay,
                            onUpdateIntegration = viewModel::updateIntegration,
                            onAddIntegration = viewModel::addIntegration
                        )
                    }
                }
            }
            
            // Loading overlay
            if (state.isLoading || state.isFetchingStats || state.isPushing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ProofFilingColors.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                state.isPushing -> "Pushing to GitHub..."
                                state.isFetchingStats -> "Fetching stats..."
                                else -> "Loading..."
                            },
                            color = ProofFilingColors.onSurface
                        )
                    }
                }
            }
        }
        
        // Add dialogs
        showAddDialog?.let { dialogType ->
            AddItemDialog(
                type = dialogType,
                onDismiss = { showAddDialog = null },
                onAdd = { description, category ->
                    when (dialogType) {
                        AddDialogType.WIN -> viewModel.addWin(description, category as WinCategory)
                        AddDialogType.LEARNING -> viewModel.addLearning(
                            description, 
                            category as LearningCategory
                        )
                        AddDialogType.EVIDENCE -> viewModel.addEvidence(
                            description,
                            category as EvidenceType
                        )
                    }
                    showAddDialog = null
                }
            )
        }
    }
}

enum class ProofFilingSubScreen {
    MAIN, PREVIEW, HISTORY, SETTINGS
}

enum class AddDialogType {
    WIN, LEARNING, EVIDENCE
}

// ═══════════════════════════════════════════════════════════════════════════
// Main Content
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MainContent(
    state: ProofFilingUIState,
    suggestedLearnings: List<LearningItem>,
    onAddWin: () -> Unit,
    onAddLearning: () -> Unit,
    onAddEvidence: () -> Unit,
    onRemoveWin: (String) -> Unit,
    onRemoveLearning: (String) -> Unit,
    onRemoveEvidence: (String) -> Unit,
    onAcceptSuggestion: (LearningItem) -> Unit,
    onDismissSuggestion: (String) -> Unit,
    onFetchStats: () -> Unit,
    onFetchLeetCode: () -> Unit,
    onGenerateSummary: () -> Unit,
    onPreview: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val entry = state.currentEntry
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📊 ProofFiling",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProofFilingColors.onSurface
                )
                if (entry != null) {
                    Text(
                        text = "Week: ${entry.weekStartDate} → ${entry.weekEndDate}",
                        fontSize = 12.sp,
                        color = ProofFilingColors.onSurfaceVariant
                    )
                }
            }
            
            Row {
                IconButton(onClick = onHistory) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = "History",
                        tint = ProofFilingColors.primary
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = ProofFilingColors.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = ProofFilingColors.primary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Quick Actions
        QuickActionsRow(
            onFetchStats = onFetchStats,
            onFetchLeetCode = onFetchLeetCode,
            onGenerateSummary = onGenerateSummary,
            onPreview = onPreview,
            hasSummary = entry?.weeklySummary != null,
            isPushed = entry?.isPushedToGitHub ?: false
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Stats Dashboard (if available)
        if (entry?.githubStats != null || entry?.leetcodeStats != null) {
            StatsDashboard(
                githubStats = entry.githubStats,
                leetcodeStats = entry.leetcodeStats
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Suggested Learnings from Commits
        if (suggestedLearnings.isNotEmpty()) {
            SuggestedLearningsSection(
                suggestions = suggestedLearnings,
                onAccept = onAcceptSuggestion,
                onDismiss = onDismissSuggestion
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Wins Section
        EntrySection(
            title = "🏆 Wins",
            subtitle = "What you accomplished this week",
            color = ProofFilingColors.winsColor,
            items = entry?.wins?.map { win ->
                EntryDisplayItem(
                    id = win.id,
                    text = win.description,
                    badge = win.category.name
                )
            } ?: emptyList(),
            onAdd = onAddWin,
            onRemove = onRemoveWin
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Learnings Section
        EntrySection(
            title = "📚 Learnings",
            subtitle = "New concepts, patterns, lessons",
            color = ProofFilingColors.learningsColor,
            items = entry?.learnings?.map { learning ->
                EntryDisplayItem(
                    id = learning.id,
                    text = learning.description,
                    badge = learning.category.name,
                    secondaryBadge = if (learning.source != LearningSource.MANUAL) learning.source.name else null
                )
            } ?: emptyList(),
            onAdd = onAddLearning,
            onRemove = onRemoveLearning
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Evidence Section
        EntrySection(
            title = "📊 Evidence",
            subtitle = "Tangible proof of progress",
            color = ProofFilingColors.evidenceColor,
            items = entry?.evidence?.map { evidence ->
                EntryDisplayItem(
                    id = evidence.id,
                    text = evidence.description,
                    badge = evidence.type.name
                )
            } ?: emptyList(),
            onAdd = onAddEvidence,
            onRemove = onRemoveEvidence
        )
        
        Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for nav bar
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Quick Actions
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickActionsRow(
    onFetchStats: () -> Unit,
    onFetchLeetCode: () -> Unit,
    onGenerateSummary: () -> Unit,
    onPreview: () -> Unit,
    hasSummary: Boolean,
    isPushed: Boolean
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ActionChip(
                text = "Fetch GitHub",
                icon = Icons.Default.Build,
                onClick = onFetchStats
            )
        }
        item {
            ActionChip(
                text = "Fetch LeetCode",
                icon = Icons.Default.Star,
                onClick = onFetchLeetCode
            )
        }
        item {
            ActionChip(
                text = if (hasSummary) "Regenerate Summary" else "Generate Summary",
                icon = Icons.Default.Create,
                onClick = onGenerateSummary
            )
        }
        item {
            ActionChip(
                text = if (isPushed) "View & Update" else "Preview & Push",
                icon = Icons.AutoMirrored.Filled.Send,
                onClick = onPreview,
                highlighted = true
            )
        }
    }
}

@Composable
private fun ActionChip(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = if (highlighted) ProofFilingColors.secondary else ProofFilingColors.surface,
        border = BorderStroke(1.dp, ProofFilingColors.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (highlighted) Color.White else ProofFilingColors.primary
            )
            Text(
                text = text,
                fontSize = 12.sp,
                color = if (highlighted) Color.White else ProofFilingColors.onSurface
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Stats Dashboard
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun StatsDashboard(
    githubStats: GitHubWeeklyStats?,
    leetcodeStats: LeetCodeWeeklyStats?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ProofFilingColors.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📈 This Week's Stats",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProofFilingColors.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                githubStats?.let { stats ->
                    StatItem(
                        value = stats.totalCommits.toString(),
                        label = "Commits",
                        color = ProofFilingColors.winsColor
                    )
                    StatItem(
                        value = stats.totalPRs.toString(),
                        label = "PRs",
                        color = ProofFilingColors.learningsColor
                    )
                    StatItem(
                        value = stats.reposContributed.size.toString(),
                        label = "Repos",
                        color = ProofFilingColors.evidenceColor
                    )
                }
                
                leetcodeStats?.let { stats ->
                    StatItem(
                        value = "+${stats.problemsSolvedThisWeek}",
                        label = "LeetCode",
                        color = ProofFilingColors.tertiary
                    )
                }
            }
            
            // GitHub summary
            githubStats?.llmSummary?.let { summary ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = ProofFilingColors.border)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = summary,
                    fontSize = 13.sp,
                    color = ProofFilingColors.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = ProofFilingColors.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Suggested Learnings
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SuggestedLearningsSection(
    suggestions: List<LearningItem>,
    onAccept: (LearningItem) -> Unit,
    onDismiss: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ProofFilingColors.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = ProofFilingColors.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Suggested from your commits",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = ProofFilingColors.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            suggestions.take(3).forEach { suggestion ->
                SuggestionItem(
                    suggestion = suggestion,
                    onAccept = { onAccept(suggestion) },
                    onDismiss = { onDismiss(suggestion.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: LearningItem,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ProofFilingColors.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = suggestion.description,
            fontSize = 12.sp,
            color = ProofFilingColors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        IconButton(onClick = onAccept, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Accept",
                tint = ProofFilingColors.winsColor,
                modifier = Modifier.size(18.dp)
            )
        }
        
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = ProofFilingColors.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Entry Section (Wins/Learnings/Evidence)
// ═══════════════════════════════════════════════════════════════════════════

data class EntryDisplayItem(
    val id: String,
    val text: String,
    val badge: String,
    val secondaryBadge: String? = null
)

@Composable
private fun EntrySection(
    title: String,
    subtitle: String,
    color: Color,
    items: List<EntryDisplayItem>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ProofFilingColors.surface),
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
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = ProofFilingColors.onSurfaceVariant
                    )
                }
                
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .size(36.dp)
                        .background(color.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                items.forEach { item ->
                    EntryItem(
                        item = item,
                        color = color,
                        onRemove = { onRemove(item.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No items yet. Tap + to add.",
                    fontSize = 12.sp,
                    color = ProofFilingColors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EntryItem(
    item: EntryDisplayItem,
    color: Color,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ProofFilingColors.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.text,
                fontSize = 13.sp,
                color = ProofFilingColors.onSurface
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Badge(text = item.badge, color = color)
                item.secondaryBadge?.let { Badge(text = it, color = ProofFilingColors.tertiary) }
            }
        }
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = ProofFilingColors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text.lowercase().replace("_", " "),
            fontSize = 9.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Add Item Dialog
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemDialog(
    type: AddDialogType,
    onDismiss: () -> Unit,
    onAdd: (String, Any) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    
    val (title, categories, defaultCategory) = when (type) {
        AddDialogType.WIN -> Triple(
            "Add Win 🏆",
            WinCategory.entries.toList(),
            WinCategory.OTHER as Any
        )
        AddDialogType.LEARNING -> Triple(
            "Add Learning 📚",
            LearningCategory.entries.toList(),
            LearningCategory.OTHER as Any
        )
        AddDialogType.EVIDENCE -> Triple(
            "Add Evidence 📊",
            EvidenceType.entries.toList(),
            EvidenceType.OTHER as Any
        )
    }
    
    var selectedCategory by remember { mutableStateOf(defaultCategory) }
    val focusManager = LocalFocusManager.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProofFilingColors.surface,
        title = {
            Text(title, color = ProofFilingColors.onSurface)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ProofFilingColors.primary,
                        unfocusedBorderColor = ProofFilingColors.border,
                        focusedLabelColor = ProofFilingColors.primary,
                        unfocusedLabelColor = ProofFilingColors.onSurfaceVariant,
                        cursorColor = ProofFilingColors.primary,
                        focusedTextColor = ProofFilingColors.onSurface,
                        unfocusedTextColor = ProofFilingColors.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    minLines = 2,
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.toString().lowercase().replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ProofFilingColors.primary,
                            unfocusedBorderColor = ProofFilingColors.border,
                            focusedLabelColor = ProofFilingColors.primary,
                            unfocusedLabelColor = ProofFilingColors.onSurfaceVariant,
                            focusedTextColor = ProofFilingColors.onSurface,
                            unfocusedTextColor = ProofFilingColors.onSurface
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = ProofFilingColors.surfaceVariant
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        category.toString().lowercase().replace("_", " "),
                                        color = ProofFilingColors.onSurface
                                    ) 
                                },
                                onClick = {
                                    selectedCategory = category
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (description.isNotBlank()) {
                        onAdd(description.trim(), selectedCategory)
                    }
                },
                enabled = description.isNotBlank()
            ) {
                Text("Add", color = ProofFilingColors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ProofFilingColors.onSurfaceVariant)
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// Preview Screen
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewContent(
    state: ProofFilingUIState,
    onBack: () -> Unit,
    onPush: () -> Unit
) {
    val entry = state.currentEntry
    val markdown = entry?.toMarkdown() ?: ""
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProofFilingColors.background)
    ) {
        // Top bar
        TopAppBar(
            title = { Text("Preview Entry", color = ProofFilingColors.onSurface) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ProofFilingColors.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ProofFilingColors.surface
            )
        )
        
        // Markdown preview
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ProofFilingColors.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                // Simple markdown rendering
                Text(
                    text = markdown,
                    fontSize = 12.sp,
                    color = ProofFilingColors.onSurface,
                    modifier = Modifier.padding(16.dp),
                    lineHeight = 18.sp
                )
            }
        }
        
        // Push button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ProofFilingColors.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (entry?.isPushedToGitHub == true) {
                    Text(
                        text = "✅ Already pushed at ${entry.pushedAt}",
                        fontSize = 12.sp,
                        color = ProofFilingColors.winsColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                Button(
                    onClick = onPush,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProofFilingColors.secondary
                    ),
                    enabled = !state.isPushing
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (entry?.isPushedToGitHub == true) "Update on GitHub" else "Push to GitHub"
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "This will push to: github.com/VigneshwaraChinnadurai/proof_journal",
                    fontSize = 10.sp,
                    color = ProofFilingColors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// History Screen
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(
    entries: List<ProofFilingEntry>,
    onBack: () -> Unit,
    onSelectEntry: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProofFilingColors.background)
    ) {
        TopAppBar(
            title = { Text("History", color = ProofFilingColors.onSurface) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ProofFilingColors.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ProofFilingColors.surface
            )
        )
        
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = ProofFilingColors.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No history yet",
                        color = ProofFilingColors.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries) { entry ->
                    HistoryCard(
                        entry = entry,
                        onClick = { onSelectEntry(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: ProofFilingEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = ProofFilingColors.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Week: ${entry.weekStartDate} → ${entry.weekEndDate}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = ProofFilingColors.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${entry.wins.size} wins",
                        fontSize = 11.sp,
                        color = ProofFilingColors.winsColor
                    )
                    Text(
                        text = "${entry.learnings.size} learnings",
                        fontSize = 11.sp,
                        color = ProofFilingColors.learningsColor
                    )
                    Text(
                        text = "${entry.evidence.size} evidence",
                        fontSize = 11.sp,
                        color = ProofFilingColors.evidenceColor
                    )
                }
            }
            
            if (entry.isPushedToGitHub) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Pushed",
                    tint = ProofFilingColors.winsColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Settings Screen
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    config: ProofFilingConfig,
    onBack: () -> Unit,
    onUpdateReminderTime: (Int, Int) -> Unit,
    onUpdateReminderDay: (Int) -> Unit,
    onUpdateIntegration: (IntegrationConfig) -> Unit,
    onAddIntegration: (String, String, Boolean) -> Unit
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProofFilingColors.background)
    ) {
        TopAppBar(
            title = { Text("Settings", color = ProofFilingColors.onSurface) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ProofFilingColors.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ProofFilingColors.surface
            )
        )
        
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Reminder Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ProofFilingColors.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⏰ Reminder Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ProofFilingColors.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Day of week",
                        fontSize = 12.sp,
                        color = ProofFilingColors.onSurfaceVariant
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        items(days.size) { index ->
                            val dayIndex = index + 1
                            FilterChip(
                                selected = config.reminderDayOfWeek == dayIndex,
                                onClick = { onUpdateReminderDay(dayIndex) },
                                label = { Text(days[index]) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ProofFilingColors.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Time: ${config.reminderHour}:${config.reminderMinute.toString().padStart(2, '0')}",
                        fontSize = 14.sp,
                        color = ProofFilingColors.onSurface
                    )
                    
                    // Simple time selector with +/- buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        IconButton(
                            onClick = { 
                                val newHour = if (config.reminderHour > 0) config.reminderHour - 1 else 23
                                onUpdateReminderTime(newHour, config.reminderMinute)
                            }
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Decrease hour",
                                tint = ProofFilingColors.primary
                            )
                        }
                        
                        Text(
                            text = "${config.reminderHour}h",
                            color = ProofFilingColors.onSurface
                        )
                        
                        IconButton(
                            onClick = { 
                                val newHour = (config.reminderHour + 1) % 24
                                onUpdateReminderTime(newHour, config.reminderMinute)
                            }
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Increase hour",
                                tint = ProofFilingColors.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        IconButton(
                            onClick = { 
                                val newMinute = if (config.reminderMinute >= 30) 0 else 30
                                onUpdateReminderTime(config.reminderHour, newMinute)
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Toggle minute",
                                tint = ProofFilingColors.primary
                            )
                        }
                        
                        Text(
                            text = "${config.reminderMinute.toString().padStart(2, '0')}m",
                            color = ProofFilingColors.onSurface
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Integrations
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ProofFilingColors.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔗 Integrations",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ProofFilingColors.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    config.integrations.forEach { integration ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = integration.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ProofFilingColors.onSurface
                                )
                                Text(
                                    text = if (integration.profileUrl.isNotBlank()) 
                                        integration.profileUrl else "Not configured",
                                    fontSize = 11.sp,
                                    color = ProofFilingColors.onSurfaceVariant
                                )
                            }
                            
                            Switch(
                                checked = integration.enabled,
                                onCheckedChange = { enabled ->
                                    onUpdateIntegration(integration.copy(enabled = enabled))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ProofFilingColors.secondary,
                                    checkedTrackColor = ProofFilingColors.secondary.copy(alpha = 0.5f)
                                )
                            )
                        }
                        
                        HorizontalDivider(color = ProofFilingColors.border)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "More integrations coming soon (Coursera, Medium, etc.)",
                        fontSize = 11.sp,
                        color = ProofFilingColors.onSurfaceVariant
                    )
                }
            }
        }
    }
}
