package com.vignesh.leetcodechecker.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * GitHub-flavored Markdown renderer for profile README files
 * Supports: Headers, Bold, Italic, Links, Lists, Images, Code blocks, Emojis, Badges
 */
@Composable
fun GitHubMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFFE6EDF3),
    linkColor: Color = Color(0xFF58A6FF)
) {
    val context = LocalContext.current
    
    // Parse markdown into blocks
    val blocks = remember(markdown) { parseGitHubMarkdownBlocks(markdown) }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is GitHubMarkdownBlock.Header -> {
                    val (fontSize, fontWeight) = when (block.level) {
                        1 -> 24.sp to FontWeight.Bold
                        2 -> 20.sp to FontWeight.Bold
                        3 -> 18.sp to FontWeight.SemiBold
                        4 -> 16.sp to FontWeight.SemiBold
                        else -> 14.sp to FontWeight.Medium
                    }
                    RenderGitHubInlineMarkdown(
                        text = block.content,
                        baseColor = baseColor,
                        linkColor = linkColor,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        onLinkClick = { url ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) { }
                        }
                    )
                }
                
                is GitHubMarkdownBlock.Paragraph -> {
                    RenderGitHubInlineMarkdown(
                        text = block.content,
                        baseColor = baseColor,
                        linkColor = linkColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        onLinkClick = { url ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) { }
                        }
                    )
                }
                
                is GitHubMarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = baseColor,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                        )
                        RenderGitHubInlineMarkdown(
                            text = block.content,
                            baseColor = baseColor,
                            linkColor = linkColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            onLinkClick = { url ->
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) { }
                            }
                        )
                    }
                }
                
                is GitHubMarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF161B22))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = block.content,
                            color = Color(0xFFE6EDF3),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                is GitHubMarkdownBlock.Image -> {
                    // Render image using Coil (badges, profile images, etc.)
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(block.url)
                            .crossfade(true)
                            .build(),
                        contentDescription = block.altText,
                        modifier = Modifier
                            .wrapContentSize()
                            .clip(RoundedCornerShape(4.dp))
                            .let { mod ->
                                if (block.linkUrl != null) {
                                    mod.clickable {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(block.linkUrl)))
                                        } catch (_: Exception) { }
                                    }
                                } else mod
                            },
                        contentScale = ContentScale.Fit
                    )
                }
                
                is GitHubMarkdownBlock.ImageRow -> {
                    // Render multiple images in a row (for badges)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        block.images.forEach { img ->
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(img.url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = img.altText,
                                modifier = Modifier
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .let { mod ->
                                        if (img.linkUrl != null) {
                                            mod.clickable {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(img.linkUrl)))
                                                } catch (_: Exception) { }
                                            }
                                        } else mod
                                    },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                
                is GitHubMarkdownBlock.HorizontalRule -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF30363D))
                    )
                }
                
                is GitHubMarkdownBlock.Empty -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RenderGitHubInlineMarkdown(
    text: String,
    baseColor: Color,
    linkColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    onLinkClick: (String) -> Unit
) {
    val (annotatedString, linkPositions) = remember(text) {
        buildGitHubAnnotatedString(text, baseColor, linkColor, fontSize, fontWeight)
    }
    
    if (linkPositions.isEmpty()) {
        Text(
            text = annotatedString,
            fontSize = fontSize
        )
    } else {
        ClickableText(
            text = annotatedString,
            onClick = { offset ->
                linkPositions.forEach { (range, url) ->
                    if (offset in range.first..range.last) {
                        onLinkClick(url)
                    }
                }
            }
        )
    }
}

