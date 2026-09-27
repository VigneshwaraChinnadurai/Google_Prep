package com.vignesh.leetcodechecker.reminders

import java.util.Calendar
import java.util.UUID

enum class RepeatUnit { NONE, MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }
enum class WeeklyMode { EVERY_INTERVAL, WEEKDAYS_ONLY, WEEKENDS_ONLY, SPECIFIC_DAYS }
enum class MonthlyMode { SAME_DAY, LAST_DAY, NTH_WEEKDAY }

/**
 * Modeled on Samsung Reminder's actual repeat options (confirmed via its string
 * resources): every-N minutes/hours/days/weeks/months/years, a weekly repeat limited to
 * weekdays-only or weekends-only, and a monthly repeat that can mean "same day each
 * month", "last day of the month", or "same Nth weekday" (e.g. "3rd Friday").
 */
data class RepeatRule(
    val unit: RepeatUnit = RepeatUnit.NONE,
    val interval: Int = 1,
    val weeklyMode: WeeklyMode = WeeklyMode.EVERY_INTERVAL,
    val specificWeekdays: Set<Int> = emptySet(), // Calendar.SUNDAY(1)..Calendar.SATURDAY(7), only for WeeklyMode.SPECIFIC_DAYS
    val monthlyMode: MonthlyMode = MonthlyMode.SAME_DAY
)

data class ReminderCategory(
    val name: String,
    val colorHex: String
)

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val dueAtMillis: Long,
    val repeatRule: RepeatRule = RepeatRule(),
    val category: String = "General",
    val isImportant: Boolean = false,
    val isCompleted: Boolean = false,
    val photoUri: String? = null,
    val voiceMemoPath: String? = null,
    val googleCalendarEventId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

val DEFAULT_CATEGORIES = listOf(
    ReminderCategory("General", "#6E7681"),
    ReminderCategory("Work", "#58A6FF"),
    ReminderCategory("Personal", "#39D353"),
    ReminderCategory("Shopping", "#F0883E")
)

val CATEGORY_COLOR_PALETTE = listOf(
    "#6E7681", "#58A6FF", "#39D353", "#F0883E",
    "#A371F7", "#FF7B72", "#FFD700", "#00D4AA"
)

/** Sunday(1)..Saturday(7) short labels, matching java.util.Calendar's day-of-week ints. */
val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

fun isWeekend(dayOfWeek: Int) = dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY
