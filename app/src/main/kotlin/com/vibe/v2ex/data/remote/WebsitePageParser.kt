package com.vibe.v2ex.data.remote

import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.model.Topic
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PublicTopicPage(
    val topics: List<Topic>,
    val hasMore: Boolean,
)

data class WebsiteBlockSnapshot(
    val usernames: List<String>,
    val unavailableMemberIds: List<Long>,
)

data class WebsiteBlockMutation(
    val username: String,
    val memberId: Long,
    val blocked: Boolean,
)

data class WebsiteNotificationState(
    val username: String,
    val unreadCount: Int,
)

internal data class WebsiteMemberBlockPage(
    val blocked: Boolean,
    val memberId: Long,
    val actionPath: String,
)

internal data class WebsiteNodeFavoritePage(val following: Boolean, val nodeId: Long, val actionPath: String)

/** Pure, side-effect-free parsers shared by website flows and JVM fixture tests. */
internal object WebsitePageParser {
    fun publicTopics(
        html: String,
        baseUrl: String,
        fallbackNodeName: String? = null,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1_000,
    ): List<Topic> {
        val document = Jsoup.parse(html, baseUrl)
        val seen = mutableSetOf<Long>()
        return document.select("div.cell").mapNotNull { cell ->
            val classes = cell.classNames()
            val isTopicRow = "item" in classes || (
                classes.any { FROM_CLASS.matches(it) } && classes.any { TOPIC_CLASS.matches(it) }
            )
            if (!isTopicRow) return@mapNotNull null

            val link = cell.selectFirst("span.item_title > a.topic-link[href], a.topic-link[href]")
                ?: return@mapNotNull null
            val id = TOPIC_ID.find(link.attr("href"))?.groupValues?.get(1)?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: return@mapNotNull null
            val title = link.text().trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            val avatar = cell.selectFirst("img.avatar[src]")?.attr("src")?.takeIf(String::isNotBlank)
            val author = cell.selectFirst("strong > a[href^=/member/]")
                ?.attr("href")
                ?.let { MEMBER_PATH.matchEntire(it)?.groupValues?.get(1) }
            val memberId = cell.select("[data-uid]").firstNotNullOfOrNull { element ->
                element.attr("data-uid").toLongOrNull()?.takeIf { it > 0 }
            }
            val parsedNode = cell.selectFirst("a.node[href^=/go/]")?.let { nodeLink ->
                val name = NODE_PATH.matchEntire(nodeLink.attr("href"))?.groupValues?.get(1)
                    ?: return@let null
                Node(name = name, title = nodeLink.text().trim().ifEmpty { name })
            }
            val node = parsedNode ?: fallbackNodeName?.let { Node(name = it, title = it) }
            val absoluteTouched = cell.select("span[title]")
                .firstNotNullOfOrNull(::absoluteTime)
            val relativeTouched = cell.selectFirst("span.small.fade")
                ?.text()
                ?.let { relativeTime(it, nowEpochSeconds) }

            Topic(
                id = id,
                title = title,
                url = "https://www.v2ex.com/t/$id",
                replies = cell.selectFirst("a[class^=count_]")?.text()?.trim()?.toIntOrNull() ?: 0,
                lastTouched = absoluteTouched ?: relativeTouched,
                node = node,
                member = author?.let {
                    Member(
                        id = memberId,
                        username = it,
                        avatarNormal = avatar,
                        avatarLarge = upscaleAvatar(avatar),
                    )
                },
            )
        }
    }

    fun hasMorePublicTopics(html: String, baseUrl: String, expectedPath: String, page: Int): Boolean {
        val document = Jsoup.parse(html, baseUrl)
        return document.select("a[href]").any { link ->
            runCatching {
                val absolute = link.absUrl("href").takeIf(String::isNotBlank) ?: return@runCatching false
                val uri = URI(absolute)
                if (uri.path != expectedPath || uri.host?.lowercase(Locale.US) !in V2EX_HOSTS) {
                    return@runCatching false
                }
                uri.rawQuery.orEmpty().split('&').any { item ->
                    val key = item.substringBefore('=')
                    val value = item.substringAfter('=', missingDelimiterValue = "")
                    key == "p" && (value.toIntOrNull() ?: 0) > page
                }
            }.getOrDefault(false)
        }
    }

    /** Missing or conflicting buttons never imply an unfollowed node. */
    fun nodeFavoritePage(html: String): WebsiteNodeFavoritePage? {
        val candidates = Jsoup.parse(html).select("a[href]").mapNotNull { link ->
            val match = NODE_FAVORITE_ACTION.matchEntire(link.attr("href")) ?: return@mapNotNull null
            val following = match.groupValues[1] == "unfavorite"
            val labels = if (following) setOf("取消收藏", "unfavorite") else setOf("加入收藏", "favorite this node")
            if (link.text().trim().lowercase(Locale.US) !in labels) return@mapNotNull null
            val id = match.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            WebsiteNodeFavoritePage(following, id, link.attr("href"))
        }
        return candidates.distinct().singleOrNull()
    }

    /** Only accepts one real Block/Unblock input whose label and exact local action agree. */
    fun memberBlockPage(html: String): WebsiteMemberBlockPage? {
        val document = Jsoup.parse(html)
        val candidates = document.select("input[value][onclick]").mapNotNull { input ->
            val label = input.attr("value").trim().lowercase(Locale.US)
            if (label != "block" && label != "unblock") return@mapNotNull null
            val matches = MEMBER_BLOCK_ACTION.findAll(input.attr("onclick")).toList()
            if (matches.size != 1) return@mapNotNull null
            val match = matches.single()
            val kind = match.groupValues[3].lowercase(Locale.US)
            val memberId = match.groupValues[4].toLongOrNull()?.takeIf { it > 0 }
                ?: return@mapNotNull null
            if (kind != label) return@mapNotNull null
            WebsiteMemberBlockPage(
                blocked = kind == "unblock",
                memberId = memberId,
                actionPath = match.groupValues[2],
            )
        }
        return candidates.singleOrNull()
    }

