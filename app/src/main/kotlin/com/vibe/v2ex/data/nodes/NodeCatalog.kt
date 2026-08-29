package com.vibe.v2ex.data.nodes

/**
 * V2EX's `nodes/all.json` has no category field, so the site's own category tabs
 * are reproduced here — node-name lists copied 1:1 from the iOS NodeCatalog.swift.
 * `icon` names a Material icon id the UI layer maps to an ImageVector; kept as a
 * string here to keep this file UI-framework-free.
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
            nodeNames = listOf(
                "create", "design", "ideas", "sandbox", "career", "startup",
                "sspai", "share", "writing", "productivity",
            ),
        ),
        NodeCategory(
            title = "生活",
            icon = "coffee",
            nodeNames = listOf(
                "life", "coffee", "shanghai", "beijing", "shenzhen", "guangzhou",
                "hangzhou", "chengdu", "pets", "cat", "dog", "food", "health",
                "fitness", "travel", "car", "bicycle", "invest", "career",
            ),
        ),
        NodeCategory(
            title = "好玩",
            icon = "games",
            nodeNames = listOf(
                "share", "games", "movie", "music", "tv", "anime", "books",
                "reading", "photograph", "nintendo", "steam", "playstation",
            ),
        ),
        NodeCategory(
            title = "Apple",
            icon = "apple",
            nodeNames = listOf(
                "apple", "macos", "iphone", "ipad", "macbookpro", "watchos",
                "visionpro", "appstore", "icloud", "airpods",
            ),
        ),
        NodeCategory(
            title = "硬件与自建",
            icon = "storage",
            nodeNames = listOf(
                "nas", "hardware", "diy", "router", "keyboard", "monitor",
                "raspberrypi", "homelab", "vps", "domain", "idc",
            ),
        ),
        NodeCategory(
            title = "酷工作",
            icon = "work",
            nodeNames = listOf("jobs", "career", "outsourcing", "internship", "remotework"),
        ),
        NodeCategory(
            title = "交易",
            icon = "sell",
            nodeNames = listOf("all4all", "exchange", "free", "dn", "tuan", "promotions"),
        ),
        NodeCategory(
            title = "问与答",
            icon = "help",
            nodeNames = listOf("qna", "howto", "search", "opensource"),
        ),
    )

    /** Small bootstrap dict of Chinese titles for common nodes, used before `allNodes()` resolves. */
    private val seedTitles: Map<String, String> = mapOf(
        "programmer" to "程序员", "python" to "Python", "linux" to "Linux", "create" to "分享创造",
        "apple" to "Apple", "qna" to "问与答", "jobs" to "酷工作", "life" to "生活",
        "coffee" to "咖啡", "autistic" to "自言自语", "share" to "分享发现", "nas" to "NAS",
        "macos" to "macOS", "iphone" to "iPhone", "ipad" to "iPad", "games" to "游戏",
        "movie" to "电影", "music" to "音乐", "design" to "设计", "career" to "职场话题",
        "shanghai" to "上海", "beijing" to "北京", "hardware" to "硬件", "cat" to "猫",
        "invest" to "投资", "ideas" to "奇思妙想", "all4all" to "二手交易", "health" to "健康",
    )

    fun displayName(nodeName: String, liveNodes: Map<String, String> = emptyMap()): String =
        liveNodes[nodeName] ?: seedTitles[nodeName] ?: nodeName
}
