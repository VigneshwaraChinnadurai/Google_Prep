package com.vignesh.leetcodechecker.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.ai.*
import com.vignesh.leetcodechecker.data.GeminiApi
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * AI Features Hub - Unified interface for all AI-powered learning features.
 * 
 * Features accessible:
 * 1. Progressive Hints - 5-level hint system
 * 2. Smart Recommendations - Personalized problem suggestions
 * 3. Knowledge Graph - Learning path visualization
 * 4. Voice Walkthrough - Audio-guided learning
 * 5. Complexity Analyzer - Code analysis
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIFeaturesHubScreen(
    apiKey: String,
    onBackClick: () -> Unit = {},
    onProblemClick: (String, String) -> Unit = { _, _ -> }, // titleSlug, title
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    var selectedTab by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // API
    val geminiApi = remember {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiApi::class.java)
    }
    
    // Engines
    val recommendationEngine = remember { ProblemRecommendationEngine(context, geminiApi, apiKey) }
    val knowledgeGraph = remember { ProblemKnowledgeGraph(context, geminiApi, apiKey) }
    val hintsEngine = remember { ProgressiveHintsEngine(geminiApi, apiKey) }
    val complexityAnalyzer = remember { ComplexityAnalyzer(geminiApi, apiKey) }
    
    // Recommendations state
    var recommendations by remember { mutableStateOf<List<ProblemRecommendationEngine.Recommendation>>(emptyList()) }
    var topicAnalysis by remember { mutableStateOf<List<ProblemRecommendationEngine.TopicAnalysis>>(emptyList()) }
    
    // Learning paths state
    var availablePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedPath by remember { mutableStateOf<ProblemKnowledgeGraph.LearningPath?>(null) }
    
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            recommendations = recommendationEngine.getRecommendations(count = 8)
            topicAnalysis = recommendationEngine.analyzeTopics(
                com.vignesh.leetcodechecker.data.LeetCodeActivityStorage.loadCompletionHistory(context)
            )
            availablePaths = knowledgeGraph.getAvailableLearningPaths()
            knowledgeGraph.syncWithHistory(context)
        } catch (e: Exception) {
            errorMessage = e.message
        }
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        // Header
        TopAppBar(
            title = {
                Text(
                    "🤖 AI Learning Hub",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF58A6FF)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF161B22)
            )
        )

        // Tab Row
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF161B22),
            contentColor = Color(0xFF58A6FF),
            edgePadding = 16.dp
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("📚 Recommendations") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("🗺️ Learning Paths") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("📊 Skill Analysis") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("⚡ Quick Tools") }
            )
        }

        // Content
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF58A6FF))
            }
        } else {
            when (selectedTab) {
                0 -> RecommendationsTab(
                    recommendations = recommendations,
                    onProblemClick = onProblemClick,
                    onRefresh = {
                        scope.launch {
                            isLoading = true
                            recommendations = recommendationEngine.getRecommendations(count = 8)
                            isLoading = false
                        }
                    }
                )
                1 -> LearningPathsTab(
                    availablePaths = availablePaths,
                    selectedPath = selectedPath,
                    knowledgeGraph = knowledgeGraph,
                    onSelectPath = { pathName ->
                        selectedPath = knowledgeGraph.getLearningPath(pathName)
                    },
                    onProblemClick = onProblemClick
                )
                2 -> SkillAnalysisTab(
                    topicAnalysis = topicAnalysis
                )
                3 -> QuickToolsTab(
                    hintsEngine = hintsEngine,
                    complexityAnalyzer = complexityAnalyzer
                )
            }
        }
    }
}

