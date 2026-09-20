package com.vibe.v2ex.data.tags

import java.util.Locale

/** 一位用户的本地标记：用户名保留用户输入时的大小写，比较时统一按小写。 */
data class MemberTagRecord(
    val username: String,
    val tags: List<String>,
    val avatarUrl: String? = null,
)

/**
 * 界面查表用的只读快照。「显示用户标记」关闭时恒为 [Disabled]：所有画标记的地方
 * 都只问这一个对象，关掉开关后整套 UI 就和没有这个功能时一模一样。
 */
class MemberTagLookup(
    val enabled: Boolean,
    private val tagsByLowercaseName: Map<String, List<String>>,
) {
    val taggedCount: Int get() = tagsByLowercaseName.size

    fun tagsFor(username: String): List<String> {
        if (!enabled || username.isBlank()) return emptyList()
        return tagsByLowercaseName[username.trim().lowercase(Locale.ROOT)].orEmpty()
    }

    companion object {
        val Disabled = MemberTagLookup(enabled = false, tagsByLowercaseName = emptyMap())

        fun of(records: Collection<MemberTagRecord>): MemberTagLookup = MemberTagLookup(
            enabled = true,
            tagsByLowercaseName = records
                .filter { it.tags.isNotEmpty() }
                .associate { it.username.lowercase(Locale.ROOT) to it.tags },
        )
    }
}

/** 去空白、去空项、去重，保持原顺序。所有写入口都过这一道，存进去的永远是干净的。 */
fun normalizeMemberTags(tags: Iterable<String>): List<String> =
    tags.map(String::trim).filter(String::isNotEmpty).distinct()

/**
 * 按用户名（不区分大小写）取并集：[primary] 的顺序在前，[secondary] 里新增的用户和标签追加在后。
 * 谁都不会删掉谁的标签 —— 拉取时本地是 primary，上传时远端是 primary，一台落后的设备
 * 永远只会补充，不会把另一边刚加的标记冲掉。
 */
fun mergeMemberTags(
    primary: Collection<MemberTagRecord>,
    secondary: Collection<MemberTagRecord>,
): List<MemberTagRecord> {
    val merged = LinkedHashMap<String, MemberTagRecord>()
    for (record in primary + secondary) {
        val tags = normalizeMemberTags(record.tags)
        if (tags.isEmpty()) continue
        val key = record.username.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) continue
        val existing = merged[key]
        merged[key] = if (existing == null) {
            MemberTagRecord(record.username.trim(), tags, record.avatarUrl?.takeIf(String::isNotBlank))
        } else {
            existing.copy(
                tags = normalizeMemberTags(existing.tags + tags),
                avatarUrl = existing.avatarUrl ?: record.avatarUrl?.takeIf(String::isNotBlank),
            )
        }
    }
    return merged.values.toList()
}
