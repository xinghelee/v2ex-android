package com.vibe.v2ex.designsystem

import org.jsoup.Jsoup

/**
 * Interim plain-text renderer for V2EX's rendered-HTML fields (topic/reply body).
 * Will be replaced by a structured block renderer (paragraphs / images / code /
 * blockquotes as separate composables, matching the iOS custom HTML parser).
 */
fun htmlToPlainText(html: String): String =
    if (html.isBlank()) "" else Jsoup.parse(html).text()
