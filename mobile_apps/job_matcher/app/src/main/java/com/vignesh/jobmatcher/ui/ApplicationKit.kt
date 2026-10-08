package com.vignesh.jobmatcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.vignesh.jobmatcher.data.ContactInfo
import com.vignesh.jobmatcher.export.CoverLetterDocument
import com.vignesh.jobmatcher.export.KitFiles
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobStatus

/** What each kit section is for -- shown under its title, and in Help. */
object KitHelp {
    const val SUMMARY = "Your 30-second pitch for this role, plus the doubts a screener will have. Use it to decide " +
        "whether to apply and to prepare for the recruiter call."
    const val BULLETS = "Your real achievements rewritten in this job's language, so keyword filters (ATS) and " +
        "recruiters see the fit. Paste the relevant ones into a copy of your resume for this application -- facts unchanged."
    const val COVER = "A formal, ready-to-send letter. It honestly addresses your biggest gaps with the steps you're " +
        "taking and a realistic timeline. Edit it if needed, then save as Word/PDF/text to upload, or share it with your resume."
    const val GAPS = "Each gap vs. this posting: why it matters, how you'll close it, an honest timeline, and the " +
        "one-liner to use if an interviewer asks."
    const val OUTREACH = "A short connection-request note and a formal message for recruiters or employees, with the " +
        "job link, signed with your details. {Name} is filled per person."
}

@Composable
fun ApplicationKitSection(job: Job, contact: ContactInfo, busy: Boolean, vm: JobViewModel) {
    SectionHeader("Application kit")
    if (job.status == JobStatus.NEW || job.status == JobStatus.DISMISSED) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔒 Shortlist this job to prepare its kit", fontWeight = FontWeight.SemiBold)
                Text(
                    "Kits (tailored resume bullets, cover letter, gap plan, outreach) are only made for jobs you shortlist, " +
                        "so your effort goes into the few applications you'll actually send.",
                    fontSize = 13.sp
                )
                Button(onClick = { vm.setStatus(job.id, JobStatus.SHORTLISTED) }, enabled = !busy) { Text("⭐ Shortlist") }
            }
        }
        return
    }

    val t = job.tailoring
    ClaudeRoundTripCard(
        title = if (t == null) "Prepare the application kit" else "Regenerate the kit",
        description = "One Claude round-trip writes all five parts below from your resume and this posting." +
            if (t?.coverLetterEdited == true) " Regenerating replaces your edited cover letter." else "",
        copyLabel = "✍️ Copy prompt",
        enabled = !busy,
        buildPrompt = { vm.tailoringPrompt(job.id) },
        onPaste = { vm.applyTailoring(job.id, it) }
    )
    if (t == null) return

    KitBlock("🎯 Match summary", KitHelp.SUMMARY, t.summary)
    KitBlock("📝 Resume bullets", KitHelp.BULLETS, t.resumeBullets)
    CoverLetterBlock(job, contact, busy, vm)
    KitBlock("🧭 Gap plan", KitHelp.GAPS, t.gapPlan)
    OutreachBlock(job, contact)
}

@Composable
fun KitBlock(title: String, why: String, body: String, extra: @Composable () -> Unit = {}) {
    if (body.isBlank()) return
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { ClaudeHandoff.copy(context, body, title.drop(2).trim()) }) { Text("Copy") }
            }
            Text(why, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(body, fontSize = 13.sp) }
            extra()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverLetterBlock(job: Job, contact: ContactInfo, busy: Boolean, vm: JobViewModel) {
    val t = job.tailoring ?: return
    if (t.coverLetter.isBlank()) return
    val context = LocalContext.current
    var editing by remember(job.id) { mutableStateOf(false) }
    val doc = CoverLetterDocument(contact, job, t.coverLetter)

    // One SAF launcher per format: CreateDocument's MIME type is fixed at registration.
    val launchers = CoverLetterDocument.Format.entries.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri ->
            uri?.let { vm.saveCoverLetter(doc, format, it) }
        }
    }

    KitBlock("✉️ Cover letter" + if (t.coverLetterEdited) " (edited)" else "", KitHelp.COVER, t.coverLetter) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { editing = true }, enabled = !busy) { Text("✏️ Edit", fontSize = 12.sp) }
            CoverLetterDocument.Format.entries.forEach { f ->
                OutlinedButton(onClick = { launchers.getValue(f).launch(doc.fileName(f)) }, enabled = !busy) {
                    Text("⬇️ .${f.extension}", fontSize = 12.sp)
                }
            }
        }
        Button(
            onClick = {
                runCatching { context.startActivity(KitFiles.shareIntent(context, doc, CoverLetterDocument.Format.PDF, "")) }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("📤 Share cover letter (PDF) + resume") }
        if (KitFiles.resumePdf(context) == null) {
            Text(
                "Tip: import your resume PDF in Setup → Profile and it will be attached automatically when you share.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(t.coverLetter) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit cover letter") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 460.dp)
                )
            },
            confirmButton = { TextButton(onClick = { vm.setCoverLetter(job.id, text); editing = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
}

/** Phase-2 outreach: the two message templates. (Recruiter finding is added on top in OutreachBlock.) */
@Composable
private fun OutreachBlock(job: Job, contact: ContactInfo) {
    val t = job.tailoring ?: return
    if (t.connectionNote.isBlank() && t.referralMessage.isBlank()) return
    KitBlock("🤝 Connection note (${t.connectionNote.length}/300)", KitHelp.OUTREACH, t.connectionNote)
    KitBlock("📨 Formal outreach message", KitHelp.OUTREACH, t.referralMessage)
}
