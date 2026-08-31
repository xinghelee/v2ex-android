package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.local.FeedCacheDao
import com.vibe.v2ex.data.local.FeedCacheEntity
import com.vibe.v2ex.data.model.Topic
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 一份列表快照：话题 + 抓取时间，用于「离线内容 · 更新于 …」的提示。 */
data class CachedFeed(val topics: List<Topic>, val updatedAt: Long)

/**
 * 首页 / 节点列表的离线快照。话题正文由 [OfflineRepository] 负责，这里只管
 * 「能看到有哪些帖」——两者缺一，断网时都是一片空白。
 */
@Singleton
class FeedCacheRepository @Inject constructor(
    private val feedCacheDao: FeedCacheDao,
    private val json: Json,
) {
    suspend fun load(feedKey: String): CachedFeed? {
        val entity = feedCacheDao.get(feedKey) ?: return null
        val topics = runCatching { json.decodeFromString(TopicsSerializer, entity.topicsJson) }.getOrNull()
        return topics?.takeIf { it.isNotEmpty() }?.let { CachedFeed(it, entity.updatedAt) }
    }

    suspend fun save(feedKey: String, topics: List<Topic>) {
        if (topics.isEmpty()) return
        feedCacheDao.upsert(
            FeedCacheEntity(
                feedKey = feedKey,
                // 一屏之外的条目离线时也用不上，截断以免快照无限长。
                topicsJson = json.encodeToString(TopicsSerializer, topics.take(MAX_TOPICS)),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clear() = feedCacheDao.clear()

    private companion object {
        val TopicsSerializer = ListSerializer(Topic.serializer())
        const val MAX_TOPICS = 60
    }
}
