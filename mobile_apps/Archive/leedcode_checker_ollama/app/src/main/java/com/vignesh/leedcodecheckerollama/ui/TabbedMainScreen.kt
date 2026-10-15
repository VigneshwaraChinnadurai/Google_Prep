package com.vignesh.leedcodecheckerollama.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.leedcodecheckerollama.LeetCodeViewModel

enum class AppTab {
    OLLAMA_LEETCODE,
    FEATURES
}

// Sub-navigation for Features tab
enum class FeatureScreen {
    HUB,
    ANALYTICS,
    GOALS,
    ACHIEVEMENTS,
    FLASHCARDS,
    FOCUS,
    LEADERBOARD,
    OFFLINE,
    PROTECTION,
    AI_NEWS,
    AI_NEWS_SETTINGS
}

/**
 * TabbedMainScreen — Main navigation wrapper for Ollama LeetCode Checker
 *
 * Provides:
 * 1. Bottom navigation between "Ollama LeetCode" and "Features" tabs
 * 2. Features hub with sub-navigation
 */
@Composable
fun TabbedMainScreen(
    application: Application,
    viewModel: LeetCodeViewModel,
    ollamaContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.OLLAMA_LEETCODE) }
    var featureScreen by rememberSaveable { mutableStateOf(FeatureScreen.HUB) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF161B22)
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Ollama") },
                    label = { Text("Ollama", fontSize = 10.sp) },
                    selected = selectedTab == AppTab.OLLAMA_LEETCODE,
                    onClick = { 
                        selectedTab = AppTab.OLLAMA_LEETCODE
                        featureScreen = FeatureScreen.HUB
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF58A6FF),
                        selectedTextColor = Color(0xFF58A6FF),
                        unselectedIconColor = Color(0xFF8B949E),
                        unselectedTextColor = Color(0xFF8B949E),
                        indicatorColor = Color(0xFF21262D)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Star, contentDescription = "Features") },
                    label = { Text("Features", fontSize = 10.sp) },
                    selected = selectedTab == AppTab.FEATURES,
                    onClick = { 
                        selectedTab = AppTab.FEATURES 
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF58A6FF),
                        selectedTextColor = Color(0xFF58A6FF),
                        unselectedIconColor = Color(0xFF8B949E),
                        unselectedTextColor = Color(0xFF8B949E),
                        indicatorColor = Color(0xFF21262D)
                    )
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                AppTab.OLLAMA_LEETCODE -> {
                    ollamaContent()
                }
                AppTab.FEATURES -> {
                    // Features sub-navigation
                    when (featureScreen) {
                        FeatureScreen.HUB -> FeaturesHubScreen(
                            onNavigate = { destination ->
                                featureScreen = when (destination) {
                                    FeatureDestination.ANALYTICS -> FeatureScreen.ANALYTICS
                                    FeatureDestination.GOALS -> FeatureScreen.GOALS
                                    FeatureDestination.ACHIEVEMENTS -> FeatureScreen.ACHIEVEMENTS
                                    FeatureDestination.FLASHCARDS -> FeatureScreen.FLASHCARDS
                                    FeatureDestination.FOCUS -> FeatureScreen.FOCUS
                                    FeatureDestination.INTERVIEW -> FeatureScreen.HUB // Skip interview for now
                                    FeatureDestination.LEADERBOARD -> FeatureScreen.LEADERBOARD
                                    FeatureDestination.OFFLINE -> FeatureScreen.OFFLINE
                                    FeatureDestination.PROTECTION -> FeatureScreen.PROTECTION
                                    FeatureDestination.AI_NEWS -> FeatureScreen.AI_NEWS
                                    FeatureDestination.AI_NEWS_SETTINGS -> FeatureScreen.AI_NEWS_SETTINGS
                                }
                            }
                        )
                        FeatureScreen.ANALYTICS -> AnalyticsDashboard(
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.GOALS -> GoalTrackingScreen(
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.ACHIEVEMENTS -> AchievementsScreen(
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.FLASHCARDS -> FlashcardScreen(
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.FOCUS -> FocusModeScreen(
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.LEADERBOARD -> LeaderboardScreen(
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.OFFLINE -> OfflineModeScreen(
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.PROTECTION -> UninstallProtectionScreen(
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.AI_NEWS -> AINewsScreen(
                            onBackClick = { featureScreen = FeatureScreen.HUB },
                            onSettingsClick = { featureScreen = FeatureScreen.AI_NEWS_SETTINGS }
                        )
                        FeatureScreen.AI_NEWS_SETTINGS -> AINewsSettingsScreen(
                            onBackClick = { featureScreen = FeatureScreen.AI_NEWS }
                        )
                    }
                }
            }
        }
    }
}
