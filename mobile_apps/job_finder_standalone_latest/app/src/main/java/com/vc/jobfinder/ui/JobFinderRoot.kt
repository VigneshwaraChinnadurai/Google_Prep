package com.vc.jobfinder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vc.jobfinder.data.local.AppSettings
import com.vc.jobfinder.data.local.SettingsStore
import com.vc.jobfinder.ui.contests.ContestsScreen
import com.vc.jobfinder.ui.jobs.RunScreen
import com.vc.jobfinder.ui.matches.ApplyPacketScreen
import com.vc.jobfinder.ui.matches.MatchesScreen
import com.vc.jobfinder.ui.resume.ResumeScreen
import com.vc.jobfinder.ui.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private data class TabSpec(val dest: Dest, val icon: ImageVector, val label: String)

private val TABS = listOf(
    TabSpec(Dest.Resume,   Icons.Default.Description, "Resume"),
    TabSpec(Dest.Run,      Icons.Default.PlayArrow,   "Run"),
    TabSpec(Dest.Matches,  Icons.Default.Star,        "Matches"),
    TabSpec(Dest.Contests, Icons.Default.EmojiEvents, "Contests"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobFinderRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    val rootVm: RootViewModel = hiltViewModel()
    val settings by rootVm.store.settings.collectAsState(initial = AppSettings(false, 0.5f, "gemini-2.5-flash"))
    val startDest = if (settings.hasApiKey) Dest.Run.route else Dest.Settings.route

    Scaffold(
        topBar = {
            if (current != Dest.Settings.route) {
                TopAppBar(
                    title = { Text("Job Finder") },
                    actions = {
                        IconButton(onClick = { nav.navigate(Dest.Settings.route) }) {
                            Icon(Icons.Default.Settings, null)
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (current != Dest.Settings.route && current?.startsWith("apply/") != true) {
                NavigationBar {
                    TABS.forEach { (dest, icon, label) ->
                        NavigationBarItem(
                            selected = current == dest.route,
                            onClick = {
                                nav.navigate(dest.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            icon = { Icon(icon, null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = startDest, modifier = Modifier.padding(padding)) {
            composable(Dest.Settings.route) {
                SettingsScreen(onSaved = {
                    nav.navigate(Dest.Run.route) {
                        popUpTo(Dest.Settings.route) { inclusive = true }
                    }
                })
            }
            composable(Dest.Resume.route)  { ResumeScreen() }
            composable(Dest.Run.route)     { RunScreen(onSeeMatches = { nav.navigate(Dest.Matches.route) }) }
            composable(Dest.Matches.route) {
                MatchesScreen(onOpenApply = { id -> nav.navigate(Dest.Apply.build(id)) })
            }
            composable(Dest.Contests.route) { ContestsScreen() }
            composable(
                Dest.Apply.route,
                arguments = listOf(navArgument("matchId") { type = NavType.StringType }),
            ) { entry ->
                ApplyPacketScreen(matchId = entry.arguments?.getString("matchId").orEmpty())
            }
        }
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(val store: SettingsStore) : ViewModel()
