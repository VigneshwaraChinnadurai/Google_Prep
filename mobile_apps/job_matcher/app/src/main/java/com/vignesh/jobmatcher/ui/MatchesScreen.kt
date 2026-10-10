package com.vignesh.jobmatcher.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.JobViewModel
import com.vignesh.jobmatcher.UiState
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobOrigin
import com.vignesh.jobmatcher.model.JobStatus
import com.vignesh.jobmatcher.model.MatchFilters
import com.vignesh.jobmatcher.model.toggle

/** A horizontally scrolling row of multi-select chips. */
@Composable
fun <T> MultiChipRow(options: List<T>, selected: Set<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(label(o), fontSize = 12.sp) })
        }
    }
}

/** "Filters" caption with a Clear button whenever any filter is on. */
@Composable
fun FilterHeader(active: Boolean, summary: String, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (active) TextButton(onClick = onClear) { Text("✕ Clear filters", fontSize = 12.sp) }
    }
}

private val MATCH_STATUSES = listOf(
    JobStatus.NEW, JobStatus.SHORTLISTED, JobStatus.APPLIED, JobStatus.INTERVIEWING,
    JobStatus.OFFER, JobStatus.REJECTED, JobStatus.DISMISSED
)

@Composable
fun MatchesScreen(state: UiState, vm: JobViewModel, onOpenJob: (String) -> Unit) {
    val threshold = state.settings.matchThreshold
    val f = state.matchFilters
    val (shown, below) = remember(state.jobs, state.settings, f, state.companies) { state.filteredMatches() }
    val manual = remember(state.jobs, f) { state.filteredManual() }
    val counts = remember(state.jobs) { state.jobs.groupingBy { it.status }.eachCount() }
    val quick: (Job) -> ((JobStatus) -> Unit) = { job -> { s -> vm.setStatus(job.id, s) } }
    val card: @Composable (Job) -> Unit = { job ->
        JobCard(job, threshold, state.isTopChoice(job), footer = dismissedFooter(job), onQuickStatus = quick(job)) { onOpenJob(job.id) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterHeader(
                    active = f.isActive,
                    summary = if (f.isActive) "Filters on -- select several to combine" else "Filters (select several to combine)",
                    onClear = { vm.setMatchFilters(MatchFilters()) }
                )
                MultiChipRow(JobOrigin.entries, f.origins, { if (it == JobOrigin.AUTOMATIC) "🤖 Automatic" else "✋ Manual" }) {
                    vm.setMatchFilters(f.copy(origins = f.origins.toggle(it)))
                }
                MultiChipRow(MATCH_STATUSES, f.statuses, { "${it.emoji} ${it.label} (${counts[it] ?: 0})" }) {
                    vm.setMatchFilters(f.copy(statuses = f.statuses.toggle(it)))
                }
            }
        }

        if (f.manualOnly) {
            // Jobs you added yourself are shown whatever their score -- you chose them.
            item {
                SectionHeader("Added by you (${manual.size})")
                Text("All jobs you added from links, scored or not.", fontSize = 12.sp)
            }
            if (manual.isEmpty()) item { EmptyHint("No manual jobs match these filters.") }
            items(manual, key = { "m-" + it.id }) { card(it) }
            return@LazyColumn
        }

        item {
            SectionHeader("Claude-scored ≥$threshold% (${shown.size})")
            Text("Change the cutoff in Setup → Settings.", fontSize = 12.sp)
        }
        if (shown.isEmpty()) {
            item {
                EmptyHint(
                    if (f.isActive) "No matches with these filters." else "No matches yet. Fetch on Discover, then score the shortlist with Claude."
                )
            }
        }
        items(shown, key = { it.id }) { card(it) }

        // Always visible: scored jobs under the cutoff, so nothing Claude scored is hidden.
        item {
            SectionHeader("Below threshold <$threshold% (${below.size})")
            if (below.isEmpty()) EmptyHint("None.")
        }
        items(below, key = { "below-" + it.id }) { card(it) }
    }
}

/** When a job was marked not interested (from its history), for the card footer. */
fun dismissedFooter(job: Job): String? {
    if (job.status != JobStatus.DISMISSED) return null
    val at = job.history.lastOrNull { it.status == JobStatus.DISMISSED }?.at ?: job.statusUpdatedAt
    return "🚫 Not interested since ${formatDay(at)} -- open it to change your mind"
}
