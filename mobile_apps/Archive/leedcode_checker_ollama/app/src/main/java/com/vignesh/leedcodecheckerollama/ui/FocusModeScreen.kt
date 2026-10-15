package com.vignesh.leedcodecheckerollama.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leedcodecheckerollama.data.LeetCodeActivityStorage
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.Canvas

/**
 * Focus Mode Screen - Pomodoro-style timer for concentrated problem solving
 */
@Composable
fun FocusModeScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedDuration by remember { mutableStateOf(25) } // minutes
    var remainingSeconds by remember { mutableStateOf(selectedDuration * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var sessionCompleted by remember { mutableStateOf(false) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    
    // Timer effect
    LaunchedEffect(isRunning, isPaused) {
        while (isRunning && !isPaused && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
        if (isRunning && remainingSeconds == 0) {
            sessionCompleted = true
            isRunning = false
            // Save session
            currentSessionId?.let { id ->
                LeetCodeActivityStorage.saveFocusSession(
                    context,
                    LeetCodeActivityStorage.FocusSession(
                        id = id,
                        startTime = System.currentTimeMillis() - (selectedDuration * 60 * 1000),
                        endTime = System.currentTimeMillis(),
                        durationMinutes = selectedDuration,
                        completed = true
                    )
                )
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF58A6FF)
                )
            }
            Text(
                text = "🎯 Focus Mode",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Concentrate on problem-solving without distractions",
            fontSize = 14.sp,
            color = Color(0xFF8B949E),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Timer Circle
        Box(
            modifier = Modifier
                .size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Progress ring
            val progress = remainingSeconds.toFloat() / (selectedDuration * 60)
            val sweepAngle = 360f * progress
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background ring
                drawArc(
                    color = Color(0xFF30363D),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
                // Progress ring
                drawArc(
                    color = when {
                        sessionCompleted -> Color(0xFF39D353)
                        progress < 0.25f -> Color(0xFFF85149)
                        progress < 0.5f -> Color(0xFFF0883E)
                        else -> Color(0xFF58A6FF)
                    },
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (sessionCompleted) {
                    Text(
                        text = "🎉",
                        fontSize = 48.sp
                    )
                    Text(
                        text = "Session Complete!",
                        fontSize = 20.sp,
                        color = Color(0xFF39D353),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    val minutes = remainingSeconds / 60
                    val seconds = remainingSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3)
                    )
                    if (isPaused) {
                        Text(
                            text = "PAUSED",
                            fontSize = 14.sp,
                            color = Color(0xFFF0883E)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Duration selector (only when not running)
        if (!isRunning && !sessionCompleted) {
            Text(
                text = "Select Duration",
                fontSize = 14.sp,
                color = Color(0xFF8B949E)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(15, 25, 45, 60).forEach { duration ->
                    DurationChip(
                        duration = duration,
                        isSelected = selectedDuration == duration,
                        onClick = {
                            selectedDuration = duration
                            remainingSeconds = duration * 60
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (sessionCompleted) {
                Button(
                    onClick = {
                        sessionCompleted = false
                        remainingSeconds = selectedDuration * 60
                        currentSessionId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Session", fontSize = 16.sp)
                }
            } else if (!isRunning) {
                Button(
                    onClick = {
                        isRunning = true
                        isPaused = false
                        currentSessionId = java.util.UUID.randomUUID().toString()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                    modifier = Modifier
                        .height(56.dp)
                        .width(160.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start", fontSize = 18.sp)
                }
            } else {
                // Pause/Resume button
                Button(
                    onClick = { isPaused = !isPaused },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) Color(0xFF238636) else Color(0xFFF0883E)
                    ),
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(
                        if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Refresh,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPaused) "Resume" else "Pause")
                }
                
                // Stop button
                Button(
                    onClick = {
                        isRunning = false
                        isPaused = false
                        remainingSeconds = selectedDuration * 60
                        currentSessionId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF85149)),
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Focus tips
        if (!isRunning) {
            FocusTipsCard()
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun DurationChip(
    duration: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF58A6FF) else Color(0xFF21262D)
    ) {
        Text(
            text = "${duration}m",
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color(0xFF8B949E),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun FocusTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "💡 Focus Tips",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            val tips = listOf(
                "🔕 Silence notifications",
                "📝 Read the problem twice",
                "🧠 Think before coding",
                "✍️ Write pseudocode first",
                "🎯 Focus on one approach"
            )
            
            tips.forEach { tip ->
                Text(
                    text = tip,
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
