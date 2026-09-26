package com.vignesh.leetcodechecker.reminders

import java.util.UUID

enum class ReminderRepeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val dueAtMillis: Long,
    val repeat: ReminderRepeat = ReminderRepeat.NONE,
    val category: String = "General",
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
