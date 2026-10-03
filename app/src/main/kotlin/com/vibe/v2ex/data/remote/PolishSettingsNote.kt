package com.vibe.v2ex.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.jsoup.Jsoup

/** V2EX Polish `member-tag` 里一位用户的条目：标签名列表 + 插件顺手存下的头像地址。 */
data class PolishMemberTag(val tags: List<String>, val avatar: String? = null)

/**
 * 记事本里解析出来的插件备份。[root] 是整份 JSON 原样保留 —— 插件会把它自己的全部设置
 * （含 API Token）都备份在这里，App 写回时只允许改 `member-tag` 和 `settings-sync` 两个键。
 */
data class PolishSettingsNote(
    val noteId: Long,
    val root: JsonObject,
    val memberTags: Map<String, PolishMemberTag>,
    val syncVersion: Int,
)

/**
 * V2EX Polish 浏览器插件把设置备份成一篇 V2EX 记事本：标题以 `V2EX_Polish_settings` 开头，
 * 正文 = 同样的前缀 + 整份存储的 JSON。这里是纯解析/拼装，不碰网络，方便 JVM 单测。
 *
 * 协议以商店版 2.x（核对于 2.4.47）的 getV2P_Settings / setV2P_Settings 为准。GitHub 上的源码
 * 停在 1.11.10，和 2.x 有两处不兼容，别照着它改：
 * - 标签从 `[{name}]` 改成了纯字符串数组；2.x 拉取远端备份时原样并入，不做转换，
 *   写成对象会在插件里显示成 `[object Object]`；
 * - 合法性要求 `options` 里同时有 `theme` 和 `nestedReply`，不合法的记事本插件直接无视。
 *
 * 其余不变：列表页 `/notes` 里 `.note_item > .note_item_title > a[href^="/notes"]` 取第一篇标题
 * 匹配的；编辑页 `/notes/edit/{id}` 的 `#note_content.note_editor` 就是原文。
 */
internal object PolishSettingsNoteParser {
    const val MARK = "V2EX_Polish_settings"
    const val KEY_MEMBER_TAG = "member-tag"
    const val KEY_SYNC_INFO = "settings-sync"
    const val KEY_OPTIONS = "options"

    /** 正常备份远小于此；超出直接当异常内容拒绝。 */
    const val MAX_CONTENT_CHARS = 512 * 1024

    private val json = Json { ignoreUnknownKeys = true }
    private val NOTE_ID = Regex("""^/notes/(?:edit/)?([0-9]+)/?$""")
    private val USERNAME = Regex("""^[A-Za-z0-9_]+$""")

    /** 列表页里第一篇标题以 [MARK] 开头的记事本 id；和插件一样只认第一篇。 */
    fun findNoteId(listHtml: String): Long? =
        Jsoup.parse(listHtml).select(".note_item > .note_item_title > a[href^=/notes]")
            .firstOrNull { it.text().trim().startsWith(MARK) }
            ?.let { link ->
                NOTE_ID.matchEntire(link.attr("href").substringBefore('?').substringBefore('#'))
                    ?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
            }

    /** 编辑页编辑框里的原文；页面上没有编辑框（未登录跳转、异常页）返回 null。 */
    fun noteContent(editHtml: String): String? =
        Jsoup.parse(editHtml).selectFirst("textarea#note_content.note_editor, textarea#note_content")
            ?.wholeText()

    /** 不是插件写的、JSON 坏了、插件自己也不认的都返回 null，调用方据此提示用户而不是崩掉。 */
    fun parse(noteId: Long, content: String): PolishSettingsNote? {
        if (content.length > MAX_CONTENT_CHARS) return null
        val body = content.trim()
        if (!body.startsWith(MARK)) return null
        val root = runCatching { json.parseToJsonElement(body.removePrefix(MARK).trim()) }
            .getOrNull() as? JsonObject ?: return null
        val options = root[KEY_OPTIONS] as? JsonObject ?: return null
        // 和插件的 isValidSettings 保持一致：插件不认的记事本，App 往里写也同步不过去。
        if ("theme" !in options || "nestedReply" !in options) return null
        val version = ((root[KEY_SYNC_INFO] as? JsonObject)?.get("version") as? JsonPrimitive)?.intOrNull ?: 0
        return PolishSettingsNote(
            noteId = noteId,
            root = root,
            memberTags = memberTags(root[KEY_MEMBER_TAG]),
            syncVersion = version.coerceAtLeast(0),
        )
    }

    /**
     * 逐条宽松解析：用户名不合法、标签为空的条目跳过，绝不因一条坏数据整体失败。
     * 标签两种写法都认：2.x 的纯字符串，和 1.x 遗留的 `{name}` 对象。
     */
    fun memberTags(element: JsonElement?): Map<String, PolishMemberTag> {
        val obj = element as? JsonObject ?: return emptyMap()
        val result = LinkedHashMap<String, PolishMemberTag>()
        for ((rawName, value) in obj) {
            val name = rawName.trim()
            if (!USERNAME.matches(name)) continue
            val entry = value as? JsonObject ?: continue
            val tags = (entry["tags"] as? JsonArray).orEmpty().mapNotNull { tag ->
                val text = tag as? JsonPrimitive ?: (tag as? JsonObject)?.get("name") as? JsonPrimitive
                text?.takeIf { it.isString }?.content?.trim()?.takeIf(String::isNotEmpty)
            }.distinct()
            if (tags.isEmpty()) continue
            val avatar = (entry["avatar"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") || it.startsWith("//") }
            result[name] = PolishMemberTag(tags, avatar)
        }
        return result
    }

    /**
     * 拼出要写回记事本的正文：原 JSON 的其他键原样保留，只替换 `member-tag`，`settings-sync`
     * 按插件备份时的写法填新版本号和时间。只改插件建好的记事本，不自己新建：App 拼不出一份
     * 插件认可的 `options`，凑一份假的又会在插件拉取时覆盖用户的真实设置。
     */
    fun buildContent(
        root: JsonObject,
        memberTags: Map<String, PolishMemberTag>,
        version: Int,
        nowMillis: Long,
    ): String {
        val merged = buildJsonObject {
            root.forEach { (key, value) ->
                if (key != KEY_MEMBER_TAG && key != KEY_SYNC_INFO) put(key, value)
            }
            put(KEY_MEMBER_TAG, memberTagsJson(memberTags))
            put(
                KEY_SYNC_INFO,
                buildJsonObject {
                    put("version", version)
                    put("lastSyncTime", nowMillis)
                    put("lastCheckTime", nowMillis)
                },
            )
        }
        return MARK + json.encodeToString(JsonObject.serializer(), merged)
    }

    private fun memberTagsJson(memberTags: Map<String, PolishMemberTag>): JsonObject = buildJsonObject {
        memberTags.forEach { (username, entry) ->
            put(
                username,
                buildJsonObject {
                    put("tags", buildJsonArray { entry.tags.forEach { add(it) } })
                    entry.avatar?.let { put("avatar", it) }
                },
            )
        }
    }
}
