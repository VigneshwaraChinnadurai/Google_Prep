package com.vc.jobfinder.data.remote

import com.vc.jobfinder.data.local.SettingsStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigHolder @Inject constructor(store: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile var config: ApiConfig
        private set
    @Volatile var client: HttpClient
        private set

    init {
        val initial = runBlocking { store.settings.first() }
        config = ApiConfig(initial.baseUrl, initial.apiKey)
        client = buildClient(config)

        scope.launch {
            store.settings.collect { s ->
                val next = ApiConfig(s.baseUrl, s.apiKey)
                if (next != config) {
                    val old = client
                    config = next
                    client = buildClient(next)
                    runCatching { old.close() }
                }
            }
        }
    }
}
