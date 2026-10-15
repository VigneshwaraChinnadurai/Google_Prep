package com.vc.jobfinder.ui.contests

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
import com.vc.jobfinder.data.JobFinderRepository
import com.vc.jobfinder.domain.Competition
import com.vc.jobfinder.domain.Platform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContestsUiState(
    val refreshing: Boolean = false,
    val refreshMessage: String = "",
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContestsViewModel @Inject constructor(
    private val repo: JobFinderRepository,
) : ViewModel() {
    private val _trackedOnly = MutableStateFlow(false)
    val trackedOnly: StateFlow<Boolean> = _trackedOnly

    private val _hiringOnly = MutableStateFlow(false)
    val hiringOnly: StateFlow<Boolean> = _hiringOnly

    private val _ui = MutableStateFlow(ContestsUiState())
    val ui: StateFlow<ContestsUiState> = _ui

    val items: StateFlow<List<Competition>> =
        combine(_trackedOnly, _hiringOnly) { t, h -> t to h }
            .flatMapLatest { (t, h) -> repo.competitionsFlow(t, h) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTrackedOnly(v: Boolean) { _trackedOnly.value = v }
    fun setHiringOnly(v: Boolean) { _hiringOnly.value = v }

    fun refresh() = viewModelScope.launch {
        _ui.value = ContestsUiState(refreshing = true, refreshMessage = "Starting…")
        repo.refreshContests { ev ->
            when (ev) {
                is JobFinderRepository.ContestEvent.Progress ->
                    _ui.value = _ui.value.copy(
                        refreshMessage = "${ev.platform}: ${ev.count} contests",
                    )
                is JobFinderRepository.ContestEvent.Done ->
                    _ui.value = ContestsUiState(refreshMessage = "${ev.total} total")
                is JobFinderRepository.ContestEvent.Error ->
                    _ui.value = ContestsUiState(error = ev.message)
            }
        }
    }
}

@Composable
fun ContestsScreen(vm: ContestsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val items by vm.items.collectAsState()
    val tracked by vm.trackedOnly.collectAsState()
    val hiring by vm.hiringOnly.collectAsState()
    val ui by vm.ui.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Contests",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f))
                IconButton(
                    onClick = vm::refresh,
                    enabled = !ui.refreshing,
                ) {
                    if (ui.refreshing) {
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
                    selected = tracked,
                    onClick = { vm.setTrackedOnly(!tracked) },
                    label = { Text("Tracked companies") },
                    leadingIcon = if (tracked) {
                        { Icon(Icons.Default.Verified, null, Modifier.size(18.dp)) }
                    } else null,
                )
                FilterChip(
                    selected = hiring,
                    onClick = { vm.setHiringOnly(!hiring) },
                    label = { Text("Hiring only") },
                    leadingIcon = if (hiring) {
                        { Icon(Icons.Default.Work, null, Modifier.size(18.dp)) }
                    } else null,
                )
            }
            if (ui.refreshing) {
                Spacer(Modifier.height(8.dp))
                Text(ui.refreshMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ui.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (tracked || hiring)
                        "No contests match these filters."
                    else "No contests cached. Tap refresh to fetch.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { c ->
                    CompetitionCard(c, onOpen = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.registrationUrl)))
                    })
                }
            }
        }
    }
}

@Composable
private fun CompetitionCard(c: Competition, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformPill(c.platform)
                Spacer(Modifier.width(8.dp))
                if (c.isTrackedCompany) TrackedBadge()
                if (c.isHiring) {
                    Spacer(Modifier.width(6.dp))
                    HiringBadge()
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(c.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)

            val sponsorLabel = c.matchedCompany ?: c.sponsorCompany
            if (!sponsorLabel.isNullOrBlank()) {
                Text(
                    if (c.isTrackedCompany && c.matchedCompany != null)
                        "${c.matchedCompany} ✓"
                    else sponsorLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (c.isTrackedCompany)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            val footer = listOfNotNull(
                c.startsAt?.take(10),
                c.endsAt?.let { "→ ${it.take(10)}" },
                c.prizePool?.takeIf { it.isNotBlank() },
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
private fun PlatformPill(platform: Platform) {
    val color = when (platform) {
        Platform.HACKEREARTH -> Color(0xFF323754)
        Platform.HACKERRANK  -> Color(0xFF1BA94C)
        Platform.UNSTOP      -> Color(0xFF2A52BE)
        Platform.KAGGLE      -> Color(0xFF20BEFF)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(platform.displayName,
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
