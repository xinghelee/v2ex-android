package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.local.FavoriteTopicDao
import com.vibe.v2ex.data.local.FavoriteTopicEntity
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.remote.WebSessionService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 本地收藏列表 + 网页远端同步（mirrors iOS FavoritesStore）：
 * - 话题页星标即写本地（登录态下同时打到 V2EX，见 TopicViewModel）；
 * - 登录态下从 /my/topics 抓取网页收藏并入本地 —— 本地已有的保留，新出现的插入。
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val favoriteTopicDao: FavoriteTopicDao,
    private val webSessionService: WebSessionService,
    private val secureStore: SecureStore,
) {
    fun observeAll(): Flow<List<FavoriteTopicEntity>> = favoriteTopicDao.observeAll()
    fun observeIds(): Flow<List<Long>> = favoriteTopicDao.observeIds()

    suspend fun addLocal(topic: Topic) {
        favoriteTopicDao.upsert(
            FavoriteTopicEntity(
                topicId = topic.id,
                title = topic.title,
                nodeName = topic.node?.name ?: topic.nodeTitle,
                authorName = topic.authorName,
                savedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeLocal(topicId: Long) = favoriteTopicDao.remove(topicId)

    /** 拉取网页收藏并合并进本地；未登录或抓取失败静默跳过。 */
    suspend fun syncFromRemote(maxPages: Int = 10) {
        if (!secureStore.isWebSessionActive) return
        val remote = webSessionService.favoriteTopics(maxPages).getOrNull() ?: return
        if (remote.isEmpty()) return
        val now = System.currentTimeMillis()
        // 倒序插入让网页列表的第一条拿到最大的 savedAt，从而排在本地列表最前。
        favoriteTopicDao.insertIfAbsent(
            remote.asReversed().mapIndexed { index, scraped ->
                FavoriteTopicEntity(
                    topicId = scraped.topicId,
                    title = scraped.title,
                    nodeName = scraped.nodeName.ifBlank { scraped.nodeTitle },
                    authorName = scraped.authorName,
                    savedAt = now - remote.size + index,
                )
            },
        )
    }
}
