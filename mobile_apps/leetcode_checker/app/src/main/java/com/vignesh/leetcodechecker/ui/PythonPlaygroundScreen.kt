package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leetcodechecker.BuildConfig
import com.vignesh.leetcodechecker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Python Playground Screen — IDE-like interface for writing and testing Python code.
 * 
 * Features:
 * - Syntax-highlighted code editor
 * - Code execution via Gemini AI (simulates Python runtime)
 * - Output panel with results
 * - Code templates and examples
 * - History of recent code executions
 */
@Composable
fun PythonPlaygroundScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var code by rememberSaveable { mutableStateOf(DEFAULT_CODE) }
    var output by rememberSaveable { mutableStateOf("") }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "🐍 Python Playground",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    text = "Write and test Python code",
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E)
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showTemplates = !showTemplates },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF58A6FF)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("📋 Templates", fontSize = 12.sp)
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            errorMessage = null
                            try {
                                output = executePythonCode(code)
                            } catch (e: Exception) {
                                errorMessage = "Error: ${e.message}"
                            }
                            isRunning = false
                        }
                    },
                    enabled = !isRunning && code.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF238636)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running...", fontSize = 12.sp)
                    } else {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Run",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run", fontSize = 12.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Templates Panel
        if (showTemplates) {
            TemplatesPanel(
                onSelectTemplate = { template ->
                    code = template.code
                    showTemplates = false
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Error Message
        errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF85149).copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️ $error",
                        color = Color(0xFFF85149),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { errorMessage = null },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Dismiss",
                            tint = Color(0xFFF85149),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Code Editor
        Text(
            text = "📝 Code Editor",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8B949E)
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF161B22)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column {
                // Line numbers + editor
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Line numbers
                    Column(
                        modifier = Modifier
                            .width(32.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        val lines = code.lines()
                        lines.forEachIndexed { index, _ ->
                            Text(
                                text = "${index + 1}",
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF484F58),
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Code editor
                    BasicTextField(
                        value = code,
                        onValueChange = { code = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFE6EDF3),
                            lineHeight = 18.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF58A6FF))
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { code = DEFAULT_CODE },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF8B949E)
                ),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Reset",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { code = "" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF8B949E)
                ),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Clear",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { output = "" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF8B949E)
                ),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("Clear Output", fontSize = 11.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Output Panel
        Text(
            text = "📤 Output",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8B949E)
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0D1117)
            ),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (output.isBlank()) {
                    Text(
                        text = "Run your code to see output here...",
                        color = Color(0xFF484F58),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = output,
                        color = Color(0xFF7EE787),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatesPanel(
    onSelectTemplate: (CodeTemplate) -> Unit
) {
    val templates = listOf(
        CodeTemplate("Hello World", "# Hello World\nprint(\"Hello, World!\")"),
        CodeTemplate("Fibonacci", """# Fibonacci Sequence
def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

for i in range(10):
    print(f"F({i}) = {fibonacci(i)}")"""),
        CodeTemplate("Two Sum", """# LeetCode #1: Two Sum
def twoSum(nums, target):
    seen = {}
    for i, num in enumerate(nums):
        complement = target - num
        if complement in seen:
            return [seen[complement], i]
        seen[num] = i
    return []

# Test
nums = [2, 7, 11, 15]
target = 9
print(f"Input: {nums}, target: {target}")
print(f"Output: {twoSum(nums, target)}")"""),
        CodeTemplate("Binary Search", """# Binary Search
def binary_search(arr, target):
    left, right = 0, len(arr) - 1
    while left <= right:
        mid = (left + right) // 2
        if arr[mid] == target:
            return mid
        elif arr[mid] < target:
            left = mid + 1
        else:
            right = mid - 1
    return -1

# Test
arr = [1, 3, 5, 7, 9, 11, 13]
target = 7
print(f"Array: {arr}")
print(f"Index of {target}: {binary_search(arr, target)}")"""),
        CodeTemplate("Quick Sort", """# Quick Sort
def quick_sort(arr):
    if len(arr) <= 1:
        return arr
    pivot = arr[len(arr) // 2]
    left = [x for x in arr if x < pivot]
    middle = [x for x in arr if x == pivot]
    right = [x for x in arr if x > pivot]
    return quick_sort(left) + middle + quick_sort(right)

# Test
arr = [64, 34, 25, 12, 22, 11, 90]
print(f"Original: {arr}")
print(f"Sorted: {quick_sort(arr)}")""")
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161B22)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "Code Templates",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                templates.forEach { template ->
                    OutlinedButton(
                        onClick = { onSelectTemplate(template) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF58A6FF)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(template.name, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private data class CodeTemplate(
    val name: String,
    val code: String
)

private const val DEFAULT_CODE = """# Python Playground
# Write your Python code here and click Run!

def greet(name):
    return f"Hello, {name}!"

# Test the function
result = greet("LeetCode Learner")
print(result)

# Try some calculations
numbers = [1, 2, 3, 4, 5]
print(f"Sum: {sum(numbers)}")
print(f"Average: {sum(numbers) / len(numbers)}")
"""

/**
 * Execute Python code using Gemini AI to simulate Python runtime.
 * This is a workaround since Android can't run Python directly.
 */
private suspend fun executePythonCode(code: String): String = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isBlank()) {
        return@withContext "Error: No API key configured. Add GEMINI_API_KEY to local.properties."
    }
    
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    val api = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiApi::class.java)
    
    val systemPrompt = """You are a Python interpreter. Execute the following Python code and return ONLY the output that would be printed to stdout.

Rules:
1. Return ONLY the output, no explanations or markdown
2. If there's an error, return the error message as Python would show it
3. If there's no output, return "(No output)"
4. Execute the code exactly as written
5. Handle print statements, function calls, and variable outputs"""
    
    val request = GeminiGenerateRequest(
        systemInstruction = GeminiContent(
            parts = listOf(GeminiPart(text = systemPrompt))
        ),
        contents = listOf(
            GeminiContent(
                parts = listOf(GeminiPart(text = "```python\n$code\n```"))
            )
        ),
        generationConfig = GeminiGenerationConfig(
            temperature = 0.0,
            maxOutputTokens = 2048,
            responseMimeType = "text/plain"
        )
    )
    
    val response = api.generateContent("gemini-2.5-flash", apiKey, request)
    val output = response.candidates?.firstOrNull()?.content?.parts
        ?.mapNotNull { it.text }?.joinToString("") ?: "(No response)"
    
    // Clean up output
    output.trim()
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
}
