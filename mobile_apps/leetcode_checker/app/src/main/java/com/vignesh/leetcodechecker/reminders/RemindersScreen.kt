package com.vignesh.leetcodechecker.reminders

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Samsung-Reminder-style reminder list: title/notes, due date+time, repeat, category,
 * grouped Overdue/Today/Upcoming/Completed. Exact-time notifications via ReminderScheduler
 * (AlarmManager), matching this app's existing daily-fetch alarm pattern.
 *
 * Import: Samsung Reminder itself has no public API/export format a third-party app can
 * read (closed system app, no content provider) -- not solvable without rooting the
 * device, which isn't worth it for this. Two real alternatives instead: paste a list of
 * titles in bulk, or import from Google Calendar via Android's system Calendar Provider
 * (same READ_CALENDAR permission already used for the LeetCode-completion event, no OAuth).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBackClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reminders by remember { mutableStateOf(ReminderStorage.loadReminders(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showPasteImport by remember { mutableStateOf(false) }
    var showCalendarImport by remember { mutableStateOf(false) }

    fun persist(updated: List<Reminder>) {
        ReminderStorage.saveReminders(context, updated)
        reminders = updated
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showCalendarImport = true
        else Toast.makeText(context, "Calendar permission needed to import.", Toast.LENGTH_SHORT).show()
    }

    val now = System.currentTimeMillis()
    val todayEnd = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    val active = reminders.filter { !it.isCompleted }
    val overdue = active.filter { it.dueAtMillis < now }.sortedBy { it.dueAtMillis }
    val today = active.filter { it.dueAtMillis in now..todayEnd }.sortedBy { it.dueAtMillis }
    val upcoming = active.filter { it.dueAtMillis > todayEnd }.sortedBy { it.dueAtMillis }
    val completed = reminders.filter { it.isCompleted }.sortedByDescending { it.dueAtMillis }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⏰ Reminders") },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Import")
                        }
                        DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Paste a list...") },
                                onClick = { showImportMenu = false; showPasteImport = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Import from Google Calendar...") },
                                onClick = {
                                    showImportMenu = false
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.READ_CALENDAR
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) showCalendarImport = true
                                    else calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingReminder = null; showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add reminder")
            }
        }
    ) { padding ->
        if (reminders.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏰", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No reminders yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val errorColor = MaterialTheme.colorScheme.error
            val primaryColor = MaterialTheme.colorScheme.primary
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reminderSection(
                    title = "Overdue", items = overdue, titleColor = errorColor,
                    onToggle = { r -> val u = r.copy(isCompleted = true); persist(reminders.map { if (it.id == r.id) u else it }); ReminderScheduler.cancel(context, r.id) },
                    onEdit = { editingReminder = it; showAddDialog = true },
                    onDelete = { r -> persist(reminders.filterNot { it.id == r.id }); ReminderScheduler.cancel(context, r.id) }
                )
                reminderSection(
                    title = "Today", items = today, titleColor = primaryColor,
                    onToggle = { r -> val u = r.copy(isCompleted = true); persist(reminders.map { if (it.id == r.id) u else it }); ReminderScheduler.cancel(context, r.id) },
                    onEdit = { editingReminder = it; showAddDialog = true },
                    onDelete = { r -> persist(reminders.filterNot { it.id == r.id }); ReminderScheduler.cancel(context, r.id) }
                )
                reminderSection(
                    title = "Upcoming", items = upcoming,
                    onToggle = { r -> val u = r.copy(isCompleted = true); persist(reminders.map { if (it.id == r.id) u else it }); ReminderScheduler.cancel(context, r.id) },
                    onEdit = { editingReminder = it; showAddDialog = true },
                    onDelete = { r -> persist(reminders.filterNot { it.id == r.id }); ReminderScheduler.cancel(context, r.id) }
                )
                reminderSection(
                    title = "Completed", items = completed,
                    onToggle = { r -> val u = r.copy(isCompleted = false); persist(reminders.map { if (it.id == r.id) u else it }); ReminderScheduler.schedule(context, u) },
                    onEdit = { editingReminder = it; showAddDialog = true },
                    onDelete = { r -> persist(reminders.filterNot { it.id == r.id }) }
                )
                item { Spacer(modifier = Modifier.height(60.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddEditReminderDialog(
            existing = editingReminder,
            categories = ReminderStorage.categories(context),
            onDismiss = { showAddDialog = false; editingReminder = null },
            onSave = { r ->
                val exists = reminders.any { it.id == r.id }
                persist(if (exists) reminders.map { if (it.id == r.id) r else it } else reminders + r)
                ReminderScheduler.schedule(context, r)
                showAddDialog = false
                editingReminder = null
            }
        )
    }

    if (showPasteImport) {
        PasteImportDialog(
            onDismiss = { showPasteImport = false },
            onImport = { titles, dueAtMillis ->
                val newOnes = titles.map { title ->
                    Reminder(title = title, dueAtMillis = dueAtMillis, category = "Imported")
                }
                persist(reminders + newOnes)
                newOnes.forEach { ReminderScheduler.schedule(context, it) }
                showPasteImport = false
                Toast.makeText(context, "Imported ${newOnes.size} reminder(s).", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showCalendarImport) {
        var events by remember { mutableStateOf<List<ImportableEvent>?>(null) }
        LaunchedEffect(Unit) {
            events = GoogleCalendarImporter.fetchUpcomingGoogleEvents(context)
        }
        CalendarImportDialog(
            events = events,
            onDismiss = { showCalendarImport = false },
            onImport = { selected ->
                val newOnes = selected.map { e -> Reminder(title = e.title, dueAtMillis = e.startMillis, category = "Google Calendar") }
                persist(reminders + newOnes)
                newOnes.forEach { ReminderScheduler.schedule(context, it) }
                showCalendarImport = false
                Toast.makeText(context, "Imported ${newOnes.size} reminder(s).", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reminderSection(
    title: String,
    items: List<Reminder>,
    titleColor: androidx.compose.ui.graphics.Color? = null,
    onToggle: (Reminder) -> Unit,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    if (items.isEmpty()) return
    item {
        Text(
            text = "$title (${items.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = titleColor ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    items(items, key = { it.id }) { reminder ->
        ReminderRow(reminder = reminder, onToggle = { onToggle(reminder) }, onEdit = { onEdit(reminder) }, onDelete = { onDelete(reminder) })
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
    Card(onClick = onEdit) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = reminder.isCompleted, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${dateFormat.format(reminder.dueAtMillis)} · ${reminder.category}" +
                        if (reminder.repeat != ReminderRepeat.NONE) " · ${reminder.repeat.name.lowercase().replaceFirstChar { it.uppercase() }}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun AddEditReminderDialog(
    existing: Reminder?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: categories.firstOrNull() ?: "General") }
    var repeat by remember { mutableStateOf(existing?.repeat ?: ReminderRepeat.NONE) }
    var repeatMenuExpanded by remember { mutableStateOf(false) }
    var dueAtMillis by remember {
        mutableStateOf(
            existing?.dueAtMillis ?: Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    val dateTimeFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Reminder" else "Edit Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { pickDateTime(context, dueAtMillis) { picked -> dueAtMillis = picked } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(dateTimeFormat.format(dueAtMillis))
                }
                Box {
                    OutlinedButton(onClick = { repeatMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Repeat: ${repeat.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    }
                    DropdownMenu(expanded = repeatMenuExpanded, onDismissRequest = { repeatMenuExpanded = false }) {
                        ReminderRepeat.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = { repeat = option; repeatMenuExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        (existing ?: Reminder(title = title, dueAtMillis = dueAtMillis)).copy(
                            title = title.trim(),
                            notes = notes.trim(),
                            dueAtMillis = dueAtMillis,
                            repeat = repeat,
                            category = category.trim().ifBlank { "General" }
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PasteImportDialog(onDismiss: () -> Unit, onImport: (titles: List<String>, dueAtMillis: Long) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var dueAtMillis by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    val dateTimeFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.US) }
    val lineCount = text.lines().count { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste a List") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "One reminder per line. All will share the due date/time below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Reminder titles, one per line") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
                OutlinedButton(
                    onClick = { pickDateTime(context, dueAtMillis) { picked -> dueAtMillis = picked } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Due: ${dateTimeFormat.format(dueAtMillis)}")
                }
                if (lineCount > 0) {
                    Text("$lineCount reminder(s) will be created.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = lineCount > 0,
                onClick = { onImport(text.lines().map { it.trim() }.filter { it.isNotBlank() }, dueAtMillis) }
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CalendarImportDialog(events: List<ImportableEvent>?, onDismiss: () -> Unit, onImport: (List<ImportableEvent>) -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
    val selected = remember { mutableStateMapOf<Int, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import from Google Calendar") },
        text = {
            when {
                events == null -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                events.isEmpty() -> Text("No upcoming Google Calendar events found in the next 90 days.")
                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(events.size) { index ->
                        val event = events[index]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected[index] ?: false,
                                onCheckedChange = { checked -> selected[index] = checked }
                            )
                            Column {
                                Text(event.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    dateFormat.format(event.startMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !events.isNullOrEmpty() && selected.values.any { it },
                onClick = {
                    val chosen = events.orEmpty().filterIndexed { index, _ -> selected[index] == true }
                    onImport(chosen)
                }
            ) { Text("Import Selected") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Chains the framework DatePickerDialog then TimePickerDialog -- no extra dependency. */
private fun pickDateTime(context: android.content.Context, initialMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            cal.set(Calendar.YEAR, year); cal.set(Calendar.MONTH, month); cal.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute); cal.set(Calendar.SECOND, 0)
                    onPicked(cal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}
