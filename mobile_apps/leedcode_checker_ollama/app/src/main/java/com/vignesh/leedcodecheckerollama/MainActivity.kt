package com.vignesh.leedcodecheckerollama

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vignesh.leedcodecheckerollama.data.DailyChallengeUiModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeetCodeCheckerScreen(
    viewModel: LeetCodeViewModel = viewModel(),
    onOpenLink: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var markedCompleted by rememberSaveable { mutableStateOf(false) }
    var showPipelineLog by rememberSaveable { mutableStateOf(false) }
    var showPythonFile by rememberSaveable { mutableStateOf(true) }
    var showExplanation by rememberSaveable { mutableStateOf(true) }
    var showConcepts by rememberSaveable { mutableStateOf(true) }
    var handledChallengeUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var modelMenuExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.challenge?.url) {
        val challenge = state.challenge ?: return@LaunchedEffect
        if (handledChallengeUrl == challenge.url) return@LaunchedEffect

        Toast.makeText(
            context,
            "Fetched daily problem. Generating detailed Python solution automatically...",
            Toast.LENGTH_SHORT
        ).show()
        handledChallengeUrl = challenge.url
        markedCompleted = false
        showPipelineLog = false
        showPythonFile = true
        showExplanation = true
        showConcepts = true
    }

    LaunchedEffect(state.infoMessage) {
        val message = state.infoMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearInfoMessage()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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
                    text = "LeedCode Checker Ollama",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Button(
                onClick = { viewModel.fetchDailyChallenge() },
                enabled = !state.selectedModel.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("LeetCode")
            }

            Button(
                onClick = { viewModel.refreshAvailableModels() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh Local Ollama Models")
            }

            if (state.isModelLoading) {
                CircularProgressIndicator()
            }

            ExposedDropdownMenuBox(
                expanded = modelMenuExpanded,
                onExpandedChange = {
                    if (state.availableModels.isNotEmpty()) {
                        modelMenuExpanded = !modelMenuExpanded
                    }
                }
            ) {
                OutlinedTextField(
                    value = state.selectedModel.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Selected Ollama Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    state.availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                viewModel.selectModel(model)
                                modelMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (state.availableModels.isEmpty()) {
                Text(
                    text = "No local Ollama models listed yet. Refresh after your phone-side Ollama runtime pulls a model.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.isLoading) {
                CircularProgressIndicator()
            }

            state.infoMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
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

                        Button(onClick = { onOpenLink(challenge.url) }) {
                            Text("Open LeetCode Editor (Manual Submit)")
                        }

                        Spacer(modifier = Modifier.size(4.dp))
                        if (state.isAiLoading) {
                            Text("Generating detailed answer with Ollama model...")
                            CircularProgressIndicator()
                        }

                        state.aiError?.let { aiError ->
                            Text(
                                text = aiError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        state.aiCode?.let { aiCode ->
                            Button(
                                onClick = { showPythonFile = !showPythonFile },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showPythonFile) "Hide Generated Python File" else "Show Generated Python File")
                            }

                            if (showPythonFile) {
                                Text(
                                    text = "Generated Python File",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Filename: solution_${challenge.titleSlug}.py",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                SelectionContainer {
                                    Text(
                                        text = aiCode,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("LeetCode Python3 Code", aiCode))
                                    Toast.makeText(context, "LeetCode Python3 code copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Copy Python3 Code")
                            }
                        }

                        state.aiTestcaseValidation?.takeIf { it.isNotBlank() }?.let { validation ->
                            Text(
                                text = "Testcase Validation",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = validation,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        state.aiExplanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                            val concepts = extractDsaConcepts(explanation, challenge.tags)

                            if (concepts.isNotEmpty()) {
                                Button(
                                    onClick = { showConcepts = !showConcepts },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (showConcepts) "Hide Concepts Used" else "Show Concepts Used")
                                }

                                if (showConcepts) {
                                    Text(
                                        text = "Concepts Used (DSA Learning)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = concepts.joinToString(" | "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Button(
                                onClick = { showExplanation = !showExplanation },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showExplanation) "Hide Explanation" else "Show Explanation")
                            }

                            if (showExplanation) {
                                Text(
                                    text = "Detailed Explanation (DSA Learning)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                SelectionContainer {
                                    Text(
                                        text = explanation,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        state.aiDebugLog?.takeIf { it.isNotBlank() }?.let { pipelineLog ->
                            Button(
                                onClick = { showPipelineLog = !showPipelineLog },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showPipelineLog) "Hide Pipeline Logs" else "Show Pipeline Logs")
                            }

                            if (showPipelineLog) {
                                Text(
                                    text = "Pipeline Debug Log",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = pipelineLog,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Text(
                            text = "Auto sign-in and auto-submit are not supported safely in this app. Use Open LeetCode Editor, sign in, paste Python code, and submit.",
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

private fun extractDsaConcepts(explanation: String, tags: List<String>): List<String> {
    val text = explanation.lowercase()
    val concepts = linkedSetOf<String>()

    // Keep LeetCode tags first; they are high-confidence concepts.
    tags.map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { concepts.add(it) }

    val keywordMap = listOf(
        "prefix sum" to "Prefix Sum",
        "sliding window" to "Sliding Window",
        "two pointers" to "Two Pointers",
        "binary search" to "Binary Search",
        "dynamic programming" to "Dynamic Programming",
        "dp" to "Dynamic Programming",
        "greedy" to "Greedy",
        "monotonic stack" to "Monotonic Stack",
        "stack" to "Stack",
        "queue" to "Queue",
        "deque" to "Deque",
        "hash map" to "Hash Map",
        "hashmap" to "Hash Map",
        "dictionary" to "Hash Map",
        "set" to "Set",
        "sort" to "Sorting",
        "matrix" to "Matrix",
        "graph" to "Graph",
        "tree" to "Tree",
        "dfs" to "Depth-First Search",
        "bfs" to "Breadth-First Search",
        "union-find" to "Union Find",
        "disjoint set" to "Union Find",
        "backtracking" to "Backtracking",
        "recursion" to "Recursion",
        "bitmask" to "Bit Manipulation",
        "bit" to "Bit Manipulation",
        "heap" to "Heap / Priority Queue",
        "priority queue" to "Heap / Priority Queue"
    )

    keywordMap.forEach { (token, label) ->
        if (text.contains(token)) {
            concepts.add(label)
        }
    }

    return concepts.toList()
}

