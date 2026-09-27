package com.vignesh.leetcodechecker.reminders

import java.util.Calendar

/** Pure computation of a repeating reminder's next occurrence -- kept separate from
 *  ReminderReceiver so the rules (mirroring Samsung Reminder's own repeat options) are
 *  easy to reason about and adjust in isolation. */
object RepeatEngine {

    /** Advances [fromMillis] by one occurrence of [rule], then keeps advancing (for
     *  WEEK/MONTH rules with sub-modes, one "occurrence" can require several field
     *  increments) until the result is strictly after [nowMillis] -- guards against a
     *  stale next-occurrence if the device was off across one or more firings. */
    fun nextOccurrence(fromMillis: Long, rule: RepeatRule, nowMillis: Long = System.currentTimeMillis()): Long {
        if (rule.unit == RepeatUnit.NONE) return fromMillis
        var next = advanceOnce(fromMillis, rule)
        var guard = 0
        while (next <= nowMillis && guard < 1000) {
            next = advanceOnce(next, rule)
            guard++
        }
        return next
    }

    private fun advanceOnce(fromMillis: Long, rule: RepeatRule): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
        when (rule.unit) {
            RepeatUnit.MINUTE -> cal.add(Calendar.MINUTE, rule.interval)
            RepeatUnit.HOUR -> cal.add(Calendar.HOUR_OF_DAY, rule.interval)
            RepeatUnit.DAY -> cal.add(Calendar.DAY_OF_YEAR, rule.interval)
            RepeatUnit.YEAR -> cal.add(Calendar.YEAR, rule.interval)
            RepeatUnit.WEEK -> advanceWeekly(cal, rule)
            RepeatUnit.MONTH -> advanceMonthly(cal, rule)
            RepeatUnit.NONE -> {}
        }
        return cal.timeInMillis
    }

    private fun advanceWeekly(cal: Calendar, rule: RepeatRule) {
        when (rule.weeklyMode) {
            WeeklyMode.EVERY_INTERVAL -> cal.add(Calendar.WEEK_OF_YEAR, rule.interval)
            WeeklyMode.WEEKDAYS_ONLY -> {
                do { cal.add(Calendar.DAY_OF_YEAR, 1) } while (isWeekend(cal.get(Calendar.DAY_OF_WEEK)))
            }
            WeeklyMode.WEEKENDS_ONLY -> {
                do { cal.add(Calendar.DAY_OF_YEAR, 1) } while (!isWeekend(cal.get(Calendar.DAY_OF_WEEK)))
            }
            WeeklyMode.SPECIFIC_DAYS -> {
                val days = rule.specificWeekdays.ifEmpty { setOf(cal.get(Calendar.DAY_OF_WEEK)) }
                do { cal.add(Calendar.DAY_OF_YEAR, 1) } while (cal.get(Calendar.DAY_OF_WEEK) !in days)
            }
        }
    }

    private fun advanceMonthly(cal: Calendar, rule: RepeatRule) {
        when (rule.monthlyMode) {
            MonthlyMode.SAME_DAY -> {
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, 1) // avoid month-length rollover skew (e.g. Jan 31 -> Mar 3)
                cal.add(Calendar.MONTH, rule.interval)
                val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceAtMost(lastDay))
            }
            MonthlyMode.LAST_DAY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, rule.interval)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            MonthlyMode.NTH_WEEKDAY -> {
                val weekday = cal.get(Calendar.DAY_OF_WEEK)
                val occurrenceIndex = (cal.get(Calendar.DAY_OF_MONTH) - 1) / 7 // 0-based: 1st, 2nd, 3rd...
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, rule.interval)
                val targetMonth = cal.get(Calendar.MONTH)
                // First occurrence of the target weekday in the new month, then jump ahead
                // by the same occurrence index (1st/2nd/3rd/...) as the original due date.
                while (cal.get(Calendar.DAY_OF_WEEK) != weekday) cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.add(Calendar.WEEK_OF_YEAR, occurrenceIndex)
                // Requested occurrence doesn't exist this month (e.g. a 5th Friday) -- fall
                // back to the last occurrence of that weekday instead of spilling into the
                // next month.
                if (cal.get(Calendar.MONTH) != targetMonth) {
                    cal.add(Calendar.WEEK_OF_YEAR, -1)
                }
            }
        }
    }

    fun describe(rule: RepeatRule): String {
        if (rule.unit == RepeatUnit.NONE) return "Don't repeat"
        val unitWord = rule.unit.name.lowercase()
        return when (rule.unit) {
            RepeatUnit.WEEK -> when (rule.weeklyMode) {
                WeeklyMode.EVERY_INTERVAL -> everyLabel(rule.interval, "week")
                WeeklyMode.WEEKDAYS_ONLY -> "Every weekday"
                WeeklyMode.WEEKENDS_ONLY -> "Every weekend"
                WeeklyMode.SPECIFIC_DAYS -> if (rule.specificWeekdays.isEmpty()) "Weekly"
                    else "Weekly on " + rule.specificWeekdays.sorted().joinToString(", ") { WEEKDAY_LABELS[it - 1] }
            }
            RepeatUnit.MONTH -> when (rule.monthlyMode) {
                MonthlyMode.SAME_DAY -> everyLabel(rule.interval, "month")
                MonthlyMode.LAST_DAY -> "Monthly, last day"
                MonthlyMode.NTH_WEEKDAY -> "Monthly, same weekday"
            }
            else -> everyLabel(rule.interval, unitWord)
        }
    }

    private fun everyLabel(interval: Int, unit: String): String =
        if (interval <= 1) "Every $unit" else "Every $interval ${unit}s"
}
