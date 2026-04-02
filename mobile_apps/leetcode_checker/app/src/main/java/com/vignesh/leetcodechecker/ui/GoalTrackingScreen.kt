package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.vignesh.leetcodechecker.data.LeetCodeActivityStorage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Goal Tracking Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalTrackingScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var goals by remember { mutableStateOf(LeetCodeActivityStorage.loadGoals(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    
    // Update goal progress on composition
    LaunchedEffect(Unit) {
        LeetCodeActivityStorage.updateGoalProgress(context)
        goals = LeetCodeActivityStorage.loadGoals(context)
    }
    
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
            Text(
                text = "🎯 Goal Tracking",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )
            
            IconButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add Goal",
                    tint = Color(0xFF58A6FF)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Quick Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickStatCard(
                modifier = Modifier.weight(1f),
                value = goals.count { it.isCompleted }.toString(),
                label = "Completed",
                color = Color(0xFF238636)
            )
            QuickStatCard(
                modifier = Modifier.weight(1f),
                value = goals.count { !it.isCompleted }.toString(),
                label = "In Progress",
                color = Color(0xFFF0883E)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Goals List
        if (goals.isEmpty()) {
            EmptyGoalsPlaceholder(onAddGoal = { showAddDialog = true })
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(goals.sortedBy { it.isCompleted }) { goal ->
                    GoalCard(goal = goal)
                }
            }
        }
    }
    
    // Add Goal Dialog
    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { goal ->
                LeetCodeActivityStorage.saveGoal(context, goal)
                goals = LeetCodeActivityStorage.loadGoals(context)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF8B949E)
            )
        }
    }
}

@Composable
private fun GoalCard(goal: LeetCodeActivityStorage.Goal) {
    val progress = if (goal.target > 0) (goal.current.toFloat() / goal.target).coerceIn(0f, 1f) else 0f
    val progressColor = when {
        goal.isCompleted -> Color(0xFF238636)
        progress >= 0.7f -> Color(0xFF39D353)
        progress >= 0.3f -> Color(0xFFF0883E)
        else -> Color(0xFF58A6FF)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (goal.isCompleted) Color(0xFF0D2818) else Color(0xFF161B22)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getGoalIcon(goal.type),
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = getGoalTitle(goal.type, goal.target),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE6EDF3)
                        )
                        Text(
                            text = "${goal.startDate} → ${goal.endDate}",
                            fontSize = 11.sp,
                            color = Color(0xFF8B949E)
                        )
                    }
                }
                
                if (goal.isCompleted) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color(0xFF238636),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = Color(0xFF30363D)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${goal.current} / ${goal.target}",
                    fontSize = 13.sp,
                    color = progressColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E)
                )
            }
        }
    }
}

@Composable
private fun EmptyGoalsPlaceholder(onAddGoal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🎯", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No goals yet",
            fontSize = 18.sp,
            color = Color(0xFFE6EDF3),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Set a goal to track your progress",
            fontSize = 14.sp,
            color = Color(0xFF8B949E)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAddGoal,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Goal")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onAdd: (LeetCodeActivityStorage.Goal) -> Unit
) {
    var selectedType by remember { mutableStateOf(LeetCodeActivityStorage.GoalType.WEEKLY_PROBLEMS) }
    var targetValue by remember { mutableStateOf("5") }
    
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today = fmt.format(Date())
    val calendar = Calendar.getInstance()
    
    val endDate = when (selectedType) {
        LeetCodeActivityStorage.GoalType.DAILY_PROBLEMS -> today
        LeetCodeActivityStorage.GoalType.WEEKLY_PROBLEMS -> {
            calendar.add(Calendar.DAY_OF_YEAR, 7)
            fmt.format(calendar.time)
        }
        LeetCodeActivityStorage.GoalType.MONTHLY_PROBLEMS -> {
            calendar.add(Calendar.MONTH, 1)
            fmt.format(calendar.time)
        }
        else -> {
            calendar.add(Calendar.DAY_OF_YEAR, 30)
            fmt.format(calendar.time)
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161B22),
        title = {
            Text(
                text = "Add New Goal",
                color = Color(0xFFE6EDF3)
            )
        },
        text = {
            Column {
                Text(
                    text = "Goal Type",
                    fontSize = 14.sp,
                    color = Color(0xFF8B949E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                LeetCodeActivityStorage.GoalType.values().forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF58A6FF)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${getGoalIcon(type)} ${type.name.replace("_", " ")}",
                            color = Color(0xFFE6EDF3)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { targetValue = it.filter { c -> c.isDigit() } },
                    label = { Text("Target", color = Color(0xFF8B949E)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFE6EDF3),
                        unfocusedTextColor = Color(0xFFE6EDF3),
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = targetValue.toIntOrNull() ?: 5
                    val goal = LeetCodeActivityStorage.Goal(
                        type = selectedType,
                        target = target,
                        startDate = today,
                        endDate = endDate
                    )
                    onAdd(goal)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8B949E))
            }
        }
    )
}

private fun getGoalIcon(type: LeetCodeActivityStorage.GoalType): String {
    return when (type) {
        LeetCodeActivityStorage.GoalType.DAILY_PROBLEMS -> "📅"
        LeetCodeActivityStorage.GoalType.WEEKLY_PROBLEMS -> "📆"
        LeetCodeActivityStorage.GoalType.MONTHLY_PROBLEMS -> "🗓️"
        LeetCodeActivityStorage.GoalType.STREAK_DAYS -> "🔥"
        LeetCodeActivityStorage.GoalType.TOPIC_MASTERY -> "🎨"
        LeetCodeActivityStorage.GoalType.DIFFICULTY_CHALLENGE -> "💪"
    }
}

private fun getGoalTitle(type: LeetCodeActivityStorage.GoalType, target: Int): String {
    return when (type) {
        LeetCodeActivityStorage.GoalType.DAILY_PROBLEMS -> "Solve $target problem(s) today"
        LeetCodeActivityStorage.GoalType.WEEKLY_PROBLEMS -> "Solve $target problem(s) this week"
        LeetCodeActivityStorage.GoalType.MONTHLY_PROBLEMS -> "Solve $target problem(s) this month"
        LeetCodeActivityStorage.GoalType.STREAK_DAYS -> "Maintain a $target-day streak"
        LeetCodeActivityStorage.GoalType.TOPIC_MASTERY -> "Master $target topic(s)"
        LeetCodeActivityStorage.GoalType.DIFFICULTY_CHALLENGE -> "Solve $target hard problem(s)"
    }
}
