package com.vibe.v2ex.data.push

import com.vibe.v2ex.data.model.Notification
import org.jsoup.Jsoup
import java.util.Locale

/** 一次轮询的结论：要推哪些、以及推完后的新基线。 */
data class NotificationPushPlan(
    val toNotify: List<Notification>,
    val newLastSeenId: Long,
)

/**
 * 纯逻辑，方便单测。V2EX 的通知 id 全站递增，「比基线大」就是「新的」。
 * 基线为 0 表示刚打开功能：只记下当前最新的一条，不把历史提醒一股脑推出去。
 */
object NotificationPushPlanner {
    fun plan(
        lastSeenId: Long,
        notifications: List<Notification>,
        blockedUsernames: Set<String> = emptySet(),
    ): NotificationPushPlan {
        val maxId = notifications.maxOfOrNull { it.id } ?: 0L
        val newLastSeenId = maxOf(lastSeenId, maxId)
        if (lastSeenId <= 0L) return NotificationPushPlan(emptyList(), newLastSeenId)
        val fresh = notifications
            .filter { it.id > lastSeenId }
            .filterNot { it.member?.username?.trim()?.lowercase(Locale.ROOT)?.let(blockedUsernames::contains) == true }
            .sortedByDescending { it.id }
        return NotificationPushPlan(fresh, newLastSeenId)
    }

    /** `text` 是带链接的 HTML，通知里只要文字。 */
    fun plainText(notification: Notification): String =
        Jsoup.parse(notification.text.orEmpty()).text().trim().ifEmpty { "你有一条新提醒" }
}
