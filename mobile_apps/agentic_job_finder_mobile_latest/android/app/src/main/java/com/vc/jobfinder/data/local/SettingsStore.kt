package com.vc.jobfinder.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("jobfinder_settings")

data class AppSettings(val baseUrl: String, val apiKey: String) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object K {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY  = stringPreferencesKey("api_key")
    }

    val settings: Flow<AppSettings> = ctx.dataStore.data.map { p ->
        AppSettings(
            baseUrl = p[K.BASE_URL] ?: "http://10.0.2.2:8000/",
            apiKey  = p[K.API_KEY]  ?: "",
        )
    }

    suspend fun save(baseUrl: String, apiKey: String) {
        ctx.dataStore.edit { p ->
            p[K.BASE_URL] = baseUrl.trim().let { if (it.endsWith("/")) it else "$it/" }
            p[K.API_KEY]  = apiKey.trim()
        }
    }
}
