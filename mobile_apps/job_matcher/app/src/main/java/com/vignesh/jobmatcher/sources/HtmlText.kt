package com.vignesh.jobmatcher.sources

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

/**
 * Converts ATS job-description HTML to readable plain text, keeping paragraph breaks
 * and turning <li> into bullets -- Jsoup's own .text() flattens everything onto one line,
 * which makes the description unreadable in the detail screen and in Claude prompts.
 */
object HtmlText {
    private val BLOCK_TAGS = setOf(
        "p", "div", "br", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6",
        "section", "article", "table", "tr", "blockquote"
    )

    fun toText(html: String?, alreadyEscaped: Boolean = false): String {
        if (html.isNullOrBlank()) return ""
        // Greenhouse double-encodes its content field (&lt;p&gt;...), so unescape first.
        val source = if (alreadyEscaped) Parser.unescapeEntities(html, false) else html
        val body = Jsoup.parse(source).body()
        val out = StringBuilder()
        walk(body, out)
        return out.toString()
            .replace(' ', ' ')
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun walk(node: Node, out: StringBuilder) {
        when (node) {
            is TextNode -> out.append(node.text())
            is Element -> {
                val tag = node.normalName()
                val isBlock = tag in BLOCK_TAGS
                if (isBlock && out.isNotEmpty() && out.last() != '\n') out.append('\n')
                if (tag == "li") out.append("• ")
                node.childNodes().forEach { walk(it, out) }
                if (isBlock && tag != "br" && out.isNotEmpty() && out.last() != '\n') out.append('\n')
            }
        }
    }
}
