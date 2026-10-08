package com.vignesh.jobmatcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.UiState
import com.vignesh.jobmatcher.model.JobStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrackerScreen(state: UiState, onOpenJob: (String) -> Unit) {
    val grouped = remember(state.jobs) {
        state.tracked.groupBy { it.status }.mapValues { (_, v) -> v.sortedByDescending { it.statusUpdatedAt } }
    }
    val fmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (grouped.isEmpty()) {
            item { EmptyHint("Nothing tracked yet. Open a match and mark it Saved or Applied.") }
        }
        JobStatus.TRACKED.forEach { status ->
            val jobs = grouped[status].orEmpty()
            if (jobs.isEmpty()) return@forEach
            item(key = "h-${status.name}") { SectionHeader("${status.emoji} ${status.label} (${jobs.size})") }
            items(jobs, key = { it.id }) { job -> Column {
                JobCard(job, state.settings.matchThreshold) { onOpenJob(job.id) }
                val since = if (job.appliedAt > 0) "Applied ${fmt.format(Date(job.appliedAt))}" else
                    "Updated ${fmt.format(Date(job.statusUpdatedAt))}"
                Text(
                    since + if (job.notes.isNotBlank()) " · ${job.notes.lineSequence().first().take(60)}" else "",
                    fontSize = 11.sp
                )
            } }
        }
    }
}
