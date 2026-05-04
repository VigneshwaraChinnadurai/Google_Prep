package com.vc.jobfinder.ui.resume

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vc.jobfinder.data.JobFinderRepository
import com.vc.jobfinder.domain.Resume
import com.vc.jobfinder.parser.ResumeParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ResumeUiState {
    data object Idle : ResumeUiState
    data object Parsing : ResumeUiState
    data class Error(val message: String) : ResumeUiState
}

@HiltViewModel
class ResumeViewModel @Inject constructor(
    private val parser: ResumeParser,
    private val repo: JobFinderRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow<ResumeUiState>(ResumeUiState.Idle)
    val ui: StateFlow<ResumeUiState> = _ui

    val resume: StateFlow<Resume?> = repo.resumeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun upload(bytes: ByteArray, filename: String) = viewModelScope.launch {
        _ui.value = ResumeUiState.Parsing
        val result = withContext(Dispatchers.IO) {
            runCatching {
                parser.parse(bytes.inputStream(), filename)
            }
        }
        result
            .onSuccess { r ->
                repo.saveResume(r)
                _ui.value = ResumeUiState.Idle
            }
            .onFailure { t ->
                _ui.value = ResumeUiState.Error(t.message ?: "Parse failed")
            }
    }
}

@Composable
fun ResumeScreen(vm: ResumeViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsState()
    val resume by vm.resume.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else "resume.pdf"
        } ?: "resume.pdf"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@rememberLauncherForActivityResult
        vm.upload(bytes, name)
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Resume", style = MaterialTheme.typography.headlineMedium)

        Button(
            onClick = {
                picker.launch(arrayOf(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ))
            },
            enabled = ui !is ResumeUiState.Parsing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(8.dp))
            Text(if (resume == null) "Pick PDF or DOCX" else "Replace resume")
        }

        when (val s = ui) {
            ResumeUiState.Idle -> Unit
            ResumeUiState.Parsing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Text("Parsing on device…")
            }
            is ResumeUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
        }

        resume?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.fullName.ifBlank { "(name not detected)" },
                        style = MaterialTheme.typography.titleLarge)
                    if (r.email.isNotBlank())
                        Text(r.email, style = MaterialTheme.typography.bodyMedium)
                    r.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    r.linkedinUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    r.githubUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    r.portfolioUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    Text("${r.rawText.length} characters extracted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (resume == null && ui is ResumeUiState.Idle) {
            Text(
                "On-device parsing uses regex heuristics — fields may be incomplete. " +
                "The full text goes to the LLM during scoring, so name/email gaps don't hurt match quality.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
