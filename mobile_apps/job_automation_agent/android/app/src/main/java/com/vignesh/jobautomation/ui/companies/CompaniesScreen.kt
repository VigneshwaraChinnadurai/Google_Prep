package com.vignesh.jobautomation.ui.companies

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.jobautomation.data.models.Company
import com.vignesh.jobautomation.viewmodel.CompaniesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompaniesScreen(
    viewModel: CompaniesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPreference by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadCompanies()
    }

    Scaffold(
        floatingActionButton = {
            Column {
                // Seed defaults button
                SmallFloatingActionButton(
                    onClick = { viewModel.seedDefaults() },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, "Seed Defaults")
                }
                // Add company button
                FloatingActionButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Default.Add, "Add Company")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPreference == null,
                    onClick = { selectedPreference = null; viewModel.loadCompanies(null) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = selectedPreference == "PRIORITY",
                    onClick = { selectedPreference = "PRIORITY"; viewModel.loadCompanies("PRIORITY") },
                    label = { Text("Priority") },
                    leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedPreference == "ALLOWED",
                    onClick = { selectedPreference = "ALLOWED"; viewModel.loadCompanies("ALLOWED") },
                    label = { Text("Allowed") },
                    leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedPreference == "BLOCKED",
                    onClick = { selectedPreference = "BLOCKED"; viewModel.loadCompanies("BLOCKED") },
                    label = { Text("Blocked") },
                    leadingIcon = { Icon(Icons.Default.Block, null, Modifier.size(16.dp)) }
                )
            }

            // Companies List
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.companies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No companies yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.seedDefaults() }) {
                            Text("Add Default Companies")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.companies) { company ->
                        CompanyCard(
                            company = company,
                            onPreferenceChange = { pref ->
                                viewModel.updatePreference(company.id, pref)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Company Dialog
    if (showAddDialog) {
        AddCompanyDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, type, careersPage ->
                viewModel.addCompany(name, type, careersPage)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun CompanyCard(
    company: Company,
    onPreferenceChange: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Company Icon/Badge
            Surface(
                color = when (company.companyType) {
                    "MAANG", "FAANG" -> Color(0xFF4285F4).copy(alpha = 0.1f)
                    "PRODUCT" -> Color(0xFF34A853).copy(alpha = 0.1f)
                    "STARTUP" -> Color(0xFFFBBC04).copy(alpha = 0.1f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = company.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (company.companyType) {
                            "MAANG", "FAANG" -> Color(0xFF4285F4)
                            "PRODUCT" -> Color(0xFF34A853)
                            "STARTUP" -> Color(0xFFFBBC04)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Company Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = company.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (company.preference == "PRIORITY") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Priority",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    company.companyType?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    company.jobCount?.let { count ->
                        if (count > 0) {
                            Text(
                                text = " • $count jobs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Preference Badge
                PreferenceBadge(preference = company.preference ?: "NEUTRAL")
            }

            // More Options
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Set as Priority") },
                        onClick = { onPreferenceChange("PRIORITY"); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Star, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Allow") },
                        onClick = { onPreferenceChange("ALLOWED"); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Check, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Block") },
                        onClick = { onPreferenceChange("BLOCKED"); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Block, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun PreferenceBadge(preference: String) {
    val (backgroundColor, textColor, icon) = when (preference) {
        "PRIORITY" -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), Icons.Default.Star)
        "ALLOWED" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.Check)
        "BLOCKED" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.Block)
        else -> Triple(Color(0xFFF5F5F5), Color(0xFF757575), Icons.Default.Remove)
    }

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(12.dp), tint = textColor)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = preference,
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCompanyDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, type: String, careersPage: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("PRODUCT") }
    var careersPage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Company") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Company Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Type dropdown would go here - simplified for now
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type (MAANG, PRODUCT, STARTUP, etc.)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = careersPage,
                    onValueChange = { careersPage = it },
                    label = { Text("Careers Page URL (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, type, careersPage.ifBlank { null }) },
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
