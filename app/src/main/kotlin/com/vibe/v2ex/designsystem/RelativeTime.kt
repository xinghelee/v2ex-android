package com.vibe.v2ex.designsystem

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
