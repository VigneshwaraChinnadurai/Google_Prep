package com.vignesh.leetcodechecker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                LeetCodeCheckerScreen(
                    onOpenLink = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun LeetCodeCheckerScreen(
    viewModel: LeetCodeViewModel = viewModel(),
    onOpenLink: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "LeetCode Daily Checker",
                style = MaterialTheme.typography.headlineSmall
            )

            Button(
                onClick = { viewModel.fetchDailyChallenge() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("LeetCode")
            }

            if (state.isLoading) {
                CircularProgressIndicator()
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.challenge?.let { challenge ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${challenge.questionId}. ${challenge.title}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Date: ${challenge.date}")
                        Text("Difficulty: ${challenge.difficulty}")
                        Text("Tags: ${challenge.tags.joinToString()}")
                        if (challenge.descriptionPreview.isNotBlank()) {
                            Text(
                                text = challenge.descriptionPreview,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(onClick = { onOpenLink(challenge.url) }) {
                            Text("Open in Browser")
                        }
                    }
                }
            }
        }
    }
}
