package com.vignesh.leetcodechecker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.vignesh.leetcodechecker.ui.TabbedMainScreen
import com.vignesh.leetcodechecker.ui.ChallengeFilter

private val AppDarkColors = darkColorScheme(
    primary = Color(0xFF9FC8FF),
    onPrimary = Color(0xFF003259),
    secondary = Color(0xFF9AD0C0),
    onSecondary = Color(0xFF07372E),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE5E9F0),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFE5E9F0)
)

private val AppLightColors = lightColorScheme(
    primary = Color(0xFF245FA8),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2B7A64),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF121417),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121417)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Schedule consistency reminders
        ConsistencyReminderScheduler.ensureHourlyReminder(this)
        // Schedule daily auto-fetch at 6 AM IST
        ConsistencyReminderScheduler.scheduleDailyAutoFetch(this)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (darkTheme) AppDarkColors else AppLightColors) {
                TabbedMainScreen(
                    application = application,
                    onOpenLink = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    },
                    leetcodeScreenContent = { onOpenLink, challengeFilter, onClearFilter ->
                        com.vignesh.leetcodechecker.ui.LeetCodeScreen(
                            onOpenLink = onOpenLink,
                            challengeFilter = challengeFilter,
                            onClearFilter = onClearFilter
                        )
                    }
                )
            }
        }
    }
}

