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

/**
 * 首页/节点列表的最后一次快照。没有它，飞机上冷启动只能看到「加载失败」——
 * 话题正文早就离线好了，却没有入口能点进去。
 */
@Entity(tableName = "feed_cache")
data class FeedCacheEntity(
    /** 与 HomeFeed.key 一致："all" / "hot" / "following" / "node:<name>"。 */
    @PrimaryKey val feedKey: String,
    val topicsJson: String,
    val updatedAt: Long,
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

/**
 * 用户标记（对齐浏览器插件 V2EX Polish 的 `member-tag`）。[usernameKey] 是小写用户名，
 * 查找不区分大小写；[username] 保留用户输入或插件里的原始写法，上传回插件时用它。
 */
@Entity(tableName = "member_tags")
data class MemberTagEntity(
    @PrimaryKey val usernameKey: String,
    val username: String,
    /** JSON 字符串数组。 */
    val tagsJson: String,
    /** 插件会顺手存头像，管理页和上传时沿用；本机打的标记也尽量补上。 */
    val avatarUrl: String?,
    val updatedAt: Long,
)
