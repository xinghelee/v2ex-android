package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.local.HistoryDao
import com.vibe.v2ex.data.local.HistoryEntity
import com.vibe.v2ex.data.model.Topic
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** 浏览历史：读过的话题按最近浏览排序，保留 30 天、500 条封顶（mirrors iOS HistoryStore）。 */
@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
) {
    fun observeAll(): Flow<List<HistoryEntity>> = historyDao.observeAll()

    /** 重复浏览同一话题只更新时间（REPLACE 主键冲突），不会留多条。 */
    suspend fun record(topic: Topic) {
        historyDao.upsert(
            HistoryEntity(
                topicId = topic.id,
                title = topic.title,
                nodeName = topic.nodeTitle,
                viewedAt = System.currentTimeMillis(),
            ),
        )
        prune()
    }

    suspend fun remove(topicId: Long) = historyDao.remove(topicId)
    suspend fun clear() = historyDao.clear()

    suspend fun prune() {
        historyDao.pruneOlderThan(System.currentTimeMillis() - RETENTION_DAYS * 86_400_000L)
        historyDao.pruneToCount(MAX_COUNT)
    }

    companion object {
        const val RETENTION_DAYS = 30
        private const val MAX_COUNT = 500
    }
}
