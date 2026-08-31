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

data class TopicDetail(
    val topic: Topic,
    val replies: List<Reply>,
    /** Non-null means the topic is usable but the reply list may be partial. */
    val replyWarning: String? = null,
)

private data class ReplyLoad(
    val replies: List<Reply>,
    val warning: String? = null,
)

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
            runCatching {
                apiV2.topic(topicId).let { envelope ->
                    if (envelope.success == false || envelope.result == null) {
                        error(envelope.message ?: "话题接口没有返回内容")
                    }
                    envelope.result
                }
            }.getOrNull()
                ?: apiV1.topic(topicId).firstOrNull()
        } else {
            apiV1.topic(topicId).firstOrNull()
        } ?: error("话题不存在或已删除")

        val replyLoad = if (hasToken) {
            loadRepliesPaged(topicId, topic.replies)
        } else {
            val replies = apiV1.repliesForTopic(topicId)
            ReplyLoad(
                replies = replies,
                warning = if (topic.replies > 0 && replies.size < topic.replies) {
                    "回复接口暂未同步完整，当前显示 ${replies.size}/${topic.replies} 条"
                } else {
                    null
                },
            )
        }

        TopicDetail(topic, replyLoad.replies, replyLoad.warning)
    }

    /** Concurrently fetches up to 20 pages (400 replies), deduped and sorted
     * ascending by id for floor order. A failed page is reported explicitly;
     * it is never silently converted into an apparently-complete empty page. */
    private suspend fun loadRepliesPaged(topicId: Long, totalReplies: Int): ReplyLoad = coroutineScope {
        if (totalReplies <= 0) return@coroutineScope ReplyLoad(emptyList())

        val pageCount = min(MAX_REPLY_PAGES, maxOf(1, ceil(totalReplies / REPLIES_PER_PAGE.toDouble()).toInt()))
        val pages = (1..pageCount).map { page ->
            async {
                page to runCatching {
                    apiV2.repliesForTopic(topicId, page).let { envelope ->
                        if (envelope.success == false || envelope.result == null) {
                            error(envelope.message ?: "第 $page 页没有返回内容")
                        }
                        envelope.result
                    }
                }
            }
        }
        val results = pages.map { it.await() }
        val firstFailedPage = results.firstOrNull { it.second.isFailure }?.first

        // Floors are derived from list position in the UI. Once a page is
        // missing, accepting a later page would renumber every later floor and
        // break quote links/discussion-track jumps. Keep only the continuous
        // success prefix starting at page 1.
        var replies = results
            .takeWhile { it.second.isSuccess }
            .flatMap { it.second.getOrDefault(emptyList()) }
            .associateBy { it.id }
            .values
            .sortedBy { it.id }

        // If every v2 page failed, the old endpoint is still better than an
        // empty discussion. Keep whichever source returned more real rows.
        var usedFallback = false
        if (replies.isEmpty() && totalReplies > 0) {
            val fallback = runCatching { apiV1.repliesForTopic(topicId) }.getOrDefault(emptyList())
            if (fallback.size > replies.size) {
                replies = fallback.distinctBy { it.id }.sortedBy { it.id }
                usedFallback = true
            }
        }

        val expectedWithinLimit = min(totalReplies, MAX_REPLY_PAGES * REPLIES_PER_PAGE)
        val warning = when {
            usedFallback && replies.size >= totalReplies -> null
            usedFallback ->
                "新版分页暂不可用，当前从兼容接口显示 ${replies.size}/$totalReplies 条"
            firstFailedPage != null ->
                "第 $firstFailedPage 页起加载失败，为避免楼层错位仅显示前 ${replies.size}/$totalReplies 条"
            replies.size < expectedWithinLimit ->
                "回复可能尚未同步完整，当前显示 ${replies.size}/$totalReplies 条"
            totalReplies > MAX_REPLY_PAGES * REPLIES_PER_PAGE ->
                "长讨论当前显示前 ${replies.size} 条，共 $totalReplies 条"
            else -> null
        }
        ReplyLoad(replies, warning)
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
