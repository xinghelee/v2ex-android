package com.vibe.v2ex.designsystem

import com.vibe.v2ex.data.model.Topic
import org.junit.Assert.assertEquals
import org.junit.Test

class TopicListTimeTest {
    private val now = System.currentTimeMillis() / 1000

    @Test
    fun `prefers the posting time when the api provides it`() {
        val topic = Topic(created = now - 3 * 3600, lastTouched = now - 120)
        assertEquals("3 小时前", topicListTimeText(topic))
    }

    @Test
    fun `web scraped rows without created are labelled as last reply`() {
        assertEquals("最后回复 2 分钟前", topicListTimeText(Topic(lastTouched = now - 120)))
    }

    @Test
    fun `no timestamps yields empty text`() {
        assertEquals("", topicListTimeText(Topic()))
    }
}
