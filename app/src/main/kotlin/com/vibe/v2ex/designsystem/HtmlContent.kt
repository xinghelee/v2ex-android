package com.vibe.v2ex.designsystem

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI

/** Parsed render tree for V2EX `content_rendered` HTML — mirrors the iOS HTMLText block model. */
sealed interface ContentBlock {
    data class Paragraph(val text: AnnotatedString) : ContentBlock
    data class Code(val code: String) : ContentBlock
    data class Quote(val text: AnnotatedString) : ContentBlock
    data class ListBlock(val items: List<AnnotatedString>) : ContentBlock
    data class Image(val url: String) : ContentBlock
    data object Rule : ContentBlock
}

/**
 * Parses V2EX-rendered HTML into [ContentBlock]s. Images are hoisted out of
 * `<p>`/`<blockquote>` (bare or `<a>`-wrapped) into standalone [ContentBlock.Image]
 * blocks, splitting the surrounding text — an image that only surfaces deeper in
 * the tree falls through to the inline pass as a tappable "[图片]" placeholder.
 */
fun parseContentBlocks(html: String): List<ContentBlock> {
    if (html.isBlank()) return emptyList()
    val sink = BlockSink()
    Jsoup.parseBodyFragment(html).body().childNodes().forEach { sink.appendBlockNode(it) }
    sink.flushPending(PARAGRAPH)
    return sink.blocks
}

/** `//host/…` → https, `/path` → v2ex.com-rooted; anything else passes through. */
fun absoluteContentUrl(raw: String?): String? {
    val url = raw?.trim().orEmpty()
    return when {
        url.isEmpty() -> null
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "https://www.v2ex.com$url"
        else -> url
    }
}

private val PARAGRAPH: (AnnotatedString) -> ContentBlock = { ContentBlock.Paragraph(it) }
private val QUOTE: (AnnotatedString) -> ContentBlock = { ContentBlock.Quote(it) }
private val NEWLINE = AnnotatedString("\n")

private class BlockSink {
    val blocks = mutableListOf<ContentBlock>()
    private val pending = mutableListOf<AnnotatedString>()

    fun appendBlockNode(node: Node) {
        when (node) {
            is TextNode -> appendPending(inlineNodes(listOf(node)))
            is Element -> when (node.tagName()) {
                "p" -> {
                    flushPending(PARAGRAPH)
                    appendHoisting(node.childNodes(), PARAGRAPH)
                    flushPending(PARAGRAPH)
                }
                "blockquote" -> {
                    flushPending(PARAGRAPH)
                    appendHoisting(node.childNodes(), QUOTE)
                    flushPending(QUOTE)
                }
                "pre" -> {
                    flushPending(PARAGRAPH)
                    emitCode(node.wholeText())
                }
                "code" -> {
                    // V2EX sometimes emits multi-line code with no wrapping <pre>;
                    // single-line <code> stays an inline monospace span.
                    val text = node.wholeText()
                    if (text.contains('\n')) {
                        flushPending(PARAGRAPH)
                        emitCode(text)
                    } else {
                        appendPending(inlineNodes(listOf(node)))
                    }
                }
                "ul", "ol" -> {
                    flushPending(PARAGRAPH)
                    val items = node.children()
                        .filter { it.tagName() == "li" }
                        .map { inlineNodes(it.childNodes()).trimWhitespace() }
                        .filter { it.text.isNotBlank() }
                    if (items.isNotEmpty()) blocks += ContentBlock.ListBlock(items)
                }
                "hr" -> {
                    flushPending(PARAGRAPH)
                    blocks += ContentBlock.Rule
                }
                "img" -> {
                    flushPending(PARAGRAPH)
                    emitImage(node)
                }
                "a" -> {
                    val hoisted = wrappedImageUrls(node)
                    if (hoisted.isNotEmpty()) {
                        flushPending(PARAGRAPH)
                        hoisted.forEach { blocks += ContentBlock.Image(it) }
                    } else {
                        appendPending(inlineNodes(listOf(node)))
                    }
                }
                "br" -> appendPending(NEWLINE)
                "div", "section", "article" -> node.childNodes().forEach { appendBlockNode(it) }
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flushPending(PARAGRAPH)
                    val heading = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(inlineNodes(node.childNodes()))
                        }
                    }.trimWhitespace()
                    if (heading.text.isNotBlank()) blocks += ContentBlock.Paragraph(heading)
                }
                else -> appendPending(inlineNodes(listOf(node)))
            }
            else -> Unit
        }
    }

    /** Walks `<p>`/`<blockquote>` children, promoting images to standalone blocks and splitting text around them. */
    private fun appendHoisting(nodes: List<Node>, wrap: (AnnotatedString) -> ContentBlock) {
        nodes.forEach { node ->
            when (node) {
                is TextNode -> appendPending(inlineNodes(listOf(node)))
                is Element -> when (node.tagName()) {
                    "img" -> {
                        flushPending(wrap)
                        emitImage(node)
                    }
                    "a" -> {
                        val hoisted = wrappedImageUrls(node)
                        if (hoisted.isNotEmpty()) {
                            flushPending(wrap)
                            hoisted.forEach { blocks += ContentBlock.Image(it) }
                        } else {
                            appendPending(inlineNodes(listOf(node)))
                        }
                    }
                    "br" -> appendPending(NEWLINE)
                    "p", "div", "blockquote" -> {
                        separatorNewline()
                        appendHoisting(node.childNodes(), wrap)
                    }
                    else -> appendPending(inlineNodes(listOf(node)))
                }
                else -> Unit
            }
        }
    }

    fun flushPending(wrap: (AnnotatedString) -> ContentBlock) {
        if (pending.isEmpty()) return
        val merged = buildAnnotatedString { pending.forEach { append(it) } }.trimWhitespace()
        pending.clear()
        if (merged.text.isNotBlank()) blocks += wrap(merged)
    }

    private fun appendPending(text: AnnotatedString) {
        if (text.text.isNotEmpty()) pending += text
    }

    private fun separatorNewline() {
        if (!pendingIsBlank() && !pendingEndsWithNewline()) pending += NEWLINE
    }

    private fun pendingIsBlank() = pending.all { it.text.isBlank() }

    private fun pendingEndsWithNewline(): Boolean {
        for (i in pending.indices.reversed()) {
            val text = pending[i].text
            if (text.isNotEmpty()) return text.last() == '\n'
        }
        return true
    }

    private fun emitImage(img: Element) {
        absoluteContentUrl(img.attr("src"))?.let { blocks += ContentBlock.Image(it) }
    }

    private fun emitCode(text: String) {
        val code = text.trim { it == '\n' || it == '\r' }
        if (code.isNotBlank()) blocks += ContentBlock.Code(code)
    }
}

