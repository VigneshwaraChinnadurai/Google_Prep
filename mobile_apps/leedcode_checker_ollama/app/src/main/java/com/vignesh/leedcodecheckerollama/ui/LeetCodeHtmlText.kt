package com.vignesh.leedcodecheckerollama.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders LeetCode HTML content with proper formatting that matches LeetCode's website style.
 * Supports:
 * - Paragraphs (<p>)
 * - Bold text (<strong>, <b>)
 * - Italics (<em>, <i>)
 * - Code blocks (<pre>, <code>)
 * - Lists (<ul>, <ol>, <li>)
 * - Superscript/Subscript (<sup>, <sub>)
 * - Line breaks (<br>)
 * - Constraints sections
 * - Example formatting
 */
@Composable
fun LeetCodeHtmlText(
    htmlContent: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    codeBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
) {
    val parsedBlocks = remember(htmlContent) { parseHtmlContent(htmlContent) }

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            parsedBlocks.forEach { block ->
                when (block) {
                    is HtmlBlock.Paragraph -> {
                        Text(
                            text = block.annotatedString,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                    is HtmlBlock.CodeBlock -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(codeBackgroundColor, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = textColor,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    is HtmlBlock.UnorderedList -> {
                        Column(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            block.items.forEach { item ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "•  ",
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = item,
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }
                    is HtmlBlock.OrderedList -> {
                        Column(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            block.items.forEachIndexed { index, item ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "${index + 1}.  ",
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = item,
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }
                    is HtmlBlock.ExampleBlock -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Example ${block.number}:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            block.input?.let { input ->
                                Row {
                                    Text(
                                        text = "Input: ",
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = input,
                                        fontFamily = FontFamily.Monospace,
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            block.output?.let { output ->
                                Row {
                                    Text(
                                        text = "Output: ",
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = output,
                                        fontFamily = FontFamily.Monospace,
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            block.explanation?.let { explanation ->
                                Text(
                                    text = "Explanation: $explanation",
                                    color = textColor.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                    is HtmlBlock.ConstraintsBlock -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Constraints:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            block.constraints.forEach { constraint ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "•  ",
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = constraint,
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    is HtmlBlock.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = textColor.copy(alpha = 0.2f)
                        )
                    }
                    is HtmlBlock.Spacer -> {
                        Spacer(modifier = Modifier.height(block.height.dp))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Data classes for parsed HTML blocks
// ══════════════════════════════════════════════════════════════════════════════

private sealed class HtmlBlock {
    data class Paragraph(val annotatedString: AnnotatedString) : HtmlBlock()
    data class CodeBlock(val code: String) : HtmlBlock()
    data class UnorderedList(val items: List<AnnotatedString>) : HtmlBlock()
    data class OrderedList(val items: List<AnnotatedString>) : HtmlBlock()
    data class ExampleBlock(
        val number: Int,
        val input: String?,
        val output: String?,
        val explanation: String?
    ) : HtmlBlock()
    data class ConstraintsBlock(val constraints: List<AnnotatedString>) : HtmlBlock()
    data object Divider : HtmlBlock()
    data class Spacer(val height: Int) : HtmlBlock()
}

// ══════════════════════════════════════════════════════════════════════════════
// HTML Parsing Logic
// ══════════════════════════════════════════════════════════════════════════════

private fun parseHtmlContent(html: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    var content = html
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("\r\n", "\n")
        .replace("\r", "\n")

    // Track example number
    var exampleNumber = 0

    // Split by major block elements
    val blockPattern = Regex("""<(p|pre|ul|ol|div|br|hr)[^>]*>""", RegexOption.IGNORE_CASE)
    var lastIndex = 0
    
    // Process line by line for better control
    val lines = content.split(Regex("</?p>|<br\\s*/?>", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        
        // Skip empty lines
        if (line.isBlank() || line == "&nbsp;") {
            i++
            continue
        }

        // Check for code blocks
        if (line.contains("<pre>", ignoreCase = true)) {
            val codeContent = extractCodeBlock(line)
            if (codeContent.isNotBlank()) {
                blocks.add(HtmlBlock.CodeBlock(codeContent))
            }
            i++
            continue
        }

        // Check for unordered list
        if (line.contains("<ul>", ignoreCase = true)) {
            val listContent = extractBetweenTags(line, "ul")
            val items = extractListItems(listContent)
            if (items.isNotEmpty()) {
                blocks.add(HtmlBlock.UnorderedList(items.map { parseInlineHtml(it) }))
            }
            i++
            continue
        }

        // Check for ordered list
        if (line.contains("<ol>", ignoreCase = true)) {
            val listContent = extractBetweenTags(line, "ol")
            val items = extractListItems(listContent)
            if (items.isNotEmpty()) {
                blocks.add(HtmlBlock.OrderedList(items.map { parseInlineHtml(it) }))
            }
            i++
            continue
        }

        // Check for Example pattern
        if (line.contains("Example", ignoreCase = true) && 
            (line.contains("Input", ignoreCase = true) || line.contains(":"))) {
            exampleNumber++
            val example = parseExampleBlock(line, lines, i)
            if (example != null) {
                blocks.add(HtmlBlock.ExampleBlock(
                    number = exampleNumber,
                    input = example.input,
                    output = example.output,
                    explanation = example.explanation
                ))
                i = example.nextIndex
                continue
            }
        }

        // Check for Constraints
        if (line.contains("Constraints", ignoreCase = true) || 
            line.contains("Constraint:", ignoreCase = true)) {
            val constraints = parseConstraints(lines, i)
            if (constraints.isNotEmpty()) {
                blocks.add(HtmlBlock.ConstraintsBlock(constraints.map { parseInlineHtml(it) }))
                // Skip constraint lines
                i += constraints.size + 1
                continue
            }
        }

        // Check for horizontal rule
        if (line.contains("<hr", ignoreCase = true)) {
            blocks.add(HtmlBlock.Divider)
            i++
            continue
        }

        // Regular paragraph
        val cleanLine = stripHtmlTags(line).trim()
        if (cleanLine.isNotBlank()) {
            blocks.add(HtmlBlock.Paragraph(parseInlineHtml(line)))
        }
        i++
    }

    return blocks
}

private fun extractCodeBlock(html: String): String {
    val prePattern = Regex("""<pre[^>]*>(.*?)</pre>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val match = prePattern.find(html)
    return if (match != null) {
        stripHtmlTags(match.groupValues[1])
            .replace("\\n", "\n")
            .trim()
    } else {
        // Try to extract from code tag
        val codePattern = Regex("""<code[^>]*>(.*?)</code>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val codeMatch = codePattern.find(html)
        if (codeMatch != null) {
            stripHtmlTags(codeMatch.groupValues[1]).trim()
        } else {
            ""
        }
    }
}

private fun extractBetweenTags(html: String, tag: String): String {
    val pattern = Regex("""<$tag[^>]*>(.*?)</$tag>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val match = pattern.find(html)
    return match?.groupValues?.getOrNull(1) ?: ""
}

private fun extractListItems(listHtml: String): List<String> {
    val pattern = Regex("""<li[^>]*>(.*?)</li>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    return pattern.findAll(listHtml).map { it.groupValues[1].trim() }.toList()
}

private data class ExampleParseResult(
    val number: Int,
    val input: String?,
    val output: String?,
    val explanation: String?,
    val nextIndex: Int
)

private fun parseExampleBlock(firstLine: String, lines: List<String>, startIndex: Int): ExampleParseResult? {
    var input: String? = null
    var output: String? = null
    var explanation: String? = null
    var currentIndex = startIndex

    // Parse the example content
    val combinedContent = buildString {
        for (j in startIndex until minOf(startIndex + 5, lines.size)) {
            append(lines[j]).append(" ")
        }
    }

    // Extract input
    val inputPattern = Regex("""[Ii]nput[:\s]*(.+?)(?=[Oo]utput|[Ee]xplanation|$)""", RegexOption.DOT_MATCHES_ALL)
    inputPattern.find(combinedContent)?.let {
        input = stripHtmlTags(it.groupValues[1]).trim()
    }

    // Extract output
    val outputPattern = Regex("""[Oo]utput[:\s]*(.+?)(?=[Ee]xplanation|$)""", RegexOption.DOT_MATCHES_ALL)
    outputPattern.find(combinedContent)?.let {
        output = stripHtmlTags(it.groupValues[1]).trim()
    }

    // Extract explanation
    val explanationPattern = Regex("""[Ee]xplanation[:\s]*(.+)""", RegexOption.DOT_MATCHES_ALL)
    explanationPattern.find(combinedContent)?.let {
        explanation = stripHtmlTags(it.groupValues[1]).trim()
    }

    // Calculate how many lines were consumed
    var linesConsumed = 1
    for (j in startIndex + 1 until minOf(startIndex + 5, lines.size)) {
        val line = stripHtmlTags(lines[j]).trim().lowercase()
        if (line.contains("input") || line.contains("output") || line.contains("explanation")) {
            linesConsumed++
        } else {
            break
        }
    }

    return ExampleParseResult(
        number = 0, // Will be set by caller
        input = input,
        output = output,
        explanation = explanation,
        nextIndex = startIndex + linesConsumed
    )
}

private fun parseConstraints(lines: List<String>, startIndex: Int): List<String> {
    val constraints = mutableListOf<String>()
    
    // Look for constraint items (usually in a list or following lines)
    for (j in startIndex + 1 until minOf(startIndex + 15, lines.size)) {
        val line = lines[j].trim()
        if (line.isBlank()) continue
        
        // Stop if we hit another section
        val cleanLine = stripHtmlTags(line).trim()
        if (cleanLine.startsWith("Example") || 
            cleanLine.startsWith("Note") ||
            cleanLine.startsWith("Follow")) {
            break
        }

        // Extract list items or constraint lines
        if (line.contains("<li>", ignoreCase = true)) {
            val items = extractListItems(line)
            constraints.addAll(items.map { stripHtmlTags(it).trim() })
        } else if (cleanLine.contains("<=") || cleanLine.contains("≤") ||
                   cleanLine.contains("10^") || cleanLine.matches(Regex(".*\\d+.*"))) {
            constraints.add(cleanLine)
        }
    }

    return constraints
}

private fun parseInlineHtml(html: String): AnnotatedString {
    return buildAnnotatedString {
        val cleanHtml = html
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace("&nbsp;", " ")

        var text = cleanHtml
        var currentIndex = 0

        // Process inline formatting
        while (currentIndex < text.length) {
            // Find the next tag
            val tagStart = text.indexOf('<', currentIndex)
            
            if (tagStart == -1 || tagStart >= text.length) {
                // No more tags, append rest as plain text
                append(decodeHtmlEntities(text.substring(currentIndex)))
                break
            }

            // Append text before the tag
            if (tagStart > currentIndex) {
                append(decodeHtmlEntities(text.substring(currentIndex, tagStart)))
            }

            val tagEnd = text.indexOf('>', tagStart)
            if (tagEnd == -1) {
                // Malformed tag, append rest as text
                append(decodeHtmlEntities(text.substring(currentIndex)))
                break
            }

            val tag = text.substring(tagStart + 1, tagEnd).lowercase()
            currentIndex = tagEnd + 1

            when {
                // Bold tags
                tag == "strong" || tag == "b" -> {
                    val closeTag = findClosingTag(text, currentIndex, if (tag == "strong") "strong" else "b")
                    if (closeTag != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(decodeHtmlEntities(stripHtmlTags(text.substring(currentIndex, closeTag))))
                        }
                        currentIndex = closeTag + (if (tag == "strong") 9 else 4) // </strong> or </b>
                    }
                }
                // Italic tags
                tag == "em" || tag == "i" -> {
                    val closeTag = findClosingTag(text, currentIndex, if (tag == "em") "em" else "i")
                    if (closeTag != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(decodeHtmlEntities(stripHtmlTags(text.substring(currentIndex, closeTag))))
                        }
                        currentIndex = closeTag + (if (tag == "em") 5 else 4) // </em> or </i>
                    }
                }
                // Code tag
                tag == "code" -> {
                    val closeTag = findClosingTag(text, currentIndex, "code")
                    if (closeTag != -1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x20888888)
                        )) {
                            append(decodeHtmlEntities(text.substring(currentIndex, closeTag)))
                        }
                        currentIndex = closeTag + 7 // </code>
                    }
                }
                // Superscript
                tag == "sup" -> {
                    val closeTag = findClosingTag(text, currentIndex, "sup")
                    if (closeTag != -1) {
                        withStyle(SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = 10.sp
                        )) {
                            append(decodeHtmlEntities(stripHtmlTags(text.substring(currentIndex, closeTag))))
                        }
                        currentIndex = closeTag + 6 // </sup>
                    }
                }
                // Subscript
                tag == "sub" -> {
                    val closeTag = findClosingTag(text, currentIndex, "sub")
                    if (closeTag != -1) {
                        withStyle(SpanStyle(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = 10.sp
                        )) {
                            append(decodeHtmlEntities(stripHtmlTags(text.substring(currentIndex, closeTag))))
                        }
                        currentIndex = closeTag + 6 // </sub>
                    }
                }
                // Skip closing tags and other tags
                tag.startsWith("/") -> {
                    // Already handled
                }
                else -> {
                    // Unknown tag, try to skip it
                }
            }
        }
    }
}

private fun findClosingTag(text: String, startIndex: Int, tagName: String): Int {
    val closeTagPattern = Regex("""</$tagName>""", RegexOption.IGNORE_CASE)
    val match = closeTagPattern.find(text, startIndex)
    return match?.range?.first ?: -1
}

private fun stripHtmlTags(html: String): String {
    return html.replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
}

private fun decodeHtmlEntities(text: String): String {
    return text
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&times;", "×")
        .replace("&le;", "≤")
        .replace("&ge;", "≥")
        .replace("&ne;", "≠")
}
