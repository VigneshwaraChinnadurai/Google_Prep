package com.vc.jobfinder.ui.jobs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vc.jobfinder.data.JobFinderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunUiState(
    val phase: String = "idle",
    val progress: Float = 0f,
    val message: String = "",
)

@HiltViewModel
class RunViewModel @Inject constructor(
    private val repo: JobFinderRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RunUiState())
    val state: StateFlow<RunUiState> = _state

    val jobCount: StateFlow<Int> = repo.jobCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun runFull() = viewModelScope.launch {
        // Scrape
        _state.update { it.copy(phase = "scraping", progress = 0f, message = "Starting…") }
        repo.runScrape { ev ->
            when (ev) {
                is JobFinderRepository.ScrapeEvent.Progress ->
                    _state.update { it.copy(
                        progress = ev.cumulative.toFloat() / ev.target.coerceAtLeast(1),
                        message = "Scraped ${ev.company} — ${ev.cumulative} jobs",
                    ) }
                is JobFinderRepository.ScrapeEvent.Done ->
                    _state.update { it.copy(progress = 1f, message = "Scraped ${ev.total} jobs") }
                is JobFinderRepository.ScrapeEvent.Error ->
                    _state.update { it.copy(phase = "error", message = ev.message) }
            }
        }
        if (_state.value.phase == "error") return@launch

        // Match
        _state.update { it.copy(phase = "matching", progress = 0f, message = "Scoring…") }
        repo.runMatch { ev ->
            when (ev) {
                is JobFinderRepository.MatchEvent.Progress ->
                    _state.update { it.copy(
                        progress = ev.done.toFloat() / ev.total.coerceAtLeast(1),
                        message = "Matched ${ev.done}/${ev.total}",
                    ) }
                is JobFinderRepository.MatchEvent.Done ->
                    _state.update { it.copy(phase = "done", progress = 1f, message = "Done — ${ev.total} matches scored") }
                is JobFinderRepository.MatchEvent.Error ->
                    _state.update { it.copy(phase = "error", message = ev.message) }
            }
        }
    }
}

@Composable
fun RunScreen(vm: RunViewModel = hiltViewModel(), onSeeMatches: () -> Unit) {
    val s by vm.state.collectAsState()
    val jobs by vm.jobCount.collectAsState()
    val running = s.phase in listOf("scraping", "matching")

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Run", style = MaterialTheme.typography.headlineMedium)
        Text("$jobs jobs cached", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Phase: ${s.phase}", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Text(s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.phase == "error")
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = vm::runFull,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Run scrape + match") }

        OutlinedButton(
            onClick = onSeeMatches,
            enabled = s.phase == "done" || s.phase == "idle",
            modifier = Modifier.fillMaxWidth(),
        ) { Text("See matches") }

        Text(
            "Scoring runs on Gemini and uses one API call per cached job. " +
            "Battery and data usage scale with company count.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
