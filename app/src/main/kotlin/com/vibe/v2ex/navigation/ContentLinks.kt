package com.vibe.v2ex.navigation

import java.net.URI
import java.util.Locale

private val V2EX_WEB_HOSTS = setOf("v2ex.com", "www.v2ex.com", "global.v2ex.com", "origin.v2ex.com", "edge.v2ex.com")
private val TOPIC_PATH = Regex("""^/t/([0-9]+)(?:\.html)?/?$""")
private val MEMBER_PATH = Regex("""^/member/([A-Za-z0-9_]+)/?$""")
private val REPLY_FRAGMENT = Regex("^reply([0-9]+)$", RegexOption.IGNORE_CASE)

/** Only whole V2EX website paths are native routes; unrelated links stay with the browser. */
internal fun contentRouteForUrl(value: String): Route? {
    val uri = websiteUri(value) ?: return null
    return uri.topicRoute() ?: MEMBER_PATH.matchEntire(uri.rawPath.orEmpty())
        ?.groupValues?.get(1)?.let { Route.Member(it) }
}

internal fun topicRouteForUrl(value: String): Route.Topic? = websiteUri(value)?.topicRoute()

private fun URI.topicRoute(): Route.Topic? {
    val id = TOPIC_PATH.matchEntire(rawPath.orEmpty())?.groupValues?.get(1)
        ?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val floor = REPLY_FRAGMENT.matchEntire(fragment.orEmpty())?.groupValues?.get(1)
        ?.toIntOrNull()?.takeIf { it > 0 }
    return Route.Topic(id, initialFloor = floor)
}

private fun websiteUri(value: String): URI? = runCatching {
    val raw = value.trim()
    val absolute = when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith('/') -> "https://www.v2ex.com$raw"
        else -> raw
    }
    val uri = URI(absolute)
    val scheme = uri.scheme?.lowercase(Locale.US)
    if (scheme !in setOf("http", "https") || uri.host?.lowercase(Locale.US) !in V2EX_WEB_HOSTS ||
        uri.rawUserInfo != null || uri.port !in setOf(-1, if (scheme == "https") 443 else 80)
    ) return@runCatching null
    uri
}.getOrNull()
