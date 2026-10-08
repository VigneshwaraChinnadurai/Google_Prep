package com.vignesh.jobmatcher.claude

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Clipboard + share-intent bridge to the Claude app (same flow as leetcode_checker's
 * "Claude (Manual)" provider): copy the prompt, open Claude with it pre-filled, and later
 * read Claude's reply back off the clipboard.
 */
object ClaudeHandoff {
    private const val CLAUDE_PACKAGE = "com.anthropic.claude"

    fun sendToClaude(context: Context, prompt: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, prompt))

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, prompt)
        }
        val direct = Intent(send).setPackage(CLAUDE_PACKAGE)
        val intent = if (direct.resolveActivity(context.packageManager) != null) direct
        else Intent.createChooser(send, "Open in Claude")
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

        Toast.makeText(
            context,
            "Prompt copied (${prompt.length / 1000}k chars). Send it in Claude, copy the full reply, then tap Paste.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun readClipboard(context: Context): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
            .orEmpty()
    }

    fun copy(context: Context, text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
}
