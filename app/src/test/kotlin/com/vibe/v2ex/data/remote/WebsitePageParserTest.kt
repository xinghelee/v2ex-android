package com.vibe.v2ex.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsitePageParserTest {
    @Test
    fun `public topic parser accepts desktop and alternate node rows`() {
        val desktop = """
            <div class="cell item" style="">
              <img src="//cdn.v2ex.com/avatar_normal.png" class="avatar">
              <span class="item_title"><a class="topic-link" href="/t/7#reply2">Title &amp; More</a></span>
              <span class="topic_info">
                <a href="/go/career" class="node">Career</a>
                <strong><a href="/member/alice">alice</a></strong>
                <span data-uid="42" title="2026-08-31 11:30:18 +08:00">2 mins ago</span>
                <strong><a href="/member/last_replier">last_replier</a></strong>
              </span>
              <a class="count_livid">22</a>
            </div>
        """.trimIndent()
        val alternate = """
            <div class="cell from_99 t_8">
              <a href="/t/8" class="topic-link">Node row</a>
              <strong><a href="/member/bob">bob</a></strong>
              <i data-uid="99"></i>
              <span class="small fade">8h 56m ago&nbsp;</span>
            </div>
        """.trimIndent()

        val desktopTopic = WebsitePageParser.publicTopics(
            desktop,
            "https://www.v2ex.com/recent?p=1",
        ).single()
        assertEquals(7L, desktopTopic.id)
        assertEquals("Title & More", desktopTopic.title)
        assertEquals("alice", desktopTopic.member?.username)
        assertEquals(42L, desktopTopic.member?.id)
        assertEquals("career", desktopTopic.node?.name)
        assertEquals(22, desktopTopic.replies)
        assertNotNull(desktopTopic.lastTouched)

        val nodeTopic = WebsitePageParser.publicTopics(
            alternate,
            "https://www.v2ex.com/go/swift?p=2",
            fallbackNodeName = "swift",
            nowEpochSeconds = 1_000_000,
        ).single()
        assertEquals(8L, nodeTopic.id)
        assertEquals("swift", nodeTopic.node?.name)
        assertEquals(99L, nodeTopic.member?.id)
        assertEquals(1_000_000L - 8 * 3_600L - 56 * 60L, nodeTopic.lastTouched)

        // A page can repeat a topic row; public paging owns one stable row per topic ID.
        assertEquals(
            listOf(7L),
            WebsitePageParser.publicTopics(
                desktop + desktop,
                "https://www.v2ex.com/recent?p=1",
            ).map { it.id },
        )
    }

    @Test
    fun `public pagination only accepts a later page on the same V2EX path`() {
        val recent = """
            <a href="/recent?p=1">previous</a>
            <a href="/recent?p=2">current</a>
            <a href="/recent?p=3&amp;foo=1">next</a>
            <a href="/go/swift?p=99">other feed</a>
            <a href="https://evil.example/recent?p=99">external</a>
        """.trimIndent()
        assertTrue(
            WebsitePageParser.hasMorePublicTopics(
                recent,
                "https://www.v2ex.com/recent?p=2",
                "/recent",
                2,
            ),
        )
        assertFalse(
            WebsitePageParser.hasMorePublicTopics(
                recent.replace("/recent?p=3&amp;foo=1", "/recent?p=2"),
                "https://www.v2ex.com/recent?p=2",
                "/recent",
                2,
            ),
        )

        val node = """
            <a href="/go/swift?p=1">previous</a>
            <a href="/go/swift?sort=default&amp;p=3">next</a>
            <a href="/go/kotlin?p=50">other node</a>
            <a href="https://evil.example/go/swift?p=50">external</a>
        """.trimIndent()
        assertTrue(
            WebsitePageParser.hasMorePublicTopics(
                node,
                "https://www.v2ex.com/go/swift?p=2",
                "/go/swift",
                2,
            ),
        )
        assertFalse(
            WebsitePageParser.hasMorePublicTopics(
                node.replace("/go/swift?sort=default&amp;p=3", "/go/swift?p=2"),
                "https://www.v2ex.com/go/swift?p=2",
                "/go/swift",
                2,
            ),
        )
        assertFalse(
            WebsitePageParser.hasMorePublicTopics(
                "<a href=\"https://evil.example/recent?p=99\">external</a>",
                "https://www.v2ex.com/recent?p=2",
                "/recent",
                2,
            ),
        )
    }

    @Test
    fun `challenge or empty HTML yields no topics for the caller to reject`() {
        val challenge = """
            <html><title>Just a moment...</title><form id="challenge-form"></form></html>
        """.trimIndent()
        assertTrue(
            WebsitePageParser.publicTopics(
                challenge,
                "https://www.v2ex.com/recent?p=1",
            ).isEmpty(),
        )
        assertTrue(WebsitePageParser.publicTopics("", "https://www.v2ex.com/recent?p=1").isEmpty())

        // This pure parser has no transport. WebSessionService.publicTopicPage is the caller that
        // validates HTTP/final-path state and converts an empty parse into Result.failure.
    }

    @Test
    fun `blocked ID parser distinguishes an explicit empty list from invalid pages`() {
        assertEquals(
            listOf(7L, 42L),
            WebsitePageParser.blockedMemberIds("<script>const blocked = [42,42,7];</script>"),
        )
        assertEquals(emptyList<Long>(), WebsitePageParser.blockedMemberIds("<script>\nvar blocked = [ ];</script>"))
        assertEquals(emptyList<Long>(), WebsitePageParser.blockedMemberIds("<script>let blocked=[];</script>"))

        val invalid = listOf(
            "Sign In",
            "<script>const ignored_topics = [];</script>",
            "<p>const blocked = [42];</p>",
            "<script>const blocked = [0];</script>",
            "<script>const blocked = [1,];</script>",
            "<script>const blocked = [1];\nconst blocked = [2];</script>",
            "<script>const blocked = [1];</script><script>let blocked = [1];</script>",
        )
        invalid.forEach { assertNull("accepted invalid HTML: $it", WebsitePageParser.blockedMemberIds(it)) }
    }

    @Test
    fun `member action parser accepts exactly one matching local action`() {
        fun page(action: String, id: Long = 42): String =
            """<input type="button" value="${if (action == "block") "Block" else "Unblock"}" """ +
                """onclick="if (confirm('sure?')) { location.href = '/$action/$id?once=123'; }">"""

        val block = page("block")
        val unblock = page("unblock")
        assertEquals(false, WebsitePageParser.memberBlockPage(block)?.blocked)
        assertEquals(true, WebsitePageParser.memberBlockPage(unblock)?.blocked)
        assertEquals(42L, WebsitePageParser.memberBlockPage(block)?.memberId)
        assertEquals("/block/42?once=123", WebsitePageParser.memberBlockPage(block)?.actionPath)

        val unrelatedButton = """<input value="Cancel" onclick="history.back()">"""
        assertEquals(42L, WebsitePageParser.memberBlockPage(unrelatedButton + block)?.memberId)

        val invalid = listOf(
            "<html>Sign In</html>",
            "/block/42?once=1",
            block + unblock,
            block.replace("'/block/", "'https://evil.example/block/"),
            block.replace("'/block/", "'//evil.example/block/"),
            block.replace("?once=123", ""),
            block.replace("?once=123", "?once=123&redirect=evil"),
            block.replace("value=\"Block\"", "value=\"Unblock\""),
            block.replace(
                "; }",
                "; location.href = '/unblock/42?once=124'; }",
            ),
            "&lt;input value=\"Block\" onclick=\"location.href='/block/42?once=1'\"&gt;",
        )
        invalid.forEach { assertNull("accepted invalid HTML: $it", WebsitePageParser.memberBlockPage(it)) }
    }

    @Test
    fun `notification parser requires logged-in account and explicit unread counter`() {
        val english = """
            <a class="top" href="/member/alice">alice</a>
            <a href="/signout?once=1">Exit</a>
            <a href="/notifications">1,234 unread notifications</a>
            <a href="/notifications?p=1">1</a>
        """.trimIndent()
        val chinese = """
            <a href="/member/Bob_2" class="top">Bob_2</a>
            <a href="/signout?once=2">退出</a>
            <a href="/notifications">4 未读提醒</a>
        """.trimIndent()

        assertEquals(WebsiteNotificationState("alice", 1_234), WebsitePageParser.notificationState(english))
        assertEquals(WebsiteNotificationState("Bob_2", 4), WebsitePageParser.notificationState(chinese))
        assertEquals(
            WebsiteNotificationState("alice", 0),
            WebsitePageParser.notificationState(english.replace("1,234 unread notifications", "0 unread notifications")),
        )
        assertEquals(
            WebsiteNotificationState("Bob_2", 0),
            WebsitePageParser.notificationState(chinese.replace("4 未读提醒", "0 未读提醒")),
        )
        assertTrue(WebsitePageParser.isConfirmedNotificationsPage(english))
        assertFalse(WebsitePageParser.isConfirmedNotificationsPage(chinese))
        assertNull(WebsitePageParser.notificationState(english.replace("/signout?once=1", "/signin")))
        assertNull(WebsitePageParser.notificationState(english.replace("class=\"top\"", "class=\"fade\"")))
        assertNull(WebsitePageParser.notificationState(english.replace("href=\"/notifications\"", "href=\"/notifications?p=1\"")))
        assertNull(WebsitePageParser.notificationState(english.replace("unread notifications", "messages")))
    }
}
