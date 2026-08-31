package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.WebSessionService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val apiV1: V2exApiV1,
    private val webSessionService: WebSessionService,
) {
    suspend fun latestTopics(): Result<List<Topic>> = runCatching { apiV1.latestTopics() }

    /**
     * 「最热」。`/api/topics/hot.json` 服务端硬性只返回 10 条且没有分页参数，网页版
     * `?tab=hot` 同一时刻有 30+ 条。所以优先抓网页，解析结果确实比 API 上限多才采用；
     * 网页改版把解析打残时静默退回 API —— 最热永远不会是空的。
     */
    suspend fun hotTopics(): Result<List<Topic>> = runCatching {
        val scraped = runCatching { webSessionService.hotTopics() }.getOrDefault(emptyList())
        if (scraped.size > HOT_API_LIMIT) scraped else apiV1.hotTopics()
    }

    /**
     * 「R2」。网页端按投票算出来的排序，两版 API 都没有对应接口，只能读网页。
     * 抓不到就当失败 —— 这一栏没有可退的接口，空列表比一份错的排序诚实。
     */
    suspend fun r2Topics(): Result<List<Topic>> = runCatching {
        webSessionService.r2Topics().ifEmpty { error("R2 页面没有返回话题") }
    }

    suspend fun topicsInNode(nodeName: String): Result<List<Topic>> =
        runCatching { apiV1.topicsInNode(nodeName) }

    /**
     * "关注" feed: fetch each followed node's topics SEQUENTIALLY (deliberately not parallel —
     * the v1 API budget is 600 req/hour shared per IP), dedup by id, newest activity first.
     * Falls back to the latest feed when the merge comes back empty.
     */
    suspend fun followingTopics(nodeNames: List<String>): Result<List<Topic>> = runCatching {
        val merged = LinkedHashMap<Long, Topic>()
        for (name in nodeNames.take(MAX_FOLLOWING_NODES)) {
            val topics = runCatching { apiV1.topicsInNode(name) }.getOrElse { emptyList() }
            for (topic in topics) merged.putIfAbsent(topic.id, topic)
        }
        if (merged.isEmpty()) {
            apiV1.latestTopics()
        } else {
            merged.values.sortedByDescending { it.activityTimestamp }
        }
    }

    private companion object {
        const val MAX_FOLLOWING_NODES = 6

        /** `/api/topics/hot.json` 的服务端返回上限。 */
        const val HOT_API_LIMIT = 10
    }
}
