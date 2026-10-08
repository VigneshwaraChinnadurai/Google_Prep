package com.vignesh.jobmatcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.JobViewModel
import com.vignesh.jobmatcher.UiState

@Composable
fun DiscoverScreen(state: UiState, vm: JobViewModel, onOpenJob: (String) -> Unit) {
    val shortlist = remember(state.jobs, state.settings) { state.shortlist }
    val matchCount = remember(state.jobs, state.settings) { state.matches.size }
    val autoCompanies = state.companies.count { it.enabled && it.source.automatic }
    val searchCompanies = state.companies.filter { it.enabled && !it.source.automatic }
    val searchBatch = remember(state.companies, state.settings) { state.nextSearchBatch }
    val batch = minOf(state.settings.scoringBatchSize, shortlist.size)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pipeline", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Stat("Tracked jobs", state.jobs.count { !it.closed })
                        Stat("Awaiting Claude", shortlist.size)
                        Stat("Matches ≥${state.settings.matchThreshold}%", matchCount)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("① Fetch from careers sites", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Pulls live postings from $autoCompanies companies with a public careers API, keeps the ones in " +
                            "your locations, and pre-scores them on-device. Runs automatically every day at " +
                            "${state.settings.autoFetchHour}:00" + if (state.settings.autoFetchEnabled) "." else " (currently off).",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = vm::fetchAll, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.busy) "Working…" else "🔄 Fetch now")
                    }
                    if (state.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (state.progress.isNotBlank()) Text(state.progress, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            ClaudeRoundTripCard(
                title = "② Score the shortlist with Claude",
                description = if (shortlist.isEmpty()) "Nothing waiting. Fetch first, or lower the pre-filter in Settings."
                else "${shortlist.size} jobs passed the on-device pre-filter. Each prompt carries the next $batch; " +
                    "jobs Claude scores ≥${state.settings.matchThreshold}% land in Matches.",
                copyLabel = if (batch > 0) "🤖 Copy next $batch" else "🤖 Copy prompt",
                enabled = !state.busy && shortlist.isNotEmpty(),
                buildPrompt = { vm.scoringPrompt() },
                onPaste = vm::applyScores
            )
        }

        item {
            ClaudeRoundTripCard(
                title = "③ Claude web search",
                description = if (searchCompanies.isEmpty()) "No companies use Claude search. Companies without a public careers API (e.g. Google) go here."
                else "${searchCompanies.size} companies have no public careers API, so Claude searches them in batches " +
                    "(top-choice companies alone, once a day). Next: ${searchBatch.joinToString { it.name }}. " +
                    "Turn on web search in Claude first.",
                copyLabel = if (searchBatch.size == 1) "🌐 Search ${searchBatch.single().name}" else "🌐 Search next ${searchBatch.size}",
                enabled = !state.busy && searchCompanies.isNotEmpty(),
                buildPrompt = { vm.searchPrompt() },
                onPaste = vm::applyFoundJobs
            )
        }

        if (shortlist.isNotEmpty()) {
            item { SectionHeader("Awaiting Claude (${shortlist.size}) · highest local score first") }
            items(shortlist, key = { it.id }) { job ->
                JobCard(job, state.settings.matchThreshold, state.isTopChoice(job)) { onOpenJob(job.id) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
