package com.vignesh.jobmatcher.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobEvent
import java.util.TimeZone

/**
 * Mirrors an application's dated steps (apply-by, follow-up, interview, offer deadline) as
 * events on the phone's primary Google-account calendar via the Calendar Provider -- the
 * same approach as leetcode_checker's GoogleCalendarSync, so they sync to Google Calendar on
 * every device. Each event keeps its calendar id, so later edits update the same event and
 * deleting the date removes it from the calendar.
 */
object CalendarSync {
    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    sealed class Result {
        data class Synced(val calendarEventId: Long) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun hasPermission(context: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun primaryGoogleCalendarId(context: Context): Long? = runCatching {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
            "${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
            arrayOf("com.google"),
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }.getOrNull()

    fun calendarTitle(job: Job, event: JobEvent): String =
        "${event.type.emoji} ${event.title} -- ${job.companyName}: ${job.title}"

    private fun description(job: Job, event: JobEvent): String = buildString {
        if (event.notes.isNotBlank()) appendLine(event.notes).appendLine()
        appendLine("${job.title} at ${job.companyName} (${job.location})")
        appendLine("Posting: ${job.url}")
        append("Tracked in Job Matcher -- status: ${job.status.label}")
    }

    fun upsert(context: Context, job: Job, event: JobEvent): Result {
        if (!hasPermission(context)) return Result.Failed("Calendar permission not granted.")
        val calendarId = primaryGoogleCalendarId(context)
            ?: return Result.Failed("No Google account calendar found on this phone.")
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, calendarTitle(job, event))
            put(CalendarContract.Events.DESCRIPTION, description(job, event))
            put(CalendarContract.Events.DTSTART, event.startMillis)
            put(CalendarContract.Events.DTEND, event.startMillis + event.durationMinutes * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        return runCatching {
            val existing = event.calendarEventId
            val id = if (existing != null && context.contentResolver.update(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing), values, null, null
                ) > 0
            ) existing else insertNew(context, values, event)
            if (id == null) Result.Failed("The calendar refused the event.") else Result.Synced(id)
        }.getOrElse { Result.Failed(it.message ?: "Calendar error.") }
    }

    private fun insertNew(context: Context, values: ContentValues, event: JobEvent): Long? {
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = uri.lastPathSegment?.toLongOrNull() ?: return null
        // Interviews get a 1-hour heads-up; everything else alerts at the time itself.
        val minutesBefore = if (event.type == com.vignesh.jobmatcher.model.EventType.INTERVIEW) 60 else 0
        context.contentResolver.insert(
            CalendarContract.Reminders.CONTENT_URI,
            ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutesBefore)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
        )
        return eventId
    }

    fun delete(context: Context, calendarEventId: Long?) {
        if (calendarEventId == null || !hasPermission(context)) return
        runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, calendarEventId), null, null
            )
        }
    }
}
