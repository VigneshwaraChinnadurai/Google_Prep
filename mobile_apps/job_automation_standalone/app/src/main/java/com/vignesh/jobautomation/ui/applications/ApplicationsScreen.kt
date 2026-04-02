package com.vignesh.jobautomation.ui.applications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.vignesh.jobautomation.data.database.ApplicationStatus
import com.vignesh.jobautomation.data.database.ApplicationWithJob
import com.vignesh.jobautomation.ui.theme.JobAutomationColors
import com.vignesh.jobautomation.viewmodel.ApplicationsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationsScreen(
    viewModel: ApplicationsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showStatusUpdateDialog by remember { mutableStateOf(false) }
    var showInterviewPrepDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Tabs
        ScrollableTabRow(
            selectedTabIndex = when (uiState.filterStatus) {
                null -> 0
                ApplicationStatus.SUBMITTED -> 1
                ApplicationStatus.INTERVIEW_SCHEDULED -> 2
                ApplicationStatus.OFFER_RECEIVED -> 3
                ApplicationStatus.REJECTED -> 4
                else -> 0
            },
            edgePadding = 16.dp
        ) {
            Tab(selected = uiState.filterStatus == null, onClick = { viewModel.setFilter(null) }) {
                Text("All", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = uiState.filterStatus == ApplicationStatus.SUBMITTED, onClick = { viewModel.setFilter(ApplicationStatus.SUBMITTED) }) {
                Text("Submitted", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = uiState.filterStatus == ApplicationStatus.INTERVIEW_SCHEDULED, onClick = { viewModel.setFilter(ApplicationStatus.INTERVIEW_SCHEDULED) }) {
                Text("Interviewing", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = uiState.filterStatus == ApplicationStatus.OFFER_RECEIVED, onClick = { viewModel.setFilter(ApplicationStatus.OFFER_RECEIVED) }) {
                Text("Offers", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = uiState.filterStatus == ApplicationStatus.REJECTED, onClick = { viewModel.setFilter(ApplicationStatus.REJECTED) }) {
                Text("Rejected", modifier = Modifier.padding(16.dp))
            }
        }
        
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.applications.isEmpty()) {
                EmptyApplicationsState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.applications) { app ->
                        ApplicationCard(
                            application = app,
                            onClick = {
                                viewModel.selectApplication(app)
                                showStatusUpdateDialog = true
                            },
                            onGeneratePrep = {
                                viewModel.selectApplication(app)
                                viewModel.generateInterviewPrep(app.application.id)
                                showInterviewPrepDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Status Update Dialog
    if (showStatusUpdateDialog && uiState.selectedApplication != null) {
        StatusUpdateDialog(
            application = uiState.selectedApplication!!,
            onDismiss = { 
                showStatusUpdateDialog = false
                viewModel.selectApplication(null)
            },
            onStatusUpdate = { status ->
                viewModel.updateStatus(uiState.selectedApplication!!.application.id, status)
                showStatusUpdateDialog = false
            }
        )
    }
    
    // Interview Prep Dialog
    if (showInterviewPrepDialog) {
        InterviewPrepDialog(
            isLoading = uiState.isGeneratingPrep,
            prepContent = uiState.interviewPrep,
            onDismiss = {
                showInterviewPrepDialog = false
                viewModel.selectApplication(null)
            }
        )
    }
}

@Composable
private fun EmptyApplicationsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Assignment,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No applications yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "Apply to jobs to start tracking your applications",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun ApplicationCard(
    application: ApplicationWithJob,
    onClick: () -> Unit,
    onGeneratePrep: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ApplicationStatusBadge(status = application.application.status)
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = application.job.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = application.job.companyName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Applied ${dateFormat.format(Date(application.application.appliedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // Interview Prep button for relevant statuses
                if (application.application.status == ApplicationStatus.INTERVIEW_SCHEDULED ||
                    application.application.status == ApplicationStatus.SUBMITTED) {
                    TextButton(onClick = onGeneratePrep) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prep", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ApplicationStatusBadge(status: ApplicationStatus) {
    val (color, text) = when (status) {
        ApplicationStatus.SUBMITTED -> JobAutomationColors.StatusApplied to "Submitted"
        ApplicationStatus.CONFIRMED -> Color(0xFF4CAF50) to "Confirmed"
        ApplicationStatus.UNDER_REVIEW -> Color(0xFF9C27B0) to "Under Review"
        ApplicationStatus.INTERVIEW_SCHEDULED -> JobAutomationColors.StatusInterviewing to "Interview"
        ApplicationStatus.INTERVIEWED -> Color(0xFFFF9800) to "Interviewed"
        ApplicationStatus.OFFER_RECEIVED -> JobAutomationColors.StatusOffer to "Offer!"
        ApplicationStatus.REJECTED -> JobAutomationColors.StatusRejected to "Rejected"
        ApplicationStatus.WITHDRAWN -> Color.Gray to "Withdrawn"
        ApplicationStatus.NO_RESPONSE -> Color.Gray to "No Response"
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusUpdateDialog(
    application: ApplicationWithJob,
    onDismiss: () -> Unit,
    onStatusUpdate: (ApplicationStatus) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Application Status") },
        text = {
            Column {
                Text(
                    text = "${application.job.title} at ${application.job.companyName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Select new status:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                ApplicationStatus.entries.forEach { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStatusUpdate(status) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = application.application.status == status,
                            onClick = { onStatusUpdate(status) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ApplicationStatusBadge(status = status)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun InterviewPrepDialog(
    isLoading: Boolean,
    prepContent: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Interview Preparation") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Generating interview prep with AI...")
                    }
                } else if (prepContent != null) {
                    LazyColumn {
                        item {
                            Text(
                                text = prepContent,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    Text("Failed to generate interview prep. Please try again.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {}
    )
}
