package com.vignesh.jobautomation.ui.jobs

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
import com.vignesh.jobautomation.data.database.JobStatus
import com.vignesh.jobautomation.data.database.JobWithCompany
import com.vignesh.jobautomation.ui.theme.JobAutomationColors
import com.vignesh.jobautomation.viewmodel.JobsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    viewModel: JobsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showJobDetail by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Chips
        ScrollableTabRow(
            selectedTabIndex = when (uiState.filterStatus) {
                null -> 0
                JobStatus.NEW -> 1
                JobStatus.ANALYZED -> 2
                JobStatus.READY_TO_APPLY -> 3
                JobStatus.APPLIED -> 4
                else -> 0
            },
            edgePadding = 16.dp
        ) {
            Tab(selected = uiState.filterStatus == null, onClick = { viewModel.setFilter(null) }) {
                Text("All", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = uiState.filterStatus == JobStatus.NEW, onClick = { viewModel.setFilter(JobStatus.NEW) }) {
                Text("New", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = uiState.filterStatus == JobStatus.ANALYZED, onClick = { viewModel.setFilter(JobStatus.ANALYZED) }) {
                Text("Analyzed", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = uiState.filterStatus == JobStatus.READY_TO_APPLY, onClick = { viewModel.setFilter(JobStatus.READY_TO_APPLY) }) {
                Text("Ready", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = uiState.filterStatus == JobStatus.APPLIED, onClick = { viewModel.setFilter(JobStatus.APPLIED) }) {
                Text("Applied", modifier = Modifier.padding(16.dp))
            }
        }
        
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.jobs.isEmpty()) {
                EmptyJobsState(
                    modifier = Modifier.align(Alignment.Center),
                    onAddClick = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.jobs) { job ->
                        JobCard(
                            job = job,
                            isAnalyzing = uiState.isAnalyzing && uiState.selectedJob?.job?.id == job.job.id,
                            onClick = {
                                viewModel.selectJob(job)
                                showJobDetail = true
                            },
                            onAnalyze = { viewModel.analyzeJob(job.job.id) },
                            onApply = { viewModel.applyToJob(job.job.id) }
                        )
                    }
                }
            }
            
            // FAB to add job
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Job")
            }
        }
    }
    
    // Add Job Dialog
    if (showAddDialog) {
        AddJobDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, company, url, location, remoteType, description ->
                viewModel.addJobManually(title, company, url, location, remoteType, description)
                showAddDialog = false
            }
        )
    }
    
    // Job Detail Sheet
    if (showJobDetail && uiState.selectedJob != null) {
        JobDetailSheet(
            job = uiState.selectedJob!!,
            isAnalyzing = uiState.isAnalyzing,
            onDismiss = { 
                showJobDetail = false
                viewModel.selectJob(null)
            },
            onAnalyze = { viewModel.analyzeJob(uiState.selectedJob!!.job.id) },
            onApply = { viewModel.applyToJob(uiState.selectedJob!!.job.id) }
        )
    }
}

@Composable
private fun EmptyJobsState(
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.WorkOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No jobs yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "Add jobs manually to start tracking",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Job")
        }
    }
}

@Composable
fun JobCard(
    job: JobWithCompany,
    isAnalyzing: Boolean = false,
    onClick: () -> Unit,
    onAnalyze: () -> Unit,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status badge
                StatusBadge(status = job.job.status)
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.job.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = job.job.companyName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Match Score
                job.job.matchScore?.let { score ->
                    MatchScoreBadge(score = score)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Location and Remote Type
            Row(verticalAlignment = Alignment.CenterVertically) {
                job.job.location?.let { location ->
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                job.job.remoteType?.let { remote ->
                    AssistChip(
                        onClick = {},
                        label = { Text(remote, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (job.job.status == JobStatus.NEW) {
                    OutlinedButton(
                        onClick = onAnalyze,
                        enabled = !isAnalyzing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Analyze")
                    }
                }
                
                if (job.job.status == JobStatus.READY_TO_APPLY || job.job.status == JobStatus.ANALYZED) {
                    Button(
                        onClick = onApply,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark Applied")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: JobStatus) {
    val (color, text) = when (status) {
        JobStatus.NEW -> JobAutomationColors.StatusNew to "New"
        JobStatus.ANALYZED -> JobAutomationColors.StatusAnalyzed to "Analyzed"
        JobStatus.READY_TO_APPLY -> JobAutomationColors.StatusReadyToApply to "Ready"
        JobStatus.APPLYING -> Color.Yellow to "Applying"
        JobStatus.APPLIED -> JobAutomationColors.StatusApplied to "Applied"
        JobStatus.SKIPPED -> Color.Gray to "Skipped"
        JobStatus.ERROR -> Color.Red to "Error"
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MatchScoreBadge(score: Float) {
    Surface(
        color = JobAutomationColors.matchScoreColor(score).copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.TrendingUp,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = JobAutomationColors.matchScoreColor(score)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${score.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = JobAutomationColors.matchScoreColor(score)
            )
        }
    }
}

@Composable
fun AddJobDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, company: String, url: String, location: String?, remoteType: String?, description: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var remoteType by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Job Manually") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Job Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Job URL *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remoteType,
                    onValueChange = { remoteType = it },
                    label = { Text("Remote Type (remote/hybrid/onsite)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Job Description") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        title,
                        company,
                        url,
                        location.takeIf { it.isNotBlank() },
                        remoteType.takeIf { it.isNotBlank() },
                        description.takeIf { it.isNotBlank() }
                    )
                },
                enabled = title.isNotBlank() && company.isNotBlank() && url.isNotBlank()
            ) {
                Text("Add Job")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailSheet(
    job: JobWithCompany,
    isAnalyzing: Boolean,
    onDismiss: () -> Unit,
    onAnalyze: () -> Unit,
    onApply: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = job.job.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = job.job.companyName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Match Score if available
            job.job.matchScore?.let { score ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = JobAutomationColors.matchScoreColor(score).copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Match Score: ${score.toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JobAutomationColors.matchScoreColor(score)
                        )
                        job.job.matchJustification?.let { justification ->
                            Text(
                                text = justification,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Description
            job.job.description?.let { desc ->
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (job.job.status == JobStatus.NEW || job.job.status == JobStatus.ANALYZED) {
                    OutlinedButton(
                        onClick = onAnalyze,
                        enabled = !isAnalyzing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Analyze with AI")
                        }
                    }
                }
                
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mark as Applied")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
