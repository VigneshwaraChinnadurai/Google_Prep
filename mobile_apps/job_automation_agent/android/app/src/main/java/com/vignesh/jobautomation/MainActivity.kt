package com.vignesh.jobautomation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vignesh.jobautomation.ui.applications.ApplicationsScreen
import com.vignesh.jobautomation.ui.companies.CompaniesScreen
import com.vignesh.jobautomation.ui.dashboard.DashboardScreen
import com.vignesh.jobautomation.ui.profile.ProfileScreen
import com.vignesh.jobautomation.ui.settings.SettingsScreen
import com.vignesh.jobautomation.ui.theme.JobAutomationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JobAutomationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Dashboard : Screen("dashboard", "Dashboard", { Icon(Icons.Default.Dashboard, "Dashboard") })
    object Applications : Screen("applications", "Applications", { Icon(Icons.Default.Assignment, "Applications") })
    object Companies : Screen("companies", "Companies", { Icon(Icons.Default.Business, "Companies") })
    object Profile : Screen("profile", "Profile", { Icon(Icons.Default.Person, "Profile") })
    object Settings : Screen("settings", "Settings", { Icon(Icons.Default.Settings, "Settings") })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val screens = listOf(
        Screen.Dashboard,
        Screen.Applications,
        Screen.Companies,
        Screen.Profile,
        Screen.Settings
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = screens.find { it.route == currentRoute }?.title ?: "Job Automation"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = screen.icon,
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Applications.route) { ApplicationsScreen() }
            composable(Screen.Companies.route) { CompaniesScreen() }
            composable(Screen.Profile.route) { ProfileScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