    /** A missing declaration is invalid; only one explicit declaration (including `[]`) is accepted. */
    fun blockedMemberIds(html: String): List<Long>? {
        val lists = mutableListOf<List<Long>>()
        for (script in Jsoup.parse(html).select("script")) {
            for (match in BLOCKED_DECLARATION.findAll(script.data())) {
                val body = match.groupValues[1].trim().removePrefix("[").removeSuffix("]").trim()
                val ids = if (body.isEmpty()) {
                    emptyList()
                } else {
                    body.split(',').map { token ->
                        token.trim().toLongOrNull()?.takeIf { it > 0 } ?: return null
                    }
                }
                lists += ids
            }
        }
        return lists.singleOrNull()?.distinct()?.sorted()
    }

    fun notificationState(html: String): WebsiteNotificationState? {
        if (!html.contains("/signout?")) return null
        val document = Jsoup.parse(html)
        val username = document.select("a.top[href]").firstNotNullOfOrNull { link ->
            MEMBER_PATH.matchEntire(link.attr("href"))?.groupValues?.get(1)
        } ?: return null
        val count = document.select("a[href=/notifications]").firstNotNullOfOrNull { link ->
            UNREAD_TEXT.matchEntire(link.text())?.groupValues?.get(1)
                ?.replace(",", "")
                ?.toIntOrNull()
        } ?: return null
        return WebsiteNotificationState(username = username, unreadCount = count)
    }

    fun isConfirmedNotificationsPage(html: String): Boolean =
        Jsoup.parse(html).select("a[href]").any { it.attr("href").startsWith("/notifications?p=") }

    private fun absoluteTime(element: Element): Long? = runCatching {
        OffsetDateTime.parse(element.attr("title").trim(), WEB_TIME_FORMAT).toEpochSecond()
    }.getOrNull()

    private fun relativeTime(label: String, nowEpochSeconds: Long): Long? {
        val text = label.trim().lowercase(Locale.US)
        if (text.startsWith("just now") || text.startsWith("刚刚")) return nowEpochSeconds
        val parts = RELATIVE_PART.findAll(text).toList()
        if (parts.isEmpty()) return null
        val elapsed = parts.sumOf { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return@sumOf 0L
            amount * (RELATIVE_UNITS[match.groupValues[2].lowercase(Locale.US)] ?: 0L)
        }
        return (nowEpochSeconds - elapsed).takeIf { elapsed > 0 }
    }

    private fun upscaleAvatar(url: String?): String? = url
        ?.replace("_normal.", "_large.")
        ?.replace(GRAVATAR_SIZE, "$1" + "73")

    private val FROM_CLASS = Regex("""from_[0-9]+""")
    private val TOPIC_CLASS = Regex("""t_[0-9]+""")
    private val TOPIC_ID = Regex("""^/t/([0-9]+)(?:[?#/].*)?$""")
    private val MEMBER_PATH = Regex("""^/member/([A-Za-z0-9_]+)$""")
    private val NODE_FAVORITE_ACTION = Regex("""^/(favorite|unfavorite)/node/([1-9][0-9]*)\?once=[0-9]+$""")
    private val NODE_PATH = Regex("""^/go/([A-Za-z0-9_-]+)$""")
    private val MEMBER_BLOCK_ACTION = Regex(
        """(?:^|[;\s{])(?:window\.)?location\.href\s*=\s*(['"])(/(block|unblock)/([0-9]+)\?once=[0-9]+)\1""",
        RegexOption.IGNORE_CASE,
    )
    private val BLOCKED_DECLARATION = Regex(
        """(?:^|[;\r\n])\s*(?:const|let|var)\s+blocked\s*=\s*(\[\s*(?:[0-9]+(?:\s*,\s*[0-9]+)*)?\s*])\s*;""",
    )
    private val UNREAD_TEXT = Regex(
        """\s*([0-9][0-9,]*)\s*(?:未读提醒|unread[^<]*)\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val RELATIVE_PART = Regex(
        """([0-9]+)\s*(seconds?|secs?|minutes?|mins?|hours?|days?|weeks?|months?|mo|years?|小时|分钟|个月|[smhd秒分时天日周月年])""",
        RegexOption.IGNORE_CASE,
    )
    private val RELATIVE_UNITS = mapOf(
        "s" to 1L, "sec" to 1L, "secs" to 1L, "second" to 1L, "seconds" to 1L, "秒" to 1L,
        "m" to 60L, "min" to 60L, "mins" to 60L, "minute" to 60L, "minutes" to 60L,
        "分" to 60L, "分钟" to 60L,
        "h" to 3_600L, "hour" to 3_600L, "hours" to 3_600L, "时" to 3_600L, "小时" to 3_600L,
        "d" to 86_400L, "day" to 86_400L, "days" to 86_400L, "天" to 86_400L, "日" to 86_400L,
        "week" to 604_800L, "weeks" to 604_800L, "周" to 604_800L,
        "mo" to 2_592_000L, "month" to 2_592_000L, "months" to 2_592_000L,
        "月" to 2_592_000L, "个月" to 2_592_000L,
        "year" to 31_536_000L, "years" to 31_536_000L, "年" to 31_536_000L,
    )
    private val GRAVATAR_SIZE = Regex("""([?&]s=)[0-9]+""")
    private val WEB_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.US)
    private val V2EX_HOSTS = setOf("v2ex.com", "www.v2ex.com")
}
