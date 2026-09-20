package com.vibe.v2ex.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineTopicSummaryTest {

    @Test
    fun `parses the fields the list needs from a cached topic json`() {
        val json = """
            {"id":1238193,"title":"单休公司的今天怎么算？","content":"正文","content_rendered":"<p>正文</p>",
             "replies":102,"node":{"id":12,"name":"career","title":"职场话题"},
             "member":{"id":4567,"username":"BennnnJi"},"unknown_field":true}
        """.trimIndent()

        val summary = OfflineTopicSummary.fromTopicJson(json)

        assertEquals("单休公司的今天怎么算？", summary.title)
        assertEquals("职场话题", summary.nodeTitle)
        assertEquals("BennnnJi", summary.authorName)
        assertEquals(4567L, summary.authorId)
        assertEquals(102, summary.replyCount)
    }

    @Test
    fun `node title falls back to node name`() {
        val summary = OfflineTopicSummary.fromTopicJson("""{"id":1,"title":"t","node":{"name":"career"}}""")
        assertEquals("career", summary.nodeTitle)
        assertEquals("", summary.authorName)
        assertNull(summary.authorId)
    }

    @Test
    fun `malformed json degrades to an empty summary instead of failing`() {
        assertEquals(OfflineTopicSummary.EMPTY, OfflineTopicSummary.fromTopicJson("{not json"))
        assertEquals(OfflineTopicSummary.EMPTY, OfflineTopicSummary.fromTopicJson(""))
    }

    @Test
    fun `summary row rebuilds the minimal topic used by cards and moderation rules`() {
        val row = OfflineSummary(
            topicId = 7, title = "标题", nodeName = "python", nodeTitle = "Python", authorName = "alice",
            authorId = 99, replyCount = 3, cachedAt = 1_000, automatic = true, byteSize = 2048,
        )

        val topic = row.topic

        assertEquals(7L, topic.id)
        assertEquals("标题", topic.title)
        assertEquals("Python", topic.nodeTitle)
        assertEquals("alice", topic.authorName)
        assertEquals(99L, topic.member?.id)
        assertEquals(3, topic.replies)
    }
}
