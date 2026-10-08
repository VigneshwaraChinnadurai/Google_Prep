package com.vignesh.jobmatcher

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.jobmatcher.ui.DiscoverScreen
import com.vignesh.jobmatcher.ui.HelpScreen
import com.vignesh.jobmatcher.ui.JobDetailScreen
import com.vignesh.jobmatcher.ui.MatchesScreen
import com.vignesh.jobmatcher.ui.SetupScreen
import com.vignesh.jobmatcher.ui.TrackerScreen
import com.vignesh.jobmatcher.work.DailyFetchWorker

class MainActivity : ComponentActivity() {
    private val vm: JobViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        DailyFetchWorker.schedule(applicationContext)
        // Only on a fresh launch -- a recreated activity would re-add the same shared link.
        if (savedInstanceState == null) handleShare(intent)
        setContent { JobMatcherTheme { JobMatcherApp(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** "Share → Job Matcher" from LinkedIn, Chrome, Naukri… adds the shared link as a Manual job. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (text.isNotBlank()) vm.addJobFromLink(text)
    }

    override fun onResume() {
        super.onResume()
        // Pick up anything the background worker fetched while we were away.
        vm.reload()
    }
}

@Composable
private fun JobMatcherTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF93C5FD)) else lightColorScheme(primary = Color(0xFF1E3A8A))
    MaterialTheme(colorScheme = scheme, content = content)
}

private val TABS = listOf("🔎" to "Discover", "🎯" to "Matches", "📋" to "Applications", "⚙️" to "Setup", "❓" to "Help")

@Composable
private fun JobMatcherApp(vm: JobViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openJobId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // A job just added from a link opens straight away (once it's loaded into state).
    LaunchedEffect(state.openJobRequest, state.jobs) {
        val id = state.openJobRequest ?: return@LaunchedEffect
        if (state.jobs.any { it.id == id }) {
            openJobId = id
            vm.consumeOpenJobRequest()
        }
    }

    val openJob = openJobId?.let { id -> state.jobs.firstOrNull { it.id == id } }
    BackHandler(enabled = openJobId != null) { openJobId = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i && openJob == null,
                        onClick = { tab = i; openJobId = null },
                        icon = { Text(icon) },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (openJob != null) {
                JobDetailScreen(openJob, state.settings.matchThreshold, state.busy, vm, state.contact) { openJobId = null }
            } else {
                val open: (String) -> Unit = { openJobId = it }
                when (tab) {
                    0 -> DiscoverScreen(state, vm, open)
                    1 -> MatchesScreen(state, vm, open)
                    2 -> TrackerScreen(state, open)
                    3 -> SetupScreen(state, vm)
                    else -> HelpScreen(state.settings)
                }
            }
        }
    }
}
