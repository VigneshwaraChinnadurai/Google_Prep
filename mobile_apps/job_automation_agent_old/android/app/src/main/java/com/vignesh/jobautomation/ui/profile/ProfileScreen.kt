package com.vignesh.jobautomation.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.jobautomation.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (uiState.profile == null) {
        // No profile - show setup
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Set Up Your Profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Your profile helps the agent find and apply to matching jobs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showEditDialog = true }
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Profile")
                }
            }
        }
    } else {
        // Show profile
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(60.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = uiState.profile?.fullName?.take(2)?.uppercase() ?: "??",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.profile?.fullName ?: "Name",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = uiState.profile?.email ?: "email@example.com",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Default.Edit, "Edit profile")
                            }
                        }
                        
                        uiState.profile?.location?.let {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Summary
            uiState.profile?.summary?.let { summary ->
                item {
                    SectionCard(title = "Professional Summary") {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Skills
            uiState.profile?.skills?.let { skills ->
                if (skills.isNotEmpty()) {
                    item {
                        SectionCard(title = "Skills") {
                            skills.forEach { (category, skillList) ->
                                if (skillList.isNotEmpty()) {
                                    Text(
                                        text = category.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        items(skillList) { skill ->
                                            SuggestionChip(
                                                onClick = { },
                                                label = { Text(skill) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Experience
            uiState.profile?.experience?.let { experience ->
                if (experience.isNotEmpty()) {
                    item {
                        SectionCard(title = "Experience") {
                            experience.forEachIndexed { index, exp ->
                                if (index > 0) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                }
                                Text(
                                    text = exp.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = exp.company,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row {
                                    Text(
                                        text = "${exp.startDate?.take(7) ?: "?"} - ${exp.endDate?.take(7) ?: "Present"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                exp.description?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = it.take(200) + if (it.length > 200) "..." else "",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Target Preferences
            item {
                SectionCard(title = "Target Preferences") {
                    uiState.profile?.desiredRoles?.let { roles ->
                        if (roles.isNotEmpty()) {
                            Text(
                                "Target Roles",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                items(roles) { role ->
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(role) },
                                        leadingIcon = { Icon(Icons.Default.Work, null, Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }

                    uiState.profile?.desiredLocations?.let { locations ->
                        if (locations.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Preferred Locations",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                items(locations) { location ->
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(location) },
                                        leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }

                    // Salary expectations
                    val minSalary = uiState.profile?.minSalary
                    val maxSalary = uiState.profile?.maxSalary
                    if (minSalary != null || maxSalary != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Salary Expectations",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "\$${minSalary?.let { "%,d".format(it) } ?: "?"} - \$${maxSalary?.let { "%,d".format(it) } ?: "?"}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // Edit Dialog (simplified)
    if (showEditDialog) {
        // In a real app, this would be a full edit screen
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Text("Full profile editing would open here. For now, profiles should be imported via the backend API.")
            },
            confirmButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}
