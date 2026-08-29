package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.remote.V2exApiV1
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val apiV1: V2exApiV1,
) {
    suspend fun latestTopics(): Result<List<Topic>> = runCatching { apiV1.latestTopics() }

    suspend fun hotTopics(): Result<List<Topic>> = runCatching { apiV1.hotTopics() }

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
    }
}
