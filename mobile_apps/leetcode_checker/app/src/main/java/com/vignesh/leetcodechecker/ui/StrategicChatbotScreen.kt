package com.vignesh.leetcodechecker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.data.ChatLogEntry
import com.vignesh.leetcodechecker.data.ChatSession
import com.vignesh.leetcodechecker.data.LogEntryType
import com.vignesh.leetcodechecker.models.*
import com.vignesh.leetcodechecker.viewmodel.ChatbotViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Strategic Chatbot Screen — Multi-mode conversation UI
 *
 * Features:
 * 1. Three chat modes (Quick Chat, Deep Analysis, Follow-up)
 * 2. Real-time cost tracking with budget visualization
 * 3. Message history with formatting
 * 4. Example prompts for easy interaction
 * 5. Session state and cost warnings
 */
@Composable
fun StrategicChatbotScreen(
    viewModel: ChatbotViewModel,
    onOpenLink: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val costInfo by viewModel.costInfo.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val sessionList by viewModel.sessionList.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val activeSessionName by viewModel.activeSessionName.collectAsStateWithLifecycle()
    val logEntries by viewModel.logEntries.collectAsStateWithLifecycle()

    var messageInput by rememberSaveable { mutableStateOf("") }
    var selectedMode by rememberSaveable { mutableStateOf(ChatMode.QUICK_CHAT) }
    var showExamples by rememberSaveable { mutableStateOf(false) }
    var showSessionInfo by rememberSaveable { mutableStateOf(false) }
    var showDiagram by rememberSaveable { mutableStateOf(false) }
    var showSessionsPanel by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var renameTargetId by rememberSaveable { mutableStateOf("") }
    var renameTargetName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(selectedMode) {
        viewModel.setChatMode(selectedMode)
    }

    // Auto-dismiss success message after 3 seconds
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            delay(3000)
            viewModel.clearSuccess()
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ════════════════════════════════════════════════════════════
            // Header with Mode Selection and Cost Info
            // ════════════════════════════════════════════════════════════
            ChatbotHeader(
                selectedMode = selectedMode,
                onModeChanged = { selectedMode = it },
                costInfo = costInfo,
                onShowSessionInfo = { showSessionInfo = !showSessionInfo },
                onShowExamples = { showExamples = !showExamples },
                onShowDiagram = { showDiagram = !showDiagram },
                onShowSessions = { showSessionsPanel = !showSessionsPanel },
                onShowLogs = {
                    showLogs = !showLogs
                    if (showLogs) viewModel.refreshLogEntries()
                },
                activeSessionName = activeSessionName,
                logCount = logEntries.size
            )

            HorizontalDivider()

            // ════════════════════════════════════════════════════════════
            // Cost Tracker and Warnings
            // ════════════════════════════════════════════════════════════
            CostTrackerCard(costInfo = costInfo)

            // ════════════════════════════════════════════════════════════
            // Session Info (if expanded)
            // ════════════════════════════════════════════════════════════
            if (showSessionInfo) {
                SessionInfoCard(sessionState = sessionState)
            }

            // ════════════════════════════════════════════════════════════
            // Sessions Panel (browse, switch, rename, delete)
            // ════════════════════════════════════════════════════════════
            if (showSessionsPanel) {
                SessionsPanel(
                    sessions = sessionList,
                    activeSessionId = activeSessionId ?: "",
                    onSwitchSession = { id ->
                        viewModel.switchToSession(id)
                        showSessionsPanel = false
                    },
                    onRenameSession = { id, currentName ->
                        renameTargetId = id
                        renameTargetName = currentName
                        showRenameDialog = true
                    },
                    onDeleteSession = { id ->
                        viewModel.deleteSession(id)
                    },
                    onNewSession = {
                        viewModel.createNewSession()
                        showSessionsPanel = false
                    }
                )
            }

            // ══════════════════════════════════════════════════════════
            // Logs Panel (comprehensive logging viewer)
            // ══════════════════════════════════════════════════════════
            if (showLogs) {
                ChatbotLogsPanel(
                    logEntries = logEntries,
                    onClearLogs = { viewModel.clearLogs() },
                    onCopyLogs = { viewModel.exportLogsAsText() }
                )
            }

            // ════════════════════════════════════════════════════════════
            // Rename Dialog
            // ════════════════════════════════════════════════════════════
            if (showRenameDialog) {
                RenameSessionDialog(
                    currentName = renameTargetName,
                    onConfirm = { newName ->
                        viewModel.renameSession(renameTargetId, newName)
                        showRenameDialog = false
                    },
                    onDismiss = { showRenameDialog = false }
                )
            }

            // ════════════════════════════════════════════════════════════
            // Chat History
            // ════════════════════════════════════════════════════════════
            ChatHistorySection(
                messages = messages,
                isLoading = uiState.isLoading,
                modifier = Modifier.weight(1f)
            )

            // ════════════════════════════════════════════════════════════
            // Example Prompts (if showing)
            // ════════════════════════════════════════════════════════════
            if (showExamples) {
                ExamplePromptsSection(
                    examples = viewModel.getExamplePrompts(),
                    onSelectPrompt = {
                        messageInput = it
                        showExamples = false
                    }
                )
            }

            // ════════════════════════════════════════════════════════════
            // Mermaid Diagram (full-screen dialog)
            // ════════════════════════════════════════════════════════════
            if (showDiagram) {
                AlertDialog(
                    onDismissRequest = { showDiagram = false },
                    confirmButton = {
                        Button(onClick = { showDiagram = false }) {
                            Text("Close")
                        }
                    },
                    title = null,
                    text = {
                        StrategicChatbotMermaidDiagram()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                )
            }

            // ════════════════════════════════════════════════════════════
            // Error Message (sticky until dismissed)
            // ════════════════════════════════════════════════════════════
            if (uiState.errorMessage != null) {
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
                            text = "⚠️ ${uiState.errorMessage}",
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

            // Floating success banner (auto-dismisses in 3s, no space when gone)
            AnimatedVisibility(
                visible = uiState.successMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "✅ ${uiState.successMessage ?: ""}",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // ════════════════════════════════════════════════════════════
            // Message Input & Send Button
            // ════════════════════════════════════════════════════════════
            ChatInputSection(
                messageInput = messageInput,
                onMessageChanged = { messageInput = it },
                onSend = {
                    if (messageInput.isNotBlank()) {
                        viewModel.sendMessage(messageInput)
                        messageInput = ""
                        showExamples = false
                    }
                },
                isLoading = uiState.isLoading,
                onReset = { viewModel.resetSession() },
                onNewSession = { viewModel.createNewSession() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Supporting Composables
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatbotHeader(
    selectedMode: ChatMode,
    onModeChanged: (ChatMode) -> Unit,
    costInfo: CostInfo,
    onShowSessionInfo: () -> Unit,
    onShowExamples: () -> Unit,
    onShowDiagram: () -> Unit,
    onShowSessions: () -> Unit,
    onShowLogs: () -> Unit,
    activeSessionName: String,
    logCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Strategic Analysis Chatbot",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "📌 $activeSessionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Mode Selection
        Text("Chat Mode:", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatMode.values().forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = selectedMode == mode,
                        onClick = { onModeChanged(mode) },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Mode description
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = selectedMode.description,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Quick Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onShowExamples,
                contentPadding = PaddingValues(6.dp)
            ) {
                Text("📋 Examples", fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = onShowSessions,
                contentPadding = PaddingValues(6.dp)
            ) {
                Text("📂 Sessions", fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = onShowSessionInfo,
                contentPadding = PaddingValues(6.dp)
            ) {
                Text("📊 Info", fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = onShowLogs,
                contentPadding = PaddingValues(6.dp)
            ) {
                Text("📋 Logs${if (logCount > 0) " ($logCount)" else ""}", fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = onShowDiagram,
                contentPadding = PaddingValues(6.dp)
            ) {
                Text("🔗 Arch", fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CostTrackerCard(
    costInfo: CostInfo,
    modifier: Modifier = Modifier
) {
    val percentUsed = (costInfo.totalCost / costInfo.dailyBudget).toFloat().coerceIn(0f, 1f)
    val isNearLimit = percentUsed > 0.8f

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isNearLimit)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
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
                    text = "💰 Cost Tracking",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$%.4f / $%.2f".format(costInfo.totalCost, costInfo.dailyBudget),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { percentUsed },
                modifier = Modifier.fillMaxWidth(),
                color = if (isNearLimit)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Remaining: $%.4f".format(costInfo.remainingBudget),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isNearLimit) {
                    Text(
                        text = "⚠️ Near limit!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionInfoCard(
    sessionState: SessionState?,
    modifier: Modifier = Modifier
) {
    if (sessionState == null) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📊 Session Info",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Session ID: ${sessionState.sessionId.take(8)}...", style = MaterialTheme.typography.bodySmall)
            Text("Total Cost: $%.4f".format(sessionState.totalCostUsd), style = MaterialTheme.typography.bodySmall)
            Text("API Calls: ${sessionState.apiCalls}", style = MaterialTheme.typography.bodySmall)
            Text("Turns: ${sessionState.turnCount}", style = MaterialTheme.typography.bodySmall)
            Text("Index Built: ${if (sessionState.isIndexBuilt) "✓ Yes" else "✗ No"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChatHistorySection(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ChatMessageBubble(message = message)
            }
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Thinking...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val backgroundColor = if (isUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isUser)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.75f else 0.92f)
                .padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isUser) "You" else "Assistant",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                } else {
                    MarkdownText(
                        text = message.content,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamplePromptsSection(
    examples: List<String>,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📋 Example Prompts",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            examples.forEach { example ->
                OutlinedButton(
                    onClick = { onSelectPrompt(example) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = example,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionsPanel(
    sessions: List<ChatSession>,
    activeSessionId: String,
    onSwitchSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

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
                    text = "📂 Sessions (${sessions.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = onNewSession,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "New",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("New Session", fontSize = 11.sp)
                }
            }

            HorizontalDivider()

            if (sessions.isEmpty()) {
                Text(
                    text = "No sessions yet. Start chatting!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sessions.forEach { session ->
                        val isActive = session.id == activeSessionId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (!isActive) onSwitchSession(session.id) }
                                .then(
                                    if (isActive) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    ) else Modifier
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isActive) "📌 ${session.name}" else session.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${dateFormat.format(Date(session.updatedAt))} · ${session.messageCount} msgs · $${String.format("%.4f", session.totalCostUsd)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                    if (session.preview.isNotBlank()) {
                                        Text(
                                            text = session.preview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    IconButton(
                                        onClick = { onRenameSession(session.id, session.name) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Rename",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    if (!isActive) {
                                        IconButton(
                                            onClick = { onDeleteSession(session.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
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
    }
}

@Composable
private fun RenameSessionDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Session") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Session Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ChatInputSection(
    messageInput: String,
    onMessageChanged: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    onReset: () -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = onMessageChanged,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                placeholder = { Text("Ask about industries, companies, trends...") },
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                enabled = !isLoading
            )
            IconButton(
                onClick = onSend,
                enabled = messageInput.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxHeight()
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onNewSession,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("➕ New Session", fontSize = 12.sp)
            }
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("🗑 Reset", fontSize = 12.sp)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Chatbot Logs Panel
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatbotLogsPanel(
    logEntries: List<ChatLogEntry>,
    onClearLogs: () -> Unit,
    onCopyLogs: () -> String,
    modifier: Modifier = Modifier
) {
    var filterType by remember { mutableStateOf<LogEntryType?>(null) }
    var expandedEntryId by remember { mutableStateOf<Long?>(null) }
    var copiedText by remember { mutableStateOf<String?>(null) }

    val filteredEntries = if (filterType != null) {
        logEntries.filter { it.type == filterType }
    } else {
        logEntries
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with count and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋 Logs (${filteredEntries.size}/${logEntries.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = {
                            copiedText = onCopyLogs()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (copiedText != null) "✅ Copied" else "📋 Copy", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            onClearLogs()
                            expandedEntryId = null
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("🗑 Clear", fontSize = 10.sp)
                    }
                }
            }

            // Reset copy feedback
            LaunchedEffect(copiedText) {
                if (copiedText != null) {
                    delay(2000)
                    copiedText = null
                }
            }

            // Filter chips — scrollable row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = filterType == null,
                    onClick = { filterType = null },
                    label = { Text("All", fontSize = 9.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = filterType == LogEntryType.LLM_REQUEST || filterType == LogEntryType.LLM_RESPONSE,
                    onClick = {
                        filterType = if (filterType == LogEntryType.LLM_REQUEST) null else LogEntryType.LLM_REQUEST
                    },
                    label = { Text("📤 LLM", fontSize = 9.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = filterType == LogEntryType.LLM_RESPONSE,
                    onClick = {
                        filterType = if (filterType == LogEntryType.LLM_RESPONSE) null else LogEntryType.LLM_RESPONSE
                    },
                    label = { Text("📥 Response", fontSize = 9.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = filterType == LogEntryType.PIPELINE_STEP,
                    onClick = {
                        filterType = if (filterType == LogEntryType.PIPELINE_STEP) null else LogEntryType.PIPELINE_STEP
                    },
                    label = { Text("⚙️ Pipeline", fontSize = 9.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = filterType == LogEntryType.EMBED_REQUEST || filterType == LogEntryType.EMBED_RESPONSE,
                    onClick = {
                        filterType = if (filterType == LogEntryType.EMBED_REQUEST) null else LogEntryType.EMBED_REQUEST
                    },
                    label = { Text("🔢 Embed", fontSize = 9.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = filterType == LogEntryType.ERROR || filterType == LogEntryType.LLM_ERROR,
                    onClick = {
                        filterType = if (filterType == LogEntryType.ERROR) null else LogEntryType.ERROR
                    },
                    label = { Text("❌ Errors", fontSize = 9.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = filterType == LogEntryType.SESSION_EVENT,
                    onClick = {
                        filterType = if (filterType == LogEntryType.SESSION_EVENT) null else LogEntryType.SESSION_EVENT
                    },
                    label = { Text("📂 Session", fontSize = 9.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = filterType == LogEntryType.COST_UPDATE,
                    onClick = {
                        filterType = if (filterType == LogEntryType.COST_UPDATE) null else LogEntryType.COST_UPDATE
                    },
                    label = { Text("💰 Cost", fontSize = 9.sp) },
                    modifier = Modifier.height(28.dp)
                )
            }

            HorizontalDivider()

            // Log entries list
            if (filteredEntries.isEmpty()) {
                Text(
                    text = if (logEntries.isEmpty()) "No log entries yet. Start chatting to see logs!"
                    else "No entries match the selected filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredEntries.forEach { entry ->
                        val isExpanded = expandedEntryId == entry.id
                        LogEntryCard(
                            entry = entry,
                            isExpanded = isExpanded,
                            onToggle = {
                                expandedEntryId = if (isExpanded) null else entry.id
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(
    entry: ChatLogEntry,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when (entry.type) {
        LogEntryType.LLM_REQUEST -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        LogEntryType.LLM_RESPONSE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        LogEntryType.LLM_ERROR, LogEntryType.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        LogEntryType.WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        LogEntryType.PIPELINE_STEP -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        LogEntryType.COST_UPDATE -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Header row: icon + time + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.icon(),
                    fontSize = 12.sp
                )
                Text(
                    text = entry.formattedTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp
                )
                if (entry.durationMs != null) {
                    Text(
                        text = "${entry.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp
                    )
                }
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded details
            if (isExpanded && entry.details.isNotBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SelectionContainer {
                    Text(
                        text = entry.details,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Extension: ChatMode display names & descriptions
// ════════════════════════════════════════════════════════════════════════════

private val ChatMode.displayName: String
    get() = when (this) {
        ChatMode.QUICK_CHAT -> "Quick Chat"
        ChatMode.DEEP_ANALYSIS -> "Deep Analysis"
        ChatMode.FOLLOW_UP -> "Follow-up"
    }

private val ChatMode.description: String
    get() = when (this) {
        ChatMode.QUICK_CHAT ->
            "⚡ Quick Chat — Direct LLM conversation. Fast responses for general questions about industries, companies, or trends. Cost: ~\$0.0002/message."
        ChatMode.DEEP_ANALYSIS ->
            "🔬 Deep Analysis — Full agentic pipeline. Fetches live news (Google RSS), SEC filings (EDGAR), builds a RAG index, runs multi-agent analysis with critique loops. Takes 30–120 sec. Cost: ~\$0.01/query."
        ChatMode.FOLLOW_UP ->
            "💬 Follow-up — Query the RAG index built by Deep Analysis. Ask follow-up questions against the fetched data without re-running the full pipeline. Requires at least one Deep Analysis first. Cost: ~\$0.001/query."
    }