/** Image URLs of an anchor that is purely an image wrapper (no visible label text). */
private fun wrappedImageUrls(anchor: Element): List<String> {
    val images = anchor.select("img")
    if (images.isEmpty() || anchor.text().isNotBlank()) return emptyList()
    return images.mapNotNull { absoluteContentUrl(it.attr("src")) }
}

private fun inlineNodes(nodes: List<Node>): AnnotatedString =
    buildAnnotatedString { nodes.forEach { appendInlineNode(it) } }

private fun AnnotatedString.Builder.appendInlineNode(node: Node) {
    when (node) {
        is TextNode -> append(node.text())
        is Element -> when (node.tagName()) {
            "br" -> append("\n")
            "strong", "b" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInlineChildren(node) }
            "em", "i" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlineChildren(node) }
            "code" -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { appendInlineChildren(node) }
            "a" -> {
                val target = linkTarget(node.attr("href"), node.text())
                if (target != null) {
                    withLink(LinkAnnotation.Url(target)) { appendInlineChildren(node) }
                } else {
                    appendInlineChildren(node)
                }
            }
            "img" -> {
                val src = absoluteContentUrl(node.attr("src"))
                if (src != null) {
                    withLink(LinkAnnotation.Url(src)) { append("[图片]") }
                } else {
                    append("[图片]")
                }
            }
            else -> appendInlineChildren(node)
        }
        else -> Unit
    }
}

private fun AnnotatedString.Builder.appendInlineChildren(element: Element) {
    element.childNodes().forEach { appendInlineNode(it) }
}

/**
 * Missing/unusable href falls back to the label when the label itself is a full
 * http(s) URL with a host — handles V2EX's `[https://example.com]()` markdown quirk.
 * A plain-text label ("点这里") with an empty href stays unlinked.
 */
private fun linkTarget(href: String, label: String): String? {
    val trimmedHref = href.trim()
    if (trimmedHref.isNotEmpty()) return absoluteContentUrl(trimmedHref)
    val candidate = label.trim()
    if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) return null
    val host = runCatching { URI(candidate).host }.getOrNull()
    return if (host.isNullOrBlank()) null else candidate
}

private fun AnnotatedString.trimWhitespace(): AnnotatedString {
    var start = 0
    var end = length
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    return if (start == 0 && end == length) this else subSequence(start, end)
}
