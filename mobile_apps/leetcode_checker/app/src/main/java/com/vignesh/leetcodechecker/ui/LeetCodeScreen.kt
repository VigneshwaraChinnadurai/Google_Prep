package com.vignesh.leetcodechecker.ui

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.leetcodechecker.*
import com.vignesh.leetcodechecker.R
import com.vignesh.leetcodechecker.data.DailyChallengeUiModel
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone

/**
 * LeetCodeScreen — Redesigned LeetCode Consistency Checker tab.
 *
 * Mirrors the Ollama tab's design language:
 * 1. LazyColumn with section-based layout
 * 2. Clean header + subtitle
 * 3. Two primary action buttons in a Row
 * 4. Horizontal scrollable quick-action chips
 * 5. Dismissible error/info banners
 * 6. Collapsible sections for AI output
 * 7. Expandable settings, history, and diagram panels
 * 8. Challenge filter from Features page shown as badge
 */
@Composable
fun LeetCodeScreen(
    onOpenLink: (String) -> Unit,
    challengeFilter: ChallengeFilter? = null,
    onClearFilter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: LeetCodeViewModel = viewModel(factory = LeetCodeViewModel.factory(application))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showDiagram by rememberSaveable { mutableStateOf(false) }
    var expandedSection by rememberSaveable { mutableStateOf("") }
    var showLlmConfirmation by rememberSaveable { mutableStateOf(false) }
    var showPushConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSettingsPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var settingsPasswordInput by rememberSaveable { mutableStateOf("") }
    val expectedSettingsPassword = BuildConfig.SETTINGS_UPDATE_PASSWORD.ifBlank { "1234" }

    // Settings form state
    var settingsModelsCsv by rememberSaveable { mutableStateOf(state.settings.preferredModelsCsv) }
    var settingsMaxRetries by rememberSaveable { mutableStateOf(state.settings.maxModelRetries.toString()) }
    var settingsMaxInput by rememberSaveable { mutableStateOf(state.settings.maxInputTokens.toString()) }
    var settingsMaxOutput by rememberSaveable { mutableStateOf(state.settings.maxOutputTokens.toString()) }
    var settingsThinkingDivisor by rememberSaveable { mutableStateOf(state.settings.thinkingBudgetDivisor.toString()) }
    var settingsTimeoutMinutes by rememberSaveable { mutableStateOf(state.settings.networkTimeoutMinutes.toString()) }
    var settingsReminderStart by rememberSaveable { mutableStateOf(state.settings.reminderStartHourIst.toString()) }
    var settingsReminderEnd by rememberSaveable { mutableStateOf(state.settings.reminderEndHourIst.toString()) }
    var settingsReminderInterval by rememberSaveable { mutableStateOf(state.settings.reminderIntervalHours.toString()) }
    var settingsGithubOwner by rememberSaveable { mutableStateOf(state.settings.githubOwnerOverride) }
    var settingsGithubRepo by rememberSaveable { mutableStateOf(state.settings.githubRepoOverride) }
    var settingsGithubBranch by rememberSaveable { mutableStateOf(state.settings.githubBranchOverride) }

    // Code editing state
    var isEditingCode by rememberSaveable { mutableStateOf(false) }
    var editableCode by rememberSaveable(state.aiCode) { mutableStateOf(state.aiCode ?: "") }

    // Calendar permission state
    var pendingCalendarCompletion by rememberSaveable { mutableStateOf(false) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val granted = grantMap.values.all { it }
        if (granted && pendingCalendarCompletion) {
            pendingCalendarCompletion = false
            viewModel.markCompletedToday()
            val inserted = insertCompletionCalendarEvent(context, state.challenge)
            val message = if (inserted) "Marked completed + calendar entry created."
            else "Marked completed, but calendar entry failed."
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Auto-dismiss info message
    LaunchedEffect(state.infoMessage) {
        if (state.infoMessage != null) {
            delay(4000)
            viewModel.clearInfoMessage()
        }
    }

    // Sync settings form when loaded
    LaunchedEffect(state.settings) {
        settingsModelsCsv = state.settings.preferredModelsCsv
        settingsMaxRetries = state.settings.maxModelRetries.toString()
        settingsMaxInput = state.settings.maxInputTokens.toString()
        settingsMaxOutput = state.settings.maxOutputTokens.toString()
        settingsThinkingDivisor = state.settings.thinkingBudgetDivisor.toString()
        settingsTimeoutMinutes = state.settings.networkTimeoutMinutes.toString()
        settingsReminderStart = state.settings.reminderStartHourIst.toString()
        settingsReminderEnd = state.settings.reminderEndHourIst.toString()
        settingsReminderInterval = state.settings.reminderIntervalHours.toString()
        settingsGithubOwner = state.settings.githubOwnerOverride
        settingsGithubRepo = state.settings.githubRepoOverride
        settingsGithubBranch = state.settings.githubBranchOverride
    }

    // Request notification permission once
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ════════════════════════════════════════════════════════════
            // Header
            // ════════════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LeetCode Consistency Checker",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gemini AI • Solve daily LeetCode challenges",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Image(
                        painter = painterResource(id = R.drawable.leetcode_logo),
                        contentDescription = "LeetCode Logo",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // ════════════════════════════════════════════════════════════
            // Challenge Filter Banner (from Features page)
            // ════════════════════════════════════════════════════════════
            if (challengeFilter != null && (challengeFilter.topic != null || challengeFilter.difficulty != null)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🎯",
                                    fontSize = 18.sp
                                )
                                Column {
                                    Text(
                                        text = "Challenge Filter Active",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${challengeFilter.topic ?: "Any"} • ${challengeFilter.difficulty ?: "Any"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            TextButton(
                                onClick = onClearFilter,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // Action Buttons Row
            // ════════════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.refreshApiChallenge() },
                        enabled = !state.isLoading && !state.isAiLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Fetch Challenge", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showLlmConfirmation = true },
                        enabled = !state.isLoading && !state.isAiLoading && state.challenge != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        if (state.isAiLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("🤖 LLM Solve", fontSize = 11.sp)
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // Quick Action Chips
            // ════════════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPushConfirmation = true },
                        enabled = state.challenge != null && !state.aiCode.isNullOrBlank() && !state.isPushLoading,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("📦 GitHub Push", fontSize = 11.sp) }

                    OutlinedButton(
                        onClick = {
                            state.aiCode?.let { code ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("LeetCode Python3 Code", code))
                                Toast.makeText(context, "Python code copied!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !state.aiCode.isNullOrBlank(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("📋 Copy Code", fontSize = 11.sp) }

                    OutlinedButton(
                        onClick = {
                            showHistory = !showHistory
                            if (showHistory) viewModel.refreshLocalRevisionHistory()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("📊 History", fontSize = 11.sp) }

                    OutlinedButton(
                        onClick = { showDiagram = !showDiagram },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("🔗 Diagram", fontSize = 11.sp) }

                    OutlinedButton(
                        onClick = { showSettings = !showSettings },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("⚙️ Settings", fontSize = 11.sp) }

                    OutlinedButton(
                        onClick = {
                            val primaryIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_APP_CALENDAR)
                            }
                            val fallbackIntent = Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI)
                            runCatching { context.startActivity(primaryIntent) }
                                .onFailure {
                                    runCatching { context.startActivity(fallbackIntent) }
                                        .onFailure {
                                            Toast.makeText(context, "No calendar app found.", Toast.LENGTH_SHORT).show()
                                        }
                                }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("📅 Calendar", fontSize = 11.sp) }
                }
            }

            // ════════════════════════════════════════════════════════════
            // Error / Info Messages
            // ════════════════════════════════════════════════════════════
            item {
                // Error (sticky)
                if (state.error != null || state.aiError != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ ${state.error ?: state.aiError}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("✕", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                // Info (auto-dismiss)
                AnimatedVisibility(
                    visible = state.infoMessage != null,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = "ℹ️ ${state.infoMessage ?: ""}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // Pipeline Info Card
            // ════════════════════════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Pipeline: Fetch → Review → LLM Solve → Mark Complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.isCompletedToday) {
                            Text(
                                text = "✅ Done",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // Challenge Card
            // ════════════════════════════════════════════════════════════
            state.challenge?.let { challenge ->
                item {
                    LeetCodeChallengeCard(
                        challenge = challenge,
                        isCompletedToday = state.isCompletedToday,
                        onOpenLink = onOpenLink,
                        onMarkCompleted = {
                            if (state.isCompletedToday) {
                                viewModel.unmarkCompletedToday()
                                val removed = deleteCompletionCalendarEvent(context)
                                val msg = if (removed) "Unmarked + calendar entry removed."
                                else "Unmarked, calendar entry not found."
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                val hasWrite = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.WRITE_CALENDAR
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasRead = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.READ_CALENDAR
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasWrite && hasRead) {
                                    viewModel.markCompletedToday()
                                    val inserted = insertCompletionCalendarEvent(context, challenge)
                                    val msg = if (inserted) "Marked completed + calendar entry created."
                                    else "Marked completed, calendar entry failed."
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    pendingCalendarCompletion = true
                                    calendarPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                }
                            }
                        }
                    )
                }

                // ════════════════════════════════════════════════════════════
                // Problem Statement (Expandable) - LeetCode-style rendering
                // ════════════════════════════════════════════════════════════
                if (challenge.htmlContent.isNotBlank()) {
                    item {
                        LeetCodeCollapsibleSection(
                            title = "📋 Problem Statement",
                            isExpanded = expandedSection == "problem",
                            onToggle = { expandedSection = if (expandedSection == "problem") "" else "problem" }
                        ) {
                            LeetCodeHtmlText(
                                htmlContent = challenge.htmlContent,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // AI Response Sections (collapsible)
            // ════════════════════════════════════════════════════════════
            if (state.aiCode != null) {
                item {
                    LeetCodeCollapsibleSection(
                        title = "💻 Python Code",
                        isExpanded = expandedSection == "code",
                        onToggle = { expandedSection = if (expandedSection == "code") "" else "code" }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "solution_${state.challenge?.titleSlug ?: "unknown"}.py",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            if (isEditingCode) {
                                OutlinedTextField(
                                    value = editableCode,
                                    onValueChange = { editableCode = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 200.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    keyboardOptions = KeyboardOptions.Default.copy(
                                        keyboardType = KeyboardType.Ascii
                                    )
                                )
                            } else {
                                LeetCodeCodeBlock(state.aiCode ?: "")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (isEditingCode) {
                                            viewModel.updateAiCode(editableCode)
                                            isEditingCode = false
                                        } else {
                                            editableCode = state.aiCode ?: ""
                                            isEditingCode = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        if (isEditingCode) "💾 Save" else "✏️ Edit",
                                        fontSize = 11.sp
                                    )
                                }
                                if (isEditingCode) {
                                    OutlinedButton(
                                        onClick = {
                                            editableCode = state.aiCode ?: ""
                                            isEditingCode = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(
                                                ClipData.newPlainText("LeetCode Python3 Code", state.aiCode)
                                            )
                                            Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("📋 Copy", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!state.aiTestcaseValidation.isNullOrBlank()) {
                item {
                    LeetCodeCollapsibleSection(
                        title = "✅ Testcase Validation",
                        isExpanded = expandedSection == "validation",
                        onToggle = { expandedSection = if (expandedSection == "validation") "" else "validation" }
                    ) {
                        SelectionContainer {
                            Text(
                                text = state.aiTestcaseValidation ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            if (!state.aiExplanation.isNullOrBlank()) {
                // Extract and show DSA concepts
                val concepts = extractDsaConcepts(
                    state.aiExplanation ?: "",
                    state.challenge?.tags ?: emptyList()
                )
                if (concepts.isNotEmpty()) {
                    item {
                        LeetCodeCollapsibleSection(
                            title = "🧠 DSA Concepts (${concepts.size})",
                            isExpanded = expandedSection == "concepts",
                            onToggle = { expandedSection = if (expandedSection == "concepts") "" else "concepts" }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                concepts.forEach { concept ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(concept, fontSize = 10.sp) },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    LeetCodeCollapsibleSection(
                        title = "📝 Explanation",
                        isExpanded = expandedSection == "explanation",
                        onToggle = { expandedSection = if (expandedSection == "explanation") "" else "explanation" }
                    ) {
                        SelectionContainer {
                            MarkdownText(
                                text = state.aiExplanation ?: "",
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            if (!state.aiDebugLog.isNullOrBlank()) {
                item {
                    LeetCodeCollapsibleSection(
                        title = "📋 Pipeline Log",
                        isExpanded = expandedSection == "pipeline",
                        onToggle = { expandedSection = if (expandedSection == "pipeline") "" else "pipeline" }
                    ) {
                        SelectionContainer {
                            Text(
                                text = state.aiDebugLog ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // Local Revision Path
            // ════════════════════════════════════════════════════════════
            state.localRevisionPath?.let { localPath ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = "📁 Saved: $localPath",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // Settings Panel (expandable)
            // ════════════════════════════════════════════════════════════
            if (showSettings) {
                item {
                    LeetCodeSettingsPanel(
                        settingsModelsCsv = settingsModelsCsv,
                        onModelsChange = { settingsModelsCsv = it },
                        settingsMaxRetries = settingsMaxRetries,
                        onMaxRetriesChange = { settingsMaxRetries = it },
                        settingsMaxInput = settingsMaxInput,
                        onMaxInputChange = { settingsMaxInput = it },
                        settingsMaxOutput = settingsMaxOutput,
                        onMaxOutputChange = { settingsMaxOutput = it },
                        settingsThinkingDivisor = settingsThinkingDivisor,
                        onThinkingDivisorChange = { settingsThinkingDivisor = it },
                        settingsTimeoutMinutes = settingsTimeoutMinutes,
                        onTimeoutChange = { settingsTimeoutMinutes = it },
                        settingsReminderStart = settingsReminderStart,
                        onReminderStartChange = { settingsReminderStart = it },
                        settingsReminderEnd = settingsReminderEnd,
                        onReminderEndChange = { settingsReminderEnd = it },
                        settingsReminderInterval = settingsReminderInterval,
                        onReminderIntervalChange = { settingsReminderInterval = it },
                        settingsGithubOwner = settingsGithubOwner,
                        onGithubOwnerChange = { settingsGithubOwner = it },
                        settingsGithubRepo = settingsGithubRepo,
                        onGithubRepoChange = { settingsGithubRepo = it },
                        settingsGithubBranch = settingsGithubBranch,
                        onGithubBranchChange = { settingsGithubBranch = it },
                        onSave = { showSettingsPasswordDialog = true }
                    )
                }
            }

            // ════════════════════════════════════════════════════════════
            // Submission History (expandable)
            // ════════════════════════════════════════════════════════════
            if (showHistory) {
                item {
                    LeetCodeHistoryPanel(
                        history = state.revisionHistory,
                        isLoading = state.isHistoryLoading,
                        selectedItem = state.selectedHistoryItem,
                        onRefresh = { viewModel.refreshLocalRevisionHistory() },
                        onSelectItem = { viewModel.selectHistoryItem(it) }
                    )
                }
            }

            // ════════════════════════════════════════════════════════════
            // Flow Diagram (expandable)
            // ════════════════════════════════════════════════════════════
            if (showDiagram) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔗 Mermaid Flow Diagram",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pinch to zoom, drag to pan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LeetCodeZoomableFlowDiagram()
                        }
                    }
                }
            }

            // Bottom spacing
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Dialogs
    // ════════════════════════════════════════════════════════════════════

    if (showLlmConfirmation) {
        AlertDialog(
            onDismissRequest = { showLlmConfirmation = false },
            title = { Text("Confirm LLM Call") },
            text = { Text("This will trigger a paid Gemini LLM request. Continue?") },
            confirmButton = {
                Button(onClick = {
                    showLlmConfirmation = false
                    viewModel.refreshLlmAnswer()
                }) { Text("Confirm") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLlmConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    if (showPushConfirmation) {
        AlertDialog(
            onDismissRequest = { showPushConfirmation = false },
            title = { Text("Confirm GitHub Push") },
            text = {
                Text(
                    "This will create/update question.txt, answer.py, and explanation.txt under " +
                            "${state.settings.revisionFolderName}/<date> in the configured GitHub repo. Continue?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPushConfirmation = false
                    viewModel.pushRevisionFilesToGitHub()
                }) { Text("Push") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPushConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    if (showSettingsPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showSettingsPasswordDialog = false
                settingsPasswordInput = ""
            },
            title = { Text("Settings Password") },
            text = {
                OutlinedTextField(
                    value = settingsPasswordInput,
                    onValueChange = { settingsPasswordInput = it },
                    label = { Text("Enter password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (settingsPasswordInput == expectedSettingsPassword) {
                        showSettingsPasswordDialog = false
                        settingsPasswordInput = ""
                        viewModel.saveSettings(
                            AppSettings(
                                landingTitle = state.settings.landingTitle,
                                checkerTitle = state.settings.checkerTitle,
                                consistencyButtonLabel = state.settings.consistencyButtonLabel,
                                promptName = state.settings.promptName,
                                preferredModelsCsv = settingsModelsCsv,
                                maxModelRetries = settingsMaxRetries.toIntOrNull() ?: state.settings.maxModelRetries,
                                maxInputTokens = settingsMaxInput.toIntOrNull() ?: state.settings.maxInputTokens,
                                maxOutputTokens = settingsMaxOutput.toIntOrNull() ?: state.settings.maxOutputTokens,
                                thinkingBudgetDivisor = settingsThinkingDivisor.toIntOrNull() ?: state.settings.thinkingBudgetDivisor,
                                networkTimeoutMinutes = settingsTimeoutMinutes.toIntOrNull() ?: state.settings.networkTimeoutMinutes,
                                reminderStartHourIst = settingsReminderStart.toIntOrNull() ?: state.settings.reminderStartHourIst,
                                reminderEndHourIst = settingsReminderEnd.toIntOrNull() ?: state.settings.reminderEndHourIst,
                                reminderIntervalHours = settingsReminderInterval.toIntOrNull() ?: state.settings.reminderIntervalHours,
                                revisionFolderName = state.settings.revisionFolderName,
                                githubOwnerOverride = settingsGithubOwner,
                                githubRepoOverride = settingsGithubRepo,
                                githubBranchOverride = settingsGithubBranch
                            )
                        )
                    } else {
                        Toast.makeText(context, "Incorrect settings password.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Unlock & Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showSettingsPasswordDialog = false
                    settingsPasswordInput = ""
                }) { Text("Cancel") }
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Supporting Composables
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LeetCodeChallengeCard(
    challenge: DailyChallengeUiModel,
    isCompletedToday: Boolean,
    onOpenLink: (String) -> Unit,
    onMarkCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val difficultyColor = when (challenge.difficulty.lowercase()) {
        "easy" -> MaterialTheme.colorScheme.tertiary
        "medium" -> MaterialTheme.colorScheme.primary
        "hard" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${challenge.questionId}. ${challenge.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = challenge.difficulty,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = difficultyColor
                )
            }

            Text(
                text = challenge.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (challenge.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    challenge.tags.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(tag, fontSize = 10.sp) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }

            Text(
                text = challenge.descriptionPreview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onOpenLink(challenge.url) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) { Text("🌐 Open Problem", fontSize = 11.sp) }

                Button(
                    onClick = onMarkCompleted,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompletedToday) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (isCompletedToday) "✅ Undo Complete" else "✔️ Mark Done",
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LeetCodeCollapsibleSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (isExpanded) {
                HorizontalDivider()
                content()
            }
        }
    }
}

@Composable
private fun LeetCodeCodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp)
            )
            .padding(12.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        SelectionContainer {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun LeetCodeSettingsPanel(
    settingsModelsCsv: String,
    onModelsChange: (String) -> Unit,
    settingsMaxRetries: String,
    onMaxRetriesChange: (String) -> Unit,
    settingsMaxInput: String,
    onMaxInputChange: (String) -> Unit,
    settingsMaxOutput: String,
    onMaxOutputChange: (String) -> Unit,
    settingsThinkingDivisor: String,
    onThinkingDivisorChange: (String) -> Unit,
    settingsTimeoutMinutes: String,
    onTimeoutChange: (String) -> Unit,
    settingsReminderStart: String,
    onReminderStartChange: (String) -> Unit,
    settingsReminderEnd: String,
    onReminderEndChange: (String) -> Unit,
    settingsReminderInterval: String,
    onReminderIntervalChange: (String) -> Unit,
    settingsGithubOwner: String,
    onGithubOwnerChange: (String) -> Unit,
    settingsGithubRepo: String,
    onGithubRepoChange: (String) -> Unit,
    settingsGithubBranch: String,
    onGithubBranchChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "⚙️ Settings",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            Text("LLM Configuration", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = settingsModelsCsv, onValueChange = onModelsChange,
                label = { Text("Preferred Models CSV") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = settingsMaxRetries, onValueChange = onMaxRetriesChange,
                    label = { Text("Retries") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                OutlinedTextField(
                    value = settingsTimeoutMinutes, onValueChange = onTimeoutChange,
                    label = { Text("Timeout (min)") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = settingsMaxInput, onValueChange = onMaxInputChange,
                    label = { Text("Max Input Tokens") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                OutlinedTextField(
                    value = settingsMaxOutput, onValueChange = onMaxOutputChange,
                    label = { Text("Max Output Tokens") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }
            OutlinedTextField(
                value = settingsThinkingDivisor, onValueChange = onThinkingDivisorChange,
                label = { Text("Thinking Budget Divisor") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )

            HorizontalDivider()

            Text("Reminder Settings", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = settingsReminderStart, onValueChange = onReminderStartChange,
                    label = { Text("Start Hr (IST)") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                OutlinedTextField(
                    value = settingsReminderEnd, onValueChange = onReminderEndChange,
                    label = { Text("End Hr (IST)") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                OutlinedTextField(
                    value = settingsReminderInterval, onValueChange = onReminderIntervalChange,
                    label = { Text("Interval Hr") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }

            HorizontalDivider()

            Text("GitHub Settings", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = settingsGithubOwner, onValueChange = onGithubOwnerChange,
                label = { Text("GitHub Owner") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = settingsGithubRepo, onValueChange = onGithubRepoChange,
                    label = { Text("Repo") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                OutlinedTextField(
                    value = settingsGithubBranch, onValueChange = onGithubBranchChange,
                    label = { Text("Branch") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("🔐 Save Settings")
            }
        }
    }
}

@Composable
private fun LeetCodeHistoryPanel(
    history: List<LocalRevisionHistoryItem>,
    isLoading: Boolean,
    selectedItem: LocalRevisionHistoryItem?,
    onRefresh: () -> Unit,
    onSelectItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Submission History (${history.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Loading...", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (history.isEmpty() && !isLoading) {
                Text(
                    text = "No local submissions found yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                history.forEach { item ->
                    val isSelected = selectedItem?.folderDate == item.folderDate
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectItem(item.folderDate) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (item.questionId.isNotBlank()) "${item.folderDate} • #${item.questionId}"
                                    else item.folderDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isSelected) {
                                    Text(
                                        "▲",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = item.title.ifBlank { "(Title unavailable)" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Expanded details for selected item
                    if (isSelected && selectedItem != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "📁 ${selectedItem.folderPath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text("Question", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                SelectionContainer {
                                    Text(
                                        text = selectedItem.questionText.ifBlank { "Not found." },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 6,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text("Solution", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                SelectionContainer {
                                    Text(
                                        text = selectedItem.answerPython.ifBlank { "Not found." },
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        maxLines = 10,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text("Explanation", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                SelectionContainer {
                                    Text(
                                        text = selectedItem.explanationText.ifBlank { "Not found." },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 8,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeetCodeZoomableFlowDiagram() {
    var scale by rememberSaveable { mutableStateOf(1f) }
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
        ) {
            Image(
                painter = painterResource(id = R.drawable.runtime_flow_diagram),
                contentDescription = "Mermaid runtime flow diagram",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val updatedScale = (scale * zoom).coerceIn(1f, 5f)
                            val scaleChanged = updatedScale != scale
                            scale = updatedScale
                            if (scale > 1f || scaleChanged) {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                            if (scale <= 1f) {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = {
                scale = 1f
                offsetX = 0f
                offsetY = 0f
            }) { Text("Reset Zoom", fontSize = 11.sp) }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Utility Functions
// ════════════════════════════════════════════════════════════════════════════

private fun extractDsaConcepts(explanation: String, tags: List<String>): List<String> {
    val text = explanation.lowercase()
    val concepts = linkedSetOf<String>()
    tags.map { it.trim() }.filter { it.isNotBlank() }.forEach { concepts.add(it) }

    val keywordMap = listOf(
        "prefix sum" to "Prefix Sum",
        "sliding window" to "Sliding Window",
        "two pointers" to "Two Pointers",
        "binary search" to "Binary Search",
        "dynamic programming" to "Dynamic Programming",
        "dp" to "Dynamic Programming",
        "greedy" to "Greedy",
        "monotonic stack" to "Monotonic Stack",
        "stack" to "Stack",
        "queue" to "Queue",
        "deque" to "Deque",
        "hash map" to "Hash Map",
        "hashmap" to "Hash Map",
        "dictionary" to "Hash Map",
        "set" to "Set",
        "sort" to "Sorting",
        "matrix" to "Matrix",
        "graph" to "Graph",
        "tree" to "Tree",
        "dfs" to "Depth-First Search",
        "bfs" to "Breadth-First Search",
        "union-find" to "Union Find",
        "disjoint set" to "Union Find",
        "backtracking" to "Backtracking",
        "recursion" to "Recursion",
        "bitmask" to "Bit Manipulation",
        "bit" to "Bit Manipulation",
        "heap" to "Heap / Priority Queue",
        "priority queue" to "Heap / Priority Queue"
    )
    keywordMap.forEach { (token, label) ->
        if (text.contains(token)) concepts.add(label)
    }
    return concepts.toList()
}

private fun insertCompletionCalendarEvent(context: Context, challenge: DailyChallengeUiModel?): Boolean {
    return runCatching {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        val selection = "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val sortOrder = "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} DESC"
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, selection, selectionArgs, sortOrder
        ) ?: return false

        var calendarId: Long? = null
        cursor.use { if (it.moveToFirst()) calendarId = it.getLong(0) }
        val selectedCalendarId = calendarId ?: return false

        val timezone = TimeZone.getDefault()
        val start = Calendar.getInstance(timezone)
        val end = (start.clone() as Calendar).apply { add(Calendar.MINUTE, 30) }
        val dayStart = (start.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }

        val title = if (challenge != null) "LeetCode Completed: ${challenge.title}" else "LeetCode Completed"

        // Check for existing event
        val existingEventSelection = "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DTSTART}>=? AND ${CalendarContract.Events.DTSTART}<? AND ${CalendarContract.Events.TITLE} LIKE ?"
        val existingEventArgs = arrayOf(selectedCalendarId.toString(), dayStart.timeInMillis.toString(), dayEnd.timeInMillis.toString(), "LeetCode Completed%")
        val existingCursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID),
            existingEventSelection, existingEventArgs, "${CalendarContract.Events.DTSTART} DESC"
        )
        existingCursor?.use { if (it.moveToFirst()) return true }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, selectedCalendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, "Marked completed from LeetCode Consistency Checker")
            put(CalendarContract.Events.DTSTART, start.timeInMillis)
            put(CalendarContract.Events.DTEND, end.timeInMillis)
            put(CalendarContract.Events.ALL_DAY, 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, timezone.id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            put(CalendarContract.Events.EVENT_COLOR, 0xFF2E7D32.toInt())
        }
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return false
        val eventId = eventUri.lastPathSegment?.toLongOrNull() ?: return true
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 0)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        true
    }.getOrDefault(false)
}

private fun deleteCompletionCalendarEvent(context: Context): Boolean {
    return runCatching {
        val timezone = TimeZone.getDefault()
        val dayStart = Calendar.getInstance(timezone).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val selection = "${CalendarContract.Events.DTSTART}>=? AND ${CalendarContract.Events.DTSTART}<? AND ${CalendarContract.Events.TITLE} LIKE ?"
        val selectionArgs = arrayOf(dayStart.timeInMillis.toString(), dayEnd.timeInMillis.toString(), "LeetCode Completed%")
        val deleted = context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs)
        deleted > 0
    }.getOrDefault(false)
}
