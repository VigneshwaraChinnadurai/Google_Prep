package com.vignesh.leetcodechecker.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.email.ImapClient
import com.vignesh.leetcodechecker.email.MailSummary
import kotlinx.coroutines.launch

/**
 * A lightweight Gmail-like inbox view (sender/subject/date list -> tap to read) so email
 * can be read aloud the same way books are, reusing the same Gmail App Password already
 * configured for push notifications (Global Settings -> Email Notifications). Reads only
 * -- there's no compose/reply/delete here, this is a reading surface for the Book Reader's
 * voice-over pipeline, not a Gmail client.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailInboxScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var summaries by remember { mutableStateOf<List<MailSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMail by remember { mutableStateOf<MailSummary?>(null) }

    fun refresh() {
        val settings = AppSettingsStore.load(context)
        if (settings.notificationEmailFrom.isBlank() || settings.notificationEmailAppPassword.isBlank()) {
            error = "Configure your Gmail address and App Password in Global Settings (Email Notifications section) first."
            return
        }
        isLoading = true
        error = null
        scope.launch {
            ImapClient.fetchInboxSummaries(settings.notificationEmailFrom, settings.notificationEmailAppPassword).fold(
                onSuccess = { summaries = it },
                onFailure = { e -> error = e.message ?: "Couldn't load inbox." }
            )
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val selected = selectedMail
    if (selected != null) {
        MailReaderScreen(
            summary = selected,
            onBackClick = { selectedMail = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📧 Mail") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }, enabled = !isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!isLoading && error == null && summaries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No messages.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(summaries, key = { it.uid }) { mail ->
                        MailRow(mail, onClick = { selectedMail = mail })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MailRow(mail: MailSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mail.isUnread) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else {
            Spacer(modifier = Modifier.width(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mail.from,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (mail.isUnread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Text(
                mail.subject,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (mail.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            mail.dateText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
