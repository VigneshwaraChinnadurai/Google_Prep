package com.vignesh.jobmatcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import com.vignesh.jobmatcher.model.JobStatus
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.UiState

@Composable
fun MatchesScreen(state: UiState, vm: com.vignesh.jobmatcher.JobViewModel, onOpenJob: (String) -> Unit) {
    val threshold = state.settings.matchThreshold
    val matches = remember(state.jobs, state.settings) { state.matches }
    val below = remember(state.jobs, state.settings) { state.belowThreshold }
    var showBelow by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    fun statusOk(job: com.vignesh.jobmatcher.model.Job) = statusFilter == null || job.status.name == statusFilter
    val quick: (com.vignesh.jobmatcher.model.Job) -> ((JobStatus) -> Unit) = { job -> { s -> vm.setStatus(job.id, s) } }
    val manual = remember(state.jobs) { state.manualJobs }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "All", "AUTO" to "🤖 Automatic", "MANUAL" to "✋ Manual (${manual.size})").forEach { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label, fontSize = 12.sp) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("Any status", fontSize = 12.sp) })
                listOf(JobStatus.NEW, JobStatus.SHORTLISTED, JobStatus.APPLIED, JobStatus.INTERVIEWING, JobStatus.OFFER, JobStatus.REJECTED).forEach { s ->
                    FilterChip(
                        selected = statusFilter == s.name,
                        onClick = { statusFilter = if (statusFilter == s.name) null else s.name },
                        label = { Text("${s.emoji} ${s.label}", fontSize = 12.sp) }
                    )
                }
            }
        }
        if (filter == "MANUAL") {
            // Jobs you added yourself are shown whatever their score -- you chose them.
            item {
                SectionHeader("Added by you (${manual.size})")
                Text("All jobs you added from links, scored or not.", fontSize = 12.sp)
            }
            if (manual.isEmpty()) item { EmptyHint("No manual jobs yet. Paste a job link on Discover, or Share one to Job Matcher.") }
            items(manual.filter(::statusOk), key = { "m-" + it.id }) { job ->
                JobCard(job, threshold, state.isTopChoice(job), onQuickStatus = quick(job)) { onOpenJob(job.id) }
            }
            return@LazyColumn
        }
        val shown = (if (filter == "AUTO") matches.filter { !it.isManual } else matches).filter(::statusOk)
        val shownBelow = (if (filter == "AUTO") below.filter { !it.isManual } else below).filter(::statusOk)
        item {
            SectionHeader("Claude-scored ≥$threshold% (${shown.size})")
            Text("Change the cutoff in Setup → Settings.", fontSize = 12.sp)
        }
        if (shown.isEmpty()) {
            item { EmptyHint("No matches yet. Fetch on Discover, then score the shortlist with Claude.") }
        }
        items(shown, key = { it.id }) { job -> JobCard(job, threshold, state.isTopChoice(job), onQuickStatus = quick(job)) { onOpenJob(job.id) } }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Show below-threshold (${shownBelow.size})", modifier = Modifier.weight(1f))
                Switch(checked = showBelow, onCheckedChange = { showBelow = it })
            }
        }
        if (showBelow) {
            items(shownBelow, key = { "below-" + it.id }) { job -> JobCard(job, threshold, state.isTopChoice(job), onQuickStatus = quick(job)) { onOpenJob(job.id) } }
        }
    }
}
