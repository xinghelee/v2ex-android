package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.model.Reply
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.data.remote.WebSessionService
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Deferred
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
    /**
     * 回复和话题同时发：首屏要等两者都回来，串行就是两次往返，走代理时能差出 1 秒以上（issue #7）。
     * 话题失败时整个 scope 失败，同时发出去的回复请求随之取消。
     */
    suspend fun loadTopic(topicId: Long): Result<TopicDetail> = runCatching {
        coroutineScope {
            val hasToken = !secureStore.personalAccessToken.isNullOrBlank()
            // v2 第 1 页不依赖楼层总数，先发；其余页等话题返回知道总数后再并发拉。
            val firstV2Page = if (hasToken) async { v2RepliesPage(topicId, 1) } else null
            val v1Replies = if (hasToken) null else async { apiV1.repliesForTopic(topicId) }

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

            val replyLoad = if (firstV2Page != null) {
                loadRepliesPaged(topicId, topic.replies, firstV2Page)
            } else {
                val replies = checkNotNull(v1Replies).await()
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
    }

    /** Concurrently fetches up to 20 pages (400 replies), deduped and sorted
     * ascending by id for floor order. A failed page is reported explicitly;
     * it is never silently converted into an apparently-complete empty page.
     * [firstPage] is already in flight (started alongside the topic request). */
    private suspend fun loadRepliesPaged(
        topicId: Long,
        totalReplies: Int,
        firstPage: Deferred<Result<List<Reply>>>,
    ): ReplyLoad = coroutineScope {
        if (totalReplies <= 0) {
            firstPage.cancel()
            return@coroutineScope ReplyLoad(emptyList())
        }

        val pageCount = min(MAX_REPLY_PAGES, maxOf(1, ceil(totalReplies / REPLIES_PER_PAGE.toDouble()).toInt()))
        val laterPages = (2..pageCount).map { page -> async { v2RepliesPage(topicId, page) } }
        val results = (listOf(firstPage) + laterPages).mapIndexed { index, page -> index + 1 to page.await() }
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

    private suspend fun v2RepliesPage(topicId: Long, page: Int): Result<List<Reply>> = runCatching {
        apiV2.repliesForTopic(topicId, page).let { envelope ->
            if (envelope.success == false || envelope.result == null) {
                error(envelope.message ?: "第 $page 页没有返回内容")
            }
            envelope.result
        }
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
