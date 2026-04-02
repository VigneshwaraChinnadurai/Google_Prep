package com.vignesh.leetcodechecker.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.security.LeetCodeDeviceAdmin

/**
 * Uninstall Protection Settings Screen
 * Allows user to enable/disable app uninstall protection with password
 */
@Composable
fun UninstallProtectionScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val devicePolicyManager = remember {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    val adminComponent = remember {
        ComponentName(context, LeetCodeDeviceAdmin::class.java)
    }
    
    var isAdminActive by remember { 
        mutableStateOf(devicePolicyManager.isAdminActive(adminComponent)) 
    }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showEnableDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    
    // Launcher to request device admin
    val adminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isAdminActive = devicePolicyManager.isAdminActive(adminComponent)
    }
    
    // Password Dialog for disabling
    if (showPasswordDialog) {
        PasswordDialog(
            title = "Enter Password to Disable",
            onDismiss = { 
                showPasswordDialog = false
                passwordError = null
            },
            onConfirm = { password ->
                if (LeetCodeDeviceAdmin.isPasswordCorrect(password)) {
                    // Disable device admin
                    devicePolicyManager.removeActiveAdmin(adminComponent)
                    isAdminActive = false
                    showPasswordDialog = false
                    passwordError = null
                } else {
                    passwordError = "Incorrect password"
                }
            },
            errorMessage = passwordError
        )
    }
    
    // Enable Confirmation Dialog
    if (showEnableDialog) {
        AlertDialog(
            onDismissRequest = { showEnableDialog = false },
            containerColor = Color(0xFF161B22),
            title = {
                Text(
                    text = "🔒 Enable Protection?",
                    color = Color(0xFFE6EDF3),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "This will prevent accidental uninstallation of the app.",
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "⚠️ Important:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFF0883E)
                            )
                            Text(
                                text = "• Password to disable: 123\n• You can disable anytime with password\n• Keeps you focused on daily practice",
                                fontSize = 12.sp,
                                color = Color(0xFF8B949E),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEnableDialog = false
                        // Request device admin activation
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Enable to prevent accidental app uninstallation. Password: 123"
                            )
                        }
                        adminLauncher.launch(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                ) {
                    Text("Enable Protection")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnableDialog = false }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            }
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF58A6FF)
                )
            }
            Text(text = "🔒", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Uninstall Protection",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    text = "Prevent accidental app removal",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAdminActive) Color(0xFF0D2818) else Color(0xFF161B22)
            ),
            border = if (isAdminActive) BorderStroke(1.dp, Color(0xFF238636)) else null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isAdminActive) "🛡️" else "⚠️",
                    fontSize = 64.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (isAdminActive) "Protection Active" else "Protection Disabled",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAdminActive) Color(0xFF39D353) else Color(0xFFF0883E)
                )
                
                Text(
                    text = if (isAdminActive) 
                        "App cannot be uninstalled without password" 
                    else 
                        "App can be uninstalled normally",
                    fontSize = 14.sp,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (isAdminActive) {
                    OutlinedButton(
                        onClick = { showPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF85149)),
                        border = BorderStroke(1.dp, Color(0xFFF85149))
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disable Protection (Password Required)")
                    }
                } else {
                    Button(
                        onClick = { showEnableDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Protection")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💡 Why Enable Protection?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6EDF3)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                val benefits = listOf(
                    "🎯 Stay committed to daily LeetCode practice",
                    "🚫 Prevent impulsive app deletion",
                    "📈 Maintain your streak and progress",
                    "🔐 Password protection ensures intentional actions"
                )
                
                benefits.forEach { benefit ->
                    Text(
                        text = benefit,
                        fontSize = 13.sp,
                        color = Color(0xFF8B949E),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Password Hint Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Password Hint",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE6EDF3)
                    )
                    Text(
                        text = "Three digits, ascending order starting from 1",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    errorMessage: String? = null
) {
    var password by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161B22),
        title = {
            Text(
                text = "🔑 $title",
                color = Color(0xFFE6EDF3),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the protection password to continue",
                    color = Color(0xFF8B949E),
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Password", color = Color(0xFF6E7681)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = errorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFE6EDF3),
                        unfocusedTextColor = Color(0xFFE6EDF3),
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D),
                        errorBorderColor = Color(0xFFF85149)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFF85149),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8B949E))
            }
        }
    )
}
