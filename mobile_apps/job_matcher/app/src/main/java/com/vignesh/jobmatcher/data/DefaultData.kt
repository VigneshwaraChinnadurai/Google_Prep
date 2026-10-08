package com.vignesh.jobmatcher.data

import android.content.Context
import com.vignesh.jobmatcher.model.CandidateProfile
import com.vignesh.jobmatcher.model.Company
import org.json.JSONArray
import org.json.JSONObject

/**
 * First-run seed data bundled in assets/. The default profile is hand-derived from the
 * Sep 2026 resume so the pre-filter works before any Claude round-trip; running Profile
 * Analysis in the app replaces it.
 */
object DefaultData {
    private fun asset(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    fun resume(context: Context): String = runCatching { asset(context, "default_resume.txt") }.getOrDefault("")

    fun profile(context: Context): CandidateProfile = runCatching {
        JsonCodec.profileFromJson(JSONObject(asset(context, "default_profile.json")))
    }.getOrDefault(CandidateProfile())

    fun companies(context: Context): List<Company> = runCatching {
        val arr = JSONArray(asset(context, "default_companies.json"))
        (0 until arr.length()).map { JsonCodec.companyFromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())
}
