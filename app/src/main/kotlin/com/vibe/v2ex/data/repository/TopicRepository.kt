package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.model.Reply
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.data.remote.WebSessionService
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class TopicDetail(val topic: Topic, val replies: List<Reply>)

private const val REPLIES_PER_PAGE = 20
private const val MAX_REPLY_PAGES = 20

@Singleton
class TopicRepository @Inject constructor(
    private val apiV1: V2exApiV1,
    private val apiV2: V2exApiV2,
    private val webSessionService: WebSessionService,
    private val secureStore: SecureStore,
    private val okHttpClient: OkHttpClient,
) {
    suspend fun loadTopic(topicId: Long): Result<TopicDetail> = runCatching {
        val hasToken = !secureStore.personalAccessToken.isNullOrBlank()

        // v2 is kept current; v1 is unmaintained and can be stale for recent threads.
        val topic = if (hasToken) {
            runCatching { apiV2.topic(topicId).result }.getOrNull()
                ?: apiV1.topic(topicId).firstOrNull()
        } else {
            apiV1.topic(topicId).firstOrNull()
        } ?: error("话题不存在或已删除")

        val replies = if (hasToken) {
            loadRepliesPaged(topicId, topic.replies)
        } else {
            apiV1.repliesForTopic(topicId)
        }

        TopicDetail(topic, replies)
    }

    /** Concurrently fetches up to 20 pages (400 replies), deduped and sorted ascending by id for floor order. */
    private suspend fun loadRepliesPaged(topicId: Long, totalReplies: Int): List<Reply> = coroutineScope {
        val pageCount = min(MAX_REPLY_PAGES, maxOf(1, ceil(totalReplies / REPLIES_PER_PAGE.toDouble()).toInt()))
        val pages = (1..pageCount).map { page ->
            async { runCatching { apiV2.repliesForTopic(topicId, page).result.orEmpty() }.getOrDefault(emptyList()) }
        }
        pages.flatMap { it.await() }
            .associateBy { it.id }
            .values
            .sortedBy { it.id }
    }

    suspend fun postReply(topicId: Long, content: String): Result<Unit> =
        webSessionService.postReply(topicId, content)

    suspend fun setFavorite(topicId: Long, favorited: Boolean): Result<Unit> =
        webSessionService.setFavoriteTopic(topicId, favorited)

    /**
     * Server-authoritative favorite state, scraped off the topic page (no API field exists).
     * null = undeterminable (logged out, network error) — callers keep their current state.
     */
    suspend fun fetchFavoriteState(topicId: Long): Boolean? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("https://www.v2ex.com/t/$topicId").build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string().orEmpty()
                when {
                    html.contains("/unfavorite/topic/$topicId") -> true
                    html.contains("/favorite/topic/$topicId") || html.contains("加入收藏") -> false
                    else -> null
                }
            }
        }.getOrNull()
    }
}
