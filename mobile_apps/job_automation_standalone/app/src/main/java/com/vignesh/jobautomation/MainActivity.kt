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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vignesh.jobautomation.ui.applications.ApplicationsScreen
import com.vignesh.jobautomation.ui.chat.ChatScreen
import com.vignesh.jobautomation.ui.companies.CompaniesScreen
import com.vignesh.jobautomation.ui.dashboard.DashboardScreen
import com.vignesh.jobautomation.ui.jobs.JobsScreen
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

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val showInBottomBar: Boolean = true
) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Dashboard)
    object Jobs : Screen("jobs", "Jobs", Icons.Default.Work)
    object Applications : Screen("applications", "Applied", Icons.Default.Assignment)
    object Chat : Screen("chat", "AI Chat", Icons.Default.SmartToy)
    object Companies : Screen("companies", "Companies", Icons.Default.Business, showInBottomBar = false)
    object Profile : Screen("profile", "Profile", Icons.Default.Person, showInBottomBar = false)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings, showInBottomBar = false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val bottomBarScreens = listOf(
        Screen.Dashboard,
        Screen.Jobs,
        Screen.Applications,
        Screen.Chat
    )
    
    val allScreens = listOf(
        Screen.Dashboard,
        Screen.Jobs,
        Screen.Applications,
        Screen.Chat,
        Screen.Companies,
        Screen.Profile,
        Screen.Settings
    )
    
    Scaffold(
        topBar = {
            val currentScreen = allScreens.find { it.route == currentRoute }
            TopAppBar(
                title = { Text(currentScreen?.title ?: "Job Automation") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Profile button
                    IconButton(onClick = { 
                        navController.navigate(Screen.Profile.route) {
                            launchSingleTop = true
                        }
                    }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    // Settings button
                    IconButton(onClick = { 
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                navigationIcon = {
                    // Show back button for non-bottom-bar screens
                    if (currentRoute in listOf(Screen.Companies.route, Screen.Profile.route, Screen.Settings.route)) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Only show bottom bar for main screens
            if (currentRoute in bottomBarScreens.map { it.route }) {
                NavigationBar {
                    bottomBarScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToProfile = {
                        navController.navigate(Screen.Profile.route)
                    },
                    onNavigateToJobs = {
                        navController.navigate(Screen.Jobs.route)
                    },
                    onNavigateToApplications = {
                        navController.navigate(Screen.Applications.route)
                    },
                    onJobClick = {
                        navController.navigate(Screen.Jobs.route)
                    }
                )
            }
            composable(Screen.Jobs.route) {
                JobsScreen()
            }
            composable(Screen.Applications.route) {
                ApplicationsScreen()
            }
            composable(Screen.Chat.route) {
                ChatScreen()
            }
            composable(Screen.Companies.route) {
                CompaniesScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
