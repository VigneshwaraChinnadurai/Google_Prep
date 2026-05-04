package com.vc.jobfinder.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vc.jobfinder.data.JobFinderRepository
import com.vc.jobfinder.data.local.SettingsStore
import com.vc.jobfinder.domain.MatchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MatchesViewModel @Inject constructor(
    private val repo: JobFinderRepository,
    settings: SettingsStore,
) : ViewModel() {

    private val _minScore = MutableStateFlow(0.5f)
    val minScore: StateFlow<Float> = _minScore

    init {
        // Initialize from settings once
        viewModelScope.launch {
            settings.settings.map { it.minFitScore }.collect { _minScore.value = it }
        }
    }

    val matches: StateFlow<List<MatchResult>> = _minScore
        .flatMapLatest { score -> repo.matchesFlow(score.toDouble()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMinScore(v: Float) { _minScore.value = v }
    fun approve(id: String) = viewModelScope.launch { repo.setMatchStatus(id, "approved") }
    fun skip(id: String) = viewModelScope.launch { repo.setMatchStatus(id, "skipped") }
}

@Composable
fun MatchesScreen(
    vm: MatchesViewModel = hiltViewModel(),
    onOpenApply: (String) -> Unit,
) {
    val matches by vm.matches.collectAsState()
    val minScore by vm.minScore.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("Matches", style = MaterialTheme.typography.headlineMedium)
            Text("Min fit score: ${"%.2f".format(minScore)}",
                style = MaterialTheme.typography.bodySmall)
            Slider(value = minScore, onValueChange = vm::setMinScore,
                valueRange = 0f..1f, steps = 19)
        }

        if (matches.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No matches above this threshold yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(matches, key = { it.id }) { m ->
                    MatchCard(
                        m = m,
                        onApprove = { vm.approve(m.id) },
                        onSkip = { vm.skip(m.id) },
                        onApply = { onOpenApply(m.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchCard(
    m: MatchResult,
    onApprove: () -> Unit, onSkip: () -> Unit, onApply: () -> Unit,
) {
    val scoreColor = when {
        m.fitScore >= 0.8 -> Color(0xFF2E7D32)
        m.fitScore >= 0.6 -> Color(0xFFEF6C00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(m.job.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${m.job.company}${m.job.location?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScoreBadge(m.fitScore, scoreColor)
            }

            if (m.matchedSkills.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    m.matchedSkills.take(8).forEach { s ->
                        SuggestionChip(onClick = {}, label = { Text(s) })
                    }
                }
            }

            Text(m.fitReasoning,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3, overflow = TextOverflow.Ellipsis)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (m.status == "new") {
                    OutlinedButton(onClick = onSkip) { Text("Skip") }
                    Button(onClick = onApprove) { Text("Approve") }
                } else {
                    AssistChip(onClick = {}, label = { Text(m.status.replaceFirstChar { it.uppercase() }) })
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onApply) {
                    Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Double, color: Color) {
    Box(
        Modifier.size(56.dp).clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("%.0f".format(score * 100), color = color, style = MaterialTheme.typography.titleMedium)
    }
}
