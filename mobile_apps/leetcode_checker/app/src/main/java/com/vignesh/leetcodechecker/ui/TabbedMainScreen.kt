package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.prooffiling.ProofFilingScreen
import com.vignesh.leetcodechecker.prooffiling.ProofFilingViewModel
import com.vignesh.leetcodechecker.viewmodel.ChatbotViewModel
import com.vignesh.leetcodechecker.viewmodel.GitHubProfileViewModel
import com.vignesh.leetcodechecker.viewmodel.OllamaViewModel
import android.app.Application

enum class AppTab {
    LEETCODE,
    OLLAMA_LEETCODE,
    PROOFFILING,
    FEATURES,
    PROFILE
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
    AI_NEWS_SETTINGS,
    GLOBAL_SETTINGS,
    AI_LEARNING_HUB,
    STRATEGIC_CHATBOT,
    WHATS_NEW,
    PROFILE,
    OLLAMA,
    CHATBOT,
    GITHUB_PROFILE
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedMainScreen(
    application: Application,
    onOpenLink: (String) -> Unit = {},
    leetcodeScreenContent: @Composable (onOpenLink: (String) -> Unit, challengeFilter: ChallengeFilter?, onClearFilter: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.LEETCODE) }
    var featureScreen by rememberSaveable { mutableStateOf(FeatureScreen.HUB) }
    var challengeFilter by remember { mutableStateOf<ChallengeFilter?>(null) }
    val ollamaViewModel: OllamaViewModel = viewModel()
    val gitHubProfileViewModel: GitHubProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val proofFilingViewModel: ProofFilingViewModel = viewModel()

    // Navigation here is plain state (selectedTab/featureScreen), not a NavController,
    // so the system back button/gesture has no built-in awareness of it -- without this,
    // back on any non-root screen closes the whole app instead of stepping back one
    // level. Mirrors exactly what each screen's own in-UI back button already does:
    // a Features sub-screen returns to the Hub; any other non-Leetcode tab returns to
    // the Leetcode tab (the app's home). Only falls through to the OS default (exit)
    // once already at that true root, matching standard bottom-nav back behavior.
    androidx.activity.compose.BackHandler(
        enabled = featureScreen != FeatureScreen.HUB || selectedTab != AppTab.LEETCODE
    ) {
        when {
            featureScreen != FeatureScreen.HUB -> featureScreen = FeatureScreen.HUB
            selectedTab != AppTab.LEETCODE -> selectedTab = AppTab.LEETCODE
        }
    }

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
                    selected = selectedTab == AppTab.PROOFFILING,
                    onClick = { selectedTab = AppTab.PROOFFILING },
                    label = { Text("ProofFile", fontSize = 10.sp) },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = "ProofFiling") }
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
                    selected = selectedTab == AppTab.PROFILE,
                    onClick = { selectedTab = AppTab.PROFILE },
                    label = { Text("Profile", fontSize = 10.sp) },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") }
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
                                    FeatureDestination.GLOBAL_SETTINGS -> FeatureScreen.GLOBAL_SETTINGS
                                    FeatureDestination.PROFILE, FeatureDestination.GITHUB_PROFILE -> {
                                        // Both live on the bottom-nav Profile tab; jump there instead of a dead-end screen.
                                        selectedTab = AppTab.PROFILE
                                        FeatureScreen.HUB
                                    }
                                    FeatureDestination.OLLAMA -> FeatureScreen.OLLAMA
                                    FeatureDestination.CHATBOT -> FeatureScreen.CHATBOT
                                    FeatureDestination.AI_LEARNING_HUB -> FeatureScreen.AI_LEARNING_HUB
                                    FeatureDestination.STRATEGIC_CHATBOT -> FeatureScreen.STRATEGIC_CHATBOT
                                    FeatureDestination.WHATS_NEW -> FeatureScreen.WHATS_NEW
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
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.GLOBAL_SETTINGS -> GlobalSettingsScreen(
                            onBack = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.AI_LEARNING_HUB -> AIFeaturesHubScreen(
                            apiKey = AppSettingsStore.load(context).globalGeminiApiKey,
                            onBackClick = { featureScreen = FeatureScreen.HUB },
                            onProblemClick = { slug, title ->
                                // Could navigate to problem
                            }
                        )
                        FeatureScreen.STRATEGIC_CHATBOT -> StrategicChatbotScreen(
                            viewModel = viewModel(),
                            onOpenLink = onOpenLink,
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        FeatureScreen.WHATS_NEW -> WhatsNewScreen(
                            onBackClick = { featureScreen = FeatureScreen.HUB }
                        )
                        else -> {
                            // These features are handled by root tabs or are not implemented as standalone screens
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Not implemented or handled elsewhere")
                            }
                        }
                    }
                }
                AppTab.PROOFFILING -> {
                    ProofFilingScreen(
                        viewModel = proofFilingViewModel
                    )
                }
                AppTab.PROFILE -> {
                    ProfileScreen(
                        gitHubViewModel = gitHubProfileViewModel,
                        proofFilingViewModel = proofFilingViewModel
                    )
                }
            }
        }
    }
}
