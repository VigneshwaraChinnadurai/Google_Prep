package com.vc.jobfinder.ui.competitions

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vc.jobfinder.data.remote.CompetitionDto
import com.vc.jobfinder.data.repository.JobFinderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompetitionsUiState(
    val items: List<CompetitionDto> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val refreshMessage: String = "",
    val trackedOnly: Boolean = false,
    val hiringOnly: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CompetitionsViewModel @Inject constructor(
    private val repo: JobFinderRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CompetitionsUiState())
    val state: StateFlow<CompetitionsUiState> = _state

    init { load() }

    fun setTrackedOnly(v: Boolean) {
        _state.update { it.copy(trackedOnly = v) }
        load()
    }

    fun setHiringOnly(v: Boolean) {
        _state.update { it.copy(hiringOnly = v) }
        load()
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching {
            repo.listCompetitions(
                trackedOnly = _state.value.trackedOnly,
                hiringOnly = _state.value.hiringOnly,
            )
        }.onSuccess { items ->
            _state.update { it.copy(loading = false, items = items) }
        }.onFailure { t ->
            _state.update { it.copy(loading = false, error = t.message) }
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(refreshing = true, refreshMessage = "Starting…") }
        try {
            val runId = repo.refreshCompetitions()
            repo.competitionEvents(runId).collect { ev ->
                _state.update { it.copy(refreshMessage = ev.message) }
                if (ev.status == "done" || ev.status == "error") {
                    _state.update { it.copy(refreshing = false) }
                    load()
                    return@collect
                }
            }
        } catch (t: Throwable) {
            _state.update { it.copy(refreshing = false, error = t.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionsScreen(vm: CompetitionsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        // Header + filters
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Contests",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f))
                IconButton(
                    onClick = vm::refresh,
                    enabled = !s.refreshing,
                ) {
                    if (s.refreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                FilterChip(
                    selected = s.trackedOnly,
                    onClick = { vm.setTrackedOnly(!s.trackedOnly) },
                    label = { Text("Tracked companies") },
                    leadingIcon = if (s.trackedOnly) {
                        { Icon(Icons.Default.Verified, null, Modifier.size(18.dp)) }
                    } else null,
                )
                FilterChip(
                    selected = s.hiringOnly,
                    onClick = { vm.setHiringOnly(!s.hiringOnly) },
                    label = { Text("Hiring only") },
                    leadingIcon = if (s.hiringOnly) {
                        { Icon(Icons.Default.Work, null, Modifier.size(18.dp)) }
                    } else null,
                )
            }
            if (s.refreshing) {
                Spacer(Modifier.height(8.dp))
                Text(s.refreshMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            s.loading && s.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            s.error != null && s.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(s.error ?: "Error", color = MaterialTheme.colorScheme.error)
                }

            s.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        if (s.trackedOnly || s.hiringOnly)
                            "No contests match these filters. Try refreshing."
                        else "No contests cached. Tap refresh to fetch.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(s.items, key = { it.id }) { c ->
                    CompetitionCard(c, onOpen = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.registration_url)))
                    })
                }
            }
        }
    }
}

@Composable
private fun CompetitionCard(c: CompetitionDto, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top row: platform pill + tracked badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformPill(c.platform)
                Spacer(Modifier.width(8.dp))
                if (c.is_tracked_company) TrackedBadge()
                if (c.is_hiring) {
                    Spacer(Modifier.width(6.dp))
                    HiringBadge()
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.OpenInNew, null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(c.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)

            // Sponsor — show matched canonical name if available
            val sponsorLabel = c.matched_company ?: c.sponsor_company
            if (!sponsorLabel.isNullOrBlank()) {
                Text(
                    if (c.is_tracked_company && c.matched_company != null)
                        "${c.matched_company} ✓"
                    else sponsorLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (c.is_tracked_company)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            // Footer line: dates + prize
            val footer = listOfNotNull(
                c.starts_at?.take(10),
                c.ends_at?.let { "→ ${it.take(10)}" },
                c.prize_pool?.takeIf { it.isNotBlank() }?.let { "₹$it" },
                c.location,
            ).joinToString(" · ")
            if (footer.isNotBlank()) {
                Text(footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlatformPill(platform: String) {
    val (label, color) = when (platform) {
        "hackerearth" -> "HackerEarth" to Color(0xFF323754)
        "hackerrank"  -> "HackerRank"  to Color(0xFF1BA94C)
        "unstop"      -> "Unstop"      to Color(0xFF2A52BE)
        "kaggle"      -> "Kaggle"      to Color(0xFF20BEFF)
        else          -> platform.replaceFirstChar { it.uppercase() } to Color.Gray
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = color)
    }
}

@Composable
private fun TrackedBadge() {
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Verified, null, Modifier.size(12.dp), tint = color)
            Spacer(Modifier.width(3.dp))
            Text("In your list", style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun HiringBadge() {
    val color = Color(0xFFEF6C00)
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text("Hiring", style = MaterialTheme.typography.labelSmall, color = color)
    }
}
