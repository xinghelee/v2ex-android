package com.vibe.v2ex.data.datastore

import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.data.remote.WebSessionService
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Opaque account/version key carried with a page-level unread result; never log its fields. */
internal data class NotificationCredentialKey(
    val token: String,
    val cookieHeader: String,
    val sessionUsername: String,
    val webSessionActive: Boolean,
)

/**
 * 底部「通知」角标只反映 V2EX 官网的账号级未读计数。
 *
 * API 2.0 没有单条 read/unread 字段，因此不能用「最近一页通知减去本地 seen IDs」
 * 推断。启动时这里用 PAT 确认账号，再用同账号网页会话读取官网计数；通知页完成
 * 同样的已校验流程后通过 [publishOfficialCount] 更新共享角标。
 */
@Singleton
class UnreadNotificationsStore @Inject constructor(
    private val apiV2: V2exApiV2,
    private val secureStore: SecureStore,
    private val webSessionService: WebSessionService,
) {
    private val generation = AtomicLong(0)
    private val credentialsLock = Any()
    private var selectedCredentials: NotificationCredentialKey? = null
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    /** A page result supersedes an app refresh only while its captured credentials are still current. */
    internal fun publishOfficialCount(count: Int?, source: NotificationCredentialKey): Boolean =
        synchronized(credentialsLock) {
            if (credentialsSnapshot() != source) return@synchronized false
            generation.incrementAndGet()
            selectedCredentials = source
            _unreadCount.value = count?.coerceAtLeast(0) ?: 0
            true
        }

    /** Selects a newly observed account without invalidating a refresh already running for it. */
    internal fun selectCredentials(source: NotificationCredentialKey): Boolean =
        synchronized(credentialsLock) {
            if (credentialsSnapshot() != source) return@synchronized false
            if (selectedCredentials == source) return@synchronized true
            generation.incrementAndGet()
            selectedCredentials = source
            _unreadCount.value = 0
            true
        }

    suspend fun refresh() {
        val credentials = credentialsSnapshot()
        val request = synchronized(credentialsLock) {
            val changed = selectedCredentials != credentials
            selectedCredentials = credentials
            generation.incrementAndGet().also {
                // Never display account A's badge while account B is being verified.
                if (changed) _unreadCount.value = 0
            }
        }
        if (credentials.token.isEmpty() || !credentials.webSessionActive || credentials.cookieHeader.isEmpty()) {
            applyIfCurrent(request, credentials, count = null)
            return
        }

        val count = runCatching {
            val envelope = apiV2.me(authorization = credentials.token.asBearer())
            val member = envelope.result
            check(envelope.success == true && member != null && member.username.isNotBlank()) {
                envelope.message ?: "Token 无法确认当前账号"
            }
            webSessionService.notificationReadState(
                cookieHeader = credentials.cookieHeader,
                expectedUsername = member.username,
            ).getOrThrow().unreadCount
        }.getOrNull()

        applyIfCurrent(request, credentials, count)
    }

    private fun applyIfCurrent(
        request: Long,
        credentials: NotificationCredentialKey,
        count: Int?,
    ): Boolean = synchronized(credentialsLock) {
        if (
            generation.get() != request ||
            selectedCredentials != credentials ||
            credentialsSnapshot() != credentials
        ) {
            return@synchronized false
        }
        _unreadCount.value = count?.coerceAtLeast(0) ?: 0
        true
    }

    private fun credentialsSnapshot(): NotificationCredentialKey = NotificationCredentialKey(
        token = secureStore.personalAccessToken.orEmpty().filterNot(Char::isWhitespace),
        cookieHeader = secureStore.sessionCookieHeader.orEmpty(),
        sessionUsername = secureStore.sessionUsername.orEmpty(),
        webSessionActive = secureStore.isWebSessionActive,
    )

    private fun String.asBearer(): String = "Bearer $this"
}
