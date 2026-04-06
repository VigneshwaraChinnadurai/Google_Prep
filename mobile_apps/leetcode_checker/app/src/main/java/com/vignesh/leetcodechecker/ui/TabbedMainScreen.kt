package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.leetcodechecker.viewmodel.ChatbotViewModel
import com.vignesh.leetcodechecker.viewmodel.GitHubProfileViewModel
import com.vignesh.leetcodechecker.viewmodel.OllamaViewModel
import android.app.Application

enum class AppTab {
    LEETCODE,
    OLLAMA_LEETCODE,
    FEATURES,
    STRATEGIC_CHATBOT,
    GITHUB_PROFILE
}

// Sub-navigation for Features tab
enum class FeatureScreen {
    HUB,
    ANALYTICS,
    GOALS,
    ACHIEVEMENTS,
    FLASHCARDS,
    FOCUS,
    INTERVIEW,
    LEADERBOARD,
    OFFLINE,
    PROTECTION,
    AI_NEWS,
    AI_NEWS_SETTINGS
}

/**
 * Data class for challenge filter selected from Features page
 */
data class ChallengeFilter(
    val topic: String? = null,
    val difficulty: String? = null
)

/**
 * TabbedMainScreen — Main navigation wrapper for Vignesh Personal Development app
 *
 * Provides:
 * 1. Bottom navigation between "Leetcode", "Ollama", "Features", "Chatbot", and "GitHub Profile" tabs
 * 2. Separate ViewModels for each section
 * 3. State persistence across tab switches
 * 4. Filter sharing between Features and LeetCode tabs
 */
@Composable
fun TabbedMainScreen(
    application: Application,
    onOpenLink: (String) -> Unit = {},
    leetcodeScreenContent: @Composable (onOpenLink: (String) -> Unit, challengeFilter: ChallengeFilter?, onClearFilter: () -> Unit) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.LEETCODE) }
    var featureScreen by rememberSaveable { mutableStateOf(FeatureScreen.HUB) }
    var challengeFilter by remember { mutableStateOf<ChallengeFilter?>(null) }
    val chatbotViewModel: ChatbotViewModel = viewModel()
    val ollamaViewModel: OllamaViewModel = viewModel()
    val gitHubProfileViewModel: GitHubProfileViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.LEETCODE,
                    onClick = { selectedTab = AppTab.LEETCODE },
                    label = { Text("Leetcode", fontSize = 10.sp) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Leetcode") }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.OLLAMA_LEETCODE,
                    onClick = { selectedTab = AppTab.OLLAMA_LEETCODE },
                    label = { Text("Ollama", fontSize = 10.sp) },
                    icon = { Icon(Icons.Filled.Build, contentDescription = "Ollama") }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.FEATURES,
                    onClick = { 
                        selectedTab = AppTab.FEATURES
                        featureScreen = FeatureScreen.HUB
                    },
                    label = { Text("Features", fontSize = 10.sp) },
                    icon = { Icon(Icons.Filled.Star, contentDescription = "Features") }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.STRATEGIC_CHATBOT,
                    onClick = { selectedTab = AppTab.STRATEGIC_CHATBOT },
                    label = { Text("Chatbot", fontSize = 10.sp) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Strategic Chatbot") }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.GITHUB_PROFILE,
                    onClick = { selectedTab = AppTab.GITHUB_PROFILE },
                    label = { Text("GitHub", fontSize = 10.sp) },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "GitHub Profile") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                AppTab.LEETCODE -> {
                    leetcodeScreenContent(
                        onOpenLink,
                        challengeFilter,
                        { challengeFilter = null }
                    )
                }
                AppTab.OLLAMA_LEETCODE -> {
                    OllamaLeetCodeScreen(
                        viewModel = ollamaViewModel
                    )
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
                                    FeatureDestination.INTERVIEW -> FeatureScreen.INTERVIEW
                                    FeatureDestination.LEADERBOARD -> FeatureScreen.LEADERBOARD
                                    FeatureDestination.OFFLINE -> FeatureScreen.OFFLINE
                                    FeatureDestination.PROTECTION -> FeatureScreen.PROTECTION
                                    FeatureDestination.AI_NEWS -> FeatureScreen.AI_NEWS
                                    FeatureDestination.AI_NEWS_SETTINGS -> FeatureScreen.AI_NEWS_SETTINGS
                                }
                            },
                            onFilterSelected = { topic, difficulty ->
                                challengeFilter = ChallengeFilter(topic, difficulty)
                                selectedTab = AppTab.LEETCODE
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
                        FeatureScreen.INTERVIEW -> AIInterviewPrepScreen(
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
                AppTab.STRATEGIC_CHATBOT -> {
                    StrategicChatbotScreen(
                        viewModel = chatbotViewModel,
                        onOpenLink = onOpenLink
                    )
                }
                AppTab.GITHUB_PROFILE -> {
                    GitHubProfileScreen(
                        viewModel = gitHubProfileViewModel
                    )
                }
            }
        }
    }
}