@Composable
private fun RecommendationsTab(
    recommendations: List<ProblemRecommendationEngine.Recommendation>,
    onProblemClick: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Personalized for You",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF58A6FF)
                    )
                }
            }
        }
        
        items(recommendations) { rec ->
            RecommendationCard(
                recommendation = rec,
                onClick = {
                    val slug = rec.problem.title.lowercase().replace(" ", "-")
                    onProblemClick(slug, rec.problem.title)
                }
            )
        }
        
        if (recommendations.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Star,
                    message = "Solve a few problems to get personalized recommendations!"
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: ProblemRecommendationEngine.Recommendation,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Difficulty indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when (recommendation.problem.difficulty) {
                            "Easy" -> Color(0xFF4ade80)
                            "Medium" -> Color(0xFFfacc15)
                            "Hard" -> Color(0xFFf87171)
                            else -> Color(0xFF8B949E)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    recommendation.problem.id,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recommendation.problem.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE6EDF3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    recommendation.reason,
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recommendation.problem.topics.take(2).forEach { topic ->
                        Surface(
                            color = Color(0xFF30363D),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                topic,
                                fontSize = 10.sp,
                                color = Color(0xFF58A6FF),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
            // Category icon
            Icon(
                when (recommendation.category) {
                    ProblemRecommendationEngine.RecommendationCategory.WEAKNESS_IMPROVEMENT -> Icons.Filled.ArrowForward
                    ProblemRecommendationEngine.RecommendationCategory.SKILL_BUILDING -> Icons.Filled.Build
                    ProblemRecommendationEngine.RecommendationCategory.CHALLENGE -> Icons.Filled.Star
                    ProblemRecommendationEngine.RecommendationCategory.REVIEW -> Icons.Filled.Refresh
                    ProblemRecommendationEngine.RecommendationCategory.DAILY_GOAL -> Icons.Filled.DateRange
                },
                contentDescription = null,
                tint = Color(0xFF58A6FF),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun LearningPathsTab(
    availablePaths: List<String>,
    selectedPath: ProblemKnowledgeGraph.LearningPath?,
    knowledgeGraph: ProblemKnowledgeGraph,
    onSelectPath: (String) -> Unit,
    onProblemClick: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Choose a Learning Path",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE6EDF3)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Path selector
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availablePaths) { path ->
                FilterChip(
                    selected = selectedPath?.name?.contains(path) == true,
                    onClick = { onSelectPath(path) },
                    label = { Text(path) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF21262D),
                        labelColor = Color(0xFFE6EDF3),
                        selectedContainerColor = Color(0xFF238636),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Selected path content
        if (selectedPath != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        selectedPath.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF58A6FF)
                    )
                    Text(
                        "${selectedPath.problems.size} problems • ~${selectedPath.estimatedHours}h",
                        fontSize = 14.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedPath.problems) { problem ->
                    LearningPathProblemCard(
                        problem = problem,
                        onClick = {
                            val slug = problem.title.lowercase().replace(" ", "-")
                            onProblemClick(slug, problem.title)
                        }
                    )
                }
            }
        } else {
            EmptyStateCard(
                icon = Icons.Filled.Place,
                message = "Select a learning path to see the recommended problem sequence"
            )
        }
    }
}

@Composable
private fun LearningPathProblemCard(
    problem: ProblemKnowledgeGraph.ProblemNode,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (problem.solved) Color(0xFF1C3A2B) else Color(0xFF161B22)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Progress indicator
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (problem.solved) Color(0xFF238636) else Color(0xFF30363D)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (problem.solved) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        problem.id,
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    problem.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    problem.difficulty,
                    fontSize = 12.sp,
                    color = when (problem.difficulty) {
                        "Easy" -> Color(0xFF4ade80)
                        "Medium" -> Color(0xFFfacc15)
                        "Hard" -> Color(0xFFf87171)
                        else -> Color(0xFF8B949E)
                    }
                )
            }
            
            // Proficiency bar
            if (problem.proficiency > 0) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF30363D))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(problem.proficiency)
                            .background(Color(0xFF238636))
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillAnalysisTab(
    topicAnalysis: List<ProblemRecommendationEngine.TopicAnalysis>
) {
    val sortedAnalysis = topicAnalysis
        .filter { it.solvedCount > 0 || it.topic in listOf("Array", "String", "Dynamic Programming", "Tree", "Graph") }
        .sortedByDescending { it.proficiencyScore }
    
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Your Skill Profile",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Based on ${topicAnalysis.sumOf { it.solvedCount }} solved problems",
                fontSize = 14.sp,
                color = Color(0xFF8B949E)
            )
        }
        
        items(sortedAnalysis) { topic ->
            SkillCard(topic)
        }
        
        if (sortedAnalysis.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Info,
                    message = "Solve problems to build your skill profile!"
                )
            }
        }
    }
}

