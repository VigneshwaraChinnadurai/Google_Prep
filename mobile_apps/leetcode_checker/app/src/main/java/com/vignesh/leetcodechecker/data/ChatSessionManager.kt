package com.vignesh.leetcodechecker.data

import android.content.Context
import android.util.Log
import com.vignesh.leetcodechecker.models.ChatMessage
import com.vignesh.leetcodechecker.models.CostInfo
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class representing a saved chat session.
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String = generateDefaultName(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val totalCostUsd: Double = 0.0,
    val apiCalls: Int = 0,
    val turnCount: Int = 0,
    val chatMode: String = "QUICK_CHAT"
) {
    companion object {
        fun generateDefaultName(): String {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            return "Session ${sdf.format(Date())}"
        }
    }

    val preview: String
        get() {
            val lastUserMsg = messages.lastOrNull { it.role == "user" }
            return lastUserMsg?.content?.take(80) ?: "Empty session"
        }

    val messageCount: Int get() = messages.size
}

/**
 * Manages multiple chat sessions with persistence via SharedPreferences.
 *
 * Features:
 * - Create / switch / delete / rename sessions
 * - Auto-save active session on every message
 * - Track session list with metadata (name, date, cost, message count)
 * - Persists across app restarts
 */
object ChatSessionManager {
    private const val TAG = "ChatSessionManager"
    private const val PREFS = "chatbot_sessions_prefs"
    private const val KEY_SESSION_LIST = "session_list_json"
    private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
    private const val KEY_SESSION_PREFIX = "session_data_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Session List Operations ─────────────────────────────────

