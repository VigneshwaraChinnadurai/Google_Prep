package com.vignesh.jobautomation.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vignesh.jobautomation.BuildConfig

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        // API Configuration
        item {
            SettingsSectionCard(title = "API Configuration") {
                SettingsItem(
                    icon = Icons.Default.Key,
                    title = "Gemini API Key",
                    subtitle = if (BuildConfig.GEMINI_API_KEY.isNotBlank()) 
                        "Configured via local.properties" else "Not configured",
                    onClick = { showApiKeyDialog = true }
                )
            }
        }
        
        // Job Search Settings
        item {
            SettingsSectionCard(title = "Job Search") {
                var minMatchScore by remember { mutableStateOf(70f) }
                
                Column {
                    Text(
                        text = "Minimum Match Score for Auto-Apply: ${minMatchScore.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = minMatchScore,
                        onValueChange = { minMatchScore = it },
                        valueRange = 50f..95f,
                        steps = 8
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                var autoAnalyze by remember { mutableStateOf(true) }
                SettingsSwitchItem(
                    icon = Icons.Default.AutoAwesome,
                    title = "Auto-Analyze New Jobs",
                    subtitle = "Automatically analyze jobs when added",
                    checked = autoAnalyze,
                    onCheckedChange = { autoAnalyze = it }
                )
            }
        }
        
        // Notifications
        item {
            SettingsSectionCard(title = "Notifications") {
                var notifyHighMatch by remember { mutableStateOf(true) }
                SettingsSwitchItem(
                    icon = Icons.Default.NotificationsActive,
                    title = "High Match Alerts",
                    subtitle = "Notify when jobs score 80%+",
                    checked = notifyHighMatch,
                    onCheckedChange = { notifyHighMatch = it }
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                var notifyInterview by remember { mutableStateOf(true) }
                SettingsSwitchItem(
                    icon = Icons.Default.Event,
                    title = "Interview Reminders",
                    subtitle = "Remind before scheduled interviews",
                    checked = notifyInterview,
                    onCheckedChange = { notifyInterview = it }
                )
            }
        }
        
        // Data Management
        item {
            SettingsSectionCard(title = "Data Management") {
                SettingsItem(
                    icon = Icons.Default.Download,
                    title = "Export Data",
                    subtitle = "Export jobs and applications to JSON",
                    onClick = { /* TODO: Implement export */ }
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                SettingsItem(
                    icon = Icons.Default.Upload,
                    title = "Import Profile",
                    subtitle = "Import profile from JSON",
                    onClick = { /* TODO: Implement import */ }
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Clear All Data",
                    subtitle = "Delete all local data",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { /* TODO: Implement with confirmation */ }
                )
            }
        }
        
        // About
        item {
            SettingsSectionCard(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0",
                    onClick = { showAboutDialog = true }
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                SettingsItem(
                    icon = Icons.Default.Code,
                    title = "Open Source Licenses",
                    subtitle = "View third-party licenses",
                    onClick = { /* TODO: Show licenses */ }
                )
            }
        }
        
        // Footer
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Job Automation Agent v1.0.0\nPowered by Google Gemini",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    
    // API Key Dialog
    if (showApiKeyDialog) {
        ApiKeyInfoDialog(onDismiss = { showApiKeyDialog = false })
    }
    
    // About Dialog
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ApiKeyInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini API Key") },
        text = {
            Column {
                Text("To configure your Gemini API key:")
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Create a file named local.properties in your project root")
                Text("2. Add: GEMINI_API_KEY=your_api_key_here")
                Text("3. Rebuild the app")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Get your API key from: ai.google.dev",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Job Automation Agent") },
        text = {
            Column {
                Text("Version 1.0.0")
                Spacer(modifier = Modifier.height(8.dp))
                Text("A standalone Android app for automating and managing your job search process.")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Features:")
                Text("â€¢ AI-powered job matching")
                Text("â€¢ Resume customization")
                Text("â€¢ Interview preparation")
                Text("â€¢ Application tracking")
                Text("â€¢ Company management")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Powered by Google Gemini AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
