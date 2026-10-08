package com.vignesh.jobmatcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.JobViewModel
import com.vignesh.jobmatcher.UiState
import com.vignesh.jobmatcher.model.AppSettings
import com.vignesh.jobmatcher.model.Company
import com.vignesh.jobmatcher.model.SourceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SetupScreen(state: UiState, vm: JobViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("Profile", "Companies", "Settings").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        when (tab) {
            0 -> ProfileTab(state, vm)
            1 -> CompaniesTab(state, vm)
            else -> SettingsTab(state, vm)
        }
    }
}

// ---- Profile --------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTab(state: UiState, vm: JobViewModel) {
    val profile = state.profile
    var editingResume by remember { mutableStateOf(false) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importResumePdf)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Resume", fontWeight = FontWeight.SemiBold)
                Text(
                    "${state.resume.length} characters. Sent to Claude with every scoring, search and tailoring prompt.",
                    fontSize = 13.sp
                )
                Text(state.resume.take(220) + "…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, enabled = !state.busy) { Text("Import PDF") }
                    OutlinedButton(onClick = { editingResume = true }, enabled = !state.busy) { Text("Edit text") }
                }
            }
        }

        ClaudeRoundTripCard(
            title = "Profile analysis",
            description = "Claude turns your resume into the skill/title profile used by the on-device pre-filter. Re-run after updating your resume.",
            copyLabel = "🧠 Copy prompt",
            enabled = !state.busy,
            buildPrompt = { vm.profilePrompt() },
            onPaste = vm::applyProfile
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Current profile (${if (profile.origin == "claude") "from Claude" else "bundled default"})", fontWeight = FontWeight.SemiBold)
                Text(profile.headline, fontSize = 13.sp)
                Text("${profile.yearsExperience} years of experience", fontSize = 12.sp)
                val suggested = profile.searchTerms.filter { t -> state.settings.searchTerms.none { it.equals(t, true) } }
                if (suggested.isNotEmpty()) {
                    Text("Search terms suggested by the profile: ${suggested.joinToString()}", fontSize = 12.sp)
                    TextButton(onClick = { vm.addSearchTerms(suggested) }, enabled = !state.busy) { Text("Add to my search terms") }
                }
                Text("Target titles", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(profile.targetTitles.joinToString(), fontSize = 12.sp)
                Text("Skills (★ = weight)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile.skills.sortedByDescending { it.weight }.forEach { s ->
                        AssistChip(onClick = {}, label = { Text("${s.name} ${"★".repeat(s.weight)}", fontSize = 11.sp) })
                    }
                }
                TextButton(onClick = vm::resetProfile, enabled = !state.busy) { Text("Reset to bundled default") }
            }
        }
    }

    if (editingResume) {
        var text by remember { mutableStateOf(state.resume) }
        AlertDialog(
            onDismissRequest = { editingResume = false },
            title = { Text("Resume text") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.saveResumeText(text); editingResume = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingResume = false }) { Text("Cancel") } }
        )
    }
}

// ---- Companies ------------------------------------------------------------------------