    /**
     * Get all saved session metadata (lightweight, no messages loaded).
     */
    fun loadSessionList(context: Context): List<ChatSession> {
        return try {
            val raw = prefs(context).getString(KEY_SESSION_LIST, null) ?: return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ChatSession(
                    id = obj.getString("id"),
                    name = obj.optString("name", "Unnamed"),
                    createdAt = obj.optLong("createdAt", 0),
                    updatedAt = obj.optLong("updatedAt", 0),
                    totalCostUsd = obj.optDouble("totalCostUsd", 0.0),
                    apiCalls = obj.optInt("apiCalls", 0),
                    turnCount = obj.optInt("turnCount", 0),
                    chatMode = obj.optString("chatMode", "QUICK_CHAT"),
                    // Load message count only for preview, not full messages
                    messages = emptyList()  // Messages loaded separately on demand
                )
            }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session list", e)
            emptyList()
        }
    }

    /**
     * Get the active session ID (null if none).
     */
    fun getActiveSessionId(context: Context): String? {
        return prefs(context).getString(KEY_ACTIVE_SESSION_ID, null)
    }

    /**
     * Set the active session ID.
     */
    fun setActiveSessionId(context: Context, sessionId: String) {
        prefs(context).edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply()
    }

    // ── Full Session CRUD ───────────────────────────────────────

    /**
     * Save a full session (messages + metadata).
     */
    fun saveSession(context: Context, session: ChatSession) {
        try {
            // Save session data (messages + cost)
            val sessionJson = JSONObject().apply {
                put("id", session.id)
                put("name", session.name)
                put("createdAt", session.createdAt)
                put("updatedAt", session.updatedAt)
                put("totalCostUsd", session.totalCostUsd)
                put("apiCalls", session.apiCalls)
                put("turnCount", session.turnCount)
                put("chatMode", session.chatMode)
                put("messages", messagesToJson(session.messages))
            }
            prefs(context).edit()
                .putString("$KEY_SESSION_PREFIX${session.id}", sessionJson.toString())
                .apply()

            // Update session list (metadata only)
            updateSessionInList(context, session)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session ${session.id}", e)
        }
    }

    /**
     * Load a full session by ID (with messages).
     */
    fun loadSession(context: Context, sessionId: String): ChatSession? {
        return try {
            val raw = prefs(context).getString("$KEY_SESSION_PREFIX$sessionId", null)
                ?: return null
            val json = JSONObject(raw)
            ChatSession(
                id = json.getString("id"),
                name = json.optString("name", "Unnamed"),
                createdAt = json.optLong("createdAt", 0),
                updatedAt = json.optLong("updatedAt", 0),
                totalCostUsd = json.optDouble("totalCostUsd", 0.0),
                apiCalls = json.optInt("apiCalls", 0),
                turnCount = json.optInt("turnCount", 0),
                chatMode = json.optString("chatMode", "QUICK_CHAT"),
                messages = messagesFromJson(json.optJSONArray("messages"))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session $sessionId", e)
            null
        }
    }

    /**
     * Delete a session by ID.
     */
    fun deleteSession(context: Context, sessionId: String) {
        try {
            // Remove session data
            prefs(context).edit()
                .remove("$KEY_SESSION_PREFIX$sessionId")
                .apply()

            // Remove from session list
            val list = loadSessionList(context).toMutableList()
            list.removeAll { it.id == sessionId }
            saveSessionList(context, list)

            // If it was the active session, clear active
            if (getActiveSessionId(context) == sessionId) {
                prefs(context).edit().remove(KEY_ACTIVE_SESSION_ID).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session $sessionId", e)
        }
    }

    /**
     * Rename a session.
     */
    fun renameSession(context: Context, sessionId: String, newName: String) {
        try {
            val session = loadSession(context, sessionId) ?: return
            val updated = session.copy(name = newName, updatedAt = System.currentTimeMillis())
            saveSession(context, updated)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename session $sessionId", e)
        }
    }

    /**
     * Get the number of messages in a session (without loading all data).
     */
    fun getSessionMessageCount(context: Context, sessionId: String): Int {
        return try {
            val raw = prefs(context).getString("$KEY_SESSION_PREFIX$sessionId", null)
                ?: return 0
            val json = JSONObject(raw)
            json.optJSONArray("messages")?.length() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get the preview text for a session.
     */
    fun getSessionPreview(context: Context, sessionId: String): String {
        return try {
            val raw = prefs(context).getString("$KEY_SESSION_PREFIX$sessionId", null)
                ?: return "Empty session"
            val json = JSONObject(raw)
            val messages = json.optJSONArray("messages") ?: return "Empty session"
            // Find last user message
            for (i in messages.length() - 1 downTo 0) {
                val msg = messages.getJSONObject(i)
                if (msg.optString("role") == "user") {
                    return msg.optString("content", "").take(80)
                }
            }
            "Empty session"
        } catch (e: Exception) {
            "Empty session"
        }
    }

    // ── Private helpers ─────────────────────────────────────────

    private fun saveSessionList(context: Context, sessions: List<ChatSession>) {
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("createdAt", s.createdAt)
                put("updatedAt", s.updatedAt)
                put("totalCostUsd", s.totalCostUsd)
                put("apiCalls", s.apiCalls)
                put("turnCount", s.turnCount)
                put("chatMode", s.chatMode)
            })
        }
        prefs(context).edit()
            .putString(KEY_SESSION_LIST, arr.toString())
            .apply()
    }

    private fun updateSessionInList(context: Context, session: ChatSession) {
        val list = loadSessionList(context).toMutableList()
        val idx = list.indexOfFirst { it.id == session.id }
        val meta = session.copy(messages = emptyList())  // Don't store messages in list
        if (idx >= 0) {
            list[idx] = meta
        } else {
            list.add(0, meta)
        }
        saveSessionList(context, list)
    }

    private fun messagesToJson(messages: List<ChatMessage>): JSONArray {
        val arr = JSONArray()
        messages.forEach { msg ->
            arr.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
            })
        }
        return arr
    }

    private fun messagesFromJson(arr: JSONArray?): List<ChatMessage> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ChatMessage(
                role = obj.getString("role"),
                content = obj.getString("content"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }

    /**
     * Migrate from old ChatHistoryStore (single session) to multi-session format.
     * Called once on first launch after the update.
     */
    fun migrateFromLegacy(context: Context) {
        // If we already have sessions, skip migration
        if (loadSessionList(context).isNotEmpty()) return

        // Check if old data exists
        val oldMessages = ChatHistoryStore.loadMessages(context)
        if (oldMessages.isEmpty()) return

        // Create a session from old data
        val oldCost = ChatHistoryStore.loadTotalCostUsd(context)
        val oldApiCalls = ChatHistoryStore.loadApiCallCount(context)
        val oldTurnCount = ChatHistoryStore.loadTurnCount(context)

        val migratedSession = ChatSession(
            name = "Migrated Session",
            messages = oldMessages,
            totalCostUsd = oldCost,
            apiCalls = oldApiCalls,
            turnCount = oldTurnCount
        )

        saveSession(context, migratedSession)
        setActiveSessionId(context, migratedSession.id)

        Log.i(TAG, "Migrated legacy session: ${oldMessages.size} messages, cost=\$${oldCost}")
    }
}
