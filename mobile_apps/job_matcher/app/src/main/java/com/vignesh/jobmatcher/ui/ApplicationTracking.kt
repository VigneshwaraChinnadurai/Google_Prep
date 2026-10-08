package com.vignesh.jobmatcher.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.JobViewModel
import com.vignesh.jobmatcher.calendar.CalendarSync
import com.vignesh.jobmatcher.data.JobRepository
import com.vignesh.jobmatcher.model.EventType
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobEvent
import com.vignesh.jobmatcher.model.JobStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---- Formatting & "what's next" ---------------------------------------------------------

private val DAY_FMT = SimpleDateFormat("EEE d MMM", Locale.getDefault())
private val DAY_TIME_FMT = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

fun formatDay(millis: Long): String = DAY_FMT.format(Date(millis))
fun formatDayTime(millis: Long): String = DAY_TIME_FMT.format(Date(millis))

/** The one thing to do next on an application, shown on cards in Applications. */
fun nextAction(job: Job, now: Long = System.currentTimeMillis()): String? {
    fun next(type: EventType) = job.events.filter { it.type == type && it.startMillis >= now }.minByOrNull { it.startMillis }
    return when (job.status) {
        JobStatus.SHORTLISTED -> {
            val applyBy = next(EventType.APPLY_BY)?.let { " by ${formatDay(it.startMillis)}" }.orEmpty()
            if (job.tailoring == null) "Next: prepare the application kit, then apply$applyBy"
            else "Next: apply$applyBy, then mark Applied"
        }
        JobStatus.APPLIED -> next(EventType.FOLLOW_UP)?.let { "Next: follow up on ${formatDay(it.startMillis)}" }
            ?: "Waiting to hear back"
        JobStatus.INTERVIEWING -> next(EventType.INTERVIEW)?.let { "Next: ${it.title} -- ${formatDayTime(it.startMillis)}" }
            ?: "Next: add your next interview date"
        JobStatus.OFFER -> next(EventType.OFFER_DEADLINE)?.let { "Next: decide by ${formatDay(it.startMillis)}" }
            ?: "Next: add the decision deadline"
        else -> null
    }
}

// ---- Status --------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusSection(job: Job, busy: Boolean, vm: JobViewModel) {
    SectionHeader("Status")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        JobStatus.entries.forEach { s ->
            FilterChip(
                selected = job.status == s,
                onClick = { vm.setStatus(job.id, s) },
                enabled = !busy,
                label = { Text("${s.emoji} ${s.label}", fontSize = 12.sp) }
            )
        }
    }
    Text("${job.status.emoji} ${job.status.label}: ${job.status.meaning}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (job.history.isNotEmpty()) {
        Text(
            "History: " + job.history.joinToString("  →  ") { "${it.status.emoji} ${it.status.label} ${formatDay(it.at)}" },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---- Dates & Google Calendar ------------------------------------------------------------

@Composable
fun DatesSection(job: Job, busy: Boolean, vm: JobViewModel) {
    val context = LocalContext.current
    var editing by remember(job.id) { mutableStateOf<JobEvent?>(null) }
    var pendingSync by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val eventId = pendingSync
        pendingSync = null
        if (eventId != null && granted.values.all { it }) vm.syncEvent(job.id, eventId)
    }
    fun sync(eventId: String) {
        if (CalendarSync.hasPermission(context)) vm.syncEvent(job.id, eventId)
        else { pendingSync = eventId; permissionLauncher.launch(CalendarSync.PERMISSIONS) }
    }

    SectionHeader("Dates & Google Calendar")
    Text(
        "Apply-by, follow-up, interview and offer-deadline dates. Tap 📅 to add one to your Google Calendar " +
            "(it syncs to every device); edits update the same calendar event.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    job.events.sortedBy { it.startMillis }.forEach { e ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${e.type.emoji} ${e.title}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        formatDayTime(e.startMillis) + when {
                            e.calendarEventId == null -> ""
                            e.calendarStale -> " · edited, calendar out of date"
                            else -> " · ✅ in Google Calendar"
                        },
                        fontSize = 12.sp
                    )
                    if (e.notes.isNotBlank()) Text(e.notes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (e.calendarEventId == null || e.calendarStale) {
                    TextButton(onClick = { sync(e.id) }, enabled = !busy) { Text(if (e.calendarEventId == null) "📅 Add" else "📅 Update") }
                }
                TextButton(onClick = { editing = e }, enabled = !busy) { Text("Edit") }
            }
        }
    }
    OutlinedButton(
        onClick = { editing = JobEvent(id = JobRepository.newEventId(), type = suggestedType(job), title = suggestedType(job).label, startMillis = defaultStart()) },
        enabled = !busy
    ) { Text("➕ Add a date") }

    editing?.let { event ->
        EventDialog(
            initial = event,
            isNew = job.events.none { it.id == event.id },
            onDismiss = { editing = null },
            onSave = { vm.saveEvent(job.id, it); editing = null },
            onDelete = { vm.deleteEvent(job.id, event.id); editing = null }
        )
    }
}

private fun suggestedType(job: Job): EventType = when (job.status) {
    JobStatus.INTERVIEWING -> EventType.INTERVIEW
    JobStatus.OFFER -> EventType.OFFER_DEADLINE
    JobStatus.APPLIED -> EventType.FOLLOW_UP
    else -> EventType.APPLY_BY
}

private fun defaultStart(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventDialog(initial: JobEvent, isNew: Boolean, onDismiss: () -> Unit, onSave: (JobEvent) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(initial.type) }
    var title by remember { mutableStateOf(initial.title) }
    var start by remember { mutableLongStateOf(initial.startMillis) }
    var notes by remember { mutableStateOf(initial.notes) }

    fun pickDate() {
        val c = Calendar.getInstance().apply { timeInMillis = start }
        DatePickerDialog(context, { _, y, m, d ->
            start = Calendar.getInstance().apply { timeInMillis = start; set(y, m, d) }.timeInMillis
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    fun pickTime() {
        val c = Calendar.getInstance().apply { timeInMillis = start }
        TimePickerDialog(context, { _, h, min ->
            start = Calendar.getInstance().apply {
                timeInMillis = start; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min)
            }.timeInMillis
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add a date" else "Edit date") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EventType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                // Keep a custom title; swap the default one along with the type.
                                if (title.isBlank() || EventType.entries.any { it.label == title }) title = t.label
                                type = t
                            },
                            label = { Text("${t.emoji} ${t.label}", fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text("Title (e.g. Round 2 -- System design)") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::pickDate) { Text("📆 ${formatDay(start)}") }
                    OutlinedButton(onClick = ::pickTime) { Text("🕐 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(start))}") }
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, minLines = 2,
                    label = { Text("Notes (interviewer, meeting link, prep…)") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(type = type, title = title.trim().ifBlank { type.label }, startMillis = start, durationMinutes = type.defaultMinutes, notes = notes.trim())) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
