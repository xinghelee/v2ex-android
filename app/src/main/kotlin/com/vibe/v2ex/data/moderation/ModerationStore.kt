package com.vibe.v2ex.data.moderation

import android.content.Context
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.local.HiddenReplyEntity
import com.vibe.v2ex.data.local.HiddenTopicEntity
import com.vibe.v2ex.data.local.ModerationVisibilityDao
import com.vibe.v2ex.data.local.ReportDao
import com.vibe.v2ex.data.local.ReportEntity
import com.vibe.v2ex.data.model.Reply
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.remote.WebSessionService
import com.vibe.v2ex.data.remote.WebsiteBlockMutation
import com.vibe.v2ex.data.remote.WebsiteBlockSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ReportReason(val slug: String, val title: String) {
    SPAM("spam", "垃圾信息或广告"),
    HARASSMENT("harassment", "骚扰、辱骂或人身攻击"),
    HATE("hate", "仇恨言论或歧视"),
    SEXUAL("sexual", "色情或性暗示内容"),
    VIOLENCE("violence", "暴力、血腥或自残"),
    ILLEGAL("illegal", "违法或欺诈内容"),
    PRIVACY("privacy", "泄露他人隐私"),
    OTHER("other", "其他"),
}

enum class ReportTargetType(val slug: String) { TOPIC("topic"), REPLY("reply"), MEMBER("member") }

data class WebsiteModerationState(
    val accountName: String? = null,
    val isWebSessionActive: Boolean = false,
    val isSyncing: Boolean = false,
    val actionUsername: String? = null,
    val message: String? = null,
)

private data class WebsiteIdentity(
    val accountName: String,
    val accountKey: String,
    val cookieHeader: String,
)

/**
 * Moderation has two deliberately separate persistence domains:
 *
 * - V2EX's official, account-scoped member block list is cached in SharedPreferences and is
 *   replaced only after a complete, verified website request.
 * - locally hidden topics/replies and the report outbox remain in Room.
 *
 * The pre-1.3 local username/keyword tables are intentionally not read or migrated. Importing
 * those rows would silently mutate a user's website account, which is never a safe migration.
 */
