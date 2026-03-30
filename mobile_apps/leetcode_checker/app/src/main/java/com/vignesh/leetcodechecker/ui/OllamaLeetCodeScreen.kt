package com.vignesh.leetcodechecker.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.AppSettings
import com.vignesh.leetcodechecker.R
import com.vignesh.leetcodechecker.SavedOllamaHost
import com.vignesh.leetcodechecker.data.OllamaModelInfo
import com.vignesh.leetcodechecker.llm.DownloadedModel
import com.vignesh.leetcodechecker.llm.DownloadProgress
import com.vignesh.leetcodechecker.viewmodel.OllamaUiState
import com.vignesh.leetcodechecker.viewmodel.OllamaViewModel
import kotlinx.coroutines.delay

/**
 * OllamaLeetCodeScreen — Full-featured Ollama LeetCode checker tab.
 *
 * Features:
 * 1. Fetch daily LeetCode challenge
 * 2. Generate answer via local Ollama LLM
 * 3. Model management (list, download, select)
 * 4. Connection diagnostics
 * 5. Debug/pipeline logs
 * 6. Ollama server settings
 */
@Composable
fun OllamaLeetCodeScreen(
    viewModel: OllamaViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showDebugLog by rememberSaveable { mutableStateOf(false) }
    var showDiagram by rememberSaveable { mutableStateOf(false) }
    var expandedSection by rememberSaveable { mutableStateOf("") } // "code", "explanation", "validation"

    // Auto-dismiss info message after 4 seconds
    LaunchedEffect(state.infoMessage) {
        if (state.infoMessage != null) {
            delay(4000)
            viewModel.clearInfoMessage()
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
                Text(
                    text = "Ollama LeetCode Checker",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Local LLM • Solve daily LeetCode with Ollama",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        onClick = { viewModel.refreshOllamaAnswer() },
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
                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ollama Solve", fontSize = 11.sp)
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
                        onClick = { viewModel.runDiagnostics() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("🔍 Diagnostics", fontSize = 11.sp) }

                    OutlinedButton(
                        onClick = { showModels = !showModels },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("📦 Models", fontSize = 11.sp) }

                    OutlinedButton(
                        onClick = { showDebugLog = !showDebugLog },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("📋 Logs", fontSize = 11.sp) }

                    OutlinedButton(
                        onClick = { showSettings = !showSettings },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("⚙️ Settings", fontSize = 11.sp) }

                    OutlinedButton(
                        onClick = { showDiagram = !showDiagram },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("🔗 Diagram", fontSize = 11.sp) }
                }
            }

            // ════════════════════════════════════════════════════════════
            // Info/Error Messages
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
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
            // Challenge Card
            // ════════════════════════════════════════════════════════════
            state.challenge?.let { challenge ->
                item {
                    OllamaChallengeCard(challenge)
                }
            }

            // ════════════════════════════════════════════════════════════
            // AI Response Sections (collapsible)
            // ════════════════════════════════════════════════════════════
            if (state.aiCode != null) {
                item {
                    CollapsibleSection(
                        title = "💻 Python Code",
                        isExpanded = expandedSection == "code",
                        onToggle = { expandedSection = if (expandedSection == "code") "" else "code" }
                    ) {
                        CodeBlock(state.aiCode ?: "")
                    }
                }
            }

            if (!state.aiTestcaseValidation.isNullOrBlank()) {
                item {
                    CollapsibleSection(
                        title = "✅ Testcase Validation",
                        isExpanded = expandedSection == "validation",
                        onToggle = { expandedSection = if (expandedSection == "validation") "" else "validation" }
                    ) {
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

            if (!state.aiExplanation.isNullOrBlank()) {
                item {
                    CollapsibleSection(
                        title = "📝 Explanation",
                        isExpanded = expandedSection == "explanation",
                        onToggle = { expandedSection = if (expandedSection == "explanation") "" else "explanation" }
                    ) {
                        MarkdownText(
                            text = state.aiExplanation ?: "",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // Model Manager (expandable)
            // ════════════════════════════════════════════════════════════
            if (showModels) {
                item {
                    ModelManagerSection(
                        state = state,
                        onRefreshInstalled = { viewModel.refreshInstalledModels() },
                        onRefreshCatalog = { viewModel.refreshCatalogModels() },
                        onDownload = { viewModel.downloadModel(it) },
                        onSelect = { viewModel.setPreferredModel(it) }
                    )
                }
            }

            // ════════════════════════════════════════════════════════════
            // Debug Log (expandable)
            // ════════════════════════════════════════════════════════════
            if (showDebugLog && !state.aiDebugLog.isNullOrBlank()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "📋 Pipeline Debug Log",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.aiDebugLog ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // Ollama Settings (expandable)
            // ════════════════════════════════════════════════════════════
            if (showSettings) {
                item {
                    OllamaFullSettingsSection(
                        state = state,
                        downloadedLocalModels = state.downloadedLocalModels,
                        savedHosts = state.savedHosts,
                        onSaveOllama = { url, models, backend, modelPath, contextSize, maxTokens -> 
                            viewModel.saveOllamaSettings(url, models, backend, modelPath, contextSize, maxTokens) 
                        },
                        onSaveFull = { settings -> viewModel.saveFullSettings(settings) },
                        onShowModelManager = { viewModel.showModelManager() },
                        onSelectHost = { host -> viewModel.selectSavedHost(host) },
                        onAddHost = { name, url, models -> viewModel.addSavedHost(name, url, models) },
                        onDeleteHost = { hostId -> viewModel.deleteSavedHost(hostId) }
                    )
                }
            }

            // ════════════════════════════════════════════════════════════
            // Mermaid Diagram (expandable)
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
                            OllamaZoomableFlowDiagram()
                        }
                    }
                }
            }

            // Spacing at bottom
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    
    // Model Manager Bottom Sheet
    if (state.showModelManager) {
        ModelManagerScreen(
            downloadedModels = state.downloadedLocalModels,
            selectedModelPath = state.settings.localModelPath,
            downloadProgress = state.localModelDownloadProgress,
            onDownloadModel = { modelId -> viewModel.downloadLocalModel(modelId) },
            onDeleteModel = { fileName -> viewModel.deleteLocalModel(fileName) },
            onSelectModel = { modelPath -> viewModel.selectLocalModel(modelPath) },
            onDismiss = { viewModel.hideModelManager() }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Supporting Composables
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun OllamaChallengeCard(challenge: com.vignesh.leetcodechecker.data.DailyChallengeUiModel) {
    var showFullStatement by rememberSaveable { mutableStateOf(false) }

    val difficultyColor = when (challenge.difficulty.lowercase()) {
        "easy" -> MaterialTheme.colorScheme.tertiary
        "medium" -> MaterialTheme.colorScheme.primary
        "hard" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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

            if (showFullStatement && challenge.fullStatement.isNotBlank()) {
                MarkdownText(
                    text = challenge.fullStatement,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = challenge.descriptionPreview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = { showFullStatement = !showFullStatement },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (showFullStatement) "Show Less ▲" else "View More ▼",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
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
private fun CodeBlock(code: String) {
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
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun ModelManagerSection(
    state: OllamaUiState,
    onRefreshInstalled: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onDownload: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    var downloadInput by rememberSaveable { mutableStateOf("") }
    var searchFilter by rememberSaveable { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📦 Ollama Model Manager",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Fetch installed models from your server and full model catalog from Ollama. You can download directly and track progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )

            // Current preferred
            val preferred = state.settings.ollamaPreferredModels
                .split(',').firstOrNull()?.trim() ?: "Not set"
            Text(
                text = "Active model: $preferred",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Search filter
            OutlinedTextField(
                value = searchFilter,
                onValueChange = { searchFilter = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search Model Name", fontSize = 12.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )

            // Download new model
            OutlinedTextField(
                value = downloadInput,
                onValueChange = { downloadInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Model Name To Download (e.g. qwen2.5:3b)", fontSize = 12.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )

            // Refresh buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRefreshInstalled,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isModelActionLoading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Refresh Installed", fontSize = 11.sp) }

                OutlinedButton(
                    onClick = onRefreshCatalog,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isModelActionLoading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Refresh Catalog", fontSize = 11.sp) }
            }

            // Download button
            Button(
                onClick = {
                    if (downloadInput.isNotBlank()) {
                        onDownload(downloadInput.trim())
                        downloadInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = downloadInput.isNotBlank() && !state.isModelActionLoading
            ) { Text("Download") }

            // Download progress
            if (state.modelDownloadProgress != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = state.modelDownloadProgress ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // Installed models
            val filteredInstalled = if (searchFilter.isBlank()) state.installedModels
            else state.installedModels.filter { it.name.contains(searchFilter, ignoreCase = true) }

            Text(
                text = "Installed Models (${filteredInstalled.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            if (filteredInstalled.isEmpty()) {
                Text(
                    text = "No models detected. Use Download with a model name, then refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                filteredInstalled.forEach { model ->
                    ModelRow(
                        model = model,
                        isPreferred = preferred.equals(model.name, ignoreCase = true),
                        onSelect = { onSelect(model.name) },
                        onDownload = null
                    )
                }
            }

            // Catalog models
            if (state.catalogModels.isNotEmpty()) {
                HorizontalDivider()

                val filteredCatalog = if (searchFilter.isBlank()) state.catalogModels
                else state.catalogModels.filter { it.name.contains(searchFilter, ignoreCase = true) }

                Text(
                    text = "Available Catalog Models (${filteredCatalog.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                filteredCatalog.forEach { model ->
                    ModelRow(
                        model = model,
                        isPreferred = preferred.equals(model.name, ignoreCase = true),
                        onSelect = { onSelect(model.name) },
                        onDownload = { onDownload(model.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: OllamaModelInfo,
    isPreferred: Boolean,
    onSelect: () -> Unit,
    onDownload: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isPreferred) FontWeight.Bold else FontWeight.Normal,
                color = if (isPreferred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (model.sizeBytes != null && model.sizeBytes > 0) {
                Text(
                    text = formatSize(model.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onDownload != null) {
                OutlinedButton(
                    onClick = onDownload,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) { Text("Download", fontSize = 10.sp) }
            }
            if (isPreferred) {
                Text(
                    text = "✓ Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) { Text("Use", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun OllamaFullSettingsSection(
    state: OllamaUiState,
    downloadedLocalModels: List<DownloadedModel>,
    savedHosts: List<SavedOllamaHost>,
    onSaveOllama: (String, String, String, String, Int, Int) -> Unit,
    onSaveFull: (AppSettings) -> Unit,
    onShowModelManager: () -> Unit,
    onSelectHost: (SavedOllamaHost) -> Unit,
    onAddHost: (String, String, String) -> Unit,
    onDeleteHost: (String) -> Unit
) {
    var baseUrl by rememberSaveable { mutableStateOf(state.settings.ollamaBaseUrl) }
    var preferredModels by rememberSaveable { mutableStateOf(state.settings.ollamaPreferredModels) }
    
    // Local LLM settings
    var selectedBackend by rememberSaveable { mutableStateOf(state.settings.ollamaBackend) }
    var localModelPath by rememberSaveable { mutableStateOf(state.settings.localModelPath) }
    var localContextSize by rememberSaveable { mutableStateOf(state.settings.localContextSize.toString()) }
    var localMaxTokens by rememberSaveable { mutableStateOf(state.settings.localMaxTokens.toString()) }

    // Full app settings
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
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    
    // Add host dialog state
    var showAddHostDialog by rememberSaveable { mutableStateOf(false) }
    var newHostName by rememberSaveable { mutableStateOf("") }
    var newHostUrl by rememberSaveable { mutableStateOf("") }
    var newHostModels by rememberSaveable { mutableStateOf("qwen2.5:3b") }

    // Sync when settings reload
    LaunchedEffect(state.settings) {
        baseUrl = state.settings.ollamaBaseUrl
        preferredModels = state.settings.ollamaPreferredModels
        selectedBackend = state.settings.ollamaBackend
        localModelPath = state.settings.localModelPath
        localContextSize = state.settings.localContextSize.toString()
        localMaxTokens = state.settings.localMaxTokens.toString()
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "⚙️ Settings",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            // ── Backend Selection ──
            Text("LLM Backend", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { selectedBackend = "ollama" }
                ) {
                    RadioButton(
                        selected = selectedBackend == "ollama",
                        onClick = { selectedBackend = "ollama" }
                    )
                    Text("Ollama (HTTP)", fontSize = 12.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { selectedBackend = "local" }
                ) {
                    RadioButton(
                        selected = selectedBackend == "local",
                        onClick = { selectedBackend = "local" }
                    )
                    Text("Local (llama.cpp)", fontSize = 12.sp)
                }
            }
            
            // ── Ollama Settings (shown when ollama backend selected) ──
            if (selectedBackend == "ollama") {
                Text("Ollama Connection", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                // ── Saved Hosts Section ──
                if (savedHosts.isNotEmpty()) {
                    Text(
                        text = "Saved Hosts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    savedHosts.forEach { host ->
                        val isSelected = host.url == state.settings.ollamaBaseUrl
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    onSelectHost(host)
                                    baseUrl = host.url
                                    preferredModels = host.preferredModels
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelected) {
                                            Text(
                                                text = "✓ ",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text(
                                            text = host.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Text(
                                        text = host.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Delete button (only if not the only host)
                                if (savedHosts.size > 1) {
                                    IconButton(
                                        onClick = { onDeleteHost(host.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("🗑️", fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    // Add new host button
                    OutlinedButton(
                        onClick = { showAddHostDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("➕ Add New Host", fontSize = 12.sp)
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                OutlinedTextField(
                    value = preferredModels,
                    onValueChange = { preferredModels = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Preferred Models CSV") },
                    placeholder = { Text("qwen2.5:3b,llama3.2:3b") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ollama Base URL") },
                    placeholder = { Text("http://127.0.0.1:11434") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                Text(
                    text = "Tip: If Ollama runs on your PC, use 'adb reverse tcp:11434 tcp:11434' or set URL to your PC's IP.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // ── Add Host Dialog ──
            if (showAddHostDialog) {
                AlertDialog(
                    onDismissRequest = { showAddHostDialog = false },
                    title = { Text("Add New Host") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newHostName,
                                onValueChange = { newHostName = it },
                                label = { Text("Host Name") },
                                placeholder = { Text("e.g., Home PC, Mobile, Office") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newHostUrl,
                                onValueChange = { newHostUrl = it },
                                label = { Text("Ollama URL") },
                                placeholder = { Text("http://192.168.1.100:11434") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newHostModels,
                                onValueChange = { newHostModels = it },
                                label = { Text("Preferred Models (optional)") },
                                placeholder = { Text("qwen2.5:3b") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onAddHost(newHostName, newHostUrl, newHostModels)
                                showAddHostDialog = false
                                newHostName = ""
                                newHostUrl = ""
                                newHostModels = "qwen2.5:3b"
                            },
                            enabled = newHostName.isNotBlank() && newHostUrl.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddHostDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            // ── Local LLM Settings (shown when local backend selected) ──
            if (selectedBackend == "local") {
                Text("Local LLM (llama.cpp)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                
                // Model Manager Button
                FilledTonalButton(
                    onClick = onShowModelManager,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⬇️", modifier = Modifier.padding(end = 4.dp))
                    Text("Download & Manage Models")
                }
                
                // Show selected model info
                if (downloadedLocalModels.isNotEmpty()) {
                    val selectedModel = downloadedLocalModels.firstOrNull { 
                        localModelPath.contains(it.file.name) 
                    }
                    if (selectedModel != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✓ Selected: ${selectedModel.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = selectedModel.sizeFormatted,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (localModelPath.isBlank()) {
                    Text(
                        text = "No model selected. Tap above to download models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                // Manual path input (advanced)
                OutlinedTextField(
                    value = localModelPath,
                    onValueChange = { localModelPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model Path (auto-set or manual)") },
                    placeholder = { Text("/data/.../models/qwen2.5-0.5b.gguf") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = localContextSize,
                        onValueChange = { localContextSize = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Context Size") },
                        placeholder = { Text("2048") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = localMaxTokens,
                        onValueChange = { localMaxTokens = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Max Tokens") },
                        placeholder = { Text("512") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                }
                
                Text(
                    text = "Download models directly or place a GGUF file manually. Models are stored in app data.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { 
                    onSaveOllama(
                        baseUrl, 
                        preferredModels, 
                        selectedBackend,
                        localModelPath,
                        localContextSize.toIntOrNull() ?: 2048,
                        localMaxTokens.toIntOrNull() ?: 512
                    ) 
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save LLM Settings") }

            HorizontalDivider()

            // ── LLM Configuration ──
            Text("LLM Configuration", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = settingsModelsCsv, onValueChange = { settingsModelsCsv = it },
                label = { Text("Preferred Models CSV (Gemini)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = settingsMaxRetries, onValueChange = { settingsMaxRetries = it },
                    label = { Text("Max Model Retries") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                OutlinedTextField(
                    value = settingsTimeoutMinutes, onValueChange = { settingsTimeoutMinutes = it },
                    label = { Text("Network Timeout Minutes") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = settingsMaxInput, onValueChange = { settingsMaxInput = it },
                    label = { Text("Max Input Tokens") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                OutlinedTextField(
                    value = settingsMaxOutput, onValueChange = { settingsMaxOutput = it },
                    label = { Text("Max Output Tokens") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }
            OutlinedTextField(
                value = settingsThinkingDivisor, onValueChange = { settingsThinkingDivisor = it },
                label = { Text("Thinking Budget Divisor") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )

            HorizontalDivider()

            // ── Reminder Settings ──
            Text("Reminder Settings", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = settingsReminderStart, onValueChange = { settingsReminderStart = it },
                label = { Text("Reminder Start Hour IST") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            OutlinedTextField(
                value = settingsReminderEnd, onValueChange = { settingsReminderEnd = it },
                label = { Text("Reminder End Hour IST") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            OutlinedTextField(
                value = settingsReminderInterval, onValueChange = { settingsReminderInterval = it },
                label = { Text("Reminder Interval Hours") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )

            HorizontalDivider()

            // ── GitHub Settings ──
            Text("GitHub Settings", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = settingsGithubOwner, onValueChange = { settingsGithubOwner = it },
                label = { Text("GitHub Owner Override") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            OutlinedTextField(
                value = settingsGithubRepo, onValueChange = { settingsGithubRepo = it },
                label = { Text("GitHub Repo Override") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            OutlinedTextField(
                value = settingsGithubBranch, onValueChange = { settingsGithubBranch = it },
                label = { Text("GitHub Branch Override") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )

            Button(
                onClick = { showPasswordDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🔐 Save All Settings") }
        }
    }

    if (showPasswordDialog) {
        val expectedPassword = com.vignesh.leetcodechecker.BuildConfig.SETTINGS_UPDATE_PASSWORD.ifBlank { "1234" }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false; passwordInput = "" },
            title = { Text("Settings Password") },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Enter password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (passwordInput == expectedPassword) {
                        showPasswordDialog = false
                        passwordInput = ""
                        onSaveFull(
                            state.settings.copy(
                                preferredModelsCsv = settingsModelsCsv,
                                maxModelRetries = settingsMaxRetries.toIntOrNull() ?: state.settings.maxModelRetries,
                                maxInputTokens = settingsMaxInput.toIntOrNull() ?: state.settings.maxInputTokens,
                                maxOutputTokens = settingsMaxOutput.toIntOrNull() ?: state.settings.maxOutputTokens,
                                thinkingBudgetDivisor = settingsThinkingDivisor.toIntOrNull() ?: state.settings.thinkingBudgetDivisor,
                                networkTimeoutMinutes = settingsTimeoutMinutes.toIntOrNull() ?: state.settings.networkTimeoutMinutes,
                                reminderStartHourIst = settingsReminderStart.toIntOrNull() ?: state.settings.reminderStartHourIst,
                                reminderEndHourIst = settingsReminderEnd.toIntOrNull() ?: state.settings.reminderEndHourIst,
                                reminderIntervalHours = settingsReminderInterval.toIntOrNull() ?: state.settings.reminderIntervalHours,
                                githubOwnerOverride = settingsGithubOwner,
                                githubRepoOverride = settingsGithubRepo,
                                githubBranchOverride = settingsGithubBranch
                            )
                        )
                    }
                }) { Text("Unlock & Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPasswordDialog = false; passwordInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun OllamaZoomableFlowDiagram() {
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

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
