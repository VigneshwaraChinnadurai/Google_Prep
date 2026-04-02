package com.vignesh.leetcodechecker.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Storage for AI News settings and tracked people
 */
object AINewsStorage {
    private const val PREFS_NAME = "ai_news_prefs"
    private const val KEY_TRACKED_PEOPLE = "tracked_people"
    private const val KEY_NEWS_API_KEY = "news_api_key"
    private const val KEY_ENABLED_CATEGORIES = "enabled_categories"
    private const val KEY_CACHED_NEWS = "cached_news"
    private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val trackedPeopleAdapter = moshi.adapter<List<TrackedPerson>>(
        Types.newParameterizedType(List::class.java, TrackedPerson::class.java)
    )
    
    private val newsAdapter = moshi.adapter<List<NewsArticle>>(
        Types.newParameterizedType(List::class.java, NewsArticle::class.java)
    )
    
    // Save tracked people
    fun saveTrackedPeople(context: Context, people: List<TrackedPerson>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = trackedPeopleAdapter.toJson(people)
        prefs.edit().putString(KEY_TRACKED_PEOPLE, json).apply()
    }
    
    // Load tracked people (with defaults)
    fun loadTrackedPeople(context: Context): List<TrackedPerson> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TRACKED_PEOPLE, null)
        return if (json != null) {
            try {
                trackedPeopleAdapter.fromJson(json) ?: DEFAULT_AI_PEOPLE
            } catch (e: Exception) {
                DEFAULT_AI_PEOPLE
            }
        } else {
            DEFAULT_AI_PEOPLE
        }
    }
    
    // Add a new person to track
    fun addTrackedPerson(context: Context, person: TrackedPerson) {
        val currentPeople = loadTrackedPeople(context).toMutableList()
        if (!currentPeople.any { it.name.equals(person.name, ignoreCase = true) }) {
            currentPeople.add(person)
            saveTrackedPeople(context, currentPeople)
        }
    }
    
    // Remove a tracked person
    fun removeTrackedPerson(context: Context, name: String) {
        val currentPeople = loadTrackedPeople(context).toMutableList()
        currentPeople.removeAll { it.name.equals(name, ignoreCase = true) }
        saveTrackedPeople(context, currentPeople)
    }
    
    // Toggle person enabled status
    fun togglePersonEnabled(context: Context, name: String) {
        val currentPeople = loadTrackedPeople(context).toMutableList()
        val index = currentPeople.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (index >= 0) {
            val person = currentPeople[index]
            currentPeople[index] = person.copy(isEnabled = !person.isEnabled)
            saveTrackedPeople(context, currentPeople)
        }
    }
    
    // Save news API key (optional - can use free tier)
    fun saveNewsApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NEWS_API_KEY, apiKey).apply()
    }
    
    fun getNewsApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Using a demo key - users can set their own
        return prefs.getString(KEY_NEWS_API_KEY, "") ?: ""
    }
    
    // Save enabled categories
    fun saveEnabledCategories(context: Context, categories: Set<NewsCategory>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_ENABLED_CATEGORIES, categories.map { it.name }.toSet()).apply()
    }
    
    fun loadEnabledCategories(context: Context): Set<NewsCategory> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_ENABLED_CATEGORIES, null)
        return if (savedSet != null) {
            savedSet.mapNotNull { name ->
                try { NewsCategory.valueOf(name) } catch (e: Exception) { null }
            }.toSet()
        } else {
            NewsCategory.entries.toSet() // All enabled by default
        }
    }
    
    // Cache news articles for offline viewing
    fun cacheNews(context: Context, articles: List<NewsArticle>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = newsAdapter.toJson(articles)
        prefs.edit()
            .putString(KEY_CACHED_NEWS, json)
            .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
            .apply()
    }
    
    fun getCachedNews(context: Context): List<NewsArticle> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CACHED_NEWS, null)
        return if (json != null) {
            try {
                newsAdapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    fun getLastFetchTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_FETCH_TIME, 0)
    }
    
    // Check if cache is stale (older than 1 hour)
    fun isCacheStale(context: Context): Boolean {
        val lastFetch = getLastFetchTime(context)
        val oneHourMs = 60 * 60 * 1000
        return System.currentTimeMillis() - lastFetch > oneHourMs
    }
}
