package com.vignesh.jobmatcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.JobViewModel
import com.vignesh.jobmatcher.UiState
import com.vignesh.jobmatcher.model.ApplicationFilters
import com.vignesh.jobmatcher.model.JobStatus
import com.vignesh.jobmatcher.model.toggle

/**
 * Applications: the jobs you shortlisted and everything after, plus the ones you marked
 * not interested. Multi-select status filters (kept until the app is closed); each card
 * shows the one next action and the nearest upcoming date.
 */
@Composable
fun TrackerScreen(state: UiState, vm: JobViewModel, onOpenJob: (String) -> Unit) {
    val now = System.currentTimeMillis()
    val f = state.applicationFilters
    val counts = remember(state.jobs) { state.jobs.groupingBy { it.status }.eachCount() }
    val shown = remember(state.jobs, f) { state.filteredApplications(now) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val over = state.activeCount > state.settings.shortlistLimit
                    Text(
                        "Active applications: ${state.activeCount} / ${state.settings.shortlistLimit}",
                        fontWeight = FontWeight.SemiBold,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Shortlist only what you'll really apply to. Each shortlisted job gets a tailored kit; " +
                            "move it to Applied when you've applied and the follow-up date is set for you.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterHeader(
                    active = f.isActive,
                    summary = if (f.isActive) "Showing: ${f.statuses.sortedBy { it.ordinal }.joinToString { it.label }}"
                    else "Showing: active applications (select statuses to change)",
                    onClear = { vm.setApplicationFilters(ApplicationFilters()) }
                )
                MultiChipRow(JobStatus.APPLICATION_VIEW, f.statuses, { "${it.emoji} ${it.label} (${counts[it] ?: 0})" }) {
                    vm.setApplicationFilters(f.copy(statuses = f.statuses.toggle(it)))
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyHint(
                    when {
                        f.isActive -> "Nothing with these statuses."
                        else -> "Nothing shortlisted yet. In Matches, tap ⭐ Shortlist on the jobs you really want to apply to."
                    }
                )
            }
        }
        items(shown, key = { it.id }) { job ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                JobCard(
                    job, state.settings.matchThreshold, state.isTopChoice(job),
                    footer = nextAction(job, now) ?: dismissedFooter(job)
                ) { onOpenJob(job.id) }
                val dates = listOfNotNull(
                    job.shortlistedAt.takeIf { it > 0 }?.let { "Shortlisted ${formatDay(it)}" },
                    job.appliedAt.takeIf { it > 0 }?.let { "Applied ${formatDay(it)}" },
                    job.events.filter { it.startMillis >= now }.minByOrNull { it.startMillis }
                        ?.let { "${it.type.emoji} ${formatDayTime(it.startMillis)}" + if (it.calendarEventId != null) " 📅" else "" },
                    job.notes.takeIf { it.isNotBlank() }?.lineSequence()?.first()?.take(60)
                )
                if (dates.isNotEmpty()) Text(dates.joinToString(" · "), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
