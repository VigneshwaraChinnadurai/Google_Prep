package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import com.vignesh.leetcodechecker.viewmodel.OllamaViewModel
import android.app.Application

enum class AppTab {
    LEETCODE,
    OLLAMA_LEETCODE,
    STRATEGIC_CHATBOT
}

/**
 * TabbedMainScreen — Main navigation wrapper for Vignesh Personal Development app
 *
 * Provides:
 * 1. Bottom navigation between "Leetcode" and "Strategic Chatbot" tabs
 * 2. Separate ViewModels for each section
 * 3. State persistence across tab switches
 */
@Composable
fun TabbedMainScreen(
    application: Application,
    onOpenLink: (String) -> Unit = {},
    leetcodeScreenContent: @Composable (onOpenLink: (String) -> Unit) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.LEETCODE) }
    val chatbotViewModel: ChatbotViewModel = viewModel()
    val ollamaViewModel: OllamaViewModel = viewModel()

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
                    selected = selectedTab == AppTab.STRATEGIC_CHATBOT,
                    onClick = { selectedTab = AppTab.STRATEGIC_CHATBOT },
                    label = { Text("Chatbot", fontSize = 10.sp) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Strategic Chatbot") }
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
                    leetcodeScreenContent(onOpenLink)
                }
                AppTab.OLLAMA_LEETCODE -> {
                    OllamaLeetCodeScreen(
                        viewModel = ollamaViewModel
                    )
                }
                AppTab.STRATEGIC_CHATBOT -> {
                    StrategicChatbotScreen(
                        viewModel = chatbotViewModel,
                        onOpenLink = onOpenLink
                    )
                }
            }
        }
    }
}