@Composable
private fun CompaniesTab(state: UiState, vm: JobViewModel) {
    var editing by remember { mutableStateOf<Company?>(null) }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(onClick = { editing = Company(id = "", name = "", source = SourceType.GREENHOUSE) }, modifier = Modifier.fillMaxWidth()) {
                Text("➕ Add company")
            }
        }
        items(
            state.companies.sortedWith(compareBy<Company>({ it.priority }, { !it.source.automatic }, { it.name.lowercase() })),
            key = { it.id }
        ) { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (c.priority == 1) "⭐ " else "") + c.name, fontWeight = FontWeight.SemiBold)
                            Text("${c.source.label}${if (c.identifier.isNotBlank()) " · ${c.identifier}" else ""}", fontSize = 12.sp, maxLines = 1)
                        }
                        Switch(checked = c.enabled, onCheckedChange = { vm.saveCompany(c.copy(enabled = it)) }, enabled = !state.busy)
                    }
                    val status = when {
                        c.lastError.isNotBlank() -> "⚠️ ${c.lastError}"
                        c.lastFetchedAt > 0 -> "Last fetched ${fmt.format(Date(c.lastFetchedAt))}" +
                            if (c.source.automatic) " · ${c.lastFetchCount} in your locations" else ""
                        else -> "Never fetched"
                    }
                    Text(status, fontSize = 11.sp, color = if (c.lastError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (c.source.automatic) {
                            OutlinedButton(onClick = { vm.fetchCompany(c) }, enabled = !state.busy) { Text("Test fetch", fontSize = 12.sp) }
                        }
                        TextButton(onClick = { editing = c }) { Text("Edit") }
                    }
                }
            }
        }
    }

    editing?.let { company ->
        CompanyDialog(
            initial = company,
            onDismiss = { editing = null },
            onSave = { vm.saveCompany(it); editing = null },
            onDelete = if (company.id.isNotBlank()) ({ vm.deleteCompany(company); editing = null }) else null
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanyDialog(initial: Company, onDismiss: () -> Unit, onSave: (Company) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var source by remember { mutableStateOf(initial.source) }
    var identifier by remember { mutableStateOf(initial.identifier) }
    var priority by remember { mutableIntStateOf(initial.priority) }
    val needsId = source != SourceType.AMAZON

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Add company" else "Edit ${initial.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Company name") }, singleLine = true)
                Text("Priority", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Company.PRIORITY_LABELS.forEach { (value, label) ->
                        FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
                Text("Careers source", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceType.entries.filter { it.selectableForCompany }.forEach { s ->
                        FilterChip(selected = source == s, onClick = { source = s }, label = { Text(s.label, fontSize = 11.sp) })
                    }
                }
                if (needsId) {
                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        label = { Text(if (source == SourceType.CLAUDE_SEARCH) "Careers page URL (optional)" else "Identifier") },
                        singleLine = true
                    )
                }
                Text(source.identifierHint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(initial.copy(name = name.trim(), source = source, identifier = identifier.trim(), priority = priority, lastError = ""))
                },
                enabled = name.isNotBlank() && (!needsId || !source.automatic || identifier.isNotBlank())
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// ---- Settings -------------------------------------------------------------------------

@Composable
private fun SettingsTab(state: UiState, vm: JobViewModel) {
    val s = state.settings
    var threshold by remember(s) { mutableStateOf(s.matchThreshold.toFloat()) }
    var prefilter by remember(s) { mutableStateOf(s.prefilterThreshold.toFloat()) }
    var batch by remember(s) { mutableStateOf(s.scoringBatchSize.toFloat()) }
    var searchBatch by remember(s) { mutableStateOf(s.searchBatchSize.toFloat()) }
    var shortlistLimit by remember(s) { mutableStateOf(s.shortlistLimit.toFloat()) }
    var followUpDays by remember(s) { mutableStateOf(s.followUpDays.toFloat()) }
    var descChars by remember(s) { mutableStateOf(s.maxDescriptionChars.toFloat()) }
    var includeResume by remember(s) { mutableStateOf(s.includeResumeInScoring) }
    var searchTerms by remember(s) { mutableStateOf(s.searchTerms.joinToString(", ")) }
    var locations by remember(s) { mutableStateOf(s.locationKeywords.joinToString(", ")) }
    var excludes by remember(s) { mutableStateOf(s.excludeTitleKeywords.joinToString(", ")) }
    var autoFetch by remember(s) { mutableStateOf(s.autoFetchEnabled) }
    var hour by remember(s) { mutableStateOf(s.autoFetchHour.toFloat()) }

    fun csv(text: String) = text.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchTerms, onValueChange = { searchTerms = it },
            label = { Text("Search terms (comma-separated)") },
            supportingText = {
                Text(
                    "Job titles/keywords searched on every careers site and given to Claude search, e.g. " +
                        "Gen AI Engineer, AI Architect, Software Engineer 3. Also counted as target titles when pre-scoring.",
                    fontSize = 11.sp
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        LabeledSlider("Match threshold (Claude score for Matches): ${threshold.roundToInt()}%", threshold, 50f..95f, 8) { threshold = it }
        LabeledSlider("Shortlist limit (active applications): ${shortlistLimit.roundToInt()}", shortlistLimit, 3f..30f, 26) { shortlistLimit = it }
        Text("You're warned when shortlisting past this, to keep applications few and well-prepared.", fontSize = 11.sp)
        LabeledSlider("Follow up after applying: ${followUpDays.roundToInt()} days", followUpDays, 3f..21f, 17) { followUpDays = it }
        LabeledSlider("On-device pre-filter (local score to reach Claude): ${prefilter.roundToInt()}%", prefilter, 0f..80f, 15) { prefilter = it }
        Text("Lower pre-filter = more jobs reach Claude (more copy-paste rounds, fewer missed roles).", fontSize = 11.sp)
        LabeledSlider("Jobs per Claude scoring prompt: ${batch.roundToInt()}", batch, 3f..20f, 16) { batch = it }
        LabeledSlider("Description chars per job in prompt: ${descChars.roundToInt()}", descChars, 1000f..6000f, 9) { descChars = it }
        LabeledSlider("Companies per Claude web-search prompt: ${searchBatch.roundToInt()}", searchBatch, 1f..8f, 6) { searchBatch = it }
        SwitchRow("Include full resume in scoring prompts", includeResume) { includeResume = it }

        OutlinedTextField(
            value = locations, onValueChange = { locations = it },
            label = { Text("Location keywords (comma-separated)") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = excludes, onValueChange = { excludes = it },
            label = { Text("Exclude titles containing (comma-separated)") }, modifier = Modifier.fillMaxWidth()
        )

        SwitchRow("Daily auto-fetch + notification", autoFetch) { autoFetch = it }
        if (autoFetch) {
            LabeledSlider("Fetch at ${hour.roundToInt()}:00", hour, 0f..23f, 22) { hour = it }
        }

        Button(
            onClick = {
                vm.saveSettings(
                    AppSettings(
                        searchTerms = searchTerms.split(',').map { it.trim() }.filter { it.isNotBlank() }
                            .distinctBy { it.lowercase() },
                        matchThreshold = threshold.roundToInt(),
                        prefilterThreshold = prefilter.roundToInt(),
                        scoringBatchSize = batch.roundToInt(),
                        searchBatchSize = searchBatch.roundToInt(),
                        shortlistLimit = shortlistLimit.roundToInt(),
                        followUpDays = followUpDays.roundToInt(),
                        maxDescriptionChars = (descChars / 500).roundToInt() * 500,
                        includeResumeInScoring = includeResume,
                        locationKeywords = csv(locations),
                        excludeTitleKeywords = csv(excludes),
                        autoFetchEnabled = autoFetch,
                        autoFetchHour = hour.roundToInt()
                    )
                )
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save settings") }
        TextButton(onClick = { vm.saveSettings(AppSettings()) }, enabled = !state.busy) { Text("Restore defaults") }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Text(label, fontSize = 13.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
