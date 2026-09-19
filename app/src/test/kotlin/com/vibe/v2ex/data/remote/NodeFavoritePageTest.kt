package com.vibe.v2ex.data.remote

import org.junit.Assert.*
import org.junit.Test

class NodeFavoritePageTest {
    private fun page(action: String, id: Long = 300): String =
        "<a href='/$action/node/$id?once=456'>${if (action == "favorite") "加入收藏" else "取消收藏"}</a>"

    @Test fun `reads current state and exact tokenized action`() {
        val unfollowed = WebsitePageParser.nodeFavoritePage(page("favorite"))!!
        assertFalse(unfollowed.following)
        assertEquals(300L, unfollowed.nodeId)
        assertEquals("/favorite/node/300?once=456", unfollowed.actionPath)
        val followed = WebsitePageParser.nodeFavoritePage(page("unfavorite"))!!
        assertTrue(followed.following)
        assertEquals("/unfavorite/node/300?once=456", followed.actionPath)
        assertEquals(unfollowed, WebsitePageParser.nodeFavoritePage(page("favorite") + page("favorite")))
    }

    @Test fun `accepts English labels and inline markup`() {
        assertNotNull(WebsitePageParser.nodeFavoritePage(page("favorite").replace("加入收藏", "<b>Favorite This Node</b>")))
        assertTrue(WebsitePageParser.nodeFavoritePage(page("unfavorite").replace("取消收藏", "Unfavorite"))!!.following)
    }

    @Test fun `rejects ambiguous missing and unrelated action links`() {
        val good = page("favorite")
        val invalid = listOf(
            "", "<a href='/signin'>登录</a>", "/favorite/node/300?once=456",
            good + page("unfavorite"), good + page("favorite", 301),
            good.replace("once=456", "once=123") + good,
            good.replace("加入收藏", "someone posted this"),
            good.replace("/favorite/node/", "https://evil.example/favorite/node/"),
            good.replace("/favorite/node/", "//v2ex.com/favorite/node/"),
            good.replace("/node/", "/topic/"), good.replace("?once=456", ""),
            good.replace("?once=456", "?once=456&redirect=evil"),
            good.replace("300", "0"), good.replace("300", "999999999999999999999999"),
        )
        invalid.forEach { assertNull(it, WebsitePageParser.nodeFavoritePage(it)) }
    }
}
