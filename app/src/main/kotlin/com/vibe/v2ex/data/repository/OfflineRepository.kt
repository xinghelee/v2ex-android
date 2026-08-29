package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.local.OfflineTopicDao
import com.vibe.v2ex.data.local.OfflineTopicEntity
import com.vibe.v2ex.data.model.Reply
import com.vibe.v2ex.data.model.Topic
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    fun observeAll(): Flow<List<OfflineBundle>> =
        offlineTopicDao.observeAll().map { entities -> entities.mapNotNull(::decode) }

    fun observeManualIds(): Flow<Set<Long>> =
        offlineTopicDao.observeAll().map { entities ->
            entities.filterNot { it.automatic }.mapTo(mutableSetOf()) { it.topicId }
        }

    suspend fun bundle(topicId: Long): OfflineBundle? = offlineTopicDao.get(topicId)?.let(::decode)

    suspend fun save(topic: Topic, replies: List<Reply>, automatic: Boolean = false) {
        // 手动保存过的条目不能被后来的自动缓存降级成可淘汰。
        val wasManual = offlineTopicDao.get(topic.id)?.automatic == false
        offlineTopicDao.upsert(
            OfflineTopicEntity(
                topicId = topic.id,
                topicJson = json.encodeToString(Topic.serializer(), topic),
                repliesJson = json.encodeToString(RepliesSerializer, replies),
                nodeName = topic.node?.name.orEmpty(),
                cachedAt = System.currentTimeMillis(),
                automatic = automatic && !wasManual,
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
        const val MAX_AUTOMATIC = 50
    }
}
