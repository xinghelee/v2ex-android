package com.vibe.v2ex.data.remote

import com.vibe.v2ex.data.datastore.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

private const val BASE = "https://www.v2ex.com"

/**
 * Drives the parts of V2EX that only exist as HTML forms, not the JSON API: posting a
 * reply, favoriting a topic, following a node. Login itself happens in a WebView (see
 * feature/account/WebLoginScreen.kt) — mirrors the iOS app, which ships a working
 * scripted-login implementation in V2EXClient but never wires it into the UI, using
 * WKWebView instead since it handles CAPTCHA/2FA without the app reimplementing them.
 * Session identity lives entirely in the cookie jar (see NetworkModule); the password
 * is never involved here at all.
 */
@Singleton
class WebSessionService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val secureStore: SecureStore,
    private val cookieJar: PersistentCookieJar,
) {
    private suspend fun fetchDocument(url: String): Document = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            Jsoup.parse(response.body?.string().orEmpty(), url)
        }
    }

    private fun extractOnce(doc: Document): String? =
        doc.select("input[name=once]").firstOrNull()?.attr("value")
            ?: doc.select("a[href*=once=]").firstOrNull()?.attr("href")
                ?.substringAfter("once=")?.substringBefore("&")

    /** Valid iff HTTP 200 and the resolved response URL's path is exactly `/settings`. */
    suspend fun verifySession(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE/settings").build()
        okHttpClient.newCall(request).execute().use { response ->
            response.isSuccessful && response.request.url.encodedPath == "/settings"
        }
    }

    suspend fun postReply(topicId: Long, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val topicPage = fetchDocument("$BASE/t/$topicId")
            val once = extractOnce(topicPage) ?: error("missing once token")
            val body = FormBody.Builder().add("content", content).add("once", once).build()
            val request = Request.Builder().url("$BASE/t/$topicId").post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (text.contains("你上一次回复是在")) error("回复过于频繁，请稍后再试")
                if (!response.isSuccessful && !text.take(40).let { content.take(40) == it }) {
                    error("回复失败")
                }
            }
        }
    }

    suspend fun setFavoriteTopic(topicId: Long, favorited: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val topicPage = fetchDocument("$BASE/t/$topicId")
            val action = if (favorited) "favorite" else "unfavorite"
            val href = topicPage.select("a[href*=/$action/topic/$topicId]").firstOrNull()?.attr("href")
                ?: error("missing $action link")
            val request = Request.Builder().url(BASE + href).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("$action failed: ${response.code}")
            }
        }
    }

    suspend fun setFollowNode(nodeName: String, following: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val nodePage = fetchDocument("$BASE/go/$nodeName")
            val action = if (following) "favorite" else "unfavorite"
            val href = nodePage.select("a[href*=/$action/node/]").firstOrNull()?.attr("href")
                ?: error("missing $action link")
            val request = Request.Builder().url(BASE + href).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("$action node failed: ${response.code}")
            }
        }
    }

    /** Called after a successful WebView login — see WebLoginScreen. */
    fun saveWebSession(cookieHeader: String, username: String) {
        cookieJar.setCookieHeader(cookieHeader)
        secureStore.sessionUsername = username
    }

    fun signOutWebSession() {
        cookieJar.clear()
        secureStore.clearWebSession()
    }
}
