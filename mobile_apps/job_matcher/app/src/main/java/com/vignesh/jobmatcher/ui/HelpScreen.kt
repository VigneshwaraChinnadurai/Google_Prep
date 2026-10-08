package com.vignesh.jobmatcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.jobmatcher.model.AppSettings
import com.vignesh.jobmatcher.model.EventType
import com.vignesh.jobmatcher.model.JobStatus

private data class HelpTopic(val emoji: String, val title: String, val body: String)

/** Help content. Status meanings and kit explanations come from the same text used in the app. */
private fun helpTopics(s: AppSettings): List<HelpTopic> = listOf(
    HelpTopic(
        "🧭", "How Job Matcher works",
        """
Find → Score → Shortlist → Prepare → Apply → Track.

1. FIND: the app fetches jobs from your companies' careers sites every day, Claude searches the ones without a public site, and you can add any job by link.
2. SCORE: an on-device check keeps likely fits (≥${s.prefilterThreshold}%), then Claude scores them properly. Jobs ≥${s.matchThreshold}% appear in Matches.
3. SHORTLIST: pick the few you'll really apply to (⭐ Shortlist). Only these get an application kit.
4. PREPARE: generate the kit -- tailored resume bullets, cover letter (Word/PDF/text), gap plan, outreach messages -- and find recruiters on LinkedIn.
5. APPLY: apply on the company site, then mark the job Applied. A follow-up date is set for you.
6. TRACK: Applications shows every active application with its next action; dates go to Google Calendar with one tap.
""".trim()
    ),
    HelpTopic(
        "📅", "A good daily routine (10 minutes)",
        """
• Open the 07:00 notification (or Discover → Fetch now).
• Discover ②: copy the next scoring batch to Claude, paste the reply back. Repeat until nothing is waiting.
• Discover ③: run one Claude web search (Google gets its own search once a day).
• Matches: ⭐ Shortlist the best 1-2, 🚫 the rest you won't pursue.
• Applications: do each card's "Next:" step.
""".trim()
    ),
    HelpTopic(
        "⭐", "Why shortlist-first?",
        """
Applying well beats applying widely. Instead of firing off many applications, you shortlist the few roles worth your effort, prepare each one properly (tailored resume, honest cover letter, a recruiter contact), and track it to an outcome.

Your shortlist limit is ${s.shortlistLimit} active applications (Shortlisted + Applied + Interviewing + Offer). Going past it is allowed, but the app warns you. Finish or drop some first. Change it in Setup → Settings.
""".trim()
    ),
    HelpTopic(
        "🏷️", "What each status means",
        JobStatus.entries.joinToString("\n\n") { "${it.emoji} ${it.label}\n${it.meaning}" } +
            "\n\nFilter by status on Matches (the status chips) and in Applications. Every change is kept in the job's history."
    ),
    HelpTopic(
        "🗓️", "Dates & Google Calendar",
        """
Each application can have dated steps:
${EventType.entries.joinToString("\n") { "• ${it.emoji} ${it.label}" }}

• Marking a job Applied adds a follow-up ${s.followUpDays} days later automatically.
• Add interview rounds, an apply-by date or an offer deadline with ➕ Add a date (date + time pickers).
• Tap 📅 Add to put the date in your Google Calendar (the first time, allow calendar access). It appears on every device signed in to that Google account. Interviews get a 1-hour reminder.
• Editing a date marks it "calendar out of date". Tap 📅 Update to sync the change. Deleting the date removes the calendar event too.
""".trim()
    ),
    HelpTopic(
        "🧰", "The application kit",
        """
Available once a job is Shortlisted. One Claude round-trip writes all of it from your resume and the posting. Nothing is invented.

🎯 Match summary: ${KitHelp.SUMMARY}

📝 Resume bullets: ${KitHelp.BULLETS}

✉️ Cover letter: ${KitHelp.COVER}

🧭 Gap plan: ${KitHelp.GAPS}

🤝 Outreach: ${KitHelp.OUTREACH}

Timelines for gaps are kept realistic: a new tool 1-3 weeks, a new language or cloud 1-3 months, a new domain 3-6 months. Degrees or years of experience are never "closed" quickly; the letter says so honestly and leans on equivalent experience instead.
""".trim()
    ),
    HelpTopic(
        "⬇️", "Saving & sharing the cover letter",
        """
• ⬇️ .docx / .pdf / .txt: choose where to save it (e.g. Downloads), then upload it on the application form. The file is a proper letter with your name, contact line, date, company and a "Re: Application for …" line.
• ✏️ Edit: change anything first. Your edits are kept (regenerating the kit replaces them).
• 📤 Share cover letter + resume: attaches both to email, LinkedIn, WhatsApp, Drive…
• To attach your resume automatically, import your resume PDF once in Setup → Profile → Import PDF.
""".trim()
    ),
    HelpTopic(
        "🔍", "Finding recruiters & sending messages",
        """
In a shortlisted job's Outreach section:
1. Find recruiters & referrers on LinkedIn: copy the prompt to Claude (with web search on), paste the reply. You get recruiters, the likely hiring manager and people in similar roles, with real profile links only.
2. For each person: 🔗 open their profile → send a connection request with "Copy note" (fits LinkedIn's 300-character limit).
3. When they accept, send the formal message ("Copy message"), or "📤 Message + docs" to attach your cover letter and resume. Messages are formal, include the job link, and are signed with your details. {Name} is filled with their first name.
4. ✅ Mark contacted so you don't message anyone twice.
Found someone yourself? ➕ Add someone you found.
""".trim()
    ),
    HelpTopic(
        "🤖", "Using Claude (copy-paste, no API key)",
        """
Every Claude step is the same two buttons:
1. Copy prompt: the prompt is copied and the Claude app opens with it.
2. Send it in Claude, wait for the full answer, copy the WHOLE reply (the copy icon under the message).
3. Paste Claude reply back in the app.

Turn on web search in Claude for: company searches, reading job links, finding recruiters.
If the app says "this looks like the prompt you copied", you pasted before copying Claude's answer.
""".trim()
    ),
    HelpTopic(
        "🔤", "Search terms",
        """
Setup → Settings → Search terms (e.g. Gen AI Engineer, AI Architect, Software Engineer 3). They're searched on every careers site and by Claude, and count as target titles when pre-scoring. "Software Engineer 3" also matches "Software Development Engineer III"; "Gen AI" matches "Generative AI". Broad terms bring more jobs into the scoring queue.
""".trim()
    ),
    HelpTopic(
        "🔗", "Adding a job you found (✋ Manual)",
        """
Share → Job Matcher from LinkedIn/Chrome, or paste the link in Discover → ➕ Add a job from a link. The app reads the posting (careers-site APIs, LinkedIn's public page, or the page's job data) and opens it.
If the page needs a login (e.g. Naukri): "Let Claude read this posting", or ✍️ Paste description.
Every job is tagged 🤖 Auto (found by the app) or ✋ Manual (added by you). Use the Matches filter to see only yours.
""".trim()
    ),
    HelpTopic(
        "📊", "Scores",
        """
• Local %: a quick on-device keyword check that decides what's worth sending to Claude (pre-filter ${s.prefilterThreshold}%).
• Claude %: the real match score (skills 40, seniority 25, hard requirements 20, career fit 15). Green ≥${s.matchThreshold}, amber within 15, red below.
Change both thresholds in Setup → Settings.
""".trim()
    ),
    HelpTopic(
        "🏢", "Companies & priority",
        """
Setup → Companies lists your target companies and how each is searched (Workday, Eightfold, Oracle, SmartRecruiters, Amazon, or Claude web search). ⭐ Top-choice companies (Google) are shown first in Matches and get their own Claude search daily. Add, edit, disable or Test fetch any company.
""".trim()
    ),
    HelpTopic(
        "🛠️", "Troubleshooting",
        """
• Paste does nothing useful → copy Claude's complete reply, then paste again.
• "📅 Add" fails → allow calendar access, and make sure a Google account is on the phone.
• Share has no resume → import your resume PDF in Setup → Profile.
• A company shows ⚠️ → Setup → Companies → Edit, check the identifier, then Test fetch.
• Too many jobs waiting for Claude → raise the pre-filter or remove a broad search term.
""".trim()
    ),
    HelpTopic(
        "🔒", "Your data",
        """
Everything stays on this phone: jobs, notes, kits, contacts and settings. The app only talks to companies' public careers sites. Claude only sees what you paste into it yourself, and calendar events go to your own Google calendar.
""".trim()
    )
)

@Composable
fun HelpScreen(settings: AppSettings) {
    var open by rememberSaveable { mutableStateOf("How Job Matcher works") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Help", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Tap a topic to expand it.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(helpTopics(settings), key = { it.title }) { topic ->
            val expanded = open == topic.title
            Card(Modifier.fillMaxWidth().clickable { open = if (expanded) "" else topic.title }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${topic.emoji}  ${topic.title}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (expanded) "▲" else "▼", fontSize = 12.sp)
                    }
                    if (expanded) Text(topic.body, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}
