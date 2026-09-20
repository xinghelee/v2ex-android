package com.vibe.v2ex.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * 协议来自插件源码 `src/utils.ts` 的 getV2P_Settings / setV2P_Settings：
 * - 列表页 `/notes` 里 `.note_item > .note_item_title > a[href^="/notes"]`，取第一篇标题匹配的；
 * - 编辑页 `/notes/edit/{id}` 的 `#note_content.note_editor` 就是原文；
 * - 合法性只看 JSON 顶层有没有 `options` 键（插件的 isValidSettings）。
 */
internal object PolishSettingsNoteParser {
    const val MARK = "V2EX_Polish_settings"
    const val KEY_MEMBER_TAG = "member-tag"
    const val KEY_SYNC_INFO = "settings-sync"
    const val KEY_OPTIONS = "options"

    /** 插件数据受 chrome.storage.sync 配额约束，正常远小于此；超出直接当异常内容拒绝。 */
    const val MAX_CONTENT_CHARS = 512 * 1024

    /** chrome.storage.sync 单个键的上限，超过后浏览器那边会存不进去。 */
    const val SYNC_ITEM_QUOTA_BYTES = 8_192

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

    /** 不是插件写的、JSON 坏了、缺 `options` 键都返回 null，调用方据此提示用户而不是崩掉。 */
    fun parse(noteId: Long, content: String): PolishSettingsNote? {
        if (content.length > MAX_CONTENT_CHARS) return null
        val body = content.trim()
        if (!body.startsWith(MARK)) return null
        val root = runCatching { json.parseToJsonElement(body.removePrefix(MARK).trim()) }
            .getOrNull() as? JsonObject ?: return null
        if (KEY_OPTIONS !in root) return null
        val version = ((root[KEY_SYNC_INFO] as? JsonObject)?.get("version") as? JsonPrimitive)?.intOrNull ?: 0
        return PolishSettingsNote(
            noteId = noteId,
            root = root,
            memberTags = memberTags(root[KEY_MEMBER_TAG]),
            syncVersion = version.coerceAtLeast(0),
        )
    }

    /** 逐条宽松解析：用户名不合法、标签不是字符串、标签为空的条目跳过，绝不因一条坏数据整体失败。 */
    fun memberTags(element: JsonElement?): Map<String, PolishMemberTag> {
        val obj = element as? JsonObject ?: return emptyMap()
        val result = LinkedHashMap<String, PolishMemberTag>()
        for ((rawName, value) in obj) {
            val name = rawName.trim()
            if (!USERNAME.matches(name)) continue
            val entry = value as? JsonObject ?: continue
            val tags = (entry["tags"] as? JsonArray).orEmpty().mapNotNull { tag ->
                ((tag as? JsonObject)?.get("name") as? JsonPrimitive)
                    ?.takeIf { it.isString }?.content?.trim()?.takeIf(String::isNotEmpty)
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
     * 写入新版本号和时间。没有原文（首次新建）时补一个空的 `options`，否则插件会把这篇当作无效备份。
     */
    fun buildContent(
        root: JsonObject?,
        memberTags: Map<String, PolishMemberTag>,
        version: Int,
        nowMillis: Long,
    ): String {
        val merged = buildJsonObject {
            root?.forEach { (key, value) ->
                if (key != KEY_MEMBER_TAG && key != KEY_SYNC_INFO) put(key, value)
            }
            if (root == null || KEY_OPTIONS !in root) put(KEY_OPTIONS, JsonObject(emptyMap()))
            put(KEY_MEMBER_TAG, memberTagsJson(memberTags))
            put(
                KEY_SYNC_INFO,
                buildJsonObject {
                    put("version", version)
                    put("lastSyncTime", nowMillis)
                },
            )
        }
        return MARK + json.encodeToString(JsonObject.serializer(), merged)
    }

    /** `member-tag` 这一个键在 chrome.storage.sync 里的占用（键名 + 值的 JSON 字节数，和浏览器算法一致）。 */
    fun memberTagsSyncBytes(memberTags: Map<String, PolishMemberTag>): Int =
        KEY_MEMBER_TAG.length +
            json.encodeToString(JsonObject.serializer(), memberTagsJson(memberTags)).toByteArray(Charsets.UTF_8).size

    private fun memberTagsJson(memberTags: Map<String, PolishMemberTag>): JsonObject = buildJsonObject {
        memberTags.forEach { (username, entry) ->
            put(
                username,
                buildJsonObject {
                    put(
                        "tags",
                        buildJsonArray {
                            entry.tags.forEach { tag -> add(buildJsonObject { put("name", tag) }) }
                        },
                    )
                    entry.avatar?.let { put("avatar", it) }
                },
            )
        }
    }
}
