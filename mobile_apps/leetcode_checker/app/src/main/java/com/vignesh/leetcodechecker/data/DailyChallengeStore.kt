package com.vignesh.leetcodechecker.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Storage for automatically fetched daily challenges.
 * Allows the challenge to persist even when the app is not open.
 */
object DailyChallengeStore {
    private const val PREFS_NAME = "daily_challenge_store"
    
    private const val KEY_QUESTION_ID = "question_id"
    private const val KEY_TITLE = "title"
    private const val KEY_TITLE_SLUG = "title_slug"
    private const val KEY_DIFFICULTY = "difficulty"
    private const val KEY_DATE = "date"
    private const val KEY_TOPICS = "topics"
    private const val KEY_FETCH_TIME = "fetch_time"
    private const val KEY_AUTO_FETCHED = "auto_fetched"
    
    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Save a fetched daily challenge to storage.
     */
    fun saveFetchedChallenge(
        context: Context,
        questionId: String,
        title: String,
        titleSlug: String,
        difficulty: String,
        date: String,
        topics: List<String>
    ) {
        prefs(context).edit().apply {
            putString(KEY_QUESTION_ID, questionId)
            putString(KEY_TITLE, title)
            putString(KEY_TITLE_SLUG, titleSlug)
            putString(KEY_DIFFICULTY, difficulty)
            putString(KEY_DATE, date)
            putString(KEY_TOPICS, topics.joinToString(","))
            putLong(KEY_FETCH_TIME, System.currentTimeMillis())
            putBoolean(KEY_AUTO_FETCHED, true)
            apply()
        }
    }
    
    /**
     * Get the stored challenge if it was fetched today.
     * Returns null if no challenge is stored or if it's from a previous day.
     */
    fun getTodaysChallenge(context: Context): FetchedChallenge? {
        val prefs = prefs(context)
        val fetchTime = prefs.getLong(KEY_FETCH_TIME, 0)
        
        // Check if fetched today
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val fetchDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(fetchTime))
        
        if (today != fetchDate) {
            return null // Challenge is stale
        }
        
        val questionId = prefs.getString(KEY_QUESTION_ID, null) ?: return null
        val title = prefs.getString(KEY_TITLE, null) ?: return null
        val titleSlug = prefs.getString(KEY_TITLE_SLUG, null) ?: return null
        val difficulty = prefs.getString(KEY_DIFFICULTY, null) ?: return null
        val date = prefs.getString(KEY_DATE, null) ?: return null
        val topicsStr = prefs.getString(KEY_TOPICS, "") ?: ""
        val topics = if (topicsStr.isBlank()) emptyList() else topicsStr.split(",")
        val autoFetched = prefs.getBoolean(KEY_AUTO_FETCHED, false)
        
        return FetchedChallenge(
            questionId = questionId,
            title = title,
            titleSlug = titleSlug,
            difficulty = difficulty,
            date = date,
            topics = topics,
            fetchTime = fetchTime,
            autoFetched = autoFetched
        )
    }
    
    /**
     * Check if we have a valid challenge for today (auto-fetched at 6 AM).
     */
    fun hasTodaysChallenge(context: Context): Boolean {
        return getTodaysChallenge(context) != null
    }
    
    /**
     * Clear stored challenge (useful when reset is triggered).
     */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

/**
 * Data class for a fetched challenge.
 */
data class FetchedChallenge(
    val questionId: String,
    val title: String,
    val titleSlug: String,
    val difficulty: String,
    val date: String,
    val topics: List<String>,
    val fetchTime: Long,
    val autoFetched: Boolean
)
