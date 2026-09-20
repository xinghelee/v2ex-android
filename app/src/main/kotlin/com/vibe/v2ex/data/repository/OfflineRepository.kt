package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.local.OfflineSummary
import com.vibe.v2ex.data.local.OfflineTopicDao
import com.vibe.v2ex.data.local.OfflineTopicEntity
import com.vibe.v2ex.data.local.OfflineTopicSummary
import com.vibe.v2ex.data.model.Reply
import com.vibe.v2ex.data.model.Topic
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.json.Json

/** 一份可离线打开的话题快照：正文 + 全部回复。 */
data class OfflineBundle(
    val topic: Topic,
    val replies: List<Reply>,
    val cachedAt: Long,
    /** false = 用户手动「保存以离线阅读」；true = 自动缓存（关注节点同步 / 重开缓存）。 */
    val automatic: Boolean,
    /** JSON 字节数近似值，聚合成「占用 xx MB」。 */
    val byteSize: Int,
)

/**
 * 「稍后读 / 离线」+ 重开缓存，一张表两用（mirrors iOS OfflineStore + TopicDetailCacheStore）：
 * automatic=false 是用户显式保存的离线内容，永不自动清理；automatic=true 是
 * 自动缓存（打开过的话题、关注节点的自动离线），超额时按时间淘汰。
 */
@Singleton
class OfflineRepository @Inject constructor(
    private val offlineTopicDao: OfflineTopicDao,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 首页角标、我的、设置、话题页同时观察这张表，之前各自跑一遍整表查询。合成一份共享订阅后
     * 任何时刻最多一条游标在读；配合摘要列一次装进 CursorWindow，读游标不会再在翻页途中
     * 撞上自动缓存 / 淘汰的写入（issue #5 的崩溃机制）。
     */
    private val summaries: Flow<List<OfflineSummary>> = offlineTopicDao.observeSummaries()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    /** 列表 / 角标 / 占用统计只用摘要列；整篇正文和回复只在 [bundle] 里按 id 读一行。 */
    fun observeSummaries(): Flow<List<OfflineSummary>> = summaries

    fun observeManualIds(): Flow<Set<Long>> = summaries
        .map { list -> list.filterNot { it.automatic }.mapTo(mutableSetOf()) { it.topicId } }
        .distinctUntilChanged()

    suspend fun bundle(topicId: Long): OfflineBundle? = offlineTopicDao.get(topicId)?.let(::decode)

    suspend fun save(topic: Topic, replies: List<Reply>, automatic: Boolean = false) {
        // 手动保存过的条目不能被后来的自动缓存降级成可淘汰。
        val wasManual = offlineTopicDao.isAutomatic(topic.id) == false
        val topicJson = json.encodeToString(Topic.serializer(), topic)
        val repliesJson = json.encodeToString(RepliesSerializer, replies)
        val summary = OfflineTopicSummary.fromTopic(topic)
        offlineTopicDao.upsert(
            OfflineTopicEntity(
                topicId = topic.id,
                topicJson = topicJson,
                repliesJson = repliesJson,
                nodeName = topic.node?.name.orEmpty(),
                cachedAt = System.currentTimeMillis(),
                automatic = automatic && !wasManual,
                title = summary.title,
                nodeTitle = summary.nodeTitle,
                authorName = summary.authorName,
                authorId = summary.authorId,
                replyCount = summary.replyCount,
                byteSize = topicJson.length + repliesJson.length,
            ),
        )
        offlineTopicDao.pruneAutomatic(MAX_AUTOMATIC)
    }

    /** 已缓存但回复数落后于列表数据时才值得重新下载。 */
    suspend fun needsAutomaticRefresh(topic: Topic): Boolean {
        val saved = bundle(topic.id) ?: return true
        return saved.topic.replies < topic.replies || saved.replies.size < topic.replies
    }

    suspend fun remove(topicId: Long) = offlineTopicDao.delete(topicId)
    suspend fun clear() = offlineTopicDao.clear()

    private fun decode(entity: OfflineTopicEntity): OfflineBundle? = runCatching {
        OfflineBundle(
            topic = json.decodeFromString(Topic.serializer(), entity.topicJson),
            replies = json.decodeFromString(RepliesSerializer, entity.repliesJson),
            cachedAt = entity.cachedAt,
            automatic = entity.automatic,
            byteSize = entity.topicJson.length + entity.repliesJson.length,
        )
    }.getOrNull()

    private companion object {
        val RepliesSerializer = kotlinx.serialization.builtins.ListSerializer(Reply.serializer())
        /** 自动缓存全是文本 JSON，放宽到能装下一次通勤/一趟航班的阅读量。 */
        const val MAX_AUTOMATIC = 150
    }
}
