package com.vibe.v2ex.data.local

import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.model.Topic
import kotlinx.serialization.json.Json

/**
 * `offline_topics` 里给列表用的摘要列。列表、角标、占用统计都只读这几列，正文和回复 JSON
 * 只在打开单篇时按 id 读一行。
 *
 * 背景（issue #5）：之前列表走 `SELECT *`，几十篇自动离线的话题连回复一起塞进一个 CursorWindow，
 * 超过 2 MB 后 Room 2.8 的 SQLite 包装层在翻页时会抛 "Couldn't read row N from CursorWindow"，
 * 每次打开话题都崩。摘要列一行只有几百字节，几百篇也远到不了窗口上限。
 */
data class OfflineTopicSummary(
    val title: String,
    val nodeTitle: String,
    val authorName: String,
    val authorId: Long?,
    val replyCount: Int,
) {
    companion object {
        val EMPTY = OfflineTopicSummary(title = "", nodeTitle = "", authorName = "", authorId = null, replyCount = 0)

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun fromTopic(topic: Topic): OfflineTopicSummary = OfflineTopicSummary(
            title = topic.title,
            // 节点标题为空时退回节点名，列表里不留空白。
            nodeTitle = topic.node?.title?.takeIf(String::isNotBlank) ?: topic.node?.name.orEmpty(),
            authorName = topic.authorName,
            authorId = topic.member?.id,
            replyCount = topic.replies,
        )

        /** 迁移回填用：坏 JSON 只让那一篇在列表里没标题，绝不让迁移整体失败。 */
        fun fromTopicJson(topicJson: String): OfflineTopicSummary =
            runCatching { fromTopic(json.decodeFromString(Topic.serializer(), topicJson)) }.getOrDefault(EMPTY)
    }
}

/** 列表投影的一行（Room 按列名映射），不带正文和回复 JSON。 */
data class OfflineSummary(
    val topicId: Long,
    val title: String,
    val nodeName: String,
    val nodeTitle: String,
    val authorName: String,
    val authorId: Long?,
    val replyCount: Int,
    val cachedAt: Long,
    val automatic: Boolean,
    val byteSize: Int,
) {
    /** 列表卡片与屏蔽规则需要的最小 Topic 形态；不含正文。 */
    val topic: Topic
        get() = Topic(
            id = topicId,
            title = title,
            replies = replyCount,
            node = Node(name = nodeName, title = nodeTitle),
            member = Member(id = authorId, username = authorName),
        )
}
