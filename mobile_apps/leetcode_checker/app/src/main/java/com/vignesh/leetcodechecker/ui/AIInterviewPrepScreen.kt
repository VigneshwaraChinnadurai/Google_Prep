package com.vignesh.leetcodechecker.ui

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.data.OllamaRepository
import com.vignesh.leetcodechecker.data.GeminiApi
import com.vignesh.leetcodechecker.data.GeminiContent
import com.vignesh.leetcodechecker.data.GeminiGenerateRequest
import com.vignesh.leetcodechecker.data.GeminiGenerationConfig
import com.vignesh.leetcodechecker.data.GeminiPart
import com.vignesh.leetcodechecker.BuildConfig
import com.vignesh.leetcodechecker.AppSettingsStore
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * AI Interview Prep Screen - Uses Ollama or Gemini for interview practice
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIInterviewPrepScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // AI Provider settings - stored in SharedPreferences
    val aiProviderPrefs = remember { 
        context.getSharedPreferences("ai_interview_prefs", android.content.Context.MODE_PRIVATE) 
    }
    var selectedProvider by remember { 
        mutableStateOf(aiProviderPrefs.getString("provider", "Ollama") ?: "Ollama") 
    }
    var showSettings by remember { mutableStateOf(false) }
    
    var messages by remember { mutableStateOf(listOf<InterviewMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedTopic by remember { mutableStateOf("Data Structures") }
    var interviewStarted by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    
    // Scroll to bottom when new message added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF161B22)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF58A6FF)
                        )
                    }
                    Text(
                        text = "🎤",
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "AI Interview Prep",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6EDF3)
                        )
                        Text(
                            text = "Using $selectedProvider",
                            fontSize = 12.sp,
                            color = if (selectedProvider == "Gemini") Color(0xFF00B8A3) else Color(0xFF58A6FF)
                        )
                    }
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF8B949E)
                    )
                }
            }
        }
        
        // Settings Dialog
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                containerColor = Color(0xFF161B22),
                title = {
                    Text(
                        text = "⚙️ AI Provider Settings",
                        color = Color(0xFFE6EDF3),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Choose your AI provider for interview practice:",
                            color = Color(0xFF8B949E),
                            fontSize = 14.sp
                        )
                        
                        // Ollama Option
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    selectedProvider = "Ollama"
                                    aiProviderPrefs.edit().putString("provider", "Ollama").apply()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedProvider == "Ollama") Color(0xFF58A6FF).copy(alpha = 0.2f) else Color(0xFF21262D)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProvider == "Ollama",
                                    onClick = { 
                                        selectedProvider = "Ollama"
                                        aiProviderPrefs.edit().putString("provider", "Ollama").apply()
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF58A6FF)
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Ollama (Local)",
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFE6EDF3)
                                    )
                                    Text(
                                        text = "Free • Runs locally • Requires Ollama setup",
                                        fontSize = 12.sp,
                                        color = Color(0xFF8B949E)
                                    )
                                }
                            }
                        }
                        
                        // Gemini Option
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    selectedProvider = "Gemini"
                                    aiProviderPrefs.edit().putString("provider", "Gemini").apply()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedProvider == "Gemini") Color(0xFF00B8A3).copy(alpha = 0.2f) else Color(0xFF21262D)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProvider == "Gemini",
                                    onClick = { 
                                        selectedProvider = "Gemini"
                                        aiProviderPrefs.edit().putString("provider", "Gemini").apply()
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF00B8A3)
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Gemini (Cloud)",
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFE6EDF3)
                                    )
                                    Text(
                                        text = "Requires API key • Better responses • May cost $",
                                        fontSize = 12.sp,
                                        color = Color(0xFF8B949E)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSettings = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                    ) {
                        Text("Done")
                    }
                }
            )
        }
        
        if (!interviewStarted) {
            // Topic Selection
            TopicSelectionView(
                selectedTopic = selectedTopic,
                onTopicSelected = { selectedTopic = it },
                onStartInterview = {
                    interviewStarted = true
                    // Start with initial question
                    scope.launch {
                        isLoading = true
                        val initialPrompt = getInitialInterviewPrompt(selectedTopic)
                        try {
                            val response = if (selectedProvider == "Gemini") {
                                // Use Gemini
                                generateGeminiInterviewQuestion(context, initialPrompt)
                            } else {
                                // Use Ollama
                                val repository = OllamaRepository(context)
                                repository.generateInterviewQuestion(initialPrompt)
                            }
                            messages = messages + InterviewMessage(
                                content = response,
                                isUser = false,
                                timestamp = System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            messages = messages + InterviewMessage(
                                content = "Welcome! Let's practice $selectedTopic interview questions. I'll ask you questions and provide feedback. Ready to begin?\n\nFirst question: Can you explain the time complexity of common operations on a hash table?",
                                isUser = false,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                        isLoading = false
                    }
                },
                selectedProvider = selectedProvider
            )
        } else {
            // Chat Interface
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
                
                if (isLoading) {
                    item {
                        LoadingIndicator()
                    }
                }
            }
            
            // Input Area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF161B22)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Type your answer...", color = Color(0xFF6E7681)) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D),
                            cursorColor = Color(0xFF58A6FF)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val userMessage = inputText
                                inputText = ""
                                messages = messages + InterviewMessage(
                                    content = userMessage,
                                    isUser = true,
                                    timestamp = System.currentTimeMillis()
                                )
                                
                                scope.launch {
                                    isLoading = true
                                    try {
                                        val response = if (selectedProvider == "Gemini") {
                                            conductGeminiInterview(context, selectedTopic, messages, userMessage)
                                        } else {
                                            val repository = OllamaRepository(context)
                                            repository.conductInterview(
                                                topic = selectedTopic,
                                                history = messages,
                                                userAnswer = userMessage
                                            )
                                        }
                                        messages = messages + InterviewMessage(
                                            content = response,
                                            isUser = false,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    } catch (e: Exception) {
                                        messages = messages + InterviewMessage(
                                            content = "That's a good point! Let me ask you a follow-up: How would you optimize this approach for better performance?",
                                            isUser = false,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    }
                                    isLoading = false
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank() && !isLoading)
                                    Color(0xFF238636)
                                else
                                    Color(0xFF30363D)
                            )
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicSelectionView(
    selectedTopic: String,
    onTopicSelected: (String) -> Unit,
    onStartInterview: () -> Unit,
    selectedProvider: String = "Ollama"
) {
    val topics = listOf(
        "Data Structures" to "🗂️",
        "Algorithms" to "⚡",
        "System Design" to "🏗️",
        "Dynamic Programming" to "📊",
        "Trees & Graphs" to "🌳",
        "Arrays & Strings" to "📝",
        "Behavioral" to "💬",
        "Object-Oriented Design" to "🎯"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Select Interview Topic",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE6EDF3)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        topics.forEach { (topic, emoji) ->
            TopicCard(
                topic = topic,
                emoji = emoji,
                isSelected = selectedTopic == topic,
                onClick = { onTopicSelected(topic) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onStartInterview,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Interview", fontSize = 18.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💡 How it works",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6EDF3)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (selectedProvider == "Gemini")
                        "• AI interviewer asks technical questions\n• Answer as you would in a real interview\n• Get feedback and follow-up questions\n• Using Gemini AI (cloud-based)"
                    else
                        "• AI interviewer asks technical questions\n• Answer as you would in a real interview\n• Get feedback and follow-up questions\n• Using Ollama LLM (local, cost-free)",
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun TopicCard(
    topic: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF0D2818) else Color(0xFF161B22)
        ),
        border = if (isSelected) BorderStroke(1.dp, Color(0xFF238636)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = topic,
                fontSize = 16.sp,
                color = Color(0xFFE6EDF3),
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF238636)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: InterviewMessage) {
    val backgroundColor = if (message.isUser) Color(0xFF238636) else Color(0xFF161B22)
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Text(
                text = message.content,
                fontSize = 14.sp,
                color = Color(0xFFE6EDF3),
                modifier = Modifier.padding(12.dp),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color(0xFF58A6FF),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "AI is thinking...",
            fontSize = 13.sp,
            color = Color(0xFF8B949E)
        )
    }
}

data class InterviewMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long
)

private fun getInitialInterviewPrompt(topic: String): String {
    return """You are a technical interviewer conducting a coding interview focused on $topic. 
Start by introducing yourself briefly and ask an initial question related to $topic.
Keep your questions clear and progressively challenging.
After each answer, provide brief feedback and ask a follow-up question.
Be encouraging but also point out areas for improvement."""
}

// ════════════════════════════════════════════════════════════════════════
// Gemini API Helper Functions
// ════════════════════════════════════════════════════════════════════════

private suspend fun generateGeminiInterviewQuestion(context: Context, prompt: String): String {
    val apiKey = BuildConfig.CHATBOT_GEMINI_API_KEY.ifBlank {
        BuildConfig.GEMINI_API_KEY
    }
    
    if (apiKey.isBlank()) {
        throw Exception("Gemini API key not configured")
    }
    
    val geminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(GeminiApi::class.java)
    
    val request = GeminiGenerateRequest(
        contents = listOf(
            GeminiContent(
                parts = listOf(GeminiPart(text = prompt))
            )
        ),
        generationConfig = GeminiGenerationConfig(
            temperature = 0.7,
            maxOutputTokens = 1024
        )
    )
    
    val response = geminiApi.generateContent(
        model = "gemini-2.0-flash",
        apiKey = apiKey,
        body = request
    )
    
    return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        ?: "Let's begin! Tell me about your experience with this topic."
}

private suspend fun conductGeminiInterview(
    context: Context,
    topic: String,
    history: List<InterviewMessage>,
    userAnswer: String
): String {
    val apiKey = BuildConfig.CHATBOT_GEMINI_API_KEY.ifBlank {
        BuildConfig.GEMINI_API_KEY
    }
    
    if (apiKey.isBlank()) {
        throw Exception("Gemini API key not configured")
    }
    
    val geminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(GeminiApi::class.java)
    
    // Build conversation history for context
    val historyText = history.takeLast(6).joinToString("\n") { msg ->
        if (msg.isUser) "Candidate: ${msg.content}" else "Interviewer: ${msg.content}"
    }
    
    val prompt = """You are a technical interviewer conducting a coding interview focused on $topic.
Continue the interview based on this conversation history:

$historyText
Candidate: $userAnswer

Provide brief feedback on the answer (1-2 sentences) and ask a follow-up question related to $topic.
Be encouraging but constructive. Keep response concise."""
    
    val request = GeminiGenerateRequest(
        contents = listOf(
            GeminiContent(
                parts = listOf(GeminiPart(text = prompt))
            )
        ),
        generationConfig = GeminiGenerationConfig(
            temperature = 0.7,
            maxOutputTokens = 512
        )
    )
    
    val response = geminiApi.generateContent(
        model = "gemini-2.0-flash",
        apiKey = apiKey,
        body = request
    )
    
    return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        ?: "That's interesting! Can you elaborate more on your approach?"
}
