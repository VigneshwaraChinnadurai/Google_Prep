package com.vignesh.leetcodechecker.reminders

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/**
 * Keeps each reminder mirrored as an event on the device's primary Google-account calendar
 * (the same system Calendar Provider GoogleCalendarImporter reads from, and the same
 * READ_CALENDAR/WRITE_CALENDAR permission already used for the LeetCode-completion
 * calendar event) so reminders show up in Google Calendar too. Fire-and-forget by design:
 * a sync failure (permission not granted, no Google account on the device) must never
 * block creating/editing/deleting the reminder itself.
 */
object GoogleCalendarSync {
    private const val DURATION_MILLIS = 30 * 60 * 1000L

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun primaryGoogleCalendarId(context: Context): Long? = runCatching {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
            "${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
            arrayOf("com.google"),
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }.getOrNull()

    /** Inserts or updates the mirrored event; returns the (possibly new) calendar event ID. */
    fun upsert(context: Context, reminder: Reminder): Long? {
        if (!hasPermission(context)) return reminder.googleCalendarEventId
        val calendarId = primaryGoogleCalendarId(context) ?: return reminder.googleCalendarEventId

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, reminder.title)
            put(CalendarContract.Events.DESCRIPTION, reminder.notes)
            put(CalendarContract.Events.DTSTART, reminder.dueAtMillis)
            put(CalendarContract.Events.DTEND, reminder.dueAtMillis + DURATION_MILLIS)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }

        return runCatching {
            val existingId = reminder.googleCalendarEventId
            if (existingId != null) {
                val updated = context.contentResolver.update(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId),
                    values, null, null
                )
                if (updated > 0) existingId else insertNew(context, calendarId, values)
            } else {
                insertNew(context, calendarId, values)
            }
        }.getOrDefault(reminder.googleCalendarEventId)
    }

    private fun insertNew(context: Context, calendarId: Long, values: ContentValues): Long? {
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = uri.lastPathSegment?.toLongOrNull() ?: return null
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 0)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        return eventId
    }

    fun delete(context: Context, eventId: Long?) {
        if (eventId == null || !hasPermission(context)) return
        runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null
            )
        }
    }
}
