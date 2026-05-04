package com.vc.jobfinder.ui.matches

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vc.jobfinder.data.JobFinderRepository
import com.vc.jobfinder.domain.ApplyPacket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ApplyUiState {
    data object Loading : ApplyUiState
    data class Loaded(val packet: ApplyPacket, val applyUrl: String) : ApplyUiState
    data class Error(val message: String) : ApplyUiState
}

@HiltViewModel
class ApplyPacketViewModel @Inject constructor(
    private val repo: JobFinderRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ApplyUiState>(ApplyUiState.Loading)
    val state: StateFlow<ApplyUiState> = _state

    fun load(matchId: String) = viewModelScope.launch {
        _state.value = ApplyUiState.Loading
        runCatching {
            val packet = repo.buildApplyPacket(matchId)
                ?: throw IllegalStateException("Match or resume not found")
            // Re-query for the apply URL — the packet doesn't carry it
            val matchesNow = repo.matchesFlow(0.0).first()
            val applyUrl = matchesNow.firstOrNull { it.id == matchId }?.job?.applyUrl ?: ""
            packet to applyUrl
        }.onSuccess { (packet, url) ->
            _state.value = ApplyUiState.Loaded(packet, url)
        }.onFailure { t ->
            _state.value = ApplyUiState.Error(t.message ?: "Failed")
        }
    }
}

@Composable
fun ApplyPacketScreen(matchId: String, vm: ApplyPacketViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by vm.state.collectAsState()

    LaunchedEffect(matchId) { vm.load(matchId) }

    when (val s = state) {
        ApplyUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Generating cover letter…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        is ApplyUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(s.message, color = MaterialTheme.colorScheme.error)
        }

        is ApplyUiState.Loaded -> LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cover letter",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(s.packet.coverLetter))
                                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.ContentCopy, null) }
                        }
                        Text(s.packet.coverLetter, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(s.packet.fieldHints) { h ->
                ListItem(
                    headlineContent = { Text(h.label) },
                    supportingContent = { Text(h.value, maxLines = 2) },
                    trailingContent = {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(h.value))
                        }) { Icon(Icons.Default.ContentCopy, null) }
                    }
                )
            }
            item {
                Button(
                    onClick = {
                        if (s.applyUrl.isNotBlank()) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.applyUrl)))
                        }
                    },
                    enabled = s.applyUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.OpenInBrowser, null); Spacer(Modifier.width(8.dp))
                    Text("Open application")
                }
            }
        }
    }
}
