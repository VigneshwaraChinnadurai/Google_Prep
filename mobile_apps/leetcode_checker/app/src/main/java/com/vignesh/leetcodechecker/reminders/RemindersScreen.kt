package com.vignesh.leetcodechecker.reminders

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class SortMode(val label: String) {
    DUE_DATE("Due date"), CREATED_DATE("Date created"), ALPHABETICAL("Alphabetical")
}

/** Dark palette modeled on Samsung Reminder's own near-black theme, kept local to this
 *  screen (matching how BookLibraryScreen scopes its own BookColors) rather than changing
 *  the app-wide MaterialTheme. */
private object ReminderColors {
    val background = Color(0xFF000000)
    val surface = Color(0xFF1C1C1E)
    val onSurface = Color(0xFFF2F2F7)
    val onSurfaceVariant = Color(0xFF9A9A9E)
    val today = Color(0xFFFF5C5C)
    val scheduled = Color(0xFF4DA8FF)
    val important = Color(0xFFFFD166)
    val categoriesAccent = Color(0xFFB388FF)
    val overdue = Color(0xFFFF5C5C)
    val completed = Color(0xFF8E8E93)
    val dueDate = Color(0xFFFF8A5B)
}

/**
 * Samsung-Reminder-style reminder list: title/notes, due date+time, rich repeat rules,
 * colored categories, important flag, photo/voice-memo attachments, grouped
 * Overdue/Today/Upcoming/Completed. Exact-time notifications with a Snooze action via
 * ReminderScheduler/ReminderReceiver. Each reminder is also mirrored to the device's
 * primary Google Calendar (GoogleCalendarSync) so it stays visible there too.
 *
 * Import: Samsung Reminder itself has no public API/export format a third-party app can
 * read -- not solvable without rooting the device. Two real alternatives instead: paste a
 * list of titles in bulk, or import from Google Calendar via Android's system Calendar
 * Provider (same READ_CALENDAR permission, no OAuth).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBackClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reminders by remember { mutableStateOf(ReminderStorage.loadReminders(context)) }
    var categories by remember { mutableStateOf(ReminderStorage.loadCategories(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showPasteImport by remember { mutableStateOf(false) }
    var showCalendarImport by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.DUE_DATE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var importantOnly by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    fun persist(updated: List<Reminder>) {
        ReminderStorage.saveReminders(context, updated)
        reminders = updated
        com.vignesh.leetcodechecker.widget.ReminderWidgetProvider.updateAllWidgets(context)
    }

    fun syncToCalendar(reminder: Reminder) {
        scope.launch {
            val eventId = withContext(Dispatchers.IO) { GoogleCalendarSync.upsert(context, reminder) }
            if (eventId != reminder.googleCalendarEventId) {
                persist(reminders.map { if (it.id == reminder.id) it.copy(googleCalendarEventId = eventId) else it })
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        persist(reminders.filterNot { it.id == reminder.id })
        ReminderScheduler.cancel(context, reminder.id)
        reminder.voiceMemoPath?.let { VoiceMemoRecorder.deleteMemo(it) }
        scope.launch(Dispatchers.IO) { GoogleCalendarSync.delete(context, reminder.googleCalendarEventId) }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }

    val importCalendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showCalendarImport = true
        else Toast.makeText(context, "Calendar permission needed to import.", Toast.LENGTH_SHORT).show()
    }

    val now = System.currentTimeMillis()
    val todayEnd = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    fun sortList(list: List<Reminder>): List<Reminder> = when (sortMode) {
        SortMode.DUE_DATE -> list.sortedWith(compareByDescending<Reminder> { it.isImportant }.thenBy { it.dueAtMillis })
        SortMode.CREATED_DATE -> list.sortedWith(compareByDescending<Reminder> { it.isImportant }.thenByDescending { it.createdAtMillis })
        SortMode.ALPHABETICAL -> list.sortedWith(compareByDescending<Reminder> { it.isImportant }.thenBy { it.title.lowercase() })
    }

    val filtered = reminders
        .filter { categoryFilter == null || it.category == categoryFilter }
        .filter { !importantOnly || it.isImportant }
    val active = filtered.filter { !it.isCompleted }
    val overdue = sortList(active.filter { it.dueAtMillis < now })
    val today = sortList(active.filter { it.dueAtMillis in now..todayEnd })
    val upcoming = sortList(active.filter { it.dueAtMillis > todayEnd })
    val completed = filtered.filter { it.isCompleted }.sortedByDescending { it.dueAtMillis }

    // Item indices of each section's header in the LazyColumn below, for the stat tiles'
    // tap-to-scroll behavior -- must mirror the section order actually rendered.
    val sectionIndex = remember(overdue.size, today.size, upcoming.size, completed.size) {
        val map = mutableMapOf<String, Int>()
        var idx = 0
        if (overdue.isNotEmpty()) { map["Overdue"] = idx; idx += 1 + overdue.size }
        if (today.isNotEmpty()) { map["Today"] = idx; idx += 1 + today.size }
        if (upcoming.isNotEmpty()) { map["Upcoming"] = idx; idx += 1 + upcoming.size }
        if (completed.isNotEmpty()) { map["Completed"] = idx; idx += 1 + completed.size }
        map
    }
    fun scrollToSection(name: String) {
        sectionIndex[name]?.let { index -> scope.launch { listState.animateScrollToItem(index) } }
    }

    Scaffold(
        containerColor = ReminderColors.background,
        topBar = {
            Column(modifier = Modifier.background(ReminderColors.background)) {
                TopAppBar(
                    title = { Text("Reminder", color = ReminderColors.onSurface, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (onBackClick != null) {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ReminderColors.onSurface)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ReminderColors.background),
                    actions = {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Text("⇅", style = MaterialTheme.typography.titleMedium, color = ReminderColors.onSurface)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                SortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        onClick = { sortMode = mode; showSortMenu = false }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showImportMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Import", tint = ReminderColors.onSurface)
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
                                        else importCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                    }
                                )
                            }
                        }
                    }
                )

                // ── Stat tile grid (Samsung Reminder's 2x3 layout) ──
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("📅", "Today", today.size, ReminderColors.today, modifier = Modifier.weight(1f)) { scrollToSection("Today") }
                        StatTile("🕐", "Scheduled", active.size, ReminderColors.scheduled, modifier = Modifier.weight(1f)) { scrollToSection("Upcoming") }
                        StatTile("⭐", "Important", filtered.count { it.isImportant && !it.isCompleted }, ReminderColors.important, selected = importantOnly, modifier = Modifier.weight(1f)) { importantOnly = !importantOnly }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("🏷", "Categories", categories.size, ReminderColors.categoriesAccent, modifier = Modifier.weight(1f)) { }
                        StatTile("⚠", "Overdue", overdue.size, ReminderColors.overdue, modifier = Modifier.weight(1f)) { scrollToSection("Overdue") }
                        StatTile("✔", "Completed", completed.size, ReminderColors.completed, modifier = Modifier.weight(1f)) { scrollToSection("Completed") }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (categories.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("All") })
                        categories.forEach { cat ->
                            FilterChip(
                                selected = categoryFilter == cat.name,
                                onClick = { categoryFilter = if (categoryFilter == cat.name) null else cat.name },
                                label = { Text(cat.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier.size(10.dp).clip(CircleShape)
                                            .background(runCatching { Color(android.graphics.Color.parseColor(cat.colorHex)) }.getOrDefault(Color.Gray))
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        bottomBar = {
            Surface(color = ReminderColors.background) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(ReminderColors.surface)
                        .clickable { editingReminder = null; showAddDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add reminder", tint = ReminderColors.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Add reminder", color = ReminderColors.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        if (reminders.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏰", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No reminders yet", color = ReminderColors.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reminderSection(
                    title = "Overdue", items = overdue, categories = categories, titleColor = ReminderColors.overdue,
                    onToggleComplete = { r -> persist(reminders.map { if (it.id == r.id) it.copy(isCompleted = true) else it }); ReminderScheduler.cancel(context, r.id) },
                    onToggleImportant = { r -> persist(reminders.map { if (it.id == r.id) it.copy(isImportant = !it.isImportant) else it }) },
                    onEdit = { editingReminder = it; showAddDialog = true },
                    onDelete = { deleteReminder(it) }
                )
                reminderSection(
                    title = "Today", items = today, categories = categories, titleColor = ReminderColors.today,
                    onToggleComplete = { r -> persist(reminders.map { if (it.id == r.id) it.copy(isCompleted = true) else it }); ReminderScheduler.cancel(context, r.id) },
                    onToggleImportant = { r -> persist(reminders.map { if (it.id == r.id) it.copy(isImportant = !it.isImportant) else it }) },
                    onEdit = { editingReminder = it; showAddDialog = true },
                    onDelete = { deleteReminder(it) }
                )
                reminderSection(
                    title = "Upcoming", items = upcoming, categories = categories,
                    onToggleComplete = { r -> persist(reminders.map { if (it.id == r.id) it.copy(isCompleted = true) else it }); ReminderScheduler.cancel(context, r.id) },
                    onToggleImportant = { r -> persist(reminders.map { if (it.id == r.id) it.copy(isImportant = !it.isImportant) else it }) },
                    onEdit = { editingReminder = it; showAddDialog = true },
                    onDelete = { deleteReminder(it) }
                )
                reminderSection(
                    title = "Completed", items = completed, categories = categories,
                    onToggleComplete = { r -> val u = r.copy(isCompleted = false); persist(reminders.map { if (it.id == r.id) u else it }); ReminderScheduler.schedule(context, u) },
                    onToggleImportant = { r -> persist(reminders.map { if (it.id == r.id) it.copy(isImportant = !it.isImportant) else it }) },
                    onEdit = { editingReminder = it; showAddDialog = true },
                    onDelete = { deleteReminder(it) }
                )
                item { Spacer(modifier = Modifier.height(60.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddEditReminderDialog(
            existing = editingReminder,
            categories = categories,
            onAddCategory = { newCategory ->
                val updated = categories + newCategory
                ReminderStorage.saveCategories(context, updated)
                categories = updated
            },
            onDismiss = { showAddDialog = false; editingReminder = null },
            onSave = { r ->
                val exists = reminders.any { it.id == r.id }
                persist(if (exists) reminders.map { if (it.id == r.id) r else it } else reminders + r)
                ReminderScheduler.schedule(context, r)
                syncToCalendar(r)
                showAddDialog = false
                editingReminder = null
            }
        )
    }

    if (showPasteImport) {
        PasteImportDialog(
            onDismiss = { showPasteImport = false },
            onImport = { titles, dueAtMillis ->
                val newOnes = titles.map { title -> Reminder(title = title, dueAtMillis = dueAtMillis, category = "Imported") }
                persist(reminders + newOnes)
                newOnes.forEach { ReminderScheduler.schedule(context, it); syncToCalendar(it) }
                showPasteImport = false
                Toast.makeText(context, "Imported ${newOnes.size} reminder(s).", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showCalendarImport) {
        var events by remember { mutableStateOf<List<ImportableEvent>?>(null) }
        LaunchedEffect(Unit) { events = GoogleCalendarImporter.fetchUpcomingGoogleEvents(context) }
        CalendarImportDialog(
            events = events,
            onDismiss = { showCalendarImport = false },
            onImport = { selected ->
                // Already Google Calendar events -- don't sync them back and create duplicates.
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
    categories: List<ReminderCategory>,
    titleColor: Color? = null,
    onToggleComplete: (Reminder) -> Unit,
    onToggleImportant: (Reminder) -> Unit,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    if (items.isEmpty()) return
    item {
        Text(
            text = "$title (${items.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = titleColor ?: ReminderColors.onSurface
        )
    }
    items(items, key = { it.id }) { reminder ->
        val categoryColor = categories.firstOrNull { it.name == reminder.category }?.colorHex
        ReminderRow(
            reminder = reminder,
            categoryColorHex = categoryColor,
            onToggleComplete = { onToggleComplete(reminder) },
            onToggleImportant = { onToggleImportant(reminder) },
            onEdit = { onEdit(reminder) },
            onDelete = { onDelete(reminder) }
        )
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    categoryColorHex: String?,
    onToggleComplete: () -> Unit,
    onToggleImportant: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(containerColor = ReminderColors.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularCheckbox(checked = reminder.isCompleted, onCheckedChange = { onToggleComplete() })
            Spacer(modifier = Modifier.width(8.dp))
            reminder.photoUri?.let { uri ->
                AsyncImage(
                    model = Uri.parse(uri),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(reminder.title, color = ReminderColors.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    categoryColorHex?.let { hex ->
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape)
                                .background(runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    val repeatSuffix = if (reminder.repeatRule.unit != RepeatUnit.NONE) " · ${RepeatEngine.describe(reminder.repeatRule)}" else ""
                    val voiceSuffix = if (reminder.voiceMemoPath != null) " · 🎙" else ""
                    Text(
                        dateFormat.format(reminder.dueAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = ReminderColors.dueDate
                    )
                    Text(
                        " · ${reminder.category}$repeatSuffix$voiceSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReminderColors.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onToggleImportant) {
                Icon(
                    if (reminder.isImportant) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Important",
                    tint = if (reminder.isImportant) ReminderColors.important else ReminderColors.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = ReminderColors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatTile(
    emoji: String,
    label: String,
    count: Int,
    tint: Color,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) tint.copy(alpha = 0.22f) else ReminderColors.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 14.sp) }
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, color = ReminderColors.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            Text("$count", color = ReminderColors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** Samsung Reminder uses circular (not squircle) checkboxes -- Material3's own Checkbox is
 *  square, so this is a small custom control instead. */
