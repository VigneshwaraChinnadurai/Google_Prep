package com.vignesh.leetcodechecker.reminders

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ReminderStorage {
    private const val PREFS = "leetcode_reminders_prefs"
    private const val KEY_REMINDERS = "reminders_json"

    fun loadReminders(context: Context): List<Reminder> {
        val raw = prefs(context).getString(KEY_REMINDERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Reminder(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    notes = obj.optString("notes", ""),
                    dueAtMillis = obj.getLong("dueAtMillis"),
                    repeat = runCatching { ReminderRepeat.valueOf(obj.optString("repeat", "NONE")) }
                        .getOrDefault(ReminderRepeat.NONE),
                    category = obj.optString("category", "General"),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveReminders(context: Context, reminders: List<Reminder>) {
        val arr = JSONArray()
        reminders.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("title", r.title)
                    .put("notes", r.notes)
                    .put("dueAtMillis", r.dueAtMillis)
                    .put("repeat", r.repeat.name)
                    .put("category", r.category)
                    .put("isCompleted", r.isCompleted)
                    .put("createdAtMillis", r.createdAtMillis)
            )
        }
        prefs(context).edit().putString(KEY_REMINDERS, arr.toString()).apply()
    }

    fun addReminder(context: Context, reminder: Reminder) {
        saveReminders(context, loadReminders(context) + reminder)
    }

    fun updateReminder(context: Context, reminder: Reminder) {
        saveReminders(context, loadReminders(context).map { if (it.id == reminder.id) reminder else it })
    }

    fun deleteReminder(context: Context, id: String) {
        saveReminders(context, loadReminders(context).filterNot { it.id == id })
    }

    fun categories(context: Context): List<String> {
        return (loadReminders(context).map { it.category } + "General").distinct().sorted()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
