package com.vignesh.jobmatcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.claude.ClaudeHandoff
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobStatus

/**
 * One copy-prompt / paste-reply step of the manual Claude workflow. [buildPrompt] returns
 * null when there's nothing to send (the ViewModel reports why).
 */
@Composable
fun ClaudeRoundTripCard(
    title: String,
    description: String,
    copyLabel: String,
    pasteLabel: String = "📋 Paste Claude reply",
    enabled: Boolean = true,
    buildPrompt: () -> String?,
    onPaste: (String) -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { buildPrompt()?.let { ClaudeHandoff.sendToClaude(context, it, title) } },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { Text(copyLabel, fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { onPaste(ClaudeHandoff.readClipboard(context)) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { Text(pasteLabel, fontSize = 12.sp) }
            }
        }
    }
}

fun scoreColor(score: Int, threshold: Int): Color = when {
    score >= threshold -> Color(0xFF16A34A)
    score >= threshold - 15 -> Color(0xFFD97706)
    else -> Color(0xFFDC2626)
}

@Composable
fun ScorePill(label: String, score: Int, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Text(
            "$label $score%",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun JobCard(
    job: Job,
    threshold: Int,
    topChoice: Boolean = false,
    footer: String? = null,
    /** When set, NEW jobs get one-tap ⭐ Shortlist / 🚫 Not interested buttons. */
    onQuickStatus: ((JobStatus) -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (job.closed) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    job.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                val score = job.claudeScore
                if (score != null) ScorePill("Claude", score, scoreColor(score, threshold))
                else ScorePill("Local", job.localScore, MaterialTheme.colorScheme.outline)
            }
            Text(
                (if (topChoice) "⭐ " else "") + "${job.companyName} · ${job.location}",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            run {
                Text(
                    listOfNotNull(
                        job.origin.label,
                        if (job.needsDetails) "details missing" else null,
                        job.claudeVerdict.takeIf { it.isNotBlank() },
                        if (job.status != JobStatus.NEW) "${job.status.emoji} ${job.status.label}" else null,
                        if (job.closed) "Posting closed" else null
                    ).joinToString(" · "),
                    fontSize = 12.sp
                )
            }
            if (footer != null) {
                Text(footer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            if (onQuickStatus != null && job.status == JobStatus.NEW) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onQuickStatus(JobStatus.SHORTLISTED) }) { Text("⭐ Shortlist", fontSize = 12.sp) }
                    TextButton(onClick = { onQuickStatus(JobStatus.DISMISSED) }) { Text("🚫 Not interested", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun EmptyHint(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}