@Composable
private fun SkillCard(
    topic: ProblemRecommendationEngine.TopicAnalysis
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    topic.topic,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    "${(topic.proficiencyScore * 100).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        topic.proficiencyScore >= 0.7 -> Color(0xFF4ade80)
                        topic.proficiencyScore >= 0.4 -> Color(0xFFfacc15)
                        else -> Color(0xFFf87171)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF30363D))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(topic.proficiencyScore.toFloat())
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF238636), Color(0xFF4ade80))
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${topic.solvedCount} solved",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E)
                )
                if (topic.averageTime > 0) {
                    Text(
                        "Avg: ${topic.averageTime} min",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )
                }
                if (topic.usedHintsCount > 0) {
                    Text(
                        "${topic.usedHintsCount} hints used",
                        fontSize = 12.sp,
                        color = Color(0xFFfb923c)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToolsTab(
    hintsEngine: ProgressiveHintsEngine,
    complexityAnalyzer: ComplexityAnalyzer
) {
    val scope = rememberCoroutineScope()
    
    // Complexity analyzer state
    var codeInput by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf<ComplexityAnalyzer.ComplexityResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Quick AI Tools",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )
        }
        
        // Complexity Analyzer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF58A6FF)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Complexity Analyzer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6EDF3)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        label = { Text("Paste your code here") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedLabelColor = Color(0xFF58A6FF),
                            unfocusedLabelColor = Color(0xFF8B949E),
                            cursorColor = Color(0xFF58A6FF)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (codeInput.isNotBlank()) {
                                    val quick = complexityAnalyzer.quickAnalyze(codeInput)
                                    analysisResult = ComplexityAnalyzer.ComplexityResult(
                                        timeComplexity = quick.estimatedTime,
                                        spaceComplexity = quick.estimatedSpace,
                                        timeExplanation = "Static analysis: ${quick.patterns.joinToString(", ")}",
                                        spaceExplanation = "Based on code structure",
                                        bottlenecks = quick.warnings,
                                        confidence = 0.6f
                                    )
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF58A6FF)
                            )
                        ) {
                            Text("Quick Analyze")
                        }
                        
                        Button(
                            onClick = {
                                if (codeInput.isNotBlank()) {
                                    isAnalyzing = true
                                    scope.launch {
                                        analysisResult = complexityAnalyzer.analyzeWithAI(codeInput)
                                        isAnalyzing = false
                                    }
                                }
                            },
                            enabled = !isAnalyzing && codeInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF238636)
                            )
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("AI Analyze")
                            }
                        }
                    }
                    
                    // Results
                    if (analysisResult != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = Color(0xFF30363D))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ComplexityResultView(analysisResult!!, complexityAnalyzer)
                    }
                }
            }
        }
        
        // Feature cards
        item {
            ToolFeatureCard(
                icon = Icons.Filled.Star,
                title = "Progressive Hints",
                description = "5-level hints that gradually reveal the solution",
                onClick = { /* Navigate to problem picker */ }
            )
        }
        
        item {
            ToolFeatureCard(
                icon = Icons.Filled.Person,
                title = "Voice Walkthrough",
                description = "Audio-guided problem explanations for commute or study",
                onClick = { /* Navigate to voice feature */ }
            )
        }
        
        item {
            ToolFeatureCard(
                icon = Icons.Filled.Share,
                title = "Knowledge Graph",
                description = "Visualize problem relationships and prerequisites",
                onClick = { /* Navigate to graph view */ }
            )
        }
    }
}

@Composable
private fun ComplexityResultView(
    result: ComplexityAnalyzer.ComplexityResult,
    analyzer: ComplexityAnalyzer
) {
    val (timeEmoji, timeColor) = analyzer.getComplexityDisplay(result.timeComplexity)
    val (spaceEmoji, spaceColor) = analyzer.getComplexityDisplay(result.spaceComplexity)
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Time complexity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Time Complexity", color = Color(0xFF8B949E), fontSize = 14.sp)
            Text(
                "$timeEmoji ${result.timeComplexity}",
                color = Color(android.graphics.Color.parseColor(timeColor)),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        
        Text(
            result.timeExplanation,
            color = Color(0xFFE6EDF3),
            fontSize = 13.sp
        )
        
        Divider(color = Color(0xFF30363D))
        
        // Space complexity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Space Complexity", color = Color(0xFF8B949E), fontSize = 14.sp)
            Text(
                "$spaceEmoji ${result.spaceComplexity}",
                color = Color(android.graphics.Color.parseColor(spaceColor)),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        
        Text(
            result.spaceExplanation,
            color = Color(0xFFE6EDF3),
            fontSize = 13.sp
        )
        
        // Optimizations
        if (result.optimizations.isNotEmpty()) {
            Divider(color = Color(0xFF30363D))
            Text(
                "💡 Optimization Tips",
                color = Color(0xFF58A6FF),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            result.optimizations.forEach { opt ->
                Text(
                    "• $opt",
                    color = Color(0xFFE6EDF3),
                    fontSize = 13.sp
                )
            }
        }
        
        // Warnings
        if (result.bottlenecks.isNotEmpty()) {
            result.bottlenecks.forEach { warning ->
                Surface(
                    color = Color(0xFF3D2A1E),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "⚠️ $warning",
                        color = Color(0xFFfb923c),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF30363D)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE6EDF3)
                )
                Text(
                    description,
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E)
                )
            }
            
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF8B949E)
            )
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: ImageVector,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF8B949E),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                message,
                fontSize = 14.sp,
                color = Color(0xFF8B949E),
                textAlign = TextAlign.Center
            )
        }
    }
}
