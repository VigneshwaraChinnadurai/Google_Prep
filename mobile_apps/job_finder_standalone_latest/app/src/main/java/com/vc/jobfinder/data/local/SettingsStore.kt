package com.vc.jobfinder.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("jobfinder_settings")

data class AppSettings(
    val hasApiKey: Boolean,
    val minFitScore: Float,
    val llmModel: String,
)

/**
 * Two storage targets:
 *  - Encrypted prefs for the API key (sensitive)
 *  - DataStore for everything else (model name, threshold)
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val secure: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private object K {
        val MIN_SCORE = floatPreferencesKey("min_score")
        val LLM_MODEL = stringPreferencesKey("llm_model")
    }

    val settings: Flow<AppSettings> = ctx.dataStore.data.map { p ->
        AppSettings(
            hasApiKey = !secure.getString(KEY_API, null).isNullOrBlank(),
            minFitScore = p[K.MIN_SCORE] ?: 0.5f,
            llmModel = p[K.LLM_MODEL] ?: "gemini-2.5-flash",
        )
    }

    fun apiKey(): String? = secure.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    fun saveApiKey(key: String) {
        secure.edit().putString(KEY_API, key.trim()).apply()
    }

    fun clearApiKey() {
        secure.edit().remove(KEY_API).apply()
    }

    suspend fun saveMinScore(v: Float) {
        ctx.dataStore.edit { it[K.MIN_SCORE] = v }
    }

    suspend fun saveLlmModel(name: String) {
        ctx.dataStore.edit { it[K.LLM_MODEL] = name }
    }

    private companion object {
        const val KEY_API = "gemini_api_key"
    }
}
