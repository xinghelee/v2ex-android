package com.vibe.v2ex.data.nodes

/**
 * V2EX's `nodes/all.json` carries no category field, so the site's own category tabs
 * are reproduced here verbatim (mirrors the iOS NodeCatalog.swift — node-name lists
 * copied 1:1 from source). `icon` names a Material Symbols icon id the UI layer maps
 * to an ImageVector; kept as a string here to keep this file UI-framework-free.
 */
data class NodeCategory(val title: String, val icon: String, val nodeNames: List<String>)

object NodeCatalog {
    val categories: List<NodeCategory> = listOf(
        NodeCategory(
            title = "技术",
            icon = "code",
            nodeNames = listOf(
                "programmer", "python", "linux", "nodejs", "java", "php", "go", "rust",
                "javascript", "css", "cloud", "database", "docker", "kubernetes",
                "machinelearning", "openai", "network", "security", "vim", "git",
                "android", "idev", "flutter", "reactjs", "jobs",
            ),
        ),
        NodeCategory(
            title = "创意",
            icon = "lightbulb",
            nodeNames = listOf("create", "design", "share", "resource"),
        ),
        NodeCategory(
            title = "生活",
            icon = "coffee",
            nodeNames = listOf("life", "travel", "books", "movie", "music", "food", "qna", "pets"),
        ),
        NodeCategory(
            title = "好玩",
            icon = "sports_esports",
            nodeNames = listOf("play", "game", "boardgame", "switch", "steam", "playstation"),
        ),
        NodeCategory(
            title = "Apple",
            icon = "apple",
            nodeNames = listOf("apple", "macos", "iphone", "ipad", "appstore"),
        ),
        NodeCategory(
            title = "硬件与自建",
            icon = "dns",
            nodeNames = listOf("hardware", "diy", "nas", "homeserver", "router", "raspberrypi"),
        ),
        NodeCategory(
            title = "酷工作",
            icon = "work",
            nodeNames = listOf("cv", "career", "outsourcing", "freelancer"),
        ),
        NodeCategory(
            title = "交易",
            icon = "sell",
            nodeNames = listOf("trade", "free", "app", "deals"),
        ),
        NodeCategory(
            title = "问与答",
            icon = "help",
            nodeNames = listOf("qna", "help"),
        ),
    )

    /** Small bootstrap dict of Chinese titles for common nodes, used before `allNodes()` resolves. */
    private val seedTitles: Map<String, String> = mapOf(
        "programmer" to "程序员", "python" to "Python", "linux" to "Linux", "create" to "分享创造",
        "apple" to "Apple", "qna" to "问与答", "jobs" to "职场话题", "life" to "生活",
    )

    fun displayName(nodeName: String, liveNodes: Map<String, String> = emptyMap()): String =
        liveNodes[nodeName] ?: seedTitles[nodeName] ?: nodeName
}
