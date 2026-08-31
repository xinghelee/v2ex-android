package com.vibe.v2ex.data.remote

import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.model.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val BASE = "https://www.v2ex.com"

/** 网页收藏列表里的一行（/my/topics）。API 拿不到收藏，只能靠登录态网页。 */
data class ScrapedFavorite(
    val topicId: Long,
    val title: String,
    val nodeName: String,
    val nodeTitle: String,
    val authorName: String,
    val replies: Int,
)

/** 楼主附言（Supplement）。API 1.0/2.0 都不返回，只能从话题网页解析。 */
data class TopicAppend(val index: Int, val timeLabel: String, val contentHtml: String)

/** 话题页一次抓取能给出的所有额外信息：浏览数、附言、PRO 会员。 */
data class TopicPageExtras(
    val views: Int? = null,
    val appends: List<TopicAppend> = emptyList(),
    val proMembers: Set<String> = emptySet(),
)

/** A reply form and the reply rows that existed immediately before submitting it. */
private data class ReplyFormSnapshot(
    val once: String,
    val existingReplyIds: Set<Long>,
)

/** The response facts needed to distinguish a real topic page from an error or login redirect. */
private data class ReplyHttpPage(
    val code: Int,
    val finalPath: String,
    val finalFragment: String?,
    val html: String,
    val followedRedirect: Boolean,
)

