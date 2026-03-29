package com.vignesh.leetcodechecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Simple Markdown renderer for chat messages.
 * Supports: headers (###), bold (**), italic (*), inline code (`),
 * code blocks (```), bullet points (- / *), numbered lists.
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val lines = text.lines()
    var inCodeBlock = false
    val codeBlockLines = mutableListOf<String>()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (line in lines) {
            val trimmed = line.trim()

            // ── Code block toggle ───────────────────────────
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // End code block: render accumulated code
                    CodeBlockText(codeBlockLines.joinToString("\n"))
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(line)
                continue
            }

            // ── Headers ─────────────────────────────────────
            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("### ")),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## ")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# ")),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                // ── Bullet points ───────────────────────────
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = "•  ",
                            color = color,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = parseInlineMarkdown(trimmed.substring(2)),
                            color = color,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Numbered list ───────────────────────────
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val num = trimmed.substringBefore(".")
                    val content = trimmed.substringAfter(". ")
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = "$num.  ",
                            color = color,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = parseInlineMarkdown(content),
                            color = color,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Horizontal rule ─────────────────────────
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(1.dp)
                            .background(color.copy(alpha = 0.3f))
                    )
                }

                // ── Empty line = spacing ────────────────────
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ── Normal paragraph ────────────────────────
                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmed),
                        color = color,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Close any unclosed code block
        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            CodeBlockText(codeBlockLines.joinToString("\n"))
        }
    }
}

@Composable
private fun CodeBlockText(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}

/**
 * Parse inline markdown: **bold**, *italic*, `code`
 */
fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            when {
                // Bold: **text**
                i + 1 < len && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Italic: *text* (but not **)
                text[i] == '*' && (i + 1 >= len || text[i + 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Inline code: `text`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.5).sp
                            )
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Normal character
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
