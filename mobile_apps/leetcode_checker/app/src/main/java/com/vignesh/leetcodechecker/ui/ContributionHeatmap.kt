package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.api.ContributionDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * GitHub-style Contribution Heatmap Calendar
 * 
 * Displays a year view of contributions with color intensity based on contribution count
 * Matches GitHub's dark theme aesthetic
 */
@Composable
fun ContributionHeatmap(
    contributionDays: List<ContributionDay>,
    totalContributions: Int,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    // Auto-scroll to the end (current month) when content is measured
    LaunchedEffect(Unit) {
        // Wait for the scroll state to have a valid maxValue (content measured)
        snapshotFlow { scrollState.maxValue }
            .filter { it > 0 }
            .first()
        scrollState.scrollTo(scrollState.maxValue)
    }
    
    // GitHub dark theme colors for contribution levels
    val level0 = Color(0xFF161B22) // No contributions
    val level1 = Color(0xFF0E4429) // 1-2 contributions
    val level2 = Color(0xFF006D32) // 3-5 contributions
    val level3 = Color(0xFF26A641) // 6-9 contributions
    val level4 = Color(0xFF39D353) // 10+ contributions
    
    val cellSize = 12.dp
    val cellSpacing = 3.dp
    val cornerRadius = 2.dp
    
    // Group contributions by week (for columns)
    val contributionMap = contributionDays.associateBy { it.date ?: "" }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1117))
            .padding(16.dp)
    ) {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$totalContributions contributions in the last year",
                color = Color(0xFFE6EDF3),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Calculate starting date (52 weeks ago from today, aligned to start of week)
        val today = LocalDate.now()
        val startDate = today.minusWeeks(52).minusDays(today.dayOfWeek.value.toLong() - 1)
        
        // Calculate month labels based on actual date range
        val monthLabels = remember(startDate) {
            val labels = mutableListOf<Pair<String, Int>>() // (month name, week position)
            var currentMonth = -1
            for (week in 0 until 53) {
                val weekStartDate = startDate.plusWeeks(week.toLong())
                val month = weekStartDate.monthValue
                if (month != currentMonth) {
                    currentMonth = month
                    val monthName = weekStartDate.month.getDisplayName(TextStyle.SHORT, Locale.US)
                    labels.add(monthName to week)
                }
            }
            labels
        }
        
        // Month labels - dynamically positioned based on actual weeks
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(start = 28.dp)
        ) {
            var lastWeekPos = 0
            monthLabels.forEachIndexed { index, (month, weekPos) ->
                // Add spacer for weeks since last label
                val spacerWidth = if (index == 0) {
                    (cellSize + cellSpacing) * weekPos
                } else {
                    (cellSize + cellSpacing) * (weekPos - lastWeekPos)
                }
                if (spacerWidth.value > 0) {
                    Spacer(modifier = Modifier.width(spacerWidth))
                }
                Text(
                    text = month,
                    color = Color(0xFF848D97),
                    fontSize = 10.sp
                )
                lastWeekPos = weekPos
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Heatmap grid with day labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            // Day labels (Mon, Wed, Fri)
            Column(
                modifier = Modifier.padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(cellSpacing)
            ) {
                Spacer(modifier = Modifier.height(cellSize)) // Empty for alignment
                Text("Mon", color = Color(0xFF848D97), fontSize = 9.sp, modifier = Modifier.height(cellSize))
                Spacer(modifier = Modifier.height(cellSize + cellSpacing))
                Text("Wed", color = Color(0xFF848D97), fontSize = 9.sp, modifier = Modifier.height(cellSize))
                Spacer(modifier = Modifier.height(cellSize + cellSpacing))
                Text("Fri", color = Color(0xFF848D97), fontSize = 9.sp, modifier = Modifier.height(cellSize))
            }
            
            // Contribution grid
            Canvas(
                modifier = Modifier
                    .height((cellSize + cellSpacing) * 7)
                    .width((cellSize + cellSpacing) * 53)
            ) {
                val cellSizePx = cellSize.toPx()
                val cellSpacingPx = cellSpacing.toPx()
                val cornerRadiusPx = cornerRadius.toPx()
                
                // Draw 53 weeks (columns) x 7 days (rows)
                for (week in 0 until 53) {
                    for (day in 0 until 7) {
                        val date = startDate.plusDays((week * 7 + day).toLong())
                        if (date.isAfter(today)) continue
                        
                        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val contribution = contributionMap[dateStr]
                        val count = contribution?.contributionCount ?: 0
                        
                        val color = when {
                            count == 0 -> level0
                            count <= 2 -> level1
                            count <= 5 -> level2
                            count <= 9 -> level3
                            else -> level4
                        }
                        
                        val x = week * (cellSizePx + cellSpacingPx)
                        val y = day * (cellSizePx + cellSpacingPx)
                        
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(cellSizePx, cellSizePx),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Less",
                color = Color(0xFF848D97),
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            listOf(level0, level1, level2, level3, level4).forEach { color ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "More",
                color = Color(0xFF848D97),
                fontSize = 10.sp
            )
        }
    }
}
