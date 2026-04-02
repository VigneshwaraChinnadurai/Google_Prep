package com.vignesh.leetcodechecker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vignesh.leetcodechecker.ConsistencyStorage
import com.vignesh.leetcodechecker.MainActivity
import com.vignesh.leetcodechecker.R
import com.vignesh.leetcodechecker.data.LeetCodeActivityStorage

/**
 * LeetCode Streak Widget Provider
 * 
 * Displays:
 * - Current streak
 * - Today's completion status
 * - Quick access to the app
 */
class LeetCodeWidgetProvider : AppWidgetProvider() {
    
    companion object {
        const val ACTION_REFRESH = "com.vignesh.leetcodechecker.WIDGET_REFRESH"
        
        /**
         * Update all widgets
         */
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, LeetCodeWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val widgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = widgetManager.getAppWidgetIds(
                ComponentName(context, LeetCodeWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            context.sendBroadcast(intent)
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, LeetCodeWidgetProvider::class.java)
            )
            onUpdate(context, appWidgetManager, widgetIds)
        }
    }
    
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Get data
        val stats = LeetCodeActivityStorage.loadProblemStats(context)
        val isCompletedToday = ConsistencyStorage.isCompletedToday(context)
        val challenge = ConsistencyStorage.loadChallenge(context)
        
        // Create remote views
        val views = RemoteViews(context.packageName, R.layout.widget_leetcode_streak)
        
        // Set streak
        views.setTextViewText(R.id.widget_streak_count, "${stats.currentStreak}")
        
        // Set status
        val statusText = if (isCompletedToday) "✅ Completed" else "⏳ Pending"
        views.setTextViewText(R.id.widget_status, statusText)
        
        // Set today's problem if available
        val problemText = challenge?.title?.take(25)?.let { "$it..." } ?: "Tap to fetch"
        views.setTextViewText(R.id.widget_problem, problemText)
        
        // Set stats
        views.setTextViewText(R.id.widget_total_solved, "Total: ${stats.totalSolved}")
        
        // Create pending intent for tap
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        
        // Create refresh intent
        val refreshIntent = Intent(context, LeetCodeWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
        
        // Update widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
