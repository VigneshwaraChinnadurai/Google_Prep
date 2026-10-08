package com.vignesh.jobmatcher.data

import android.content.Context
import com.vignesh.jobmatcher.model.AppSettings
import com.vignesh.jobmatcher.model.CandidateProfile
import com.vignesh.jobmatcher.model.Company
import com.vignesh.jobmatcher.model.Job
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * All persisted state. Settings live in one JSON blob in SharedPreferences (same
 * convention as leetcode_checker's AppSettingsStore); companies, jobs, profile and resume
 * are JSON/text files in context.filesDir (internal storage -- not getExternalFilesDir,
 * which can transiently EACCES).
 *
 * Every read-modify-write goes through a `synchronized(lock)` block because the daily
 * WorkManager fetch and the UI can touch jobs.json concurrently.
 */
object AppStorage {
    private const val PREFS = "job_matcher_prefs"
    private const val KEY_SETTINGS = "settings_json"
    private const val COMPANIES_FILE = "companies.json"
    private const val JOBS_FILE = "jobs.json"
    private const val PROFILE_FILE = "profile.json"
    private const val RESUME_FILE = "resume.txt"

    private val lock = Any()

    // ---- Settings ------------------------------------------------------------------

    fun loadSettings(context: Context): AppSettings {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SETTINGS, null)
            ?: return AppSettings()
        return runCatching { JsonCodec.settingsFromJson(JSONObject(raw)) }.getOrDefault(AppSettings())
    }

    fun saveSettings(context: Context, settings: AppSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SETTINGS, JsonCodec.settingsToJson(settings).toString())
            .apply()
    }

    // ---- Companies -----------------------------------------------------------------

    fun loadCompanies(context: Context): List<Company> = synchronized(lock) {
        val file = File(context.filesDir, COMPANIES_FILE)
        if (!file.exists()) {
            val seeded = DefaultData.companies(context)
            writeCompanies(context, seeded)
            return seeded
        }
        runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { JsonCodec.companyFromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun updateCompanies(context: Context, transform: (List<Company>) -> List<Company>): List<Company> =
        synchronized(lock) {
            val updated = transform(loadCompanies(context))
            writeCompanies(context, updated)
            updated
        }

    private fun writeCompanies(context: Context, companies: List<Company>) {
        val arr = JSONArray()
        companies.forEach { arr.put(JsonCodec.companyToJson(it)) }
        atomicWrite(File(context.filesDir, COMPANIES_FILE), arr.toString())
    }

    // ---- Jobs ----------------------------------------------------------------------

    fun loadJobs(context: Context): List<Job> = synchronized(lock) {
        val file = File(context.filesDir, JOBS_FILE)
        if (!file.exists()) return emptyList()
        runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { JsonCodec.jobFromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun updateJobs(context: Context, transform: (List<Job>) -> List<Job>): List<Job> = synchronized(lock) {
        val updated = transform(loadJobs(context))
        val arr = JSONArray()
        updated.forEach { arr.put(JsonCodec.jobToJson(it)) }
        atomicWrite(File(context.filesDir, JOBS_FILE), arr.toString())
        updated
    }

    // ---- Profile & resume ----------------------------------------------------------

    fun loadProfile(context: Context): CandidateProfile = synchronized(lock) {
        val file = File(context.filesDir, PROFILE_FILE)
        if (file.exists()) {
            runCatching { JsonCodec.profileFromJson(JSONObject(file.readText())) }.getOrNull()
                ?.let { return it }
        }
        DefaultData.profile(context)
    }

    fun saveProfile(context: Context, profile: CandidateProfile) = synchronized(lock) {
        atomicWrite(File(context.filesDir, PROFILE_FILE), JsonCodec.profileToJson(profile).toString(2))
    }

    fun loadResume(context: Context): String {
        val file = File(context.filesDir, RESUME_FILE)
        return if (file.exists()) file.readText() else DefaultData.resume(context)
    }

    fun saveResume(context: Context, text: String) {
        atomicWrite(File(context.filesDir, RESUME_FILE), text)
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }
}
