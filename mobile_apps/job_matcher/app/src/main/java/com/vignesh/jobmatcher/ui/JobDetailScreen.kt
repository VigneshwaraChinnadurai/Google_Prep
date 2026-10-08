package com.vignesh.jobmatcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.JobViewModel
import com.vignesh.jobmatcher.claude.ClaudeHandoff
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobStatus
import com.vignesh.jobmatcher.model.SourceType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobDetailScreen(job: Job, threshold: Int, busy: Boolean, vm: JobViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var notes by remember(job.id) { mutableStateOf(job.notes) }
    var showFullDescription by remember(job.id) { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text(job.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${job.companyName} · ${job.location}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            listOfNotNull(
                job.postedAt.takeIf { it.isNotBlank() }?.let { "Posted $it" },
                "via ${job.source.label}",
                if (job.closed) "⚠️ No longer listed" else null
            ).joinToString(" · "),
            fontSize = 12.sp
        )
        if (job.source == SourceType.CLAUDE_SEARCH) {
            Text("Found by Claude web search -- open the link to confirm it's still live.", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
        }
        Button(
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(job.url))) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("🔗 Open posting / apply") }

        // ---- Scores ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val score = job.claudeScore
                    if (score != null) ScorePill("Claude", score, scoreColor(score, threshold))
                    ScorePill("Local", job.localScore, MaterialTheme.colorScheme.outline)
                    if (job.claudeVerdict.isNotBlank()) Text(job.claudeVerdict, fontWeight = FontWeight.SemiBold)
                }
                if (job.claudeReasons.isNotBlank()) Text(job.claudeReasons, fontSize = 13.sp)
                if (job.matchedSkills.isNotEmpty()) Text("✅ ${job.matchedSkills.joinToString()}", fontSize = 13.sp)
                if (job.gaps.isNotEmpty()) Text("⚠️ Gaps: ${job.gaps.joinToString()}", fontSize = 13.sp)
                if (job.localMatchedSkills.isNotEmpty()) {
                    Text("On-device keyword hits: ${job.localMatchedSkills.joinToString()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ClaudeRoundTripCard(
            title = if (job.isScored) "Re-score this job with Claude" else "Score this job with Claude",
            description = "Sends just this posting with your resume for a 0-100 match score.",
            copyLabel = "🤖 Copy prompt",
            enabled = !busy,
            buildPrompt = { vm.scoringPrompt(listOf(job.id)) },
            onPaste = vm::applyScores
        )

        // ---- Tracking ----
        SectionHeader("Status")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            JobStatus.entries.forEach { s ->
                FilterChip(
                    selected = job.status == s,
                    onClick = { vm.setStatus(job.id, s) },
                    enabled = !busy,
                    label = { Text("${s.emoji} ${s.label}", fontSize = 12.sp) }
                )
            }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (referrals, recruiter, interview dates…)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        if (notes != job.notes) {
            OutlinedButton(onClick = { vm.setNotes(job.id, notes) }, enabled = !busy) { Text("Save notes") }
        }

        // ---- Tailoring ----
        SectionHeader("Application kit")
        ClaudeRoundTripCard(
            title = "Tailor resume & cover letter",
            description = "Claude writes job-specific resume bullets, a cover letter, a gap plan and a referral message -- using only facts from your resume.",
            copyLabel = "✍️ Copy prompt",
            enabled = !busy,
            buildPrompt = { vm.tailoringPrompt(job.id) },
            onPaste = { vm.applyTailoring(job.id, it) }
        )
        job.tailoring?.let { t ->
            TailoringSection("Match summary", t.summary)
            TailoringSection("Resume bullets", t.resumeBullets)
            TailoringSection("Cover letter", t.coverLetter)
            TailoringSection("Gap plan", t.gapPlan)
            TailoringSection("Referral message", t.referralMessage)
        }

        // ---- Description ----
        SectionHeader("Job description")
        SelectionContainer {
            Text(
                if (showFullDescription || job.description.length <= 1200) job.description
                else job.description.take(1200) + "…",
                fontSize = 13.sp
            )
        }
        if (job.description.length > 1200) {
            TextButton(onClick = { showFullDescription = !showFullDescription }) {
                Text(if (showFullDescription) "Show less" else "Show full description")
            }
        }
        OutlinedButton(onClick = { ClaudeHandoff.copy(context, job.url, "Job link") }) { Text("Copy job link") }
    }
}

@Composable
private fun TailoringSection(title: String, body: String) {
    if (body.isBlank()) return
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { ClaudeHandoff.copy(context, body, title) }) { Text("Copy") }
            }
            SelectionContainer { Text(body, fontSize = 13.sp) }
        }
    }
}
