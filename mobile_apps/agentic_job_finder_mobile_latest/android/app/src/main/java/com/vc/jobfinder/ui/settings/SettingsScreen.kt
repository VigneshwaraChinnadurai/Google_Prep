package com.vc.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings("", ""))
    fun save(url: String, key: String) = viewModelScope.launch { store.save(url, key) }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel(), onSaved: () -> Unit) {
    val s by vm.settings.collectAsState()
    var url by remember(s) { mutableStateOf(s.baseUrl) }
    var key by remember(s) { mutableStateOf(s.apiKey) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Backend connection", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Base URL") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.save(url, key); onSaved() },
            enabled = url.isNotBlank() && key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        Text(
            "Emulator → host machine = http://10.0.2.2:8000/. " +
            "Physical device on same Wi-Fi = http://<your-LAN-ip>:8000/.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
