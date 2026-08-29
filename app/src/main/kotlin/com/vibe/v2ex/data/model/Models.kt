package com.vibe.v2ex.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [id] is absent on API 2.0 notification payloads — must stay nullable. */
@Serializable
data class Member(
    val id: Long? = null,
    val username: String = "",
    val url: String? = null,
    val website: String? = null,
    val github: String? = null,
    val bio: String? = null,
    val tagline: String? = null,
    val location: String? = null,
    /** API 2.0 — already a full-size URL. */
    val avatar: String? = null,
    @SerialName("avatar_normal") val avatarNormal: String? = null,
    @SerialName("avatar_large") val avatarLarge: String? = null,
    val created: Long? = null,
) {
    /** avatar (v2, full-size) ?? avatarLarge (v1, 73px, preferred over the 48px avatarNormal). */
    val avatarUrl: String?
        get() = (avatar ?: avatarLarge ?: avatarNormal)?.let { if (it.startsWith("//")) "https:$it" else it }
}

/** V2EX's `nodes/all.json` carries no category field — categories are client-hardcoded, see NodeCatalog. */
@Serializable
data class Node(
    val id: Long = 0,
    val name: String = "",
    val title: String = "",
    @SerialName("title_alternative") val titleAlternative: String? = null,
    val url: String? = null,
    val topics: Int? = null,
    val stars: Int? = null,
    val header: String? = null,
    val footer: String? = null,
    @SerialName("avatar_normal") val avatarNormal: String? = null,
    @SerialName("avatar_large") val avatarLarge: String? = null,
    @SerialName("parent_node_name") val parentNodeName: String? = null,
) {
    val path: String get() = "/go/$name"

    val avatarUrl: String?
        get() = (avatarNormal ?: avatarLarge)?.let { if (it.startsWith("//")) "https:$it" else it }
}

@Serializable
data class Topic(
    val id: Long = 0,
    val title: String = "",
    val content: String? = null,
    @SerialName("content_rendered") val contentRendered: String? = null,
    val url: String? = null,
    val replies: Int = 0,
    val created: Long? = null,
    @SerialName("last_touched") val lastTouched: Long? = null,
    @SerialName("last_reply_by") val lastReplyBy: String? = null,
    val node: Node? = null,
    val member: Member? = null,
) {
    val webUrl: String get() = url ?: "https://www.v2ex.com/t/$id"
    val authorName: String get() = member?.username.orEmpty()
    val nodeTitle: String get() = node?.title ?: node?.name.orEmpty()
    val isPromotionNode: Boolean get() = node?.name == "promotions"

    /** The sort/display timestamp used everywhere — falls back to [created] if never touched. */
    val activityTimestamp: Long get() = lastTouched ?: created ?: 0

    val excerpt: String get() = content.orEmpty().replace('\n', ' ').trim()
}

@Serializable
data class Reply(
    val id: Long = 0,
    val content: String = "",
    @SerialName("content_rendered") val contentRendered: String = "",
    val created: Long? = null,
    @SerialName("topic_id") val topicId: Long? = null,
    @SerialName("member_id") val memberId: Long? = null,
    val member: Member? = null,
) {
    val authorName: String get() = member?.username.orEmpty()
}

/** No API read-state field exists at all for notifications — `kind` mirrors the iOS client-side classifier. */
enum class NotificationKind { REPLY, MENTION, THANKS, FAVORITE }

@Serializable
data class Notification(
    val id: Long = 0,
    @SerialName("member_id") val memberId: Long? = null,
    @SerialName("for_member_id") val forMemberId: Long? = null,
    /** HTML: `<a href="/member/x">x</a> 在 <a href="/t/123">标题</a> 里回复了你`. */
    val text: String? = null,
    val payload: String? = null,
    @SerialName("payload_rendered") val payloadRendered: String? = null,
    val created: Long? = null,
    /** Only ever carries `username` — never avatar/id; backfilled separately, see NotificationsViewModel. */
    val member: Member? = null,
) {
    val kind: NotificationKind
        get() = when {
            text?.contains("感谢") == true -> NotificationKind.THANKS
            text?.contains("提到了你") == true || text?.contains("@") == true -> NotificationKind.MENTION
            text?.contains("收藏") == true -> NotificationKind.FAVORITE
            else -> NotificationKind.REPLY
        }
}