/** One real V2EX reply row (`div#r_<id>.cell`), never a form/textarea echo. */
private data class ReplyDomRow(
    val id: Long,
    val floor: Int?,
    val author: String,
    val contentText: String,
    val contentUrls: Set<String>,
)

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

    /**
     * 用指定 cookie 试一次 `/settings`，不写入任何本地状态。null = 网络没给出结论。
     *
     * 网页登录不能只看页面长相：两步验证没走完时，页面顶部同样渲染登录态导航、
     * 也刮得到用户名，但那个 cookie 发不了帖也同步不了收藏。会话是否真的可用，
     * 一律以能否停在 `/settings` 为准。
     */
    suspend fun verifyCookie(cookieHeader: String): Boolean? = withContext(Dispatchers.IO) {
        // CookieJar 会用自己存的 cookie 覆盖掉手写的 Cookie 头，这一次请求单独关掉它。
        val client = okHttpClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
        val request = Request.Builder().url("$BASE/settings").header("Cookie", cookieHeader).build()
        runCatching {
            client.newCall(request).execute().use { response ->
                response.isSuccessful && response.request.url.encodedPath == "/settings"
            }
        }.getOrNull()
    }

    /** Valid iff HTTP 200 and the resolved response URL's path is exactly `/settings`. */
    suspend fun verifySession(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE/settings").build()
        okHttpClient.newCall(request).execute().use { response ->
            response.isSuccessful && response.request.url.encodedPath == "/settings"
        }
    }

    suspend fun postReply(topicId: Long, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = content.trim()
            require(trimmed.isNotEmpty()) { "回复内容不能为空" }

            var form = replyOnce(topicId)
            for (attempt in 0..1) {
                val body = FormBody.Builder()
                    .add("content", trimmed)
                    .add("once", form.once)
                    .build()
                val request = Request.Builder()
                    .url("$BASE/t/$topicId")
                    .header("Referer", "$BASE/t/$topicId")
                    .post(body)
                    .build()

                val page = executeReplyRequest(request)

                if (page.code == 403 && attempt == 0) {
                    // `once` is short lived. Fetch a fresh form and baseline,
                    // then retry exactly once; never replay in an unbounded loop.
                    form = replyOnce(topicId)
                    continue
                }

                if (page.finalPath == "/signin" || page.finalPath.startsWith("/2fa")) {
                    error("网页会话已失效，请重新登录 V2EX")
                }
                if ((page.code == 401 || page.code == 403) && !verifySession()) {
                    error("网页会话已失效，请重新登录 V2EX")
                }
                if (page.html.contains("你上一次回复是在")) {
                    error("回复过于频繁，V2EX 有回复间隔限制，请稍后再试")
                }
                val document = Jsoup.parse(page.html, "$BASE${page.finalPath}")
                document.selectFirst("div.problem")?.let { problem ->
                    error(problem.text().ifBlank { "回复被服务器拒绝，请稍后重试" })
                }
                if (hasCaptchaChallenge(document)) {
                    error("V2EX 要求验证码，请在网页中完成回复")
                }

                if (page.code == 200) {
                    // A rejected form may echo the draft in a textarea. Success
                    // therefore requires the POST redirect, the real topic page,
                    // and a newly-created `div#r_<id>` whose rendered semantics
                    // match the submission (plus author when the session has one).
                    val confirmed = page.followedRedirect &&
                        page.finalPath == "/t/$topicId" &&
                        confirmsNewReply(
                            document = document,
                            submitted = trimmed,
                            existingReplyIds = form.existingReplyIds,
                            expectedAuthor = secureStore.sessionUsername,
                            finalFragment = page.finalFragment,
                        )
                    if (confirmed) return@runCatching
                    error("回复结果未确认，请先到话题页确认；草稿已保留")
                }

                val detail = document.text().take(160).trim()
                error(detail.ifBlank { "回复失败（HTTP ${page.code}）" })
            }

            // Defensive fall-through: the loop otherwise always confirms or throws.
            if (!verifySession()) error("网页会话已失效，请重新登录 V2EX")
            error("回复被服务器拒绝，请稍后重试")
        }
    }

    /**
     * Reads the actual reply form and snapshots the newest reply page before a
     * POST. Long discussions render 100 replies per web page, so the final page
     * is fetched first; otherwise every old row on that page would look "new"
     * after V2EX redirects a successful POST there.
     */
    private suspend fun replyOnce(topicId: Long): ReplyFormSnapshot {
        var page = executeReplyRequest(Request.Builder().url("$BASE/t/$topicId").build())
        var document = validateReplyPage(topicId, page)

        val lastPage = lastReplyPage(document)
        if (lastPage > 1) {
            page = executeReplyRequest(Request.Builder().url("$BASE/t/$topicId?p=$lastPage").build())
            document = validateReplyPage(topicId, page)
        }

        val once = extractReplyOnce(document, topicId)
        if (once.isNullOrBlank()) {
            // A valid session can still have no form when the topic is closed,
            // restricted, or the account lacks permission. Do not call all of
            // those cases "session expired".
            if (!verifySession()) error("网页会话已失效，请重新登录 V2EX")
            error("当前话题不可回复，可能已关闭回复或账号没有权限")
        }

        return ReplyFormSnapshot(
            once = once,
            existingReplyIds = replyRows(document).mapTo(mutableSetOf()) { it.id },
        )
    }

    private fun executeReplyRequest(request: Request): ReplyHttpPage =
        okHttpClient.newCall(request).execute().use { response ->
            var prior = response.priorResponse
            var followedRedirect = false
            while (prior != null) {
                if (prior.code in 300..399) followedRedirect = true
                prior = prior.priorResponse
            }
            ReplyHttpPage(
                code = response.code,
                finalPath = response.request.url.encodedPath,
                finalFragment = response.request.url.fragment,
                html = response.body.string(),
                followedRedirect = followedRedirect,
            )
        }

    /** Validates transport/redirect/problem state without guessing from a missing once token. */
    private suspend fun validateReplyPage(topicId: Long, page: ReplyHttpPage): Document {
        if (page.finalPath == "/signin" || page.finalPath.startsWith("/2fa")) {
            error("网页会话已失效，请重新登录 V2EX")
        }
        if (page.code >= 500) {
            error("V2EX 服务暂时不可用（HTTP ${page.code}），请稍后重试")
        }
        if ((page.code == 401 || page.code == 403) && !verifySession()) {
            error("网页会话已失效，请重新登录 V2EX")
        }

        val document = Jsoup.parse(page.html, "$BASE${page.finalPath}")
        document.selectFirst("div.problem")?.let { problem ->
            error(problem.text().ifBlank { "当前话题不可回复" })
        }
        if (hasCaptchaChallenge(document)) {
            error("V2EX 要求验证码，请在网页中完成回复")
        }
        if (page.code !in 200..299) {
            error("无法读取回复表单（HTTP ${page.code}），请稍后重试")
        }
        if (page.finalPath != "/t/$topicId") {
            if (!verifySession()) error("网页会话已失效，请重新登录 V2EX")
            error("回复页面返回异常，请稍后重试")
        }
        return document
    }

    /** The reply form's once, deliberately scoped so favorite links cannot masquerade as it. */
    private fun extractReplyOnce(document: Document, topicId: Long): String? {
        val form = document.select("form").firstOrNull { candidate ->
            val action = candidate.attr("action").substringBefore('?').trimEnd('/')
            candidate.selectFirst("textarea[name=content]") != null &&
                (action.isBlank() || action == "/t/$topicId" || action == "$BASE/t/$topicId")
        }
        return form?.selectFirst("input[name=once]")?.attr("value")?.takeIf(String::isNotBlank)
    }

    private fun lastReplyPage(document: Document): Int =
        document.selectFirst("input.page_input[max]")
            ?.attr("max")
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: document.select("a[href]")
                .mapNotNull { PAGE_QUERY_REGEX.find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull() }
                .maxOrNull()
                ?.coerceAtLeast(1)
            ?: 1

    private fun replyRows(document: Document): List<ReplyDomRow> =
        document.select("div[id^=r_].cell").mapNotNull { row ->
            val id = row.id().removePrefix("r_").toLongOrNull() ?: return@mapNotNull null
            val content = row.selectFirst(".reply_content") ?: return@mapNotNull null
            ReplyDomRow(
                id = id,
                floor = row.selectFirst("span.no")?.text()?.trim()?.toIntOrNull(),
                author = row.selectFirst("strong > a[href^=/member/]")?.text().orEmpty(),
                contentText = content.text(),
                contentUrls = content.select("a[href], img[src]").flatMapTo(mutableSetOf()) { element ->
                    listOfNotNull(
                        element.attr("href").takeIf(String::isNotBlank),
                        element.attr("src").takeIf(String::isNotBlank),
                    )
                },
            )
        }

    private fun confirmsNewReply(
        document: Document,
        submitted: String,
        existingReplyIds: Set<Long>,
        expectedAuthor: String?,
        finalFragment: String?,
    ): Boolean {
        val expectedText = semanticReplyText(submitted, markdownSource = true)
        val expectedUrls = URL_REGEX.findAll(submitted).map { it.value.trimEnd('.', ',', ';') }.toSet()
        val expectedFloor = finalFragment
            ?.let { REPLY_FRAGMENT_REGEX.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        var candidates = replyRows(document).filter { it.id !in existingReplyIds }

        // V2EX normally redirects to `#replyN`. Treat it as a narrowing hint,
        // but do not fail solely because a future template changes its meaning.
        expectedFloor?.let { floor ->
            candidates.filter { it.floor == floor }.takeIf { it.isNotEmpty() }?.let { candidates = it }
        }
        expectedAuthor?.trim()?.takeIf(String::isNotEmpty)?.let { username ->
            candidates = candidates.filter { it.author.equals(username, ignoreCase = true) }
        }

        return candidates.any { row ->
            val actualText = semanticReplyText(row.contentText, markdownSource = false)
            when {
                expectedText.isNotEmpty() -> actualText == expectedText
                expectedUrls.isNotEmpty() -> expectedUrls.all { expected ->
                    row.contentUrls.any { actual -> actual.contains(expected) || expected.contains(actual) }
                }
                else -> false
            }
        }
    }

    /**
     * Compares user Markdown with the rendered reply's visible text. This is a
     * deliberately small semantic normalizer (emphasis, links, lists, quotes,
     * code fences and whitespace), not an HTML-page substring probe.
     */
    private fun semanticReplyText(value: String, markdownSource: Boolean): String {
        var text = value
        if (markdownSource) {
            text = MARKDOWN_IMAGE_REGEX.replace(text, "")
            text = MARKDOWN_LINK_REGEX.replace(text) { it.groupValues[1] }
            text = MARKDOWN_AUTOLINK_REGEX.replace(text) { it.groupValues[1] }
            text = MARKDOWN_FENCE_REGEX.replace(text, "")
            text = MARKDOWN_LINE_PREFIX_REGEX.replace(text, "")
        }
        text = Jsoup.parse(text).text()
        text = MARKDOWN_CONTROL_REGEX.replace(text, "")
        return WHITESPACE_REGEX.replace(text.replace('\u00a0', ' '), " ").trim()
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

    /**
     * 网页版「最热」（GET /?tab=hot）。官方 `/api/topics/hot.json` 服务端硬性只返回
     * 10 条且没有分页参数，网页同一时刻有 30+ 条 —— 用户对着网页看会觉得 App 少了
     * 一大截。这一页未登录也拿得全，所以不需要会话（mirrors iOS hotTopicsFromWeb）。
     */
    suspend fun hotTopics(): List<Topic> = withContext(Dispatchers.IO) {
        parseTopicRows(fetchDocument("$BASE/?tab=hot"))
    }

    /**
     * 网页版 R2（GET /?tab=r2）。R2 是网页端按投票算出来的首页排序，不是节点：
     * v1 的 latest/hot 接口会忽略 `tab` 参数，v2 也没有 tabs 接口，只能读网页
     * （mirrors iOS r2Topics）。未登录同样拿得到。
     */
    suspend fun r2Topics(): List<Topic> = withContext(Dispatchers.IO) {
        // R2 页面会被 CDN 短暂缓存，带个独立查询参数，下拉刷新才能拿到新排序。
        parseTopicRows(fetchDocument("$BASE/?tab=r2&_=${System.currentTimeMillis()}"))
    }

    /**
     * 解析首页/节点页的话题行。一行的结构：
     * ```
     * <div class="cell item"><table><tr>
     *   <td><a href="/member/foo"><img class="avatar" src="…_normal.png"></a></td>
     *   <td><span class="item_title"><a href="/t/123#reply22" class="topic-link">标题</a></span>
     *     <span class="topic_info"><a class="node" href="/go/career">职场话题</a> •
     *       <strong><a href="/member/foo">foo</a></strong> •
     *       <span title="2026-08-31 11:30:18 +08:00">6 mins ago</span> •
     *       Lastly replied by <strong><a href="/member/bar">bar</a></strong></span></td>
     *   <td><a class="count_livid">22</a></td>
     * </tr></table></div>
     * ```
     * 作者和「最后回复者」用的是同一种 `<strong><a href="/member/…">` 标记，只有按
     * 文档顺序取第一个才不会认错人。时间取 `title` 里的绝对时间戳而不是「6 mins ago」
     * 文案 —— 后者随登录语言在中英文之间切换，解析出来也只是个近似值。
     * 正文网页列表不提供，[Topic.content] 留空，只影响首页大卡片的摘要行。
     */
    private fun parseTopicRows(doc: Document): List<Topic> =
        doc.select("div.cell.item").mapNotNull { cell ->
            val link = cell.selectFirst("span.item_title > a.topic-link") ?: return@mapNotNull null
            val id = TOPIC_ID_REGEX.find(link.attr("href"))?.groupValues?.get(1)?.toLongOrNull()
                ?: return@mapNotNull null
            val avatar = cell.selectFirst("img.avatar")?.attr("src")?.takeIf(String::isNotBlank)
            val author = cell.selectFirst("strong > a[href^=/member/]")?.text().orEmpty()
            Topic(
                id = id,
                title = link.text(),
                url = "$BASE/t/$id",
                replies = cell.selectFirst("a[class^=count_]")?.text()?.toIntOrNull() ?: 0,
                lastTouched = cell.select("span[title]").firstNotNullOfOrNull { absoluteTime(it) },
                node = cell.selectFirst("a.node")?.let { node ->
                    Node(name = node.attr("href").substringAfterLast('/'), title = node.text())
                },
                member = author.takeIf(String::isNotEmpty)?.let {
                    Member(username = it, avatarNormal = avatar, avatarLarge = upscaledAvatar(avatar))
                },
            )
        }.distinctBy { it.id }

    /** `title="2026-08-31 11:30:18 +08:00"` → 秒级时间戳；认不出返回 null，行上不显示时间而不是显示一个错的。 */
    private fun absoluteTime(span: Element): Long? = runCatching {
        OffsetDateTime.parse(span.attr("title").trim(), WEB_TIME_FORMAT).toEpochSecond()
    }.getOrNull()

    /** 列表 HTML 给的是 48px 小头像，行内 34dp 方块在 3× 屏上要 ~100px 才不糊。 */
    private fun upscaledAvatar(url: String?): String? = url
        ?.replace("_normal.", "_large.")
        ?.replace(GRAVATAR_SIZE_REGEX, "$1" + "73")

    /**
     * 网页收藏列表：GET /my/topics 分页拉全（每页 20 条，最多 [maxPages] 页）。
     * 未登录会被重定向到 /signin，此时抛错让调用方静默跳过。
     */
    suspend fun favoriteTopics(maxPages: Int = 10): Result<List<ScrapedFavorite>> = withContext(Dispatchers.IO) {
        runCatching {
            val all = mutableListOf<ScrapedFavorite>()
            for (page in 1..maxPages.coerceIn(1, 10)) {
                val doc = fetchDocument(if (page == 1) "$BASE/my/topics" else "$BASE/my/topics?p=$page")
                val rows = doc.select("span.item_title > a.topic-link")
                if (rows.isEmpty()) {
                    if (page == 1 && doc.select("a[href*=/signin]").isNotEmpty()) error("未登录")
                    break
                }
                for (link in rows) {
                    val id = Regex("""/t/(\d+)""").find(link.attr("href"))?.groupValues?.get(1)?.toLongOrNull()
                        ?: continue
                    val cell = link.closest(".cell")
                    val nodeLink = cell?.selectFirst("a.node")
                    val author = cell?.selectFirst("strong > a")?.text().orEmpty()
                    val replies = cell?.selectFirst("a.count_livid, a.count_orange, a.count_gray")
                        ?.text()?.toIntOrNull() ?: 0
                    all += ScrapedFavorite(
                        topicId = id,
                        title = link.text(),
                        nodeName = nodeLink?.attr("href")?.substringAfterLast('/').orEmpty(),
                        nodeTitle = nodeLink?.text().orEmpty(),
                        authorName = author,
                        replies = replies,
                    )
                }
                if (rows.size < 20) break // 不满一页 = 最后一页
            }
            all
        }
    }

    /** 网页「我收藏的节点」（/my/nodes）— API 2.0 没有关注节点接口。 */
    suspend fun favoriteNodeNames(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument("$BASE/my/nodes")
            if (doc.select("a[href*=/signin]").isNotEmpty() && doc.select("a[href^=/go/]").isEmpty()) error("未登录")
            doc.select("a[href^=/go/]")
                .mapNotNull { Regex("""^/go/([a-zA-Z0-9_-]+)$""").find(it.attr("href"))?.groupValues?.get(1) }
                .distinct()
        }
    }

    /**
     * 一次抓取话题页，同时解析浏览数、附言与 PRO 徽章 —— 三者都只存在于网页，
     * 分开请求会浪费整次网页往返。
     */
    suspend fun topicPageExtras(topicId: Long): TopicPageExtras = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$BASE/t/$topicId").build()
            val html = okHttpClient.newCall(request).execute().use { it.body?.string().orEmpty() }
            TopicPageExtras(
                views = Regex("""([\d,]+)\s*(?:views|次点击)""").find(html)
                    ?.groupValues?.get(1)?.filter(Char::isDigit)?.toIntOrNull(),
                appends = extractAppends(html),
                proMembers = extractProMembers(html),
            )
        }.getOrDefault(TopicPageExtras())
    }

    /**
     * 解析附言块。新版结构：`<div class="subtle"><span class="fade">第 1 条附言 · 时间</span>
     * <div class="topic_content">…</div></div>`；旧帖仍可能是 topic_append 结构。
     */
    private fun extractAppends(html: String): List<TopicAppend> {
        val result = mutableListOf<TopicAppend>()
        val supplementPattern = Regex(
            """<div class="subtle">[\s\S]*?<span class="fade">[^<]*?(\d+)[^<]*?·\s*([^<]+)</span>[\s\S]*?<div class="topic_content">([\s\S]*?)</div>""",
        )
        for (match in supplementPattern.findAll(html)) {
            val content = match.groupValues[3].trim()
            if (content.isEmpty()) continue
            result += TopicAppend(
                index = match.groupValues[1].toIntOrNull() ?: (result.size + 1),
                timeLabel = match.groupValues[2].replace("&nbsp;", "").trim(),
                contentHtml = content,
            )
        }
        val legacyPattern = Regex(
            """<div class="topic_append">[\s\S]*?<span class="time">([^<]+)</span>[\s\S]*?<div class="topic_append_content">([\s\S]*?)</div>""",
        )
        for (match in legacyPattern.findAll(html)) {
            result += TopicAppend(
                index = result.size + 1,
                timeLabel = match.groupValues[1].trim(),
                contentHtml = match.groupValues[2].trim(),
            )
        }
        return result
    }

    /**
     * 本页佩戴 PRO 徽章的用户名。徽章容器紧跟在作者自己的 /member/ 链接后面，
     * 两组匹配都按文档顺序返回，单向游走即可配对。
     */
    private fun extractProMembers(html: String): Set<String> {
        val members = Regex("""/member/([A-Za-z0-9_-]+)""").findAll(html).toList()
        if (members.isEmpty()) return emptySet()
        val badges = Regex("""<div class="badge pro">""").findAll(html).toList()
        val found = mutableSetOf<String>()
        var cursor = 0
        for (badge in badges) {
            while (cursor + 1 < members.size && members[cursor + 1].range.first < badge.range.first) cursor++
            if (members[cursor].range.first < badge.range.first) found += members[cursor].groupValues[1]
        }
        return found
    }

    /**
     * 发布新主题并返回其 id。API 没有写端点，走网页 /write 表单（once + POST）。
     * 成功只认落在真实 /t/<id> 的重定向；response body 回显草稿的拒绝页绝不能当成功。
     * 无法确认时向 API 查询「我最近的话题」兜底，避免误报失败导致重复发帖。
     */
    suspend fun createTopic(
        title: String,
        content: String,
        nodeName: String,
        recentTopicIdByTitle: suspend (String) -> Long?,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedTitle = title.trim()
            require(trimmedTitle.isNotEmpty()) { "标题不能为空" }
            require(nodeName.isNotEmpty()) { "请先选择节点" }

            val formPath = "$BASE/write?node=$nodeName"
            val form = fetchDocument(formPath)
            if (mentionsCaptcha(form.html())) error("V2EX 要求验证码，这一步只能在网页完成")
            val once = extractOnce(form) ?: error("会话可能已失效，请重新登录")

            val body = FormBody.Builder()
                .add("title", trimmedTitle)
                .add("content", content)
                .add("node_name", nodeName)
                .add("syntax", "markdown")
                .add("once", once)
                .build()
            val request = Request.Builder()
                .url("$BASE/write")
                .header("Referer", formPath)
                .post(body)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                // 快路径：重定向直接落到了新帖。
                val finalPath = response.request.url.encodedPath
                Regex("""^/t/(\d+)$""").find(finalPath)?.groupValues?.get(1)?.toLongOrNull()?.let { return@use it }

                val html = response.body?.string().orEmpty()
                Jsoup.parse(html).selectFirst("div.problem")?.let { problem ->
                    error(problem.text().ifBlank { "发布被拒绝，请稍后重试" })
                }
                if (mentionsCaptcha(html)) error("V2EX 要求验证码，这一步只能在网页完成")

                // 没有重定向也没有错误页 —— 问 API 帖子是否已存在，避免误报失败。
                for (attempt in 0 until 3) {
                    if (attempt > 0) delay(2_000)
                    recentTopicIdByTitle(trimmedTitle)?.let { return@use it }
                }
                error("发布结果未确认。请到网页查看是否已发出，避免重复发送。")
            }
        }
    }

    // Kept broad for the standalone /write flow, whose page contains no user discussion text.
    private fun mentionsCaptcha(html: String): Boolean = html.contains("captcha") || html.contains("验证码")

    /** Scoped to challenge/form markup so a normal reply discussing "captcha/验证码" is not a false hit. */
    private fun hasCaptchaChallenge(document: Document): Boolean =
        document.select(
            "input[name*=captcha], input[id*=captcha], img[src*=captcha], " +
                "[class*=captcha], [id*=captcha]",
        ).isNotEmpty() || document.select("form").any { form ->
            form.text().contains("验证码") && form.select("input[name=code]").isNotEmpty()
        }

    /** Called after a successful WebView login — see WebLoginScreen. */
    fun saveWebSession(cookieHeader: String, username: String) {
        // 新登录整体替换会话，不与旧 cookie 合并 —— 混入过期条目会让后续网页请求被拒。
        cookieJar.clear()
        cookieJar.setCookieHeader(cookieHeader)
        secureStore.sessionUsername = username
        secureStore.webLoggedIn = true
    }

    fun signOutWebSession() {
        cookieJar.clear()
        secureStore.clearWebSession()
    }

    private companion object {
        val TOPIC_ID_REGEX = Regex("""/t/(\d+)""")
        val GRAVATAR_SIZE_REGEX = Regex("""([?&]s=)\d+""")
        val PAGE_QUERY_REGEX = Regex("""[?&]p=(\d+)""")
        val REPLY_FRAGMENT_REGEX = Regex("""reply(\d+)""", RegexOption.IGNORE_CASE)
        val URL_REGEX = Regex("""https?://[^\s<>()]+""", RegexOption.IGNORE_CASE)
        val MARKDOWN_IMAGE_REGEX = Regex("""!\[[^]]*]\([^\n)]+\)""")
        val MARKDOWN_LINK_REGEX = Regex("""\[([^]]+)]\([^\n)]+\)""")
        val MARKDOWN_AUTOLINK_REGEX = Regex(
            """<((?:https?://|mailto:)[^>]+)>""",
            RegexOption.IGNORE_CASE,
        )
        val MARKDOWN_FENCE_REGEX = Regex("""(?m)^\s*```[^\n]*$""")
        val MARKDOWN_LINE_PREFIX_REGEX = Regex(
            """(?m)^\s{0,3}(?:#{1,6}\s+|>\s?|[-+*]\s+|\d+[.)]\s+)""",
        )
        val MARKDOWN_CONTROL_REGEX = Regex("""[*_~`]""")
        val WHITESPACE_REGEX = Regex("""\s+""")
        val WEB_TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.US)
    }
}
