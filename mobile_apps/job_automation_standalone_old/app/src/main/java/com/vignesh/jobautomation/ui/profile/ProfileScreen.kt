package com.vignesh.jobautomation.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.jobautomation.data.database.ProfileEntity
import com.vignesh.jobautomation.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var isEditing by remember { mutableStateOf(false) }
    var showSkillsEditor by remember { mutableStateOf(false) }
    
    // Form states
    var fullName by remember(uiState.profile) { mutableStateOf(uiState.profile?.fullName ?: "") }
    var email by remember(uiState.profile) { mutableStateOf(uiState.profile?.email ?: "") }
    var phone by remember(uiState.profile) { mutableStateOf(uiState.profile?.phone ?: "") }
    var location by remember(uiState.profile) { mutableStateOf(uiState.profile?.location ?: "") }
    var linkedinUrl by remember(uiState.profile) { mutableStateOf(uiState.profile?.linkedinUrl ?: "") }
    var githubUrl by remember(uiState.profile) { mutableStateOf(uiState.profile?.githubUrl ?: "") }
    var summary by remember(uiState.profile) { mutableStateOf(uiState.profile?.summary ?: "") }
    var desiredRoles by remember(uiState.profile) { mutableStateOf(uiState.profile?.desiredRoles?.joinToString(", ") ?: "") }
    var desiredLocations by remember(uiState.profile) { mutableStateOf(uiState.profile?.desiredLocations?.joinToString(", ") ?: "") }
    var minSalary by remember(uiState.profile) { mutableStateOf(uiState.profile?.minSalary?.toString() ?: "") }
    
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            isEditing = false
        }
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.profile == null) "Create Profile" else "Your Profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                if (uiState.profile != null && !isEditing) {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            }
        }
        
        if (uiState.isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            // Basic Info Section
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Basic Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Full Name *") },
                            enabled = isEditing || uiState.profile == null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email *") },
                            enabled = isEditing || uiState.profile == null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone") },
                            enabled = isEditing || uiState.profile == null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Location") },
                            enabled = isEditing || uiState.profile == null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Links Section
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Professional Links",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = linkedinUrl,
                            onValueChange = { linkedinUrl = it },
                            label = { Text("LinkedIn URL") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            enabled = isEditing || uiState.profile == null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = githubUrl,
                            onValueChange = { githubUrl = it },
                            label = { Text("GitHub URL") },
                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                            enabled = isEditing || uiState.profile == null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Summary Section
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Professional Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = summary,
                            onValueChange = { summary = it },
                            label = { Text("Summary") },
                            enabled = isEditing || uiState.profile == null,
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Skills Section
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    onClick = { showSkillsEditor = true }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Skills",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (uiState.skills.isNotEmpty()) {
                            uiState.skills.forEach { (category, skillList) ->
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    skillList.forEach { skill ->
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(skill, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        } else {
                            Text(
                                text = "Tap to add skills",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            
            // Preferences Section
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Job Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = desiredRoles,
                            onValueChange = { desiredRoles = it },
                            label = { Text("Desired Roles (comma-separated)") },
                            placeholder = { Text("e.g., Data Scientist, ML Engineer") },
                            enabled = isEditing || uiState.profile == null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = desiredLocations,
                            onValueChange = { desiredLocations = it },
                            label = { Text("Desired Locations (comma-separated)") },
                            placeholder = { Text("e.g., San Francisco, Remote") },
                            enabled = isEditing || uiState.profile == null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = minSalary,
                            onValueChange = { minSalary = it.filter { c -> c.isDigit() } },
                            label = { Text("Minimum Salary (USD)") },
                            enabled = isEditing || uiState.profile == null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Save Button
            if (isEditing || uiState.profile == null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isEditing) {
                            OutlinedButton(
                                onClick = { isEditing = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                        }
                        
                        Button(
                            onClick = {
                                val profile = ProfileEntity(
                                    fullName = fullName,
                                    email = email,
                                    phone = phone.takeIf { it.isNotBlank() },
                                    location = location.takeIf { it.isNotBlank() },
                                    linkedinUrl = linkedinUrl.takeIf { it.isNotBlank() },
                                    githubUrl = githubUrl.takeIf { it.isNotBlank() },
                                    summary = summary.takeIf { it.isNotBlank() },
                                    desiredRoles = desiredRoles.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                    desiredLocations = desiredLocations.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                    minSalary = minSalary.toIntOrNull()
                                )
                                viewModel.saveProfile(profile)
                            },
                            enabled = fullName.isNotBlank() && email.isNotBlank() && !uiState.isSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(if (uiState.profile == null) "Create Profile" else "Save Changes")
                            }
                        }
                    }
                }
            }
        }
        
        // Error message
        uiState.error?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
    
    // Skills Editor Dialog
    if (showSkillsEditor) {
        SkillsEditorDialog(
            skills = uiState.skills,
            onDismiss = { showSkillsEditor = false },
            onSave = { newSkills ->
                viewModel.updateSkills(newSkills)
                showSkillsEditor = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SkillsEditorDialog(
    skills: Map<String, List<String>>,
    onDismiss: () -> Unit,
    onSave: (Map<String, List<String>>) -> Unit
) {
    var editableSkills by remember { mutableStateOf(skills.toMutableMap()) }
    var newCategory by remember { mutableStateOf("") }
    var newSkill by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(skills.keys.firstOrNull() ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Skills") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Add new category
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("New Category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newCategory.isNotBlank()) {
                                editableSkills[newCategory] = emptyList()
                                selectedCategory = newCategory
                                newCategory = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Category")
                    }
                }
                
                // Category selector
                if (editableSkills.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            editableSkills.keys.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        selectedCategory = cat
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Add skill to category
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newSkill,
                            onValueChange = { newSkill = it },
                            label = { Text("New Skill") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (newSkill.isNotBlank() && selectedCategory.isNotBlank()) {
                                    val currentSkills = editableSkills[selectedCategory] ?: emptyList()
                                    editableSkills[selectedCategory] = currentSkills + newSkill
                                    newSkill = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Skill")
                        }
                    }
                    
                    // Display current skills
                    if (selectedCategory.isNotBlank()) {
                        val currentSkills = editableSkills[selectedCategory] ?: emptyList()
                        FlowRow(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            currentSkills.forEach { skill ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(skill) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                editableSkills[selectedCategory] = currentSkills - skill
                                            },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(editableSkills) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
