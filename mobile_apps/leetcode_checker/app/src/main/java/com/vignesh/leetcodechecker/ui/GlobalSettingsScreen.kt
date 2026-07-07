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
import com.vignesh.leetcodechecker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

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
                        val owner = settings.githubOwnerOverride.ifBlank { BuildConfig.GITHUB_OWNER }
                        val repo = settings.githubRepoOverride.ifBlank { BuildConfig.GITHUB_REPO }
                        val branch = settings.githubBranchOverride.ifBlank { BuildConfig.GITHUB_BRANCH }
                        isTestingToken = true
                        tokenTestResult = null
                        scope.launch {
                            tokenTestResult = testGitHubToken(tokenToTest, owner, repo, branch)
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

// Checks auth (GET /user) AND a real write to the configured repo, since a token can
// authenticate fine but still lack "Contents: Read and write" permission needed for pushes
// -- that gap is exactly what let a previously "OK" test still 403 on an actual push.
private suspend fun testGitHubToken(token: String, owner: String, repo: String, branch: String): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder().build()
        val authHeader = "Bearer $token"

        val userRequest = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", authHeader)
            .header("Accept", "application/vnd.github+json")
            .build()

        val login: String = client.newCall(userRequest).execute().use { response ->
            when (response.code) {
                200 -> Regex("\"login\"\\s*:\\s*\"([^\"]+)\"")
                    .find(response.body?.string().orEmpty())
                    ?.groupValues?.getOrNull(1) ?: "unknown user"
                401 -> return@withContext "Invalid or expired token (401)"
                else -> return@withContext "Unexpected response on auth check: HTTP ${response.code}"
            }
        }

        val testPath = ".leetcode_checker/token_write_test.txt"
        val putUrl = "https://api.github.com/repos/$owner/$repo/contents/$testPath"
        val putBody = JSONObject()
            .put("message", "chore: token write-permission probe (auto-deleted)")
            .put("content", android.util.Base64.encodeToString("ok".toByteArray(), android.util.Base64.NO_WRAP))
            .put("branch", branch)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val putRequest = Request.Builder()
            .url(putUrl)
            .header("Authorization", authHeader)
            .header("Accept", "application/vnd.github+json")
            .put(putBody)
            .build()

        client.newCall(putRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            when (response.code) {
                200, 201 -> {
                    Regex("\"sha\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)?.groupValues?.getOrNull(1)?.let { sha ->
                        val deleteBody = JSONObject()
                            .put("message", "chore: clean up token write-permission probe")
                            .put("sha", sha)
                            .put("branch", branch)
                            .toString()
                            .toRequestBody("application/json".toMediaType())
                        val deleteRequest = Request.Builder()
                            .url(putUrl)
                            .header("Authorization", authHeader)
                            .header("Accept", "application/vnd.github+json")
                            .delete(deleteBody)
                            .build()
                        client.newCall(deleteRequest).execute().close()
                    }
                    "OK - authenticated as $login, write access confirmed on $owner/$repo"
                }
                403 -> "Authenticated as $login, but write access denied (403) on $owner/$repo. Regenerate the token with 'Contents: Read and write' permission for this repo."
                404 -> "Authenticated as $login, but $owner/$repo (branch $branch) was not found or not accessible by this token."
                else -> "Authenticated as $login, but write probe returned HTTP ${response.code}"
            }
        }
    } catch (e: Exception) {
        "Failed: ${e.message}"
    }
}
