package com.vc.jobfinder.ui.pipeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vc.jobfinder.data.remote.PipelineStateDto
import com.vc.jobfinder.data.repository.JobFinderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PipelineUiState(
    val phase: String = "idle",          // current running phase label
    val progress: Float = 0f,
    val message: String = "",
    val pipelineState: PipelineStateDto? = null,
    val isRunning: Boolean = false,
)

@HiltViewModel
class PipelineViewModel @Inject constructor(
    private val repo: JobFinderRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PipelineUiState())
    val state: StateFlow<PipelineUiState> = _state

    init { loadState() }

    fun loadState() = viewModelScope.launch {
        runCatching { repo.pipelineState() }
            .onSuccess { ps ->
                _state.update { it.copy(pipelineState = ps) }
            }
    }

    fun runFullPipeline() = viewModelScope.launch {
        _state.update { it.copy(isRunning = true, phase = "starting", progress = 0f, message = "Starting pipeline…") }
        try {
            val runId = repo.startFullPipeline()
            repo.runEvents(runId).collect { ev ->
                if (ev.heartbeat) return@collect
                _state.update { it.copy(
                    phase = ev.phase.ifEmpty { it.phase },
                    progress = ev.progress.toFloat(),
                    message = ev.message,
                ) }
                if (ev.status == "done") {
                    _state.update { it.copy(isRunning = false, phase = "done", progress = 1f) }
                    loadState()
                    return@collect
                }
                if (ev.status == "error") {
                    _state.update { it.copy(isRunning = false, phase = "error") }
                    loadState()
                    return@collect
                }
            }
        } catch (t: Throwable) {
            _state.update { it.copy(isRunning = false, phase = "error", message = t.message ?: "Unknown error") }
            loadState()
        }
    }

    fun resumePipeline() = viewModelScope.launch {
        _state.update { it.copy(isRunning = true, phase = "resuming", progress = 0f, message = "Resuming pipeline…") }
        try {
            val runId = repo.resumePipeline()
            repo.runEvents(runId).collect { ev ->
                if (ev.heartbeat) return@collect
                _state.update { it.copy(
                    phase = ev.phase.ifEmpty { it.phase },
                    progress = ev.progress.toFloat(),
                    message = ev.message,
                ) }
                if (ev.status == "done") {
                    _state.update { it.copy(isRunning = false, phase = "done", progress = 1f) }
                    loadState()
                    return@collect
                }
                if (ev.status == "error") {
                    _state.update { it.copy(isRunning = false, phase = "error") }
                    loadState()
                    return@collect
                }
            }
        } catch (t: Throwable) {
            _state.update { it.copy(isRunning = false, phase = "error", message = t.message ?: "Unknown error") }
            loadState()
        }
    }

    fun resetPipeline() = viewModelScope.launch {
        runCatching { repo.pipelineReset() }
        _state.update { PipelineUiState() }
        loadState()
    }
}

@Composable
fun PipelineScreen(vm: PipelineViewModel = hiltViewModel(), onSeeMatches: () -> Unit) {
    val s by vm.state.collectAsState()
    val ps = s.pipelineState

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pipeline", style = MaterialTheme.typography.headlineMedium)

        // ── Phase status cards ──
        if (ps != null) {
            val phaseNames = listOf("scrape" to "Scrape Jobs", "match" to "Match to Resume", "competitions" to "Fetch Competitions")
            phaseNames.forEach { (key, label) ->
                val phase = ps.phases[key]
                if (phase != null) {
                    PhaseCard(label = label, phase = phase, isActive = s.phase == key && s.isRunning)
                }
            }
        }

        // ── Live progress ──
        if (s.isRunning) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Running: ${s.phase}", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(s.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (s.phase == "error") {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Pipeline stopped", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    Text(s.message, style = MaterialTheme.typography.bodySmall)
                    Text("You can resume from where it stopped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Action buttons ──
        val canResume = ps?.can_resume == true && !s.isRunning
        val isComplete = ps?.is_complete == true
        val hasError = ps?.phases?.values?.any { it.status == "error" } == true

        if (canResume && hasError) {
            // Resume from error
            Button(
                onClick = vm::resumePipeline,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00))
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Resume pipeline")
            }
        }

        Button(
            onClick = {
                if (isComplete || !canResume) {
                    vm.resetPipeline()
                    vm.runFullPipeline()
                } else {
                    vm.runFullPipeline()
                }
            },
            enabled = !s.isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (isComplete) Icons.Default.Refresh else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isComplete) "Run again (fresh)" else "Run full pipeline")
        }

        OutlinedButton(
            onClick = onSeeMatches,
            enabled = ps?.phases?.get("match")?.status == "done",
            modifier = Modifier.fillMaxWidth(),
        ) { Text("See matches") }

        if (ps != null && !s.isRunning) {
            TextButton(
                onClick = vm::resetPipeline,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Reset pipeline") }
        }
    }
}

@Composable
private fun PhaseCard(label: String, phase: com.vc.jobfinder.data.remote.PipelinePhaseDto, isActive: Boolean) {
    val (bgColor, iconContent) = when (phase.status) {
        "done" -> MaterialTheme.colorScheme.secondaryContainer to @Composable {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
        }
        "error" -> MaterialTheme.colorScheme.errorContainer to @Composable {
            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        }
        "running" -> MaterialTheme.colorScheme.primaryContainer to @Composable {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        else -> MaterialTheme.colorScheme.surfaceVariant to @Composable {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )
        }
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else bgColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            iconContent()
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                if (phase.message.isNotEmpty()) {
                    Text(phase.message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (phase.status == "done" || phase.status == "running") {
                Text(
                    "${(phase.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
