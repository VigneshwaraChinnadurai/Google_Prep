package com.vignesh.leetcodechecker.email

import android.content.Context
import android.util.Log
import com.vignesh.leetcodechecker.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires an optional "pushed to GitHub" email notification, gated by
 * AppSettings.emailOnGithubPushEnabled. Fire-and-forget by design: a failed or
 * unconfigured email must never block or fail the GitHub push itself -- that's
 * the thing the user actually asked for; email is a notification on top of it.
 */
object PushEmailNotifier {
    private const val TAG = "PushEmailNotifier"

    fun notifyPushed(context: Context, scope: CoroutineScope, subject: String, body: String) {
        val settings = AppSettingsStore.load(context)
        if (!settings.emailOnGithubPushEnabled) return
        if (settings.notificationEmailFrom.isBlank() || settings.notificationEmailAppPassword.isBlank()) {
            Log.w(TAG, "Email-on-push is enabled but sender/app password isn't configured; skipping")
            return
        }
        val to = settings.notificationEmailTo.ifBlank { settings.notificationEmailFrom }
        scope.launch(Dispatchers.IO) {
            GmailSmtpSender.send(
                fromEmail = settings.notificationEmailFrom,
                appPassword = settings.notificationEmailAppPassword,
                toEmail = to,
                subject = subject,
                body = body
            ).onFailure { e -> Log.e(TAG, "Push notification email failed", e) }
        }
    }
}
