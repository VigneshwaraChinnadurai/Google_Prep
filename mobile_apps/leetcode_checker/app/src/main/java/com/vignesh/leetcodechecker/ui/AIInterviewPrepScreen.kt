package com.vignesh.leetcodechecker.ui

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
import com.vignesh.leetcodechecker.AppSettingsStore
import kotlinx.coroutines.launch

/**
 * AI Interview Prep Screen - Uses Ollama for cost-effective interview practice
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIInterviewPrepScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettingsStore.load(context) }
    
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
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎤",
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AI Interview Prep",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3)
                    )
                    Text(
                        text = "Practice with Ollama (Local LLM)",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            }
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
                            val repository = OllamaRepository(context)
                            val response = repository.generateInterviewQuestion(initialPrompt)
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
                }
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
                                        val repository = OllamaRepository(context)
                                        val response = repository.conductInterview(
                                            topic = selectedTopic,
                                            history = messages,
                                            userAnswer = userMessage
                                        )
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
    onStartInterview: () -> Unit
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
                    text = "• AI interviewer asks technical questions\n• Answer as you would in a real interview\n• Get feedback and follow-up questions\n• Uses local Ollama LLM (cost-free)",
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
