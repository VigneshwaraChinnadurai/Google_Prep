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
import com.vc.jobfinder.data.remote.ResumeDto
import com.vc.jobfinder.data.repository.JobFinderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ResumeUiState {
    data object Empty : ResumeUiState
    data object Uploading : ResumeUiState
    data class Loaded(val resume: ResumeDto) : ResumeUiState
    data class Error(val message: String) : ResumeUiState
}

@HiltViewModel
class ResumeViewModel @Inject constructor(
    private val repo: JobFinderRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ResumeUiState>(ResumeUiState.Empty)
    val state: StateFlow<ResumeUiState> = _state

    fun upload(bytes: ByteArray, filename: String) = viewModelScope.launch {
        _state.value = ResumeUiState.Uploading
        _state.value = runCatching { repo.uploadResume(bytes, filename) }
            .fold({ ResumeUiState.Loaded(it) }, { ResumeUiState.Error(it.message ?: "Upload failed") })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResumeScreen(vm: ResumeViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()

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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(8.dp))
            Text("Pick PDF or DOCX")
        }

        when (val s = state) {
            ResumeUiState.Empty -> Text("No resume uploaded yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ResumeUiState.Uploading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Text("Parsing…")
            }
            is ResumeUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is ResumeUiState.Loaded -> ParsedResumeCard(s.resume)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParsedResumeCard(r: ResumeDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${r.first_name} ${r.last_name}", style = MaterialTheme.typography.titleLarge)
            Text(r.email, style = MaterialTheme.typography.bodyMedium)
            r.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (r.top_skills.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    r.top_skills.take(12).forEach { skill ->
                        AssistChip(onClick = {}, label = { Text(skill) })
                    }
                }
            }
        }
    }
}
