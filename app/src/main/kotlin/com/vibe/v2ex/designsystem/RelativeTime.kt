package com.vibe.v2ex.designsystem

import com.vibe.v2ex.data.model.Topic

fun relativeTimeText(epochSeconds: Long?): String {
    if (epochSeconds == null || epochSeconds <= 0) return ""
    val deltaSeconds = System.currentTimeMillis() / 1000 - epochSeconds
    return when {
        deltaSeconds < 60 -> "刚刚"
        deltaSeconds < 3600 -> "${deltaSeconds / 60} 分钟前"
        deltaSeconds < 86400 -> "${deltaSeconds / 3600} 小时前"
        deltaSeconds < 86400 * 30 -> "${deltaSeconds / 86400} 天前"
        deltaSeconds < 86400 * 365 -> "${deltaSeconds / (86400 * 30)} 个月前"
        else -> "${deltaSeconds / (86400 * 365)} 年前"
    }
}

/**
 * 列表里的话题时间（issue #9）：有发帖时间就显示发帖时间；网页抓取的列表（/recent、节点翻页）
 * 只有最后回复时间，这时明确标成「最后回复」，不让两种时间混在一起。排序仍按最后回复时间。
 */
fun topicListTimeText(topic: Topic): String {
    topic.created?.takeIf { it > 0 }?.let { return relativeTimeText(it) }
    val touched = relativeTimeText(topic.lastTouched)
    return if (touched.isEmpty()) "" else "最后回复 $touched"
}
