package com.vignesh.jobautomation.ui.companies

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.jobautomation.data.database.CompanyEntity
import com.vignesh.jobautomation.data.database.CompanyPreference
import com.vignesh.jobautomation.viewmodel.CompaniesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompaniesScreen(
    viewModel: CompaniesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Tabs
        ScrollableTabRow(
            selectedTabIndex = when (uiState.filterPreference) {
                null -> 0
                CompanyPreference.PRIORITY -> 1
                CompanyPreference.ALLOWED -> 2
                CompanyPreference.BLOCKED -> 3
                CompanyPreference.NEUTRAL -> 4
            },
            edgePadding = 16.dp
        ) {
            Tab(selected = uiState.filterPreference == null, onClick = { viewModel.setFilter(null) }) {
                Text("All", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = uiState.filterPreference == CompanyPreference.PRIORITY, onClick = { viewModel.setFilter(CompanyPreference.PRIORITY) }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFFD700))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Priority")
                }
            }
            Tab(selected = uiState.filterPreference == CompanyPreference.ALLOWED, onClick = { viewModel.setFilter(CompanyPreference.ALLOWED) }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Allowed")
                }
            }
            Tab(selected = uiState.filterPreference == CompanyPreference.BLOCKED, onClick = { viewModel.setFilter(CompanyPreference.BLOCKED) }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFE91E63))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Blocked")
                }
            }
            Tab(selected = uiState.filterPreference == CompanyPreference.NEUTRAL, onClick = { viewModel.setFilter(CompanyPreference.NEUTRAL) }) {
                Text("Neutral", modifier = Modifier.padding(16.dp))
            }
        }
        
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.companies.isEmpty()) {
                EmptyCompaniesState(
                    modifier = Modifier.align(Alignment.Center),
                    onAddClick = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.companies) { company ->
                        CompanyCard(
                            company = company,
                            onPreferenceChange = { preference ->
                                viewModel.updatePreference(company.id, preference)
                            }
                        )
                    }
                }
            }
            
            // FAB to add company
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Company")
            }
        }
    }
    
    // Add Company Dialog
    if (showAddDialog) {
        AddCompanyDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, website, careersUrl, preference ->
                viewModel.addCompany(name, website, careersUrl, preference)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun EmptyCompaniesState(
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Business,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No companies added",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "Add target companies to manage your job search",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Company")
        }
    }
}

@Composable
fun CompanyCard(
    company: CompanyEntity,
    onPreferenceChange: (CompanyPreference) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preference Icon
            PreferenceIcon(preference = company.preference)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                company.industry?.let { industry ->
                    Text(
                        text = industry,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                company.website?.let { website ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = website.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Preference Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    CompanyPreference.entries.forEach { pref ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PreferenceIcon(preference = pref, size = 20)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(pref.name.lowercase().replaceFirstChar { it.uppercase() })
                                }
                            },
                            onClick = {
                                onPreferenceChange(pref)
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PreferenceIcon(preference: CompanyPreference, size: Int = 24) {
    val (icon, color) = when (preference) {
        CompanyPreference.PRIORITY -> Icons.Default.Star to Color(0xFFFFD700)
        CompanyPreference.ALLOWED -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        CompanyPreference.BLOCKED -> Icons.Default.Block to Color(0xFFE91E63)
        CompanyPreference.NEUTRAL -> Icons.Default.Circle to Color.Gray
    }
    
    Icon(
        icon,
        contentDescription = preference.name,
        modifier = Modifier.size(size.dp),
        tint = color
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCompanyDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, website: String?, careersUrl: String?, preference: CompanyPreference) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var careersUrl by remember { mutableStateOf("") }
    var preference by remember { mutableStateOf(CompanyPreference.NEUTRAL) }
    var expanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Company") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Company Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = careersUrl,
                    onValueChange = { careersUrl = it },
                    label = { Text("Careers Page URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = preference.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Preference") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        leadingIcon = { PreferenceIcon(preference, 20) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        CompanyPreference.entries.forEach { pref ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PreferenceIcon(preference = pref, size = 20)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(pref.name.lowercase().replaceFirstChar { it.uppercase() })
                                    }
                                },
                                onClick = {
                                    preference = pref
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        name,
                        website.takeIf { it.isNotBlank() },
                        careersUrl.takeIf { it.isNotBlank() },
                        preference
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
