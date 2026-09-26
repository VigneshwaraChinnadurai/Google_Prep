package com.vignesh.leetcodechecker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager alarms are wiped on reboot; this puts every pending reminder back. */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
        }
    }
}
