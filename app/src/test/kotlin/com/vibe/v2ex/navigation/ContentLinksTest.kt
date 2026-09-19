package com.vibe.v2ex.navigation

import androidx.compose.ui.text.LinkAnnotation
import com.vibe.v2ex.designsystem.ContentBlock
import com.vibe.v2ex.designsystem.parseContentBlocks
import org.junit.Assert.*
import org.junit.Test

class ContentLinksTest {
    @Test fun `topic links resolve to native routes`() {
        listOf(
            "https://www.v2ex.com/t/123456", "http://v2ex.com/t/123456",
            "https://WWW.V2EX.COM/t/123456?p=2", "https://v2ex.com/t/123456/",
            "https://v2ex.com/t/123456.html", "https://v2ex.com:443/t/123456",
            "http://v2ex.com:80/t/123456", "/t/123456", "//v2ex.com/t/123456",
            "https://global.v2ex.com/t/123456", "https://origin.v2ex.com/t/123456",
            "https://edge.v2ex.com/t/123456",
        ).forEach { assertEquals(it, Route.Topic(123456), contentRouteForUrl(it)) }
    }

    @Test fun `reply anchors preserve the requested floor`() {
        assertEquals(Route.Topic(123456, 42), contentRouteForUrl("https://v2ex.com/t/123456?p=2#reply42"))
        assertEquals(Route.Topic(123456), contentRouteForUrl("/t/123456#reply0"))
        assertEquals(Route.Topic(123456), contentRouteForUrl("/t/123456#reply999999999999999999"))
    }

    @Test fun `member mentions open native member screens`() {
        assertEquals(Route.Member("Alice_2"), contentRouteForUrl("/member/Alice_2"))
        assertNull(topicRouteForUrl("https://v2ex.com/member/Alice_2"))
    }

    @Test fun `external malformed and non-topic URLs remain browser links`() {
        listOf(
            "https://example.com/t/123456", "https://v2ex.com.evil.example/t/123456",
            "https://v2ex.com@evil.example/t/123456", "https://user@v2ex.com/t/123456",
            "https://v2ex.com:8443/t/123456", "ftp://v2ex.com/t/123456", "javascript:alert(1)",
            "https://v2ex.com/go/programmer", "https://v2ex.com/t/123456/edit",
            "https://v2ex.com/t/123456abc", "/t/0", "/t/-1", "/t/abc",
            "/t/999999999999999999999999", "/t/123.png", "https://[broken/t/123", "",
        ).forEach { assertNull(it, contentRouteForUrl(it)) }
    }

    @Test fun `body quote list and reply HTML keep native link targets`() {
        val blocks = parseContentBlocks("""
            <p>原帖：<a href="/t/123456">链接</a></p>
            <blockquote><a href="//v2ex.com/t/234567">引用</a></blockquote>
            <ul><li><a href="https://www.v2ex.com/t/345678?p=1">回复链接</a></li></ul>
        """.trimIndent())
        val routes = blocks.flatMap { block ->
            when (block) {
                is ContentBlock.Paragraph -> listOf(block.text)
                is ContentBlock.Quote -> listOf(block.text)
                is ContentBlock.ListBlock -> block.items
                else -> emptyList()
            }
        }.flatMap { text ->
            text.getLinkAnnotations(0, text.length).mapNotNull { annotation ->
                (annotation.item as? LinkAnnotation.Url)?.url?.let(::contentRouteForUrl)
            }
        }
        assertEquals(listOf(Route.Topic(123456), Route.Topic(234567), Route.Topic(345678)), routes)
    }
}
