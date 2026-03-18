package com.vignesh.leetcodechecker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vignesh.leetcodechecker.data.DailyChallengeUiModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val AI_STUDIO_PROMPT_URL = "https://aistudio.google.com/prompts/1NS1ZfsHlX_Wpf0wGVugWBou6q3hvc60n"

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
    val context = LocalContext.current
    var markedCompleted by rememberSaveable { mutableStateOf(false) }
    var handledChallengeUrl by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.challenge?.url) {
        val challenge = state.challenge ?: return@LaunchedEffect
        if (handledChallengeUrl == challenge.url) return@LaunchedEffect

        val prompt = buildAiPrompt(challenge)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LeetCode AI Prompt", prompt))
        Toast.makeText(
            context,
            "Problem copied. Paste in AI Studio to generate detailed Python solution.",
            Toast.LENGTH_LONG
        ).show()
        onOpenLink(AI_STUDIO_PROMPT_URL)
        handledChallengeUrl = challenge.url
        markedCompleted = false
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.leetcode_logo),
                    contentDescription = "LeetCode Logo",
                    modifier = Modifier.size(42.dp)
                )
                Text(
                    text = "LeetCode Daily Checker",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

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
                            Text("Open Problem")
                        }

                        Button(
                            onClick = {
                                val prompt = buildAiPrompt(challenge)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("LeetCode AI Prompt", prompt))
                                onOpenLink(AI_STUDIO_PROMPT_URL)
                            }
                        ) {
                            Text("Open AI Studio Prompt")
                        }

                        Button(onClick = { onOpenLink(challenge.url) }) {
                            Text("Open LeetCode Editor (Manual Submit)")
                        }

                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "Auto-sign-in and auto-submit are not supported safely in this app. Use the button above, sign in, paste Python code, and submit.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(
                            onClick = { markedCompleted = !markedCompleted },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (markedCompleted) "Marked Completed" else "Mark as Completed")
                        }

                        if (markedCompleted) {
                            Text(
                                text = "Status: Completed",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildAiPrompt(challenge: DailyChallengeUiModel): String {
    return """
You are an expert coding interview coach.

Explain this LeetCode daily problem in very detailed manner:
- Problem ID: ${challenge.questionId}
- Title: ${challenge.title}
- Difficulty: ${challenge.difficulty}
- Tags: ${challenge.tags.joinToString()}
- URL: ${challenge.url}
- Problem snippet: ${challenge.descriptionPreview}

Please provide:
1) Restatement and intuition.
2) Brute-force approach and why it is inefficient.
3) Optimal approach with step-by-step reasoning.
4) Time and space complexity analysis.
5) Dry run on an example.
6) Clean, production-quality Python 3 solution.
7) Edge cases and test inputs.
""".trimIndent()
}
