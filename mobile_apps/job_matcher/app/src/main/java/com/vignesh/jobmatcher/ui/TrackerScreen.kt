package com.vignesh.jobmatcher.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.UiState
import com.vignesh.jobmatcher.model.JobStatus

/**
 * Applications: the jobs you shortlisted and everything after. Filter by status; each card
 * shows the one next action and the nearest upcoming date.
 */
@Composable
fun TrackerScreen(state: UiState, onOpenJob: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("ACTIVE") }
    val now = System.currentTimeMillis()
    val counts = remember(state.jobs) { state.tracked.groupingBy { it.status }.eachCount() }
    val shown = remember(state.jobs, filter) {
        val base = when (filter) {
            "ACTIVE" -> state.tracked.filter { it.status in JobStatus.ACTIVE }
            "ALL" -> state.tracked
            else -> state.tracked.filter { it.status.name == filter }
        }
        // Soonest upcoming date first, then most recently updated.
        base.sortedWith(
            compareBy<com.vignesh.jobmatcher.model.Job> { j -> j.events.filter { it.startMillis >= now }.minOfOrNull { it.startMillis } ?: Long.MAX_VALUE }
                .thenByDescending { it.statusUpdatedAt }
        )
    }

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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter == "ACTIVE", onClick = { filter = "ACTIVE" }, label = { Text("Active (${state.activeCount})", fontSize = 12.sp) })
                JobStatus.TRACKED.forEach { s ->
                    FilterChip(
                        selected = filter == s.name,
                        onClick = { filter = s.name },
                        label = { Text("${s.emoji} ${s.label} (${counts[s] ?: 0})", fontSize = 12.sp) }
                    )
                }
                FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" }, label = { Text("All", fontSize = 12.sp) })
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyHint(
                    if (state.tracked.isEmpty()) "Nothing shortlisted yet. In Matches, tap ⭐ Shortlist on the jobs you really want to apply to."
                    else "No applications with this status."
                )
            }
        }
        items(shown, key = { it.id }) { job ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                JobCard(job, state.settings.matchThreshold, state.isTopChoice(job), footer = nextAction(job, now)) { onOpenJob(job.id) }
                val dates = listOfNotNull(
                    job.shortlistedAt.takeIf { it > 0 }?.let { "Shortlisted ${formatDay(it)}" },
                    job.appliedAt.takeIf { it > 0 }?.let { "Applied ${formatDay(it)}" },
                    job.events.filter { it.startMillis >= now }.minByOrNull { it.startMillis }
                        ?.let { "${it.type.emoji} ${formatDayTime(it.startMillis)}" + if (it.calendarEventId != null) " 📅" else "" }
                )
                if (dates.isNotEmpty()) Text(dates.joinToString(" · "), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
