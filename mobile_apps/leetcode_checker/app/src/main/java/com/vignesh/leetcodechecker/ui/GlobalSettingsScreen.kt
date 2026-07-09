package com.vignesh.leetcodechecker.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.documentfile.provider.DocumentFile
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.BuildConfig
import com.vignesh.leetcodechecker.backup.BackupManager
import com.vignesh.leetcodechecker.backup.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    var backupInProgress by remember { mutableStateOf(false) }
    var restoreInProgress by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val updated = settings.copy(backupFolderUri = uri.toString())
            AppSettingsStore.save(context, updated)
            settings = updated
            BackupWorker.ensureScheduled(context)
            backupStatusMessage = "Backup folder set. Weekly backups are now scheduled."
        }
    }

    val restoreFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
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

            HorizontalDivider()

            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Backs up progress, goals, achievements, chat history, and revision files to a folder you choose (e.g. inside Google Drive/OneDrive's synced folder). Your GitHub token and Gemini key are excluded from these backups on purpose -- re-enter them after a restore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val folderDisplayName = remember(settings.backupFolderUri) {
                settings.backupFolderUri.takeIf { it.isNotBlank() }
                    ?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() }
            }

            Text(
                text = "Folder: ${folderDisplayName ?: "Not configured"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Last backup: " + if (settings.lastBackupTimeMillis > 0) {
                    SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(settings.lastBackupTimeMillis))
                } else {
                    "Never"
                },
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { folderPickerLauncher.launch(null) }) {
                    Text("Choose Folder")
                }
                Button(
                    enabled = settings.backupFolderUri.isNotBlank() && !backupInProgress,
                    onClick = {
                        val uri = Uri.parse(settings.backupFolderUri)
                        backupInProgress = true
                        backupStatusMessage = null
                        scope.launch {
                            val result = BackupManager.createBackup(context, uri, redactSecrets = true)
                            result.fold(
                                onSuccess = { r ->
                                    val updated = settings.copy(lastBackupTimeMillis = System.currentTimeMillis())
                                    AppSettingsStore.save(context, updated)
                                    settings = updated
                                    backupStatusMessage = "Backup saved: ${r.fileName} (${r.sizeBytes / 1024} KB)"
                                },
                                onFailure = { e -> backupStatusMessage = "Backup failed: ${e.message}" }
                            )
                            backupInProgress = false
                        }
                    }
                ) {
                    Text(if (backupInProgress) "Backing up..." else "Backup Now")
                }
            }

            OutlinedButton(
                enabled = !restoreInProgress,
                onClick = { restoreFilePickerLauncher.launch(arrayOf("application/zip")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (restoreInProgress) "Restoring..." else "Restore from Backup...")
            }

            backupStatusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.contains("failed", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }

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

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore from backup?") },
            text = {
                Text(
                    "This overwrites current progress, goals, chat history, and settings with the " +
                        "contents of this backup file. Your GitHub token and Gemini key, if already " +
                        "configured on this device, are kept as-is. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = uri
                    pendingRestoreUri = null
                    restoreInProgress = true
                    backupStatusMessage = null
                    scope.launch {
                        val result = BackupManager.restoreBackup(context, target)
                        result.fold(
                            onSuccess = {
                                settings = AppSettingsStore.load(context)
                                githubToken = settings.globalGithubToken
                                geminiKey = settings.globalGeminiApiKey
                                backupStatusMessage = "Restore complete. Restart the app to fully reload."
                            },
                            onFailure = { e -> backupStatusMessage = "Restore failed: ${e.message}" }
                        )
                        restoreInProgress = false
                    }
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("Cancel")
                }
            }
        )
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
