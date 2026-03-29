package com.vignesh.jobautomation.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.jobautomation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection Status
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isConnected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (uiState.isConnected) Icons.Default.Cloud else Icons.Default.CloudOff,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (uiState.isConnected) "Connected" else "Disconnected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Backend: ${uiState.backendUrl}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = { viewModel.checkConnection() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            }
        }

        // Agent Status Section
        item {
            Text(
                "Agent Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card {
                Column {
                    uiState.agentStatus.forEach { (name, status) ->
                        AgentStatusRow(
                            name = name,
                            nextRun = status?.nextRun,
                            paused = status?.paused ?: false,
                            onToggle = { viewModel.toggleAgent(name) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        // API Cost Section
        item {
            Text(
                "API Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Today's Cost", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "$${String.format("%.4f", uiState.costStats?.dailyCost ?: 0.0)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Budget", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "$${String.format("%.2f", uiState.costStats?.dailyBudget ?: 5.0)}",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Progress bar
                    val progress = ((uiState.costStats?.dailyCost ?: 0.0) / 
                                   (uiState.costStats?.dailyBudget ?: 5.0)).toFloat()
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (progress > 0.8f) MaterialTheme.colorScheme.error 
                               else MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "API Calls: ${uiState.costStats?.totalCalls ?: 0}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Remaining: $${String.format("%.4f", uiState.costStats?.budgetRemaining ?: 0.0)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Settings Section
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Card {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Link,
                        title = "Backend URL",
                        subtitle = uiState.backendUrl,
                        onClick = { /* Show edit dialog */ }
                    )
                    HorizontalDivider()
                    SettingsRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        subtitle = "Configure alerts",
                        onClick = { /* Open notifications settings */ }
                    )
                    HorizontalDivider()
                    SettingsRow(
                        icon = Icons.Default.Security,
                        title = "Auto-Apply Rules",
                        subtitle = "Min score: 60%",
                        onClick = { /* Open rules settings */ }
                    )
                }
            }
        }

        // About Section
        item {
            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Card {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "Version",
                        subtitle = "1.0.0",
                        onClick = { }
                    )
                    HorizontalDivider()
                    SettingsRow(
                        icon = Icons.Default.Code,
                        title = "GitHub",
                        subtitle = "View source code",
                        onClick = { /* Open GitHub */ }
                    )
                    HorizontalDivider()
                    SettingsRow(
                        icon = Icons.Default.Description,
                        title = "Documentation",
                        subtitle = "API & User Guide",
                        onClick = { /* Open docs */ }
                    )
                }
            }
        }
    }
}

@Composable
fun AgentStatusRow(
    name: String,
    nextRun: String?,
    paused: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (name) {
                "scout_agent" -> Icons.Default.Search
                "analyst_agent" -> Icons.Default.Analytics
                "applicant_agent" -> Icons.Default.Send
                "tracker_agent" -> Icons.Default.Email
                else -> Icons.Default.SmartToy
            },
            contentDescription = null,
            tint = if (paused) MaterialTheme.colorScheme.onSurfaceVariant 
                   else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                formatAgentName(name),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            nextRun?.let {
                Text(
                    "Next: ${it.take(19).replace("T", " ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = !paused,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatAgentName(name: String): String {
    return name.split("_")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
