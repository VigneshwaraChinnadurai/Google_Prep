package com.vignesh.leetcodechecker

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vignesh.leetcodechecker.data.DailyChallengeUiModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import java.util.TimeZone

private val AppDarkColors = darkColorScheme(
    primary = Color(0xFF9FC8FF),
    onPrimary = Color(0xFF003259),
    secondary = Color(0xFF9AD0C0),
    onSecondary = Color(0xFF07372E),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE5E9F0),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFE5E9F0)
)

private val AppLightColors = lightColorScheme(
    primary = Color(0xFF245FA8),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2B7A64),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF121417),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121417)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConsistencyReminderScheduler.ensureHourlyReminder(this)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (darkTheme) AppDarkColors else AppLightColors) {
                LeetCodeCheckerScreen(
                    onOpenLink = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

private enum class AppScreen {
    Landing,
    ConsistencyChecker,
    Settings
}

@Composable
private fun LeetCodeCheckerScreen(
    onOpenLink: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: LeetCodeViewModel = viewModel(factory = LeetCodeViewModel.factory(application))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Landing) }
    var showPipelineLog by rememberSaveable { mutableStateOf(false) }
    var showPythonFile by rememberSaveable { mutableStateOf(true) }
    var showTestcases by rememberSaveable { mutableStateOf(true) }
    var showExplanation by rememberSaveable { mutableStateOf(true) }
    var showConcepts by rememberSaveable { mutableStateOf(true) }
    var handledChallengeUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showLlmConfirmation by rememberSaveable { mutableStateOf(false) }
    var showPushConfirmation by rememberSaveable { mutableStateOf(false) }
    var pendingCalendarCompletion by rememberSaveable { mutableStateOf(false) }

    var settingsModelsCsv by rememberSaveable { mutableStateOf(state.settings.preferredModelsCsv) }
    var settingsMaxRetries by rememberSaveable { mutableStateOf(state.settings.maxModelRetries.toString()) }
    var settingsMaxInput by rememberSaveable { mutableStateOf(state.settings.maxInputTokens.toString()) }
    var settingsMaxOutput by rememberSaveable { mutableStateOf(state.settings.maxOutputTokens.toString()) }
    var settingsThinkingDivisor by rememberSaveable { mutableStateOf(state.settings.thinkingBudgetDivisor.toString()) }
    var settingsTimeoutMinutes by rememberSaveable { mutableStateOf(state.settings.networkTimeoutMinutes.toString()) }
    var settingsReminderStart by rememberSaveable { mutableStateOf(state.settings.reminderStartHourIst.toString()) }
    var settingsReminderEnd by rememberSaveable { mutableStateOf(state.settings.reminderEndHourIst.toString()) }
    var settingsReminderInterval by rememberSaveable { mutableStateOf(state.settings.reminderIntervalHours.toString()) }
    var settingsGithubOwner by rememberSaveable { mutableStateOf(state.settings.githubOwnerOverride) }
    var settingsGithubRepo by rememberSaveable { mutableStateOf(state.settings.githubRepoOverride) }
    var settingsGithubBranch by rememberSaveable { mutableStateOf(state.settings.githubBranchOverride) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val granted = grantMap.values.all { it }
        if (granted && pendingCalendarCompletion) {
            pendingCalendarCompletion = false
            viewModel.markCompletedToday()
            val inserted = insertCompletionCalendarEvent(context, state.challenge)
            val message = if (inserted) {
                "Marked completed and calendar entry created."
            } else {
                "Marked completed, but calendar entry could not be created."
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.challenge?.url) {
        val challenge = state.challenge ?: return@LaunchedEffect
        if (handledChallengeUrl == challenge.url) return@LaunchedEffect

        Toast.makeText(
            context,
            "Daily problem loaded. Use refresh buttons manually for API and LLM.",
            Toast.LENGTH_SHORT
        ).show()
        handledChallengeUrl = challenge.url
        showPipelineLog = false
        showPythonFile = true
        showTestcases = true
        showExplanation = true
        showConcepts = true
    }

    LaunchedEffect(state.infoMessage) {
        val message = state.infoMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearInfoMessage()
    }

    LaunchedEffect(state.settings) {
        settingsModelsCsv = state.settings.preferredModelsCsv
        settingsMaxRetries = state.settings.maxModelRetries.toString()
        settingsMaxInput = state.settings.maxInputTokens.toString()
        settingsMaxOutput = state.settings.maxOutputTokens.toString()
        settingsThinkingDivisor = state.settings.thinkingBudgetDivisor.toString()
        settingsTimeoutMinutes = state.settings.networkTimeoutMinutes.toString()
        settingsReminderStart = state.settings.reminderStartHourIst.toString()
        settingsReminderEnd = state.settings.reminderEndHourIst.toString()
        settingsReminderInterval = state.settings.reminderIntervalHours.toString()
        settingsGithubOwner = state.settings.githubOwnerOverride
        settingsGithubRepo = state.settings.githubRepoOverride
        settingsGithubBranch = state.settings.githubBranchOverride
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen != AppScreen.ConsistencyChecker) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (currentScreen == AppScreen.Landing) {
                Text(
                    text = state.settings.landingTitle,
                    style = MaterialTheme.typography.headlineSmall
                )

                Button(
                    onClick = { currentScreen = AppScreen.ConsistencyChecker },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.leetcode_logo),
                            contentDescription = "LeetCode Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Text(state.settings.consistencyButtonLabel)
                    }
                }

                Button(
                    onClick = {
                        val primaryIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_APP_CALENDAR)
                        }
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI)

                        runCatching { context.startActivity(primaryIntent) }
                            .onFailure {
                                runCatching { context.startActivity(fallbackIntent) }
                                    .onFailure {
                                        Toast.makeText(context, "No calendar app found.", Toast.LENGTH_SHORT).show()
                                    }
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Calendar")
                }

                Button(
                    onClick = { currentScreen = AppScreen.Settings },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Settings")
                }

                Text(
                    text = "Use this to track daily LeetCode consistency with manual API/LLM refresh and reminders.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = { currentScreen = AppScreen.Landing }) {
                    Text("Back")
                }
                Text(
                    text = if (currentScreen == AppScreen.Settings) "Settings" else state.settings.checkerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (currentScreen == AppScreen.Settings) {
                OutlinedTextField(
                    value = settingsModelsCsv,
                    onValueChange = { settingsModelsCsv = it },
                    label = { Text("Preferred Models CSV") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsMaxRetries,
                    onValueChange = { settingsMaxRetries = it },
                    label = { Text("Max Model Retries") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsMaxInput,
                    onValueChange = { settingsMaxInput = it },
                    label = { Text("Max Input Tokens") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsMaxOutput,
                    onValueChange = { settingsMaxOutput = it },
                    label = { Text("Max Output Tokens") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsThinkingDivisor,
                    onValueChange = { settingsThinkingDivisor = it },
                    label = { Text("Thinking Budget Divisor") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsTimeoutMinutes,
                    onValueChange = { settingsTimeoutMinutes = it },
                    label = { Text("Network Timeout Minutes") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsReminderStart,
                    onValueChange = { settingsReminderStart = it },
                    label = { Text("Reminder Start Hour IST") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsReminderEnd,
                    onValueChange = { settingsReminderEnd = it },
                    label = { Text("Reminder End Hour IST") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsReminderInterval,
                    onValueChange = { settingsReminderInterval = it },
                    label = { Text("Reminder Interval Hours") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsGithubOwner,
                    onValueChange = { settingsGithubOwner = it },
                    label = { Text("GitHub Owner Override") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsGithubRepo,
                    onValueChange = { settingsGithubRepo = it },
                    label = { Text("GitHub Repo Override") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settingsGithubBranch,
                    onValueChange = { settingsGithubBranch = it },
                    label = { Text("GitHub Branch Override") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.saveSettings(
                            AppSettings(
                                landingTitle = state.settings.landingTitle,
                                checkerTitle = state.settings.checkerTitle,
                                consistencyButtonLabel = state.settings.consistencyButtonLabel,
                                promptName = state.settings.promptName,
                                preferredModelsCsv = settingsModelsCsv,
                                maxModelRetries = settingsMaxRetries.toIntOrNull() ?: state.settings.maxModelRetries,
                                maxInputTokens = settingsMaxInput.toIntOrNull() ?: state.settings.maxInputTokens,
                                maxOutputTokens = settingsMaxOutput.toIntOrNull() ?: state.settings.maxOutputTokens,
                                thinkingBudgetDivisor = settingsThinkingDivisor.toIntOrNull() ?: state.settings.thinkingBudgetDivisor,
                                networkTimeoutMinutes = settingsTimeoutMinutes.toIntOrNull() ?: state.settings.networkTimeoutMinutes,
                                reminderStartHourIst = settingsReminderStart.toIntOrNull() ?: state.settings.reminderStartHourIst,
                                reminderEndHourIst = settingsReminderEnd.toIntOrNull() ?: state.settings.reminderEndHourIst,
                                reminderIntervalHours = settingsReminderInterval.toIntOrNull() ?: state.settings.reminderIntervalHours,
                                revisionFolderName = state.settings.revisionFolderName,
                                githubOwnerOverride = settingsGithubOwner,
                                githubRepoOverride = settingsGithubRepo,
                                githubBranchOverride = settingsGithubBranch
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Settings")
                }
            } else {

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Pipeline")
                    Text("1. Refresh API content (manual)")
                    Text("2. Review challenge content")
                    Text("3. Refresh LLM output (manual confirmation)")
                    Text("4. Mark completed to stop reminders for today")
                }
            }

            Button(
                onClick = { viewModel.refreshApiChallenge() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh LeetCode API")
            }

            Button(
                onClick = { showLlmConfirmation = true },
                enabled = state.challenge != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh LLM Answer")
            }

            Button(
                onClick = { showPushConfirmation = true },
                enabled = state.challenge != null && !state.aiCode.isNullOrBlank() && !state.isPushLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isPushLoading) "Pushing To GitHub..." else "Push QA Revision To GitHub")
            }

            state.localRevisionPath?.let { localPath ->
                Text(
                    text = "Local revision folder: $localPath",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (showLlmConfirmation) {
                AlertDialog(
                    onDismissRequest = { showLlmConfirmation = false },
                    title = { Text("Confirm LLM Call") },
                    text = { Text("This will trigger a paid LLM request. Continue?") },
                    confirmButton = {
                        Button(onClick = {
                            showLlmConfirmation = false
                            viewModel.refreshLlmAnswer()
                        }) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showLlmConfirmation = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showPushConfirmation) {
                AlertDialog(
                    onDismissRequest = { showPushConfirmation = false },
                    title = { Text("Confirm GitHub Push") },
                    text = {
                        Text("This will create/update question.txt, answer.py, and explanation.txt under ${state.settings.revisionFolderName}/<date> in the configured GitHub repo. Continue?")
                    },
                    confirmButton = {
                        Button(onClick = {
                            showPushConfirmation = false
                            viewModel.pushRevisionFilesToGitHub()
                        }) {
                            Text("Push")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showPushConfirmation = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (state.isLoading || state.isAiLoading) {
                CircularProgressIndicator()
            }

            state.infoMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.challenge?.let { challenge ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${challenge.questionId}. ${challenge.title}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Date: ${challenge.date}")
                        Text("Difficulty: ${challenge.difficulty}")
                        Text("Tags: ${challenge.tags.joinToString()}")
                        if (challenge.descriptionPreview.isNotBlank()) {
                            Text(
                                text = challenge.descriptionPreview,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Button(onClick = { onOpenLink(challenge.url) }) {
                            Text("Open Problem in leetcode site")
                        }

                        Button(onClick = { onOpenLink(challenge.url) }) {
                            Text("Open LeetCode Editor (Manual Submit)")
                        }

                        Spacer(modifier = Modifier.size(4.dp))

                        state.aiError?.let { aiError ->
                            Text(
                                text = aiError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        state.aiCode?.let { aiCode ->
                            Button(
                                onClick = { showPythonFile = !showPythonFile },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showPythonFile) "Hide Generated Python File" else "Show Generated Python File")
                            }

                            if (showPythonFile) {
                                Text(
                                    text = "Generated Python File",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Filename: solution_${challenge.titleSlug}.py",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                SelectionContainer {
                                    Text(
                                        text = aiCode,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("LeetCode Python3 Code", aiCode))
                                    Toast.makeText(context, "LeetCode Python3 code copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Copy Python3 Code")
                            }
                        }

                        state.aiTestcaseValidation?.takeIf { it.isNotBlank() }?.let { validation ->
                            Button(
                                onClick = { showTestcases = !showTestcases },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showTestcases) "Hide Testcases" else "Show Testcases")
                            }

                            if (showTestcases) {
                                Text(
                                    text = "Testcase Validation",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = validation,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        state.aiExplanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                            val concepts = extractDsaConcepts(explanation, challenge.tags)

                            if (concepts.isNotEmpty()) {
                                Button(
                                    onClick = { showConcepts = !showConcepts },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (showConcepts) "Hide Concepts Used" else "Show Concepts Used")
                                }

                                if (showConcepts) {
                                    Text(
                                        text = "Concepts Used (DSA Learning)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = concepts.joinToString(" | "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Button(
                                onClick = { showExplanation = !showExplanation },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showExplanation) "Hide Explanation" else "Show Explanation")
                            }

                            if (showExplanation) {
                                Text(
                                    text = "Detailed Explanation (DSA Learning)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                SelectionContainer {
                                    Text(
                                        text = explanation,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        state.aiDebugLog?.takeIf { it.isNotBlank() }?.let { pipelineLog ->
                            Button(
                                onClick = { showPipelineLog = !showPipelineLog },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showPipelineLog) "Hide Pipeline Logs" else "Show Pipeline Logs")
                            }

                            if (showPipelineLog) {
                                Text(
                                    text = "Pipeline Debug Log",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = pipelineLog,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Text(
                            text = "Auto sign-in and auto-submit are not supported safely in this app. Use Open LeetCode Editor, sign in, paste Python code, and submit.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(
                            onClick = {
                                val hasWrite = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_CALENDAR
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasRead = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_CALENDAR
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasWrite && hasRead) {
                                    viewModel.markCompletedToday()
                                    val inserted = insertCompletionCalendarEvent(context, challenge)
                                    val message = if (inserted) {
                                        "Marked completed and calendar entry created."
                                    } else {
                                        "Marked completed, but calendar entry could not be created."
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                } else {
                                    pendingCalendarCompletion = true
                                    calendarPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                }
                            },
                            enabled = !state.isCompletedToday,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (state.isCompletedToday) "Marked Completed" else "Mark as Completed")
                        }

                        if (state.isCompletedToday) {
                            Text(
                                text = "Status: Completed",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
}

private fun insertCompletionCalendarEvent(context: Context, challenge: DailyChallengeUiModel?): Boolean {
    return runCatching {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.VISIBLE
        )
        val selection = "${CalendarContract.Calendars.VISIBLE}=1"
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            null
        ) ?: return false

        var calendarId: Long? = null
        cursor.use {
            if (it.moveToFirst()) {
                calendarId = it.getLong(0)
            }
        }
        val selectedCalendarId = calendarId ?: return false

        val timezone = TimeZone.getTimeZone("Asia/Kolkata")
        val start = Calendar.getInstance(timezone).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }

        val title = if (challenge != null) {
            "LeetCode Completed: ${challenge.title}"
        } else {
            "LeetCode Completed"
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, selectedCalendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, "Marked completed from LeetCode Consistency Checker")
            put(CalendarContract.Events.DTSTART, start.timeInMillis)
            put(CalendarContract.Events.DTEND, end.timeInMillis)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, timezone.id)
            put(CalendarContract.Events.EVENT_COLOR, 0xFF2E7D32.toInt())
        }

        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
    }.getOrDefault(false)
}

private fun extractDsaConcepts(explanation: String, tags: List<String>): List<String> {
    val text = explanation.lowercase()
    val concepts = linkedSetOf<String>()

    // Keep LeetCode tags first; they are high-confidence concepts.
    tags.map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { concepts.add(it) }

    val keywordMap = listOf(
        "prefix sum" to "Prefix Sum",
        "sliding window" to "Sliding Window",
        "two pointers" to "Two Pointers",
        "binary search" to "Binary Search",
        "dynamic programming" to "Dynamic Programming",
        "dp" to "Dynamic Programming",
        "greedy" to "Greedy",
        "monotonic stack" to "Monotonic Stack",
        "stack" to "Stack",
        "queue" to "Queue",
        "deque" to "Deque",
        "hash map" to "Hash Map",
        "hashmap" to "Hash Map",
        "dictionary" to "Hash Map",
        "set" to "Set",
        "sort" to "Sorting",
        "matrix" to "Matrix",
        "graph" to "Graph",
        "tree" to "Tree",
        "dfs" to "Depth-First Search",
        "bfs" to "Breadth-First Search",
        "union-find" to "Union Find",
        "disjoint set" to "Union Find",
        "backtracking" to "Backtracking",
        "recursion" to "Recursion",
        "bitmask" to "Bit Manipulation",
        "bit" to "Bit Manipulation",
        "heap" to "Heap / Priority Queue",
        "priority queue" to "Heap / Priority Queue"
    )

    keywordMap.forEach { (token, label) ->
        if (text.contains(token)) {
            concepts.add(label)
        }
    }

    return concepts.toList()
}
