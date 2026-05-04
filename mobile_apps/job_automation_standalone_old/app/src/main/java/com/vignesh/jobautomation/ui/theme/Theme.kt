package com.vignesh.jobautomation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Job Automation Color Scheme
private val PrimaryBlue = Color(0xFF1976D2)
private val PrimaryBlueLight = Color(0xFF63A4FF)
private val PrimaryBlueDark = Color(0xFF004BA0)

private val SecondaryGreen = Color(0xFF388E3C)
private val SecondaryGreenLight = Color(0xFF6ABF69)
private val SecondaryGreenDark = Color(0xFF00600F)

private val TertiaryOrange = Color(0xFFF57C00)
private val ErrorRed = Color(0xFFD32F2F)

private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkSurfaceVariant = Color(0xFF2D2D2D)

private val LightBackground = Color(0xFFFAFAFA)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFF5F5F5)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = Color.Black,
    primaryContainer = PrimaryBlueDark,
    onPrimaryContainer = Color.White,
    secondary = SecondaryGreenLight,
    onSecondary = Color.Black,
    secondaryContainer = SecondaryGreenDark,
    onSecondaryContainer = Color.White,
    tertiary = TertiaryOrange,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2D2D2D),
    onTertiaryContainer = TertiaryOrange,
    error = ErrorRed,
    onError = Color.White,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCACACA)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = PrimaryBlueDark,
    secondary = SecondaryGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = SecondaryGreenDark,
    tertiary = TertiaryOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFFE65100),
    error = ErrorRed,
    onError = Color.White,
    background = LightBackground,
    onBackground = Color.Black,
    surface = LightSurface,
    onSurface = Color.Black,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF5D5D5D)
)

@Composable
fun JobAutomationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// Extension colors for specific UI elements
object JobAutomationColors {
    val HighMatch = Color(0xFF4CAF50)
    val MediumMatch = Color(0xFFFFC107)
    val LowMatch = Color(0xFFF44336)
    
    val StatusNew = Color(0xFF2196F3)
    val StatusAnalyzed = Color(0xFF9C27B0)
    val StatusReadyToApply = Color(0xFF4CAF50)
    val StatusApplied = Color(0xFF00BCD4)
    val StatusInterviewing = Color(0xFFFF9800)
    val StatusOffer = Color(0xFF8BC34A)
    val StatusRejected = Color(0xFFE91E63)
    
    fun matchScoreColor(score: Float): Color = when {
        score >= 70 -> HighMatch
        score >= 50 -> MediumMatch
        else -> LowMatch
    }
}
