package com.vc.jobfinder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.vc.jobfinder.ui.apply.ApplyPacketScreen
import com.vc.jobfinder.ui.competitions.CompetitionsScreen
import com.vc.jobfinder.ui.matches.MatchesScreen
import com.vc.jobfinder.ui.pipeline.PipelineScreen
import com.vc.jobfinder.ui.resume.ResumeScreen
import com.vc.jobfinder.ui.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private data class TabSpec(val dest: Dest, val icon: ImageVector, val label: String)

private val TABS = listOf(
    TabSpec(Dest.Resume,       Icons.Default.Description,  "Resume"),
    TabSpec(Dest.Pipeline,     Icons.Default.PlayArrow,    "Run"),
    TabSpec(Dest.Matches,      Icons.Default.Star,         "Matches"),
    TabSpec(Dest.Competitions, Icons.Default.EmojiEvents,  "Contests"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobFinderRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    val rootVm: RootViewModel = hiltViewModel()
    val settings by rootVm.store.settings.collectAsState(initial = AppSettings("", ""))
    val startDest = if (settings.isConfigured) Dest.Pipeline.route else Dest.Settings.route

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
                    nav.navigate(Dest.Pipeline.route) {
                        popUpTo(Dest.Settings.route) { inclusive = true }
                    }
                })
            }
            composable(Dest.Resume.route)       { ResumeScreen() }
            composable(Dest.Pipeline.route)     {
                PipelineScreen(onSeeMatches = { nav.navigate(Dest.Matches.route) })
            }
            composable(Dest.Matches.route) {
                MatchesScreen(onOpenApply = { id -> nav.navigate(Dest.Apply.build(id)) })
            }
            composable(Dest.Competitions.route) { CompetitionsScreen() }
            composable(
                Dest.Apply.route,
                arguments = listOf(navArgument("matchId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("matchId").orEmpty()
                ApplyPacketScreen(matchId = id)
            }
        }
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(val store: SettingsStore) : ViewModel()
