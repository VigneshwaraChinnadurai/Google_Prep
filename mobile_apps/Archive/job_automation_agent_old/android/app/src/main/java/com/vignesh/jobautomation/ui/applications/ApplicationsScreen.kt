package com.vignesh.jobautomation.ui.applications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.jobautomation.data.models.ApplicationWithJob
import com.vignesh.jobautomation.viewmodel.ApplicationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationsScreen(
    viewModel: ApplicationsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadApplications()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Stats Row
        uiState.stats?.let { stats ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip("Total", stats.total, MaterialTheme.colorScheme.primary)
                StatChip("Active", stats.active, Color(0xFF4CAF50))
                StatChip("Interviews", stats.byStatus["INTERVIEW_SCHEDULED"] ?: 0, Color(0xFF2196F3))
                StatChip("Rejected", stats.rejected, Color(0xFFF44336))
            }
        }

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedStatus == null,
                onClick = { selectedStatus = null; viewModel.loadApplications(null) },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedStatus == "SUBMITTED",
                onClick = { selectedStatus = "SUBMITTED"; viewModel.loadApplications("SUBMITTED") },
                label = { Text("Submitted") }
            )
            FilterChip(
                selected = selectedStatus == "INTERVIEW_SCHEDULED",
                onClick = { selectedStatus = "INTERVIEW_SCHEDULED"; viewModel.loadApplications("INTERVIEW_SCHEDULED") },
                label = { Text("Interviews") }
            )
            FilterChip(
                selected = selectedStatus == "REJECTED",
                onClick = { selectedStatus = "REJECTED"; viewModel.loadApplications("REJECTED") },
                label = { Text("Rejected") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Applications List
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.applications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No applications yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Start by applying to some jobs!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.applications) { application ->
                    ApplicationCard(
                        application = application,
                        onClick = { viewModel.selectApplication(application.id) }
                    )
                }
            }
        }
    }

    // Application Detail Dialog
    uiState.selectedApplication?.let { detail ->
        AlertDialog(
            onDismissRequest = { viewModel.clearSelection() },
            title = { Text(detail.job?.title ?: "Application Details") },
            text = {
                Column {
                    detail.job?.companyName?.let {
                        Text("Company: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Status: ${detail.status}", style = MaterialTheme.typography.bodyMedium)
                    detail.appliedAt?.let {
                        Text("Applied: ${it.take(10)}", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Status History
                    detail.statusHistory?.let { history ->
                        Text("History:", style = MaterialTheme.typography.labelLarge)
                        history.forEach { entry ->
                            Text(
                                "• ${entry.status} (${entry.timestamp.take(10)})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Interview Prep Button
                    if (detail.status == "INTERVIEW_SCHEDULED") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.generateInterviewPrep(detail.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.School, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Interview Prep")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSelection() }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun StatChip(label: String, value: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
fun ApplicationCard(
    application: ApplicationWithJob,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon
            Icon(
                imageVector = when (application.status) {
                    "SUBMITTED" -> Icons.Default.HourglassEmpty
                    "CONFIRMED" -> Icons.Default.CheckCircle
                    "INTERVIEW_SCHEDULED" -> Icons.Default.Event
                    "OFFER_RECEIVED" -> Icons.Default.Celebration
                    "REJECTED" -> Icons.Default.Cancel
                    else -> Icons.Default.Assignment
                },
                contentDescription = null,
                tint = when (application.status) {
                    "SUBMITTED" -> Color(0xFFFFA726)
                    "CONFIRMED" -> Color(0xFF66BB6A)
                    "INTERVIEW_SCHEDULED" -> Color(0xFF42A5F5)
                    "OFFER_RECEIVED" -> Color(0xFF4CAF50)
                    "REJECTED" -> Color(0xFFEF5350)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Job Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = application.job?.title ?: "Unknown Position",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = application.job?.companyName ?: "Unknown Company",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    StatusBadge(status = application.status)
                    application.job?.matchScore?.let { score ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${score.toInt()}% match",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Arrow
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (backgroundColor, textColor) = when (status) {
        "SUBMITTED" -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        "CONFIRMED" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        "UNDER_REVIEW" -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        "INTERVIEW_SCHEDULED" -> Color(0xFFE1F5FE) to Color(0xFF0277BD)
        "INTERVIEWED" -> Color(0xFFF3E5F5) to Color(0xFF7B1FA2)
        "OFFER_RECEIVED" -> Color(0xFFE8F5E9) to Color(0xFF1B5E20)
        "REJECTED" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        "WITHDRAWN" -> Color(0xFFECEFF1) to Color(0xFF546E7A)
        else -> Color(0xFFF5F5F5) to Color(0xFF757575)
    }

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = status.replace("_", " "),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
