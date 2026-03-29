package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * StrategicChatbotMermaidDiagram — Compose-based architecture diagram
 *
 * Displays the full system architecture with labeled layers, styled cards,
 * and flow arrows. Fully theme-aware for dark/light mode.
 */
@Composable
fun StrategicChatbotMermaidDiagram(
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "📊 Strategic Chatbot Architecture",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Layer 1: Mobile App ──
        DiagramBlock(
            label = "📱 Mobile App (Android)",
            detail = "Jetpack Compose • MVVM • Retrofit",
            bgColor = Color(0xFF1976D2),
            textColor = Color.White
        )
        FlowArrow()

        // ── Layer 2: REST API ──
        DiagramBlock(
            label = "🔗 REST API Layer",
            detail = "POST /chat/quick • /chat/deep • /chat/followup",
            bgColor = Color(0xFF388E3C),
            textColor = Color.White
        )
        FlowArrow()

        // ── Layer 3: Backend Orchestrator ──
        DiagramBlock(
            label = "🎯 Backend Orchestrator",
            detail = "orchestrator.py • query_planner.py • llm_client.py",
            bgColor = Color(0xFFF57C00),
            textColor = Color.White
        )
        FlowArrow()

        // ── Layer 4: Three Chat Modes ──
        Text(
            text = "Chat Modes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SmallBlock(
                label = "⚡ Quick",
                detail = "~\$0.0002",
                bgColor = Color(0xFF7B1FA2),
                modifier = Modifier.weight(1f)
            )
            SmallBlock(
                label = "🔬 Deep",
                detail = "~\$0.01",
                bgColor = Color(0xFF7B1FA2),
                modifier = Modifier.weight(1f)
            )
            SmallBlock(
                label = "💬 Follow-up",
                detail = "~\$0.001",
                bgColor = Color(0xFF7B1FA2),
                modifier = Modifier.weight(1f)
            )
        }
        FlowArrow()

        // ── Layer 5: Agentic Pipeline ──
        DiagramBlock(
            label = "🧠 Agentic Pipeline",
            detail = "NewsAgent • FinancialModeler • Analyst • Critique • GraphMemory",
            bgColor = Color(0xFFC62828),
            textColor = Color.White
        )
        FlowArrow()

        // ── Layer 6: RAG Pipeline ──
        DiagramBlock(
            label = "📇 RAG Pipeline",
            detail = "web_fetcher → chunker → BM25 + Dense Retrieval → RRF Fusion",
            bgColor = Color(0xFF00695C),
            textColor = Color.White
        )
        FlowArrow()

        // ── Layer 7: External Services ──
        DiagramBlock(
            label = "☁️ External Services",
            detail = "Gemini API • Google News RSS • SEC EDGAR • DiskCache",
            bgColor = Color(0xFF37474F),
            textColor = Color.White
        )

        // Return arrow
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = "⬆️ Response streams back through each layer → Mobile App displays result",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Cost Guard ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "💰 Cost Guard (cross-cutting)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Tracks every LLM call • \$5/day budget • Real-time progress bar • Auto-halt on limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Building blocks
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun DiagramBlock(
    label: String,
    detail: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.85f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SmallBlock(
    label: String,
    detail: String,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun FlowArrow(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "▼",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