@Singleton
class ModerationStore @Inject constructor(
    @ApplicationContext context: Context,
    private val secureStore: SecureStore,
    private val webSessionService: WebSessionService,
    private val visibilityDao: ModerationVisibilityDao,
    private val reportDao: ReportDao,
    private val reportService: ReportService,
) {
    private val websiteCache = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
    private val websiteMutex = Mutex()
    private val reportMutex = Mutex()
    private val flushMutex = Mutex()
    private val identityLock = Any()
    /**
     * Website mutations belong to the account store rather than a screen. A member/settings
     * ViewModel may disappear while the server request is already in flight; keeping the work in
     * this singleton scope lets confirmation, cache publication and the report outbox finish as
     * one durable operation.
     */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _blockedUsernames = MutableStateFlow<List<String>>(emptyList())
    val blockedUsernames: StateFlow<List<String>> = _blockedUsernames

    private val _unavailableBlockedMemberIds = MutableStateFlow<List<Long>>(emptyList())
    val unavailableBlockedMemberIds: StateFlow<List<Long>> = _unavailableBlockedMemberIds

    /** Kept as a compatibility flow for call sites while local keyword filtering is retired. */
    val blockedKeywords: StateFlow<List<String>> = MutableStateFlow(emptyList())

    private val _websiteState = MutableStateFlow(WebsiteModerationState())
    val websiteState: StateFlow<WebsiteModerationState> = _websiteState

    val hiddenTopicIds: Flow<List<Long>> = visibilityDao.observeHiddenTopicIds()
    val hiddenReplyIds: Flow<List<Long>> = visibilityDao.observeHiddenReplyIds()

    val moderationCount: Flow<Int> = combine(
        blockedUsernames,
        unavailableBlockedMemberIds,
        hiddenTopicIds,
        hiddenReplyIds,
    ) { usernames, unavailableIds, topics, replies ->
        usernames.size + unavailableIds.size + topics.size + replies.size
    }

    private var selectedIdentity: WebsiteIdentity? = null
    private var identityRevision = 0L

    init {
        syncSessionIdentity()
    }

    /**
     * Selects the cache belonging to the current website account. This is synchronous so logout
     * and account switching stop old rules from filtering content before any network request runs.
     */
    fun syncSessionIdentity() {
        val next = secureStore.websiteIdentity()
        synchronized(identityLock) {
            if (next == selectedIdentity) return
            selectedIdentity = next
            identityRevision += 1
            val snapshot = next?.let(::cachedSnapshot) ?: WebsiteBlockSnapshot(emptyList(), emptyList())
            _blockedUsernames.value = snapshot.usernames.normalizedUsernames()
            _unavailableBlockedMemberIds.value = snapshot.unavailableMemberIds.normalizedIds()
            _websiteState.value = WebsiteModerationState(
                accountName = next?.accountName,
                isWebSessionActive = next != null,
            )
        }
    }

    suspend fun refreshWebsiteBlocks(): Result<Unit> {
        syncSessionIdentity()
        val request = identityRequestOrNull() ?: return Result.failure(
            IllegalStateException("请先登录 V2EX 网页账号再同步屏蔽名单"),
        )
        return websiteMutex.withLock {
            if (!isCurrent(request)) {
                return@withLock Result.failure(IllegalStateException("账号状态已变化，已取消旧账号的同步"))
            }
            updateWebsiteState(request.identity, isSyncing = true, message = null)

            val result = try {
                webSessionService.blockedUsers(request.identity.cookieHeader)
            } catch (error: CancellationException) {
                finishCancellationIfCurrent(request)
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
            result
                .fold(
                    onSuccess = { snapshot ->
                        if (!applySnapshotIfCurrent(request, snapshot)) {
                            Result.failure(IllegalStateException("账号状态已变化，已忽略旧账号的同步结果"))
                        } else {
                            Result.success(Unit)
                        }
                    },
                    onFailure = { error ->
                        finishWithErrorIfCurrent(request, error)
                        Result.failure(error)
                    },
                )
        }
    }

    fun consumeWebsiteMessage() {
        synchronized(identityLock) {
            _websiteState.value = _websiteState.value.copy(message = null)
        }
    }

    fun isTopicHidden(
        topic: Topic,
        hiddenIds: List<Long>,
        blockedUsers: List<String>,
        keywords: List<String> = emptyList(),
        blockedMemberIds: List<Long> = emptyList(),
    ): Boolean =
        hiddenIds.contains(topic.id) ||
            topic.member?.id?.let(blockedMemberIds::contains) == true ||
            isBlocked(topic.authorName, blockedUsers) ||
            matchesKeyword("${topic.title} ${topic.content.orEmpty()}", keywords)

    fun isReplyHidden(
        reply: Reply,
        hiddenIds: List<Long>,
        blockedUsers: List<String>,
        keywords: List<String> = emptyList(),
        blockedMemberIds: List<Long> = emptyList(),
    ): Boolean =
        hiddenIds.contains(reply.id) ||
            (reply.memberId ?: reply.member?.id)?.let(blockedMemberIds::contains) == true ||
            isBlocked(reply.authorName, blockedUsers) ||
            matchesKeyword(reply.content, keywords)

    private fun isBlocked(username: String, blockedUsers: List<String>): Boolean =
        username.isNotBlank() && blockedUsers.any { it.equals(username, ignoreCase = true) }

    private fun matchesKeyword(text: String, keywords: List<String>): Boolean =
        keywords.isNotEmpty() && keywords.any { text.contains(it, ignoreCase = true) }

    suspend fun blockUser(username: String): Result<Unit> {
        val mutation = prepareUserMutation(username, blocked = true).getOrElse {
            return Result.failure(it)
        }
        return backgroundScope.async {
            setUserBlocked(mutation).onSuccess { changed ->
                if (!changed) return@onSuccess
                enqueue(
                    ReportTargetType.MEMBER,
                    mutation.username,
                    topicId = null,
                    author = mutation.username,
                    excerpt = null,
                    reason = ReportReason.OTHER,
                    note = "用户屏蔽（从屏蔽名单添加）",
                    kind = "block",
                )
            }.map { Unit }
        }.await()
    }

    suspend fun unblockUser(username: String): Result<Unit> {
        val mutation = prepareUserMutation(username, blocked = false).getOrElse {
            return Result.failure(it)
        }
        return backgroundScope.async { setUserBlocked(mutation).map { Unit } }.await()
    }

    private data class PreparedUserMutation(
        val request: IdentityRequest,
        val username: String,
        val blocked: Boolean,
    )

    /** Captures the initiating account before any mutex wait or background dispatch. */
    private fun prepareUserMutation(rawUsername: String, blocked: Boolean): Result<PreparedUserMutation> {
        syncSessionIdentity()
        val username = rawUsername.trim().removePrefix("@")
        if (!USERNAME_REGEX.matches(username)) {
            val failure = IllegalArgumentException("请输入有效的 V2EX 用户名")
            exposeImmediateFailure(failure)
            return Result.failure(failure)
        }

        val request = identityRequestOrNull()
        if (request == null) {
            val failure = IllegalStateException("屏蔽用户需要先登录 V2EX 网页账号")
            exposeImmediateFailure(failure)
            return Result.failure(failure)
        }
        if (blocked && username.equals(request.identity.accountName, ignoreCase = true)) {
            val failure = IllegalArgumentException("不能屏蔽当前登录账号")
            exposeImmediateFailure(failure)
            return Result.failure(failure)
        }
        return Result.success(PreparedUserMutation(request, username, blocked))
    }

    private suspend fun setUserBlocked(mutation: PreparedUserMutation): Result<Boolean> {
        val request = mutation.request
        val username = mutation.username
        val blocked = mutation.blocked
        return websiteMutex.withLock {
            if (!isCurrent(request)) {
                return@withLock Result.failure(
                    IllegalStateException("账号状态已变化，已取消旧账号的屏蔽操作"),
                )
            }
            // Read inside the lock so a duplicate queued tap cannot enqueue a second block report.
            val wasBlocked = _blockedUsernames.value.any { it.equals(username, ignoreCase = true) }
            val changed = wasBlocked != blocked

            updateWebsiteState(
                request.identity,
                isSyncing = true,
                actionUsername = username,
                message = null,
            )
            val result = try {
                webSessionService.setMemberBlocked(
                    cookieHeader = request.identity.cookieHeader,
                    username = username,
                    blocked = blocked,
                )
            } catch (error: CancellationException) {
                finishCancellationIfCurrent(request)
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
            result.fold(
                onSuccess = { mutation ->
                    if (!applyMutationIfCurrent(request, mutation)) {
                        Result.failure(IllegalStateException("账号状态已变化，请回到原账号确认官网屏蔽状态"))
                    } else {
                        Result.success(changed)
                    }
                },
                onFailure = { error ->
                    finishWithErrorIfCurrent(request, error)
                    Result.failure(error)
                },
            )
        }
    }

    /** Restoring content also withdraws every local/pending report for that exact target. */
    suspend fun unhideTopic(topicId: Long) {
        reportMutex.withLock {
            visibilityDao.unhideTopic(topicId)
            reportDao.deleteForTarget(ReportTargetType.TOPIC.slug, topicId.toString())
        }
    }

    suspend fun unhideReply(replyId: Long) {
        reportMutex.withLock {
            visibilityDao.unhideReply(replyId)
            reportDao.deleteForTarget(ReportTargetType.REPLY.slug, replyId.toString())
        }
    }

    /** Hides immediately, then sends the report through the existing retryable outbox. */
    suspend fun reportTopic(topicId: Long, author: String?, excerpt: String?, reason: ReportReason, note: String?) {
        reportMutex.withLock {
            visibilityDao.hideTopic(HiddenTopicEntity(topicId, System.currentTimeMillis()))
            enqueueLocked(ReportTargetType.TOPIC, topicId.toString(), topicId, author, excerpt, reason, note, kind = "report")
        }
    }

    suspend fun reportReply(replyId: Long, topicId: Long?, author: String?, excerpt: String?, reason: ReportReason, note: String?) {
        reportMutex.withLock {
            visibilityDao.hideReply(HiddenReplyEntity(replyId, System.currentTimeMillis()))
            enqueueLocked(ReportTargetType.REPLY, replyId.toString(), topicId, author, excerpt, reason, note, kind = "report")
        }
    }

    /** A block report is created only after the website has authoritatively confirmed a new block. */
    suspend fun blockUserAndReport(
        username: String,
        targetType: ReportTargetType,
        targetId: String,
        topicId: Long?,
        excerpt: String?,
        reason: ReportReason = ReportReason.OTHER,
    ): Result<Unit> {
        val mutation = prepareUserMutation(username, blocked = true).getOrElse {
            return Result.failure(it)
        }
        return backgroundScope.async {
            setUserBlocked(mutation).onSuccess { changed ->
                if (!changed) return@onSuccess
                enqueue(
                    targetType = targetType,
                    targetId = targetId,
                    topicId = topicId,
                    author = mutation.username,
                    excerpt = excerpt,
                    reason = reason,
                    note = "用户屏蔽（由这条内容触发）",
                    kind = "block",
                )
            }.map { Unit }
        }.await()
    }

    private data class IdentityRequest(val identity: WebsiteIdentity, val revision: Long)

    private fun identityRequestOrNull(): IdentityRequest? = synchronized(identityLock) {
        selectedIdentity?.let { IdentityRequest(it, identityRevision) }
    }

    private fun isCurrent(request: IdentityRequest): Boolean = synchronized(identityLock) {
        selectedIdentity == request.identity && identityRevision == request.revision
    }

    private fun applySnapshotIfCurrent(request: IdentityRequest, snapshot: WebsiteBlockSnapshot): Boolean =
        synchronized(identityLock) {
            if (selectedIdentity != request.identity || identityRevision != request.revision) return@synchronized false
            val normalized = WebsiteBlockSnapshot(
                usernames = snapshot.usernames.normalizedUsernames(),
                unavailableMemberIds = snapshot.unavailableMemberIds.normalizedIds(),
            )
            persistSnapshot(request.identity.accountKey, normalized)
            _blockedUsernames.value = normalized.usernames
            _unavailableBlockedMemberIds.value = normalized.unavailableMemberIds
            _websiteState.value = WebsiteModerationState(
                accountName = request.identity.accountName,
                isWebSessionActive = true,
            )
            true
        }

    private fun applyMutationIfCurrent(request: IdentityRequest, mutation: WebsiteBlockMutation): Boolean =
        synchronized(identityLock) {
            if (selectedIdentity != request.identity || identityRevision != request.revision) return@synchronized false
            val names = _blockedUsernames.value
                .filterNot { it.equals(mutation.username, ignoreCase = true) }
                .toMutableList()
                .apply { if (mutation.blocked) add(mutation.username) }
                .normalizedUsernames()
            val unavailableIds = _unavailableBlockedMemberIds.value
                .filterNot { it == mutation.memberId }
                .normalizedIds()
            val snapshot = WebsiteBlockSnapshot(names, unavailableIds)
            persistSnapshot(request.identity.accountKey, snapshot)
            _blockedUsernames.value = names
            _unavailableBlockedMemberIds.value = unavailableIds
            _websiteState.value = WebsiteModerationState(
                accountName = request.identity.accountName,
                isWebSessionActive = true,
            )
            true
        }

    private fun finishWithErrorIfCurrent(request: IdentityRequest, error: Throwable) {
        synchronized(identityLock) {
            if (selectedIdentity != request.identity || identityRevision != request.revision) return
            _websiteState.value = _websiteState.value.copy(
                isSyncing = false,
                actionUsername = null,
                message = error.message ?: "官网屏蔽名单同步失败，请稍后重试",
            )
        }
    }

    private fun finishCancellationIfCurrent(request: IdentityRequest) {
        synchronized(identityLock) {
            if (selectedIdentity != request.identity || identityRevision != request.revision) return
            _websiteState.value = _websiteState.value.copy(
                isSyncing = false,
                actionUsername = null,
            )
        }
    }

    private fun exposeImmediateFailure(error: Throwable) {
        synchronized(identityLock) {
            _websiteState.value = _websiteState.value.copy(
                isSyncing = false,
                actionUsername = null,
                message = error.message,
            )
        }
    }

    private fun updateWebsiteState(
        identity: WebsiteIdentity,
        isSyncing: Boolean,
        actionUsername: String? = null,
        message: String? = null,
    ) {
        synchronized(identityLock) {
            if (selectedIdentity != identity) return
            _websiteState.value = WebsiteModerationState(
                accountName = identity.accountName,
                isWebSessionActive = true,
                isSyncing = isSyncing,
                actionUsername = actionUsername,
                message = message,
            )
        }
    }

    private fun cachedSnapshot(identity: WebsiteIdentity): WebsiteBlockSnapshot =
        cachedSnapshot(identity.accountKey)

    private fun cachedSnapshot(accountKey: String): WebsiteBlockSnapshot = WebsiteBlockSnapshot(
        usernames = websiteCache.getStringSet("$USERS_PREFIX$accountKey", emptySet()).orEmpty().toList(),
        unavailableMemberIds = websiteCache.getStringSet("$IDS_PREFIX$accountKey", emptySet())
            .orEmpty()
            .mapNotNull(String::toLongOrNull),
    )

    private fun persistSnapshot(accountKey: String, snapshot: WebsiteBlockSnapshot) {
        websiteCache.edit()
            .putStringSet("$USERS_PREFIX$accountKey", snapshot.usernames.toSet())
            .putStringSet("$IDS_PREFIX$accountKey", snapshot.unavailableMemberIds.map(Long::toString).toSet())
            .apply()
    }

    private fun SecureStore.websiteIdentity(): WebsiteIdentity? {
        if (!isWebSessionActive) return null
        val username = sessionUsername?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val cookie = sessionCookieHeader?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return WebsiteIdentity(
            accountName = username,
            accountKey = username.lowercase(Locale.ROOT),
            cookieHeader = cookie,
        )
    }

    private fun List<String>.normalizedUsernames(): List<String> =
        asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()

    private fun List<Long>.normalizedIds(): List<Long> = filter { it > 0 }.distinct().sorted()

    private suspend fun enqueue(
        targetType: ReportTargetType,
        targetId: String,
        topicId: Long?,
        author: String?,
        excerpt: String?,
        reason: ReportReason,
        note: String?,
        kind: String,
    ) = reportMutex.withLock {
        enqueueLocked(targetType, targetId, topicId, author, excerpt, reason, note, kind)
    }

    /** Caller owns [reportMutex], allowing hide/delete and outbox changes to stay ordered. */
    private suspend fun enqueueLocked(
        targetType: ReportTargetType,
        targetId: String,
        topicId: Long?,
        author: String?,
        excerpt: String?,
        reason: ReportReason,
        note: String?,
        kind: String,
    ) {
        val entity = ReportEntity(
            id = UUID.randomUUID().toString(),
            kind = kind,
            targetType = targetType.slug,
            targetId = targetId,
            topicId = topicId,
            author = author,
            excerpt = excerpt?.take(500),
            reason = reason.slug,
            reasonTitle = reason.title,
            note = note?.take(1000),
            createdAt = System.currentTimeMillis(),
            deliveredAt = null,
        )
        reportDao.upsert(entity)
        backgroundScope.launch { runCatching { flushPending() } }
    }

    /** Call on app launch and foreground resume; delivery remains retryable until confirmed. */
    suspend fun flushPending() {
        flushMutex.withLock {
            val pending = reportMutex.withLock { reportDao.pending() }
            pending.forEach { report ->
                if (reportService.submit(report)) {
                    // A concurrent restore may already have withdrawn this row; Room's update then
                    // affects zero rows, which is exactly the desired result.
                    reportMutex.withLock {
                        reportDao.update(report.copy(deliveredAt = System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    private companion object {
        const val CACHE_NAME = "website_moderation_cache"
        const val USERS_PREFIX = "users:"
        const val IDS_PREFIX = "ids:"
        val USERNAME_REGEX = Regex("^[A-Za-z0-9_]+$")
    }
}
