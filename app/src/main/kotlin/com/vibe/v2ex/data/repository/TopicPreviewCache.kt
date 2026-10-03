package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.model.Topic
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 列表里已经拿到的话题摘要（标题、作者、节点），按 id 记在内存里。详情页没有离线快照时先用它
 * 把标题卡画出来，正文和回复到了再换成完整内容 —— 走代理时这一两秒不再是整屏转圈（issue #7）。
 *
 * 只在内存里：进程重启就清空，丢了也只是退回整屏转圈。正文不存，摘要用不上，还占内存。
 */
@Singleton
class TopicPreviewCache @Inject constructor() {
    private val entries = object : LinkedHashMap<Long, Topic>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Topic>): Boolean = size > MAX_ENTRIES
    }

    fun remember(topics: List<Topic>) = synchronized(entries) {
        topics.forEach { topic ->
            if (topic.id > 0 && topic.title.isNotBlank()) {
                entries[topic.id] = topic.copy(content = null, contentRendered = null)
            }
        }
    }

    fun get(topicId: Long): Topic? = synchronized(entries) { entries[topicId] }

    private companion object {
        const val MAX_ENTRIES = 300
    }
}
