package com.vignesh.leedcodecheckerollama.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leedcodecheckerollama.data.*

/**
 * AI News Settings Screen - Manage tracked AI thought leaders
 * Users can add, remove, and toggle people to follow for announcements
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AINewsSettingsScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var trackedPeople by remember { 
        mutableStateOf(AINewsStorage.loadTrackedPeople(context)) 
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var enabledCategories by remember { 
        mutableStateOf(AINewsStorage.loadEnabledCategories(context)) 
    }
    
    // Add Person Dialog
    if (showAddDialog) {
        var personName by remember { mutableStateOf("") }
        var twitterHandle by remember { mutableStateOf("") }
        var keywords by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Color(0xFF161B22),
            title = {
                Text(
                    text = "➕ Add Person to Track",
                    color = Color(0xFFE6EDF3),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = personName,
                        onValueChange = { personName = it },
                        label = { Text("Name *", color = Color(0xFF8B949E)) },
                        placeholder = { Text("e.g., Sam Altman", color = Color(0xFF6E7681)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = twitterHandle,
                        onValueChange = { twitterHandle = it },
                        label = { Text("Twitter/X Handle (optional)", color = Color(0xFF8B949E)) },
                        placeholder = { Text("@username", color = Color(0xFF6E7681)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("Keywords (comma-separated)", color = Color(0xFF8B949E)) },
                        placeholder = { Text("OpenAI, GPT, AI", color = Color(0xFF6E7681)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "💡 Keywords help filter news related to this person",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (personName.isNotBlank()) {
                            val newPerson = TrackedPerson(
                                name = personName.trim(),
                                twitterHandle = twitterHandle.trim().ifBlank { null },
                                keywords = keywords.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                isEnabled = true
                            )
                            AINewsStorage.addTrackedPerson(context, newPerson)
                            trackedPeople = AINewsStorage.loadTrackedPeople(context)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                    enabled = personName.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            }
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        // Header
        TopAppBar(
            title = {
                Text(
                    text = "⚙️ News Settings",
                    color = Color(0xFFE6EDF3),
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF58A6FF)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161B22))
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Categories Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📂 News Categories",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE6EDF3)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        NewsCategory.entries.forEach { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category.displayName,
                                    fontSize = 14.sp,
                                    color = Color(0xFFE6EDF3)
                                )
                                
                                Switch(
                                    checked = enabledCategories.contains(category),
                                    onCheckedChange = { isChecked ->
                                        enabledCategories = if (isChecked) {
                                            enabledCategories + category
                                        } else {
                                            enabledCategories - category
                                        }
                                        AINewsStorage.saveEnabledCategories(context, enabledCategories)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF238636),
                                        uncheckedThumbColor = Color(0xFF6E7681),
                                        uncheckedTrackColor = Color(0xFF30363D)
                                    )
                                )
                            }
                        }
                    }
                }
            }
            
            // Tracked People Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👤 AI Thought Leaders",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3)
                    )
                    
                    IconButton(
                        onClick = { showAddDialog = true }
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add Person",
                            tint = Color(0xFF238636)
                        )
                    }
                }
                
                Text(
                    text = "Track announcements from AI industry leaders",
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E)
                )
            }
            
            // Tracked People List
            items(trackedPeople) { person ->
                TrackedPersonCard(
                    person = person,
                    onToggle = {
                        AINewsStorage.togglePersonEnabled(context, person.name)
                        trackedPeople = AINewsStorage.loadTrackedPeople(context)
                    },
                    onRemove = {
                        AINewsStorage.removeTrackedPerson(context, person.name)
                        trackedPeople = AINewsStorage.loadTrackedPeople(context)
                    }
                )
            }
            
            // Suggestions Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "💡 Suggestions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6EDF3)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "• Track AI researchers and CEOs for industry insights\n" +
                                   "• Add keywords related to your interests (e.g., \"AGI\", \"robotics\")\n" +
                                   "• Enable/disable people to customize your feed\n" +
                                   "• News updates include quantum computing by default",
                            fontSize = 13.sp,
                            color = Color(0xFF8B949E),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun TrackedPersonCard(
    person: TrackedPerson,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF161B22),
            title = {
                Text(
                    text = "Remove ${person.name}?",
                    color = Color(0xFFE6EDF3),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You will stop receiving news about this person.",
                    color = Color(0xFF8B949E)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemove()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633))
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            }
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (person.isEnabled) Color(0xFF161B22) else Color(0xFF161B22).copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (person.isEnabled) Color(0xFF58A6FF).copy(alpha = 0.2f)
                            else Color(0xFF30363D)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.name.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (person.isEnabled) Color(0xFF58A6FF) else Color(0xFF6E7681)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = person.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (person.isEnabled) Color(0xFFE6EDF3) else Color(0xFF8B949E)
                    )
                    
                    if (person.twitterHandle != null) {
                        Text(
                            text = person.twitterHandle,
                            fontSize = 12.sp,
                            color = Color(0xFF58A6FF)
                        )
                    }
                    
                    if (person.keywords.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            person.keywords.take(3).forEach { keyword ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF30363D)
                                ) {
                                    Text(
                                        text = keyword,
                                        fontSize = 10.sp,
                                        color = Color(0xFF8B949E),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = person.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF238636),
                        uncheckedThumbColor = Color(0xFF6E7681),
                        uncheckedTrackColor = Color(0xFF30363D)
                    )
                )
                
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove",
                        tint = Color(0xFFDA3633),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
