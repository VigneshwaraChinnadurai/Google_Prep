package com.vignesh.leetcodechecker.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI News Screen - Displays curated AI/ML and Quantum Computing news
 * Also shows announcements from tracked AI thought leaders
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AINewsScreen(
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AINewsRepository(context) }
    
    var isLoading by remember { mutableStateOf(true) }
    var newsArticles by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<NewsCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    
    // Load news on start
    LaunchedEffect(Unit) {
        try {
            val result = repository.fetchAINews()
            result.onSuccess { articles ->
                newsArticles = articles
                isLoading = false
            }.onFailure { error ->
                errorMessage = error.message
                isLoading = false
            }
        } catch (e: Exception) {
            errorMessage = e.message
            isLoading = false
        }
    }
    
    // Filter news by category if selected
    val displayedNews = remember(newsArticles, selectedCategory, searchQuery) {
        var filtered = newsArticles
        
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            filtered = filtered.filter { article ->
                val content = "${article.title} ${article.description}".lowercase()
                content.contains(query)
            }
        }
        
        if (selectedCategory != null) {
            filtered = filtered.filter { article ->
                val content = "${article.title} ${article.description}".lowercase()
                selectedCategory!!.keywords.any { keyword -> content.contains(keyword.lowercase()) }
            }
        }
        
        filtered
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        // Header
        TopAppBar(
            title = {
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search AI news...", color = Color(0xFF6E7681)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D),
                            cursorColor = Color(0xFF58A6FF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🤖", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI/ML News",
                            color = Color(0xFFE6EDF3),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
            actions = {
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                    Icon(
                        if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF58A6FF)
                    )
                }
                IconButton(onClick = {
                    isLoading = true
                    scope.launch {
                        val result = repository.fetchAINews(forceRefresh = true)
                        result.onSuccess { articles ->
                            newsArticles = articles
                            isLoading = false
                        }.onFailure { error ->
                            errorMessage = error.message
                            isLoading = false
                        }
                    }
                }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF58A6FF)
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF58A6FF)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161B22))
        )
        
        // Category Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF238636),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF21262D),
                        labelColor = Color(0xFF8B949E)
                    )
                )
            }
            items(NewsCategory.entries) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = if (selectedCategory == category) null else category },
                    label = { Text(category.displayName, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF238636),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF21262D),
                        labelColor = Color(0xFF8B949E)
                    )
                )
            }
        }
        
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF58A6FF))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading AI news...",
                            color = Color(0xFF8B949E)
                        )
                    }
                }
            }
            
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(text = "⚠️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Failed to load news",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6EDF3)
                        )
                        Text(
                            text = errorMessage ?: "Unknown error",
                            fontSize = 14.sp,
                            color = Color(0xFF8B949E)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val result = repository.fetchAINews(forceRefresh = true)
                                    result.onSuccess { articles ->
                                        newsArticles = articles
                                        isLoading = false
                                    }.onFailure { error ->
                                        errorMessage = error.message
                                        isLoading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            }
            
            displayedNews.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📰", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No news found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6EDF3)
                        )
                        Text(
                            text = "Try a different category or search term",
                            fontSize = 14.sp,
                            color = Color(0xFF8B949E)
                        )
                    }
                }
            }
            
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Featured/Top Story
                    if (displayedNews.isNotEmpty() && selectedCategory == null && searchQuery.isBlank()) {
                        item {
                            FeaturedNewsCard(article = displayedNews.first())
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Latest Updates",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE6EDF3)
                            )
                        }
                    }
                    
                    // News List
                    val startIndex = if (selectedCategory == null && searchQuery.isBlank()) 1 else 0
                    items(displayedNews.drop(startIndex)) { article ->
                        NewsArticleCard(article = article)
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedNewsCard(article: NewsArticle) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                article.url?.let { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF238636).copy(alpha = 0.3f),
                            Color(0xFF161B22)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF238636)
                    ) {
                        Text(
                            text = "⭐ FEATURED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    Text(
                        text = article.source?.name ?: article.source_id ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFF58A6FF)
                    )
                }
                
                Column {
                    Text(
                        text = article.title ?: "",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = article.description ?: "",
                        fontSize = 14.sp,
                        color = Color(0xFF8B949E),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDate(article.publishedAt ?: article.pubDate),
                        fontSize = 12.sp,
                        color = Color(0xFF6E7681)
                    )
                    
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = "Read more",
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsArticleCard(article: NewsArticle) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                article.url?.let { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(getCategoryColor(article).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getCategoryEmoji(article),
                    fontSize = 24.sp
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                // Source and Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = article.source?.name ?: article.source_id ?: "News",
                        fontSize = 11.sp,
                        color = Color(0xFF58A6FF),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatDate(article.publishedAt ?: article.pubDate),
                        fontSize = 11.sp,
                        color = Color(0xFF6E7681)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Title
                Text(
                    text = article.title ?: "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6EDF3),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Description
                Text(
                    text = article.description ?: "",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Keywords/Tags
                if (article.keywords?.isNotEmpty() == true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        article.keywords.take(3).forEach { keyword ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF30363D)
                            ) {
                                Text(
                                    text = keyword,
                                    fontSize = 10.sp,
                                    color = Color(0xFF8B949E),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getCategoryEmoji(article: NewsArticle): String {
    val content = "${article.title} ${article.description}".lowercase()
    return when {
        content.contains("quantum") -> "⚛️"
        content.contains("gpt") || content.contains("llm") || content.contains("claude") -> "💬"
        content.contains("research") || content.contains("paper") -> "📚"
        content.contains("nvidia") || content.contains("gpu") || content.contains("chip") -> "⚡"
        content.contains("image") || content.contains("dall-e") || content.contains("midjourney") -> "🎨"
        content.contains("robot") || content.contains("autonomous") -> "🤖"
        else -> "🧠"
    }
}

private fun getCategoryColor(article: NewsArticle): Color {
    val content = "${article.title} ${article.description}".lowercase()
    return when {
        content.contains("quantum") -> Color(0xFF9C27B0)
        content.contains("gpt") || content.contains("llm") -> Color(0xFF2196F3)
        content.contains("research") -> Color(0xFF4CAF50)
        content.contains("nvidia") -> Color(0xFF76B900)
        else -> Color(0xFF58A6FF)
    }
}

private fun formatDate(dateString: String?): String {
    if (dateString == null) return ""
    
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val date = inputFormat.parse(dateString)
        if (date != null) outputFormat.format(date) else dateString
    } catch (e: Exception) {
        dateString.take(10)
    }
}
