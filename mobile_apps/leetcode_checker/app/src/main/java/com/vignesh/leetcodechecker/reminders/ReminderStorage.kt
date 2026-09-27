package com.vignesh.leetcodechecker.reminders

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ReminderStorage {
    private const val PREFS = "leetcode_reminders_prefs"
    private const val KEY_REMINDERS = "reminders_json"
    private const val KEY_CATEGORIES = "reminder_categories_json"

    fun loadReminders(context: Context): List<Reminder> {
        val raw = prefs(context).getString(KEY_REMINDERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> reminderFromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun saveReminders(context: Context, reminders: List<Reminder>) {
        val arr = JSONArray()
        reminders.forEach { arr.put(reminderToJson(it)) }
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

    fun loadCategories(context: Context): List<ReminderCategory> {
        val raw = prefs(context).getString(KEY_CATEGORIES, null) ?: return DEFAULT_CATEGORIES
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ReminderCategory(name = obj.getString("name"), colorHex = obj.getString("colorHex"))
            }
        }.getOrDefault(DEFAULT_CATEGORIES).ifEmpty { DEFAULT_CATEGORIES }
    }

    fun saveCategories(context: Context, categories: List<ReminderCategory>) {
        val arr = JSONArray()
        categories.forEach { c -> arr.put(JSONObject().put("name", c.name).put("colorHex", c.colorHex)) }
        prefs(context).edit().putString(KEY_CATEGORIES, arr.toString()).apply()
    }

    private fun reminderToJson(r: Reminder): JSONObject {
        val rule = r.repeatRule
        val weekdaysArr = JSONArray()
        rule.specificWeekdays.forEach { weekdaysArr.put(it) }
        return JSONObject()
            .put("id", r.id)
            .put("title", r.title)
            .put("notes", r.notes)
            .put("dueAtMillis", r.dueAtMillis)
            .put("repeatUnit", rule.unit.name)
            .put("repeatInterval", rule.interval)
            .put("weeklyMode", rule.weeklyMode.name)
            .put("specificWeekdays", weekdaysArr)
            .put("monthlyMode", rule.monthlyMode.name)
            .put("category", r.category)
            .put("isImportant", r.isImportant)
            .put("isCompleted", r.isCompleted)
            .put("photoUri", r.photoUri)
            .put("voiceMemoPath", r.voiceMemoPath)
            .put("googleCalendarEventId", r.googleCalendarEventId ?: JSONObject.NULL)
            .put("createdAtMillis", r.createdAtMillis)
    }

    private fun reminderFromJson(obj: JSONObject): Reminder {
        val weekdays = mutableSetOf<Int>()
        obj.optJSONArray("specificWeekdays")?.let { arr ->
            for (i in 0 until arr.length()) weekdays.add(arr.getInt(i))
        }
        val rule = RepeatRule(
            unit = runCatching { RepeatUnit.valueOf(obj.optString("repeatUnit", "NONE")) }.getOrDefault(RepeatUnit.NONE),
            interval = obj.optInt("repeatInterval", 1).coerceAtLeast(1),
            weeklyMode = runCatching { WeeklyMode.valueOf(obj.optString("weeklyMode", "EVERY_INTERVAL")) }
                .getOrDefault(WeeklyMode.EVERY_INTERVAL),
            specificWeekdays = weekdays,
            monthlyMode = runCatching { MonthlyMode.valueOf(obj.optString("monthlyMode", "SAME_DAY")) }
                .getOrDefault(MonthlyMode.SAME_DAY)
        )
        return Reminder(
            id = obj.getString("id"),
            title = obj.getString("title"),
            notes = obj.optString("notes", ""),
            dueAtMillis = obj.getLong("dueAtMillis"),
            repeatRule = rule,
            category = obj.optString("category", "General"),
            isImportant = obj.optBoolean("isImportant", false),
            isCompleted = obj.optBoolean("isCompleted", false),
            photoUri = obj.optString("photoUri", "").ifBlank { null },
            voiceMemoPath = obj.optString("voiceMemoPath", "").ifBlank { null },
            googleCalendarEventId = if (obj.isNull("googleCalendarEventId")) null else obj.optLong("googleCalendarEventId"),
            createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
