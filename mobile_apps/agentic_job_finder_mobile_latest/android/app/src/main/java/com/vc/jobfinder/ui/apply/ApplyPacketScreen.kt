package com.vc.jobfinder.ui.apply

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
import com.vc.jobfinder.data.remote.ApplyPacketDto
import com.vc.jobfinder.data.repository.JobFinderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApplyPacketViewModel @Inject constructor(
    private val repo: JobFinderRepository,
) : ViewModel() {
    private val _packet = MutableStateFlow<ApplyPacketDto?>(null)
    val packet: StateFlow<ApplyPacketDto?> = _packet
    fun load(matchId: String) = viewModelScope.launch {
        _packet.value = runCatching { repo.applyPacket(matchId) }.getOrNull()
    }
}

@Composable
fun ApplyPacketScreen(matchId: String, vm: ApplyPacketViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val packet by vm.packet.collectAsState()

    LaunchedEffect(matchId) { vm.load(matchId) }

    val p = packet
    if (p == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(p.job_title, style = MaterialTheme.typography.headlineSmall)
                Text(p.company, style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cover letter",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(p.cover_letter))
                                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.ContentCopy, null) }
                        }
                        Text(p.cover_letter, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(p.field_hints) { h ->
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
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(p.application_url))
                        ctx.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, null); Spacer(Modifier.width(8.dp))
                    Text("Open application")
                }
            }
        }
    }
}
