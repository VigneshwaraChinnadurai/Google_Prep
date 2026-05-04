package com.vc.jobfinder.ui.pipeline

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vc.jobfinder.data.repository.JobFinderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PipelineUiState(
    val phase: String = "idle",
    val progress: Float = 0f,
    val message: String = "",
)

@HiltViewModel
class PipelineViewModel @Inject constructor(
    private val repo: JobFinderRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PipelineUiState())
    val state: StateFlow<PipelineUiState> = _state

    fun runFullPipeline() = viewModelScope.launch {
        try {
            _state.update { it.copy(phase = "scraping", progress = 0f, message = "Starting scrape…") }
            val scrapeRun = repo.startScrape()
            repo.runEvents(scrapeRun).collect { ev ->
                _state.update { it.copy(progress = ev.progress.toFloat(), message = ev.message) }
                if (ev.status == "done") return@collect
                if (ev.status == "error") {
                    _state.update { it.copy(phase = "error", message = ev.message) }; return@collect
                }
            }
            _state.update { it.copy(phase = "matching", progress = 0f, message = "Starting match…") }
            val matchRun = repo.startMatch()
            repo.runEvents(matchRun).collect { ev ->
                _state.update { it.copy(progress = ev.progress.toFloat(), message = ev.message) }
                if (ev.status == "done") {
                    _state.update { it.copy(phase = "done", progress = 1f) }; return@collect
                }
            }
        } catch (t: Throwable) {
            _state.update { it.copy(phase = "error", message = t.message ?: "Unknown error") }
        }
    }
}

@Composable
fun PipelineScreen(vm: PipelineViewModel = hiltViewModel(), onSeeMatches: () -> Unit) {
    val s by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pipeline", style = MaterialTheme.typography.headlineMedium)
        Text("Phase: ${s.phase}", style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
        Text(s.message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = vm::runFullPipeline,
            enabled = s.phase !in listOf("scraping", "matching"),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Run scrape + match") }
        OutlinedButton(
            onClick = onSeeMatches,
            enabled = s.phase == "done",
            modifier = Modifier.fillMaxWidth(),
        ) { Text("See matches") }
    }
}
