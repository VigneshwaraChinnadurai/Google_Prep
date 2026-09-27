package com.vignesh.leetcodechecker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vignesh.leetcodechecker.MainActivity
import com.vignesh.leetcodechecker.R
import com.vignesh.leetcodechecker.reminders.ReminderStorage
import java.text.SimpleDateFormat
import java.util.Locale

/** Home-screen widget showing the next few upcoming (non-completed) reminders. */
class ReminderWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.vignesh.leetcodechecker.REMINDERS_WIDGET_REFRESH"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, ReminderWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val widgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = widgetManager.getAppWidgetIds(ComponentName(context, ReminderWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ReminderWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, widgetIds)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        val upcoming = ReminderStorage.loadReminders(context)
            .filter { !it.isCompleted }
            .sortedBy { it.dueAtMillis }
            .take(3)

        val views = RemoteViews(context.packageName, R.layout.widget_reminders)
        val rowIds = listOf(R.id.reminders_widget_row1, R.id.reminders_widget_row2, R.id.reminders_widget_row3)
        rowIds.forEachIndexed { index, rowId ->
            val reminder = upcoming.getOrNull(index)
            views.setTextViewText(
                rowId,
                if (reminder != null) "${dateFormat.format(reminder.dueAtMillis)} · ${reminder.title}" else ""
            )
        }
        if (upcoming.isEmpty()) {
            views.setTextViewText(R.id.reminders_widget_row1, "No reminders")
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.reminders_widget_container, pendingIntent)

        val refreshIntent = Intent(context, ReminderWidgetProvider::class.java).apply { action = ACTION_REFRESH }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 1, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.reminders_widget_refresh_button, refreshPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
