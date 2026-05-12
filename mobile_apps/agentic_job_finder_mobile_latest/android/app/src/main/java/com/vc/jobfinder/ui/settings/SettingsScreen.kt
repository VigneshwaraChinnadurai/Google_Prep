package com.vc.jobfinder.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vc.jobfinder.data.local.AppSettings
import com.vc.jobfinder.data.local.SettingsStore
import com.vc.jobfinder.data.remote.ConfigHolder
import com.vc.jobfinder.data.remote.ConnectionInfoDto
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Loading : ConnectionState()
    data class Connected(val info: ConnectionInfoDto) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val configHolder: ConfigHolder,
) : ViewModel() {
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings("", ""))
    fun save(url: String, key: String) = viewModelScope.launch { store.save(url, key) }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    fun testConnection() = viewModelScope.launch {
        _connectionState.value = ConnectionState.Loading
        try {
            val resp: ConnectionInfoDto = configHolder.client.get("api/v1/connection-info").body()
            _connectionState.value = ConnectionState.Connected(resp)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel(), onSaved: () -> Unit) {
    val s by vm.settings.collectAsState()
    var url by remember(s) { mutableStateOf(s.baseUrl) }
    var key by remember(s) { mutableStateOf(s.apiKey) }
    val connState by vm.connectionState.collectAsState()

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { vm.save(url, key); onSaved() },
                enabled = url.isNotBlank() && key.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
            OutlinedButton(
                onClick = { vm.save(url, key); vm.testConnection() },
                enabled = url.isNotBlank() && key.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("Test connection") }
        }

        // Connection info card
        when (val state = connState) {
            is ConnectionState.Idle -> {}
            is ConnectionState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is ConnectionState.Connected -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "\u2705 Connected to backend",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        ConnectionRow("Computer", state.info.hostname)
                        ConnectionRow("IP address", state.info.local_ip)
                        ConnectionRow("OS", state.info.os)
                        ConnectionRow("User", state.info.user)
                        ConnectionRow("Python", state.info.python_version)
                    }
                }
            }
            is ConnectionState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "\u274C Connection failed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        Text(
            "ADB wireless → http://localhost:8000/. " +
            "Emulator → http://10.0.2.2:8000/. " +
            "Same Wi-Fi → http://<LAN-ip>:8000/.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
