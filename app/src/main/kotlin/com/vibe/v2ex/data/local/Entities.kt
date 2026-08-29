package com.vibe.v2ex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Doubles as both "稍后读"/offline-saved topics (user-explicit or auto-offline-followed-nodes) and the reopen cache. */
@Entity(tableName = "offline_topics")
data class OfflineTopicEntity(
    @PrimaryKey val topicId: Long,
    val topicJson: String,
    val repliesJson: String,
    val nodeName: String,
    val cachedAt: Long,
    val readingProgress: Int = 0,
    /** false = the user tapped "save offline"; true = auto-cached by the followed-nodes background sync. */
    val automatic: Boolean = false,
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null for a new-topic draft; otherwise the topic being replied to. */
    val topicId: Long?,
    val title: String = "",
    val content: String,
    val nodeName: String? = null,
    val updatedAt: Long,
)

@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey val username: String,
    val blockedAt: Long,
)

@Entity(tableName = "blocked_keywords")
data class BlockedKeywordEntity(
    @PrimaryKey val keyword: String,
    val blockedAt: Long,
)

@Entity(tableName = "hidden_topics")
data class HiddenTopicEntity(@PrimaryKey val topicId: Long, val hiddenAt: Long)

@Entity(tableName = "hidden_replies")
data class HiddenReplyEntity(@PrimaryKey val replyId: Long, val hiddenAt: Long)

@Entity(tableName = "favorite_topics")
data class FavoriteTopicEntity(
    @PrimaryKey val topicId: Long,
    val title: String,
    val nodeName: String,
    val authorName: String,
    val savedAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val topicId: Long,
    val title: String,
    val nodeName: String,
    val viewedAt: Long,
)

/** Mirrors the iOS `ContentReport` shape 1:1 — see ReportService for the delivery contract. */
@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    /** "report" | "block" */
    val kind: String,
    /** "topic" | "reply" | "member" */
    val targetType: String,
    val targetId: String,
    val topicId: Long?,
    val author: String?,
    val excerpt: String?,
    /** English slug sent to the server, e.g. "spam". */
    val reason: String,
    val reasonTitle: String,
    val note: String?,
    val createdAt: Long,
    val deliveredAt: Long?,
)
