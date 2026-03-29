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

// Primary colors - Professional blue theme
private val PrimaryLight = Color(0xFF1565C0)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFFD1E4FF)
private val OnPrimaryContainerLight = Color(0xFF001D36)

private val SecondaryLight = Color(0xFF535F70)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFD7E3F7)
private val OnSecondaryContainerLight = Color(0xFF101C2B)

private val TertiaryLight = Color(0xFF6B5778)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFF2DAFF)
private val OnTertiaryContainerLight = Color(0xFF251431)

private val SurfaceLight = Color(0xFFFDFCFF)
private val OnSurfaceLight = Color(0xFF1A1C1E)
private val SurfaceVariantLight = Color(0xFFDFE2EB)
private val OnSurfaceVariantLight = Color(0xFF43474E)

private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)

// Dark theme colors
private val PrimaryDark = Color(0xFFA0CAFD)
private val OnPrimaryDark = Color(0xFF003258)
private val PrimaryContainerDark = Color(0xFF00497D)
private val OnPrimaryContainerDark = Color(0xFFD1E4FF)

private val SecondaryDark = Color(0xFFBBC7DB)
private val OnSecondaryDark = Color(0xFF253140)
private val SecondaryContainerDark = Color(0xFF3B4858)
private val OnSecondaryContainerDark = Color(0xFFD7E3F7)

private val TertiaryDark = Color(0xFFD6BEE4)
private val OnTertiaryDark = Color(0xFF3B2948)
private val TertiaryContainerDark = Color(0xFF523F5F)
private val OnTertiaryContainerDark = Color(0xFFF2DAFF)

private val SurfaceDark = Color(0xFF1A1C1E)
private val OnSurfaceDark = Color(0xFFE3E2E6)
private val SurfaceVariantDark = Color(0xFF43474E)
private val OnSurfaceVariantDark = Color(0xFFC3C6CF)

private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

@Composable
fun JobAutomationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
        typography = Typography,
        content = content
    )
}
