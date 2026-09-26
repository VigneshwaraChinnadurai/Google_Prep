package com.vignesh.leetcodechecker.reminders

import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportableEvent(
    val title: String,
    val startMillis: Long
)

/**
 * Reads upcoming events from Android's system Calendar Provider -- this is what already
 * backs Google Calendar sync on the device, so no OAuth/Google Calendar API call is
 * needed, just the READ_CALENDAR permission this app already requests elsewhere (the
 * LeetCode-completion calendar event). Scoped to calendars whose account type is
 * "com.google" since the ask was specifically Google Calendar, not every calendar synced
 * to the device (e.g. a Samsung Calendar account would show up separately).
 */
object GoogleCalendarImporter {
    suspend fun fetchUpcomingGoogleEvents(context: Context, windowDays: Int = 90): List<ImportableEvent> =
        withContext(Dispatchers.IO) {
            runCatching {
                val googleCalendarIds = mutableSetOf<Long>()
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars._ID),
                    "${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
                    arrayOf("com.google"),
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) googleCalendarIds.add(cursor.getLong(0))
                }
                if (googleCalendarIds.isEmpty()) return@withContext emptyList()

                val now = System.currentTimeMillis()
                val windowEnd = now + windowDays * 24L * 60 * 60 * 1000
                val placeholders = googleCalendarIds.joinToString(",") { "?" }
                val selection = "${CalendarContract.Events.CALENDAR_ID} IN ($placeholders) AND " +
                    "${CalendarContract.Events.DTSTART}>=? AND ${CalendarContract.Events.DTSTART}<=?"
                val selectionArgs = (googleCalendarIds.map { it.toString() } + listOf(now.toString(), windowEnd.toString()))
                    .toTypedArray()

                val results = mutableListOf<ImportableEvent>()
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                        results.add(ImportableEvent(title = title, startMillis = cursor.getLong(1)))
                    }
                }
                results
            }.getOrDefault(emptyList())
        }
}
