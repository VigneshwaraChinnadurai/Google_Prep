package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vignesh.leetcodechecker.AppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppSettingsStore.load(context)) }

    var githubToken by remember { mutableStateOf(settings.globalGithubToken) }
    var geminiKey by remember { mutableStateOf(settings.globalGeminiApiKey) }
    var tokenVisible by remember { mutableStateOf(false) }
    var tokenTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingToken by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "API Keys",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "This is the single GitHub token used for all pushes (daily revisions, proof filing) and profile lookups in the app. It is stored only on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = githubToken,
                onValueChange = {
                    githubToken = it
                    tokenTestResult = null
                },
                label = { Text("Global GitHub Token") },
                singleLine = true,
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { tokenVisible = !tokenVisible }) {
                        Text(if (tokenVisible) "Hide" else "Show")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    enabled = githubToken.isNotBlank() && !isTestingToken,
                    onClick = {
                        val tokenToTest = githubToken.trim()
                        isTestingToken = true
                        tokenTestResult = null
                        scope.launch {
                            tokenTestResult = testGitHubToken(tokenToTest)
                            isTestingToken = false
                        }
                    }
                ) {
                    Text(if (isTestingToken) "Testing..." else "Test Token")
                }

                tokenTestResult?.let { result ->
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.startsWith("OK")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                label = { Text("Global Gemini API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val updatedSettings = settings.copy(
                        globalGithubToken = githubToken.trim(),
                        globalGeminiApiKey = geminiKey
                    )
                    AppSettingsStore.save(context, updatedSettings)
                    settings = updatedSettings
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }
        }
    }
}

// Hits GitHub's /user endpoint so a bad/under-scoped token is caught here instead of during a push.
private suspend fun testGitHubToken(token: String): String = withContext(Dispatchers.IO) {
    runCatching {
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> {
                    val login = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"")
                        .find(response.body?.string().orEmpty())
                        ?.groupValues
                        ?.getOrNull(1)
                    "OK - authenticated as ${login ?: "unknown user"}"
                }
                401 -> "Invalid or expired token (401)"
                403 -> "Forbidden (403) - token lacks required permissions/SSO authorization"
                else -> "Unexpected response: HTTP ${response.code}"
            }
        }
    }.getOrElse { "Failed: ${it.message}" }
}