@Composable
private fun CircularCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) ReminderColors.scheduled else Color.Transparent)
            .border(width = 2.dp, color = if (checked) ReminderColors.scheduled else ReminderColors.onSurfaceVariant, shape = CircleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AddEditReminderDialog(
    existing: Reminder?,
    categories: List<ReminderCategory>,
    onAddCategory: (ReminderCategory) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: categories.firstOrNull()?.name ?: "General") }
    var isImportant by remember { mutableStateOf(existing?.isImportant ?: false) }
    var photoUri by remember { mutableStateOf(existing?.photoUri) }
    var voiceMemoPath by remember { mutableStateOf(existing?.voiceMemoPath) }
    var isRecording by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    var unit by remember { mutableStateOf(existing?.repeatRule?.unit ?: RepeatUnit.NONE) }
    var interval by remember { mutableStateOf((existing?.repeatRule?.interval ?: 1).toString()) }
    var weeklyMode by remember { mutableStateOf(existing?.repeatRule?.weeklyMode ?: WeeklyMode.EVERY_INTERVAL) }
    var specificWeekdays by remember { mutableStateOf(existing?.repeatRule?.specificWeekdays ?: emptySet()) }
    var monthlyMode by remember { mutableStateOf(existing?.repeatRule?.monthlyMode ?: MonthlyMode.SAME_DAY) }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    var dueAtMillis by remember {
        mutableStateOf(
            existing?.dueAtMillis ?: Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    val dateTimeFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.US) }

    val reminderId = remember { existing?.id ?: java.util.UUID.randomUUID().toString() }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            photoUri = uri.toString()
        }
    }
    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            VoiceMemoRecorder.startRecording(context, reminderId)
            isRecording = true
        } else {
            Toast.makeText(context, "Microphone permission needed to record.", Toast.LENGTH_SHORT).show()
        }
    }

    val repeatRule = RepeatRule(
        unit = unit,
        interval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        weeklyMode = weeklyMode,
        specificWeekdays = specificWeekdays,
        monthlyMode = monthlyMode
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Reminder" else "Edit Reminder") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { pickDateTime(context, dueAtMillis) { picked -> dueAtMillis = picked } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(dateTimeFormat.format(dueAtMillis)) }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Important", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isImportant, onCheckedChange = { isImportant = it })
                }

                // ── Category ──
                Box {
                    OutlinedButton(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Category: $category")
                    }
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.name) }, onClick = { category = cat.name; categoryMenuExpanded = false })
                        }
                        DropdownMenuItem(
                            text = { Text("+ New category") },
                            onClick = { categoryMenuExpanded = false; showNewCategoryDialog = true }
                        )
                    }
                }

                // ── Repeat ──
                Box {
                    OutlinedButton(onClick = { unitMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Repeat: " + RepeatEngine.describe(repeatRule))
                    }
                    DropdownMenu(expanded = unitMenuExpanded, onDismissRequest = { unitMenuExpanded = false }) {
                        RepeatUnit.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = { unit = option; unitMenuExpanded = false }
                            )
                        }
                    }
                }
                if (unit != RepeatUnit.NONE && unit != RepeatUnit.WEEK) {
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { v -> if (v.all { it.isDigit() }) interval = v },
                        label = { Text("Every N ${unit.name.lowercase()}(s)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (unit == RepeatUnit.WEEK) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        WeeklyMode.entries.forEach { mode ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = weeklyMode == mode, onClick = { weeklyMode = mode })
                                Text(
                                    when (mode) {
                                        WeeklyMode.EVERY_INTERVAL -> "Every N weeks"
                                        WeeklyMode.WEEKDAYS_ONLY -> "Weekdays only"
                                        WeeklyMode.WEEKENDS_ONLY -> "Weekends only"
                                        WeeklyMode.SPECIFIC_DAYS -> "Specific days"
                                    }
                                )
                            }
                        }
                        if (weeklyMode == WeeklyMode.EVERY_INTERVAL) {
                            OutlinedTextField(
                                value = interval,
                                onValueChange = { v -> if (v.all { it.isDigit() }) interval = v },
                                label = { Text("Every N weeks") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (weeklyMode == WeeklyMode.SPECIFIC_DAYS) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                WEEKDAY_LABELS.forEachIndexed { index, label ->
                                    val dayNum = index + 1
                                    FilterChip(
                                        selected = dayNum in specificWeekdays,
                                        onClick = {
                                            specificWeekdays = if (dayNum in specificWeekdays) specificWeekdays - dayNum else specificWeekdays + dayNum
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (unit == RepeatUnit.MONTH) {
                    Column {
                        MonthlyMode.entries.forEach { mode ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = monthlyMode == mode, onClick = { monthlyMode = mode })
                                Text(
                                    when (mode) {
                                        MonthlyMode.SAME_DAY -> "Same day each month"
                                        MonthlyMode.LAST_DAY -> "Last day of the month"
                                        MonthlyMode.NTH_WEEKDAY -> "Same weekday (e.g. 3rd Friday)"
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Photo attachment ──
                if (photoUri != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = Uri.parse(photoUri), contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                        IconButton(onClick = { photoUri = null }) { Icon(Icons.Filled.Close, contentDescription = "Remove photo") }
                    }
                } else {
                    OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("📷 Attach Photo")
                    }
                }

                // ── Voice memo ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            if (isRecording) {
                                voiceMemoPath = VoiceMemoRecorder.stopRecording()
                                isRecording = false
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    VoiceMemoRecorder.startRecording(context, reminderId)
                                    isRecording = true
                                } else {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    ) { Text(if (isRecording) "⏹ Stop Recording" else "🎙 Record Voice Memo") }
                    if (voiceMemoPath != null && !isRecording) {
                        IconButton(onClick = {
                            voiceMemoPath?.let { VoiceMemoRecorder.deleteMemo(it) }
                            voiceMemoPath = null
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Remove voice memo") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    if (isRecording) { voiceMemoPath = VoiceMemoRecorder.stopRecording(); isRecording = false }
                    onSave(
                        (existing ?: Reminder(id = reminderId, title = title, dueAtMillis = dueAtMillis)).copy(
                            title = title.trim(),
                            notes = notes.trim(),
                            dueAtMillis = dueAtMillis,
                            repeatRule = repeatRule,
                            category = category,
                            isImportant = isImportant,
                            photoUri = photoUri,
                            voiceMemoPath = voiceMemoPath
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showNewCategoryDialog) {
        NewCategoryDialog(
            onDismiss = { showNewCategoryDialog = false },
            onCreate = { newCategory ->
                onAddCategory(newCategory)
                category = newCategory.name
                showNewCategoryDialog = false
            }
        )
    }
}

@Composable
private fun NewCategoryDialog(onDismiss: () -> Unit, onCreate: (ReminderCategory) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(CATEGORY_COLOR_PALETTE.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_COLOR_PALETTE.forEach { hex ->
                        val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex }
                        ) {
                            if (selectedColor == hex) {
                                Box(modifier = Modifier.fillMaxSize().padding(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = { onCreate(ReminderCategory(name.trim(), selectedColor)) }) { Text("Create") }
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
                    value = text, onValueChange = { text = it },
                    label = { Text("Reminder titles, one per line") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
                OutlinedButton(onClick = { pickDateTime(context, dueAtMillis) { picked -> dueAtMillis = picked } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Due: ${dateTimeFormat.format(dueAtMillis)}")
                }
                if (lineCount > 0) Text("$lineCount reminder(s) will be created.", style = MaterialTheme.typography.bodySmall)
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
                events == null -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                events.isEmpty() -> Text("No upcoming Google Calendar events found in the next 90 days.")
                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(events.size) { index ->
                        val event = events[index]
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected[index] ?: false, onCheckedChange = { checked -> selected[index] = checked })
                            Column {
                                Text(event.title, style = MaterialTheme.typography.bodyMedium)
                                Text(dateFormat.format(event.startMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !events.isNullOrEmpty() && selected.values.any { it },
                onClick = { onImport(events.orEmpty().filterIndexed { index, _ -> selected[index] == true }) }
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
