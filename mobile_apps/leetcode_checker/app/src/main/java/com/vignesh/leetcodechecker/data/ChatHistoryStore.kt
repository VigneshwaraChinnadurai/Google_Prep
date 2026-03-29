package com.vignesh.leetcodechecker.data

import android.content.Context
import android.util.Log
import com.vignesh.leetcodechecker.models.ChatMessage
import com.vignesh.leetcodechecker.models.CostInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists Strategic Chatbot conversation history and cost info
 * using SharedPreferences. Conversations survive app restarts.
 */
object ChatHistoryStore {
    private const val TAG = "ChatHistoryStore"
    private const val PREFS = "chatbot_history_prefs"
    private const val KEY_MESSAGES = "chat_messages_json"
    private const val KEY_COST = "cost_info_json"
    private const val KEY_TOTAL_COST = "total_cost_usd"
    private const val KEY_API_CALLS = "api_call_count"
    private const val KEY_TURN_COUNT = "turn_count"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Messages ────────────────────────────────────────────────

    fun saveMessages(context: Context, messages: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            messages.forEach { msg ->
                arr.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                    put("timestamp", msg.timestamp)
                })
            }
            prefs(context).edit()
                .putString(KEY_MESSAGES, arr.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save messages", e)
        }
    }

    fun loadMessages(context: Context): List<ChatMessage> {
        return try {
            val raw = prefs(context).getString(KEY_MESSAGES, null) ?: return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ChatMessage(
                    role = obj.getString("role"),
                    content = obj.getString("content"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load messages", e)
            emptyList()
        }
    }

    // ── Cost / Session State ────────────────────────────────────

    fun saveCostInfo(context: Context, costInfo: CostInfo, totalCostUsd: Double, apiCalls: Int, turnCount: Int) {
        try {
            val json = JSONObject().apply {
                put("totalCost", costInfo.totalCost)
                put("dailyBudget", costInfo.dailyBudget)
                put("remainingBudget", costInfo.remainingBudget)
                put("percentUsed", costInfo.percentUsed.toDouble())
                put("lastUpdated", costInfo.lastUpdated)
            }
            prefs(context).edit()
                .putString(KEY_COST, json.toString())
                .putFloat(KEY_TOTAL_COST, totalCostUsd.toFloat())
                .putInt(KEY_API_CALLS, apiCalls)
                .putInt(KEY_TURN_COUNT, turnCount)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cost info", e)
        }
    }

    fun loadCostInfo(context: Context): CostInfo {
        return try {
            val raw = prefs(context).getString(KEY_COST, null) ?: return CostInfo()
            val json = JSONObject(raw)
            CostInfo(
                totalCost = json.optDouble("totalCost", 0.0),
                dailyBudget = json.optDouble("dailyBudget", 5.0),
                remainingBudget = json.optDouble("remainingBudget", 5.0),
                percentUsed = json.optDouble("percentUsed", 0.0).toFloat(),
                lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cost info", e)
            CostInfo()
        }
    }

    fun loadTotalCostUsd(context: Context): Double =
        prefs(context).getFloat(KEY_TOTAL_COST, 0f).toDouble()

    fun loadApiCallCount(context: Context): Int =
        prefs(context).getInt(KEY_API_CALLS, 0)

    fun loadTurnCount(context: Context): Int =
        prefs(context).getInt(KEY_TURN_COUNT, 0)

    // ── Clear ───────────────────────────────────────────────────

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
