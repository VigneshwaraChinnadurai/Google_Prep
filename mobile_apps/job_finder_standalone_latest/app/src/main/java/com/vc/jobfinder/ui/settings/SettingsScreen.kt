package com.vc.jobfinder.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vc.jobfinder.data.local.AppSettings
import com.vc.jobfinder.data.local.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {
    val settings = store.settings.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        AppSettings(false, 0.5f, "gemini-2.5-flash")
    )

    fun saveAll(apiKey: String, model: String, minScore: Float) = viewModelScope.launch {
        if (apiKey.isNotBlank()) store.saveApiKey(apiKey)
        store.saveLlmModel(model)
        store.saveMinScore(minScore)
    }

    fun clearKey() = store.clearApiKey()
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel(), onSaved: () -> Unit) {
    val s by vm.settings.collectAsState()
    val uri = LocalUriHandler.current

    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var model by remember(s) { mutableStateOf(s.llmModel) }
    var minScore by remember(s) { mutableFloatStateOf(s.minFitScore) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // API key
        Text("Gemini API key", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(if (s.hasApiKey) "Replace key (leave blank to keep)" else "Paste your key") },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { keyVisible = !keyVisible }) {
                    Text(if (keyVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Get a key")
            }
            Spacer(Modifier.weight(1f))
            if (s.hasApiKey) {
                TextButton(onClick = { vm.clearKey() }) { Text("Clear stored key") }
            }
        }

        HorizontalDivider()

        Text("Model", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Gemini model") },
            singleLine = true,
            supportingText = { Text("e.g. gemini-2.5-flash, gemini-2.5-pro") },
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Text("Default minimum fit score: ${"%.2f".format(minScore)}",
            style = MaterialTheme.typography.titleMedium)
        Slider(value = minScore, onValueChange = { minScore = it },
            valueRange = 0f..1f, steps = 19)
        Text("Matches below this score won't appear by default in the Matches tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { vm.saveAll(apiKey, model, minScore); onSaved() },
            enabled = (s.hasApiKey || apiKey.isNotBlank()) && model.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save and continue") }

        Text(
            "Your key is stored in EncryptedSharedPreferences on this device only. " +
            "It is never sent anywhere except to Google's Gemini API.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