private fun buildGitHubAnnotatedString(
    text: String,
    baseColor: Color,
    linkColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight
): Pair<AnnotatedString, List<Pair<IntRange, String>>> {
    val linkPositions = mutableListOf<Pair<IntRange, String>>()
    
    val annotatedString = buildAnnotatedString {
        var currentText = text
        
        // Process inline elements
        while (currentText.isNotEmpty()) {
            // Check for bold **text**
            val boldMatch = Regex("""\*\*(.+?)\*\*""").find(currentText)
            // Check for italic *text* (but not **)
            val italicMatch = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""").find(currentText)
            // Check for inline code `text`
            val codeMatch = Regex("""`([^`]+)`""").find(currentText)
            // Check for link [text](url)
            val linkMatch = Regex("""\[([^\]]+)]\(([^)]+)\)""").find(currentText)
            // Check for emoji :emoji:
            val emojiMatch = Regex(""":([a-z_]+):""").find(currentText)
            
            // Find earliest match
            val matches = listOfNotNull(
                boldMatch?.range?.first?.let { "bold" to it },
                italicMatch?.range?.first?.let { "italic" to it },
                codeMatch?.range?.first?.let { "code" to it },
                linkMatch?.range?.first?.let { "link" to it },
                emojiMatch?.range?.first?.let { "emoji" to it }
            ).sortedBy { it.second }
            
            if (matches.isEmpty()) {
                // No more special elements, append rest as plain text
                withStyle(SpanStyle(color = baseColor, fontSize = fontSize, fontWeight = fontWeight)) {
                    append(currentText)
                }
                break
            }
            
            val (matchType, matchStart) = matches.first()
            
            // Append text before match
            if (matchStart > 0) {
                withStyle(SpanStyle(color = baseColor, fontSize = fontSize, fontWeight = fontWeight)) {
                    append(currentText.substring(0, matchStart))
                }
            }
            
            when (matchType) {
                "bold" -> {
                    val match = boldMatch!!
                    withStyle(SpanStyle(color = baseColor, fontSize = fontSize, fontWeight = FontWeight.Bold)) {
                        append(match.groupValues[1])
                    }
                    currentText = currentText.substring(match.range.last + 1)
                }
                "italic" -> {
                    val match = italicMatch!!
                    withStyle(SpanStyle(color = baseColor, fontSize = fontSize, fontStyle = FontStyle.Italic)) {
                        append(match.groupValues[1])
                    }
                    currentText = currentText.substring(match.range.last + 1)
                }
                "code" -> {
                    val match = codeMatch!!
                    withStyle(SpanStyle(
                        color = Color(0xFFE6EDF3),
                        fontSize = fontSize * 0.9f,
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF161B22)
                    )) {
                        append(match.groupValues[1])
                    }
                    currentText = currentText.substring(match.range.last + 1)
                }
                "link" -> {
                    val match = linkMatch!!
                    val linkText = match.groupValues[1]
                    val linkUrl = match.groupValues[2]
                    val startIdx = length
                    withStyle(SpanStyle(
                        color = linkColor,
                        fontSize = fontSize,
                        textDecoration = TextDecoration.Underline
                    )) {
                        append(linkText)
                    }
                    linkPositions.add(IntRange(startIdx, length - 1) to linkUrl)
                    currentText = currentText.substring(match.range.last + 1)
                }
                "emoji" -> {
                    val match = emojiMatch!!
                    val emojiName = match.groupValues[1]
                    val emoji = GITHUB_EMOJI_MAP[emojiName] ?: ":$emojiName:"
                    withStyle(SpanStyle(color = baseColor, fontSize = fontSize)) {
                        append(emoji)
                    }
                    currentText = currentText.substring(match.range.last + 1)
                }
            }
        }
    }
    
    return annotatedString to linkPositions
}

private fun parseGitHubMarkdownBlocks(markdown: String): List<GitHubMarkdownBlock> {
    val blocks = mutableListOf<GitHubMarkdownBlock>()
    val lines = markdown.lines()
    var i = 0
    var inCodeBlock = false
    val codeBlockContent = StringBuilder()
    
    while (i < lines.size) {
        val line = lines[i]
        
        // Code block handling
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(GitHubMarkdownBlock.CodeBlock(codeBlockContent.toString().trimEnd()))
                codeBlockContent.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
            }
            i++
            continue
        }
        
        if (inCodeBlock) {
            if (codeBlockContent.isNotEmpty()) codeBlockContent.append("\n")
            codeBlockContent.append(line)
            i++
            continue
        }
        
        // Empty line
        if (line.isBlank()) {
            blocks.add(GitHubMarkdownBlock.Empty)
            i++
            continue
        }
        
        // Horizontal rule
        if (line.trim().matches(Regex("""^[-*_]{3,}$"""))) {
            blocks.add(GitHubMarkdownBlock.HorizontalRule)
            i++
            continue
        }
        
        // Header
        val headerMatch = Regex("""^(#{1,6})\s+(.+)$""").find(line)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val content = headerMatch.groupValues[2]
            blocks.add(GitHubMarkdownBlock.Header(level, content))
            i++
            continue
        }
        
        // Check for row of badge images (multiple linked images on one line)
        val linkedImagePattern = Regex("""\[!\[([^\]]*)]?\(([^)]+)\)]\(([^)]+)\)""")
        val linkedImageMatches = linkedImagePattern.findAll(line.trim()).toList()
        if (linkedImageMatches.size > 1) {
            // Multiple badge images in a row
            val images = linkedImageMatches.map { match ->
                GitHubMarkdownBlock.ImageInfo(
                    url = match.groupValues[2],
                    altText = match.groupValues[1],
                    linkUrl = match.groupValues[3]
                )
            }
            blocks.add(GitHubMarkdownBlock.ImageRow(images))
            i++
            continue
        }
        
        // Single image with link: [![alt](img_url)](link_url)
        val linkedImageMatch = linkedImagePattern.find(line.trim())
        if (linkedImageMatch != null && linkedImageMatch.range.first == 0) {
            blocks.add(GitHubMarkdownBlock.Image(
                url = linkedImageMatch.groupValues[2],
                altText = linkedImageMatch.groupValues[1],
                linkUrl = linkedImageMatch.groupValues[3]
            ))
            i++
            continue
        }
        
        // Standalone image: ![alt](url)
        val imageMatch = Regex("""^!\[([^\]]*)]?\(([^)]+)\)$""").find(line.trim())
        if (imageMatch != null) {
            blocks.add(GitHubMarkdownBlock.Image(
                url = imageMatch.groupValues[2],
                altText = imageMatch.groupValues[1],
                linkUrl = null
            ))
            i++
            continue
        }
        
        // List item
        if (line.trim().startsWith("- ") || line.trim().startsWith("* ") || 
            line.trim().matches(Regex("""^\d+\.\s+.*"""))) {
            val content = line.trim()
                .replaceFirst(Regex("""^[-*]\s+"""), "")
                .replaceFirst(Regex("""^\d+\.\s+"""), "")
            blocks.add(GitHubMarkdownBlock.ListItem(content))
            i++
            continue
        }
        
        // Regular paragraph
        blocks.add(GitHubMarkdownBlock.Paragraph(line))
        i++
    }
    
    return blocks
}

private sealed class GitHubMarkdownBlock {
    data class Header(val level: Int, val content: String) : GitHubMarkdownBlock()
    data class Paragraph(val content: String) : GitHubMarkdownBlock()
    data class ListItem(val content: String) : GitHubMarkdownBlock()
    data class CodeBlock(val content: String) : GitHubMarkdownBlock()
    data class Image(val url: String, val altText: String, val linkUrl: String?) : GitHubMarkdownBlock()
    data class ImageInfo(val url: String, val altText: String, val linkUrl: String?)
    data class ImageRow(val images: List<ImageInfo>) : GitHubMarkdownBlock()
    object HorizontalRule : GitHubMarkdownBlock()
    object Empty : GitHubMarkdownBlock()
}

// Common GitHub emoji mappings
private val GITHUB_EMOJI_MAP = mapOf(
    "wave" to "👋",
    "smile" to "😊",
    "heart" to "❤️",
    "star" to "⭐",
    "fire" to "🔥",
    "rocket" to "🚀",
    "sparkles" to "✨",
    "bug" to "🐛",
    "warning" to "⚠️",
    "check" to "✓",
    "x" to "✗",
    "coffee" to "☕",
    "computer" to "💻",
    "keyboard" to "⌨️",
    "book" to "📖",
    "books" to "📚",
    "bulb" to "💡",
    "memo" to "📝",
    "pencil" to "✏️",
    "pencil2" to "✏️",
    "wrench" to "🔧",
    "hammer" to "🔨",
    "gear" to "⚙️",
    "link" to "🔗",
    "email" to "📧",
    "earth_americas" to "🌎",
    "earth_asia" to "🌏",
    "earth_africa" to "🌍",
    "globe_with_meridians" to "🌐",
    "chart_with_upwards_trend" to "📈",
    "trophy" to "🏆",
    "medal_sports" to "🏅",
    "1st_place_medal" to "🥇",
    "octocat" to "🐙",
    "point_right" to "👉",
    "point_left" to "👈",
    "point_up" to "👆",
    "point_down" to "👇",
    "thumbsup" to "👍",
    "thumbsdown" to "👎",
    "clap" to "👏",
    "raised_hands" to "🙌",
    "pray" to "🙏",
    "muscle" to "💪",
    "eyes" to "👀",
    "brain" to "🧠",
    "zap" to "⚡",
    "boom" to "💥",
    "tada" to "🎉",
    "package" to "📦",
    "dart" to "🎯",
    "100" to "💯",
    "handshake" to "🤝",
    "seedling" to "🌱",
    "high_voltage" to "⚡",
    "speech_balloon" to "💬",
    "thought_balloon" to "💭",
    "love_letter" to "💌",
    "mag" to "🔍",
    "mag_right" to "🔎",
    "open_book" to "📖",
    "closed_book" to "📕",
    "green_book" to "📗",
    "blue_book" to "📘",
    "orange_book" to "📙",
    "notebook" to "📓",
    "ledger" to "📒",
    "page_facing_up" to "📄",
    "newspaper" to "📰",
    "bar_chart" to "📊",
    "chart_with_downwards_trend" to "📉",
    "clipboard" to "📋",
    "pushpin" to "📌",
    "round_pushpin" to "📍",
    "paperclip" to "📎",
    "straight_ruler" to "📏",
    "triangular_ruler" to "📐",
    "scissors" to "✂️",
    "file_folder" to "📁",
    "open_file_folder" to "📂",
    "card_index" to "📇",
    "date" to "📅",
    "calendar" to "📆",
    "spiral_notepad" to "🗒️",
    "spiral_calendar" to "🗓️",
    "card_index_dividers" to "🗂️",
    "e-mail" to "📧",
    "incoming_envelope" to "📨",
    "envelope_with_arrow" to "📩",
    "outbox_tray" to "📤",
    "inbox_tray" to "📥",
    "mailbox" to "📫",
    "mailbox_closed" to "📪",
    "mailbox_with_mail" to "📬",
    "mailbox_with_no_mail" to "📭",
    "postbox" to "📮",
    "ballot_box" to "🗳️",
    "lock" to "🔒",
    "unlock" to "🔓",
    "lock_with_ink_pen" to "🔏",
    "closed_lock_with_key" to "🔐",
    "key" to "🔑",
    "old_key" to "🗝️"
)
