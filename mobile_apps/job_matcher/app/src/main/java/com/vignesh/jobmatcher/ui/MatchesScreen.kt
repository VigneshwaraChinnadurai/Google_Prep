package com.vignesh.jobmatcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun MatchesScreen(state: UiState, onOpenJob: (String) -> Unit) {
    val threshold = state.settings.matchThreshold
    val matches = remember(state.jobs, state.settings) { state.matches }
    val below = remember(state.jobs, state.settings) { state.belowThreshold }
    var showBelow by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader("Claude-scored ≥$threshold% (${matches.size})")
            Text("Change the cutoff in Setup → Settings.", fontSize = 12.sp)
        }
        if (matches.isEmpty()) {
            item { EmptyHint("No matches yet. Fetch on Discover, then score the shortlist with Claude.") }
        }
        items(matches, key = { it.id }) { job -> JobCard(job, threshold, state.isTopChoice(job)) { onOpenJob(job.id) } }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Show below-threshold (${below.size})", modifier = Modifier.weight(1f))
                Switch(checked = showBelow, onCheckedChange = { showBelow = it })
            }
        }
        if (showBelow) {
            items(below, key = { "below-" + it.id }) { job -> JobCard(job, threshold, state.isTopChoice(job)) { onOpenJob(job.id) } }
        }
    }
}
