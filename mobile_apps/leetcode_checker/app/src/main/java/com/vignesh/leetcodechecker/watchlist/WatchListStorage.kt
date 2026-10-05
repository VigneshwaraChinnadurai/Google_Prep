package com.vignesh.leetcodechecker.watchlist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object WatchListStorage {
    private const val PREFS = "leetcode_watchlist_prefs"
    private const val KEY_VIDEOS = "watchlist_videos_json"
    private const val KEY_CATEGORIES = "watchlist_categories_json"

    fun loadVideos(context: Context): List<BookmarkedVideo> {
        val raw = prefs(context).getString(KEY_VIDEOS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                BookmarkedVideo(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    url = obj.getString("url"),
                    videoId = obj.getString("videoId"),
                    category = obj.optString("category", "Uncategorized"),
                    isWatched = obj.optBoolean("isWatched", false),
                    importedAtMillis = obj.optLong("importedAtMillis", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveVideos(context: Context, videos: List<BookmarkedVideo>) {
        val arr = JSONArray()
        videos.forEach { v ->
            arr.put(
                JSONObject()
                    .put("id", v.id)
                    .put("title", v.title)
                    .put("url", v.url)
                    .put("videoId", v.videoId)
                    .put("category", v.category)
                    .put("isWatched", v.isWatched)
                    .put("importedAtMillis", v.importedAtMillis)
            )
        }
        prefs(context).edit().putString(KEY_VIDEOS, arr.toString()).apply()
    }

    fun loadCategories(context: Context): List<WatchListCategory> {
        val raw = prefs(context).getString(KEY_CATEGORIES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                WatchListCategory(name = obj.getString("name"), colorHex = obj.getString("colorHex"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveCategories(context: Context, categories: List<WatchListCategory>) {
        val arr = JSONArray()
        categories.forEach { c -> arr.put(JSONObject().put("name", c.name).put("colorHex", c.colorHex)) }
        prefs(context).edit().putString(KEY_CATEGORIES, arr.toString()).apply()
    }

    /**
     * Replaces the whole video list with [parsed] (true "refresh" semantics -- a video
     * removed from Chrome bookmarks disappears here too), while preserving isWatched and
     * the original importedAtMillis for videos that already existed (matched by URL).
     * Also assigns a persistent color to any newly-seen category.
     */
    fun mergeImport(context: Context, parsed: List<BookmarkedVideo>): List<BookmarkedVideo> {
        val existingByUrl = loadVideos(context).associateBy { it.url }
        val merged = parsed.map { new ->
            existingByUrl[new.url]?.let { old -> new.copy(id = old.id, isWatched = old.isWatched, importedAtMillis = old.importedAtMillis) }
                ?: new
        }
        saveVideos(context, merged)

        val existingCategories = loadCategories(context).associateBy { it.name }.toMutableMap()
        merged.map { it.category }.distinct().forEach { name ->
            if (name !in existingCategories) {
                existingCategories[name] = WatchListCategory(name, watchlistColorFor(existingCategories.size))
            }
        }
        val categories = existingCategories.values.toList()
        saveCategories(context, categories)

        return merged
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
