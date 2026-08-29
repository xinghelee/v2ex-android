package com.vibe.v2ex.data.moderation

import com.vibe.v2ex.data.local.BlockListDao
import com.vibe.v2ex.data.local.BlockedKeywordEntity
import com.vibe.v2ex.data.local.BlockedUserEntity
import com.vibe.v2ex.data.local.HiddenReplyEntity
import com.vibe.v2ex.data.local.HiddenTopicEntity
import com.vibe.v2ex.data.local.ModerationVisibilityDao
import com.vibe.v2ex.data.local.ReportDao
import com.vibe.v2ex.data.local.ReportEntity
import com.vibe.v2ex.data.model.Reply
import com.vibe.v2ex.data.model.Topic
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class ReportReason(val slug: String, val title: String) {
    SPAM("spam", "垃圾信息或广告"),
    HARASSMENT("harassment", "骚扰、辱骂或人身攻击"),
    HATE("hate", "仇恨言论或歧视"),
    SEXUAL("sexual", "色情或性暗示内容"),
    VIOLENCE("violence", "暴力、血腥或自残"),
    ILLEGAL("illegal", "违法或欺诈内容"),
    PRIVACY("privacy", "泄露他人隐私"),
    OTHER("other", "其他"),
}

enum class ReportTargetType(val slug: String) { TOPIC("topic"), REPLY("reply"), MEMBER("member") }

/**
 * Central UGC-moderation model — added for App Store Guideline 1.2 compliance (see
 * commit f5984ac): every content-listing surface must filter through [isTopicHidden] /
 * [isReplyHidden]. Reporting is local-first — the hide takes effect immediately,
 * network delivery (ReportService) is fire-and-forget and retried by AppViewModel-level
 * callers via [pendingReports].
 */
@Singleton
class ModerationStore @Inject constructor(
    private val blockListDao: BlockListDao,
    private val visibilityDao: ModerationVisibilityDao,
    private val reportDao: ReportDao,
    private val reportService: ReportService,
) {
    val blockedUsernames: Flow<List<String>> = blockListDao.observeBlockedUsers()
    val blockedKeywords: Flow<List<String>> = blockListDao.observeBlockedKeywords()
    val hiddenTopicIds: Flow<List<Long>> = visibilityDao.observeHiddenTopicIds()
    val hiddenReplyIds: Flow<List<Long>> = visibilityDao.observeHiddenReplyIds()

    val moderationCount: Flow<Int> = combine(blockedUsernames, blockedKeywords, hiddenTopicIds, hiddenReplyIds) {
        usernames, keywords, topics, replies -> usernames.size + keywords.size + topics.size + replies.size
    }

    fun isTopicHidden(topic: Topic, hiddenIds: List<Long>, blockedUsers: List<String>, keywords: List<String>): Boolean =
        hiddenIds.contains(topic.id) ||
            isBlocked(topic.authorName, blockedUsers) ||
            matchesKeyword("${topic.title} ${topic.content.orEmpty()}", keywords)

    fun isReplyHidden(reply: Reply, hiddenIds: List<Long>, blockedUsers: List<String>, keywords: List<String>): Boolean =
        hiddenIds.contains(reply.id) ||
            isBlocked(reply.authorName, blockedUsers) ||
            matchesKeyword(reply.content, keywords)

    private fun isBlocked(username: String, blockedUsers: List<String>): Boolean =
        username.isNotBlank() && blockedUsers.any { it.equals(username, ignoreCase = true) }

    private fun matchesKeyword(text: String, keywords: List<String>): Boolean =
        keywords.isNotEmpty() && keywords.any { text.contains(it, ignoreCase = true) }

    suspend fun blockUser(username: String) {
        blockListDao.blockUser(BlockedUserEntity(username, System.currentTimeMillis()))
    }

    suspend fun unblockUser(username: String) = blockListDao.unblockUser(username)

    suspend fun blockKeyword(keyword: String) {
        blockListDao.blockKeyword(BlockedKeywordEntity(keyword, System.currentTimeMillis()))
    }

    suspend fun unblockKeyword(keyword: String) = blockListDao.unblockKeyword(keyword)

    suspend fun unhideTopic(topicId: Long) = visibilityDao.unhideTopic(topicId)
    suspend fun unhideReply(replyId: Long) = visibilityDao.unhideReply(replyId)

    /** Hides immediately, then fires the report off in the background — see class doc. */
    suspend fun reportTopic(topicId: Long, author: String?, excerpt: String?, reason: ReportReason, note: String?) {
        visibilityDao.hideTopic(HiddenTopicEntity(topicId, System.currentTimeMillis()))
        enqueue(ReportTargetType.TOPIC, topicId.toString(), topicId, author, excerpt, reason, note, kind = "report")
    }

    suspend fun reportReply(replyId: Long, topicId: Long?, author: String?, excerpt: String?, reason: ReportReason, note: String?) {
        visibilityDao.hideReply(HiddenReplyEntity(replyId, System.currentTimeMillis()))
        enqueue(ReportTargetType.REPLY, replyId.toString(), topicId, author, excerpt, reason, note, kind = "report")
    }

    /** Idempotent — the DAO's REPLACE-on-conflict + a stable per-username id keeps re-blocking a no-op report. */
    suspend fun blockUserAndReport(username: String, topicId: Long?, excerpt: String?, reason: ReportReason) {
        blockUser(username)
        enqueue(ReportTargetType.MEMBER, username, topicId, username, excerpt, reason, note = null, kind = "block")
    }

    private suspend fun enqueue(
        targetType: ReportTargetType,
        targetId: String,
        topicId: Long?,
        author: String?,
        excerpt: String?,
        reason: ReportReason,
        note: String?,
        kind: String,
    ) {
        val entity = ReportEntity(
            id = UUID.randomUUID().toString(),
            kind = kind,
            targetType = targetType.slug,
            targetId = targetId,
            topicId = topicId,
            author = author,
            excerpt = excerpt?.take(500),
            reason = reason.slug,
            reasonTitle = reason.title,
            note = note?.take(1000),
            createdAt = System.currentTimeMillis(),
            deliveredAt = null,
        )
        reportDao.upsert(entity)
        flushPending()
    }

    /** Call on app-launch and whenever the app returns to the foreground — retries indefinitely until delivered. */
    suspend fun flushPending() {
        reportDao.pending().forEach { report ->
            if (reportService.submit(report)) {
                reportDao.update(report.copy(deliveredAt = System.currentTimeMillis()))
            }
        }
    }
}
