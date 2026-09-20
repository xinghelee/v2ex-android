package com.vibe.v2ex.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.NotificationCredentialKey
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.datastore.UnreadNotificationsStore
import com.vibe.v2ex.data.model.Notification
import com.vibe.v2ex.data.model.NotificationKind
import com.vibe.v2ex.data.moderation.ModerationStore
import com.vibe.v2ex.data.push.NotificationPushNotifier
import com.vibe.v2ex.data.remote.V2Envelope
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.data.remote.WebSessionService
import com.vibe.v2ex.designsystem.htmlToPlainText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotificationFilter(val label: String) {
    REPLY("回复我的"),
    MENTION("@ 我的"),
    THANKS("感谢"),
    ALL("全部");

    /** FAVORITE has no dedicated chip — it only ever surfaces under 全部 (mirrors iOS). */
    fun matches(kind: NotificationKind): Boolean = when (this) {
        REPLY -> kind == NotificationKind.REPLY
        MENTION -> kind == NotificationKind.MENTION
        THANKS -> kind == NotificationKind.THANKS
        ALL -> true
    }
}

data class NotificationRow(
    val id: Long,
    val kind: NotificationKind,
    val username: String,
    val avatarUrl: String?,
    /** Plain-text action line, e.g. "xxx 在 yyy 里回复了你". */
    val actionText: String,
    /** Plain-text reply/thanks payload preview, may be empty. */
    val payloadPreview: String,
    val createdAt: Long?,
    /** First `/t/<id>` link in the notification text. */
    val topicId: Long?,
)

enum class NotificationNoticeAction { ACCOUNT }

data class NotificationNotice(
    val id: Long,
    val message: String,
    val action: NotificationNoticeAction? = null,
)

data class NotificationsUiState(
    val isTokenSet: Boolean = true,
    val isWebSessionActive: Boolean = false,
    val rows: List<NotificationRow> = emptyList(),
    val filter: NotificationFilter = NotificationFilter.REPLY,
    val isRefreshing: Boolean = false,
    val isSyncing: Boolean = false,
    /** Null means the official website counter could not be verified. */
    val officialUnreadCount: Int? = null,
    val error: String? = null,
    val notice: NotificationNotice? = null,
) {
    val visibleRows: List<NotificationRow> get() = rows.filter { filter.matches(it.kind) }
    val canMarkAllRead: Boolean
        get() = isWebSessionActive && !isRefreshing && !isSyncing && (officialUnreadCount ?: 0) > 0
}

private data class NotificationCredentials(
    val token: String,
    val cookieHeader: String,
    val sessionUsername: String,
    val webSessionActive: Boolean,
) {
    val authorization: String get() = "Bearer $token"
}

private data class NotificationModerationRules(
    val blockedUsernames: Set<String>,
    val unavailableMemberIds: Set<Long>,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val apiV2: V2exApiV2,
    private val apiV1: V2exApiV1,
    private val secureStore: SecureStore,
    private val webSessionService: WebSessionService,
    private val moderationStore: ModerationStore,
    private val unreadNotificationsStore: UnreadNotificationsStore,
    private val settingsDataStore: SettingsDataStore,
    private val pushNotifier: NotificationPushNotifier,
) : ViewModel() {
    private val topicLinkRegex = Regex("""/t/(\d+)""")
    private val moderationRulesFlow: Flow<NotificationModerationRules> = combine(
        moderationStore.blockedUsernames,
        moderationStore.unavailableBlockedMemberIds,
    ) { usernames, memberIds ->
        NotificationModerationRules(
            blockedUsernames = usernames.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) },
            unavailableMemberIds = memberIds.toSet(),
        )
    }

    private var items: List<Notification> = emptyList()
    private var moderationRules: NotificationModerationRules? = null
    private var activeCredentials: NotificationCredentials? = null
    private var account: String? = null
    private var generation = 0L
    private var noticeID = 0L
    private var refreshJob: Job? = null
    private var observedCredentialRevision = secureStore.accountCredentialsRevision.value

    /** username → avatar URL; ViewModel-lifetime so avatars survive pull-to-refresh. */
    private val avatarCache = mutableMapOf<String, String>()

    private val _uiState = MutableStateFlow(
        NotificationsUiState(
            isTokenSet = secureStore.isTokenSet,
            isWebSessionActive = secureStore.isWebSessionActive,
        ),
    )
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            moderationRulesFlow.collect { rules ->
                moderationRules = rules
                rebuildRows()
            }
        }
        viewModelScope.launch {
            secureStore.accountCredentialsRevision.collect { revision ->
                if (revision == observedCredentialRevision) return@collect
                observedCredentialRevision = revision
                invalidateForCredentialChange()
            }
        }
    }

    /** Clears retained back-stack content before another account can be drawn. */
    private fun invalidateForCredentialChange() {
        val credentials = credentialsSnapshot()
        if (credentials == activeCredentials) return
        generation += 1
        refreshJob?.cancel()
        refreshJob = null
        items = emptyList()
        account = null
        activeCredentials = credentials
        unreadNotificationsStore.selectCredentials(credentials.unreadKey())
        _uiState.update {
            it.copy(
                isTokenSet = credentials.token.isNotEmpty(),
                isWebSessionActive = credentials.webSessionActive,
                rows = emptyList(),
                isRefreshing = false,
                isSyncing = false,
                officialUnreadCount = null,
                error = null,
                notice = null,
            )
        }
    }

    fun refresh() {
        val credentials = credentialsSnapshot()
        if (_uiState.value.isSyncing && credentials == activeCredentials) return

        val identityChanged = credentials != activeCredentials
        if (identityChanged) {
            items = emptyList()
            account = null
            activeCredentials = credentials
            unreadNotificationsStore.publishOfficialCount(null, credentials.unreadKey())
            _uiState.update {
                it.copy(
                    isTokenSet = credentials.token.isNotEmpty(),
                    isWebSessionActive = credentials.webSessionActive,
                    rows = emptyList(),
                    isRefreshing = false,
                    isSyncing = false,
                    officialUnreadCount = null,
                    error = null,
                    notice = null,
                )
            }
        }

        val request = ++generation
        refreshJob?.cancel()
        if (credentials.token.isEmpty()) {
            _uiState.update {
                it.copy(
                    isTokenSet = false,
                    isWebSessionActive = credentials.webSessionActive,
                    rows = emptyList(),
                    isRefreshing = false,
                    officialUnreadCount = null,
                    error = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isTokenSet = true,
                isWebSessionActive = credentials.webSessionActive,
                isRefreshing = true,
                error = null,
            )
        }
        refreshJob = viewModelScope.launch { refresh(request, credentials) }
    }

    private suspend fun refresh(request: Long, credentials: NotificationCredentials) {
        try {
            val rules = moderationRules ?: moderationRulesFlow.first()
            if (!isCurrent(request, credentials)) return
            moderationRules = rules

            val member = apiV2.me(authorization = credentials.authorization)
                .requireResult("Token 无法确认当前账号")
            check(member.username.isNotBlank()) { "Token 返回的账号为空" }
            val fetched = apiV2.notifications(page = 1, authorization = credentials.authorization)
                .requireResult("接口没有返回通知内容")
            if (!isCurrent(request, credentials)) return

            account = member.username
            items = fetched
            rebuildRows()
            // 用户已经在这里看到最新一页：推送的基线跟上，挂着的系统通知也收掉。
            fetched.maxOfOrNull { it.id }?.let { settingsDataStore.raiseNotificationPushLastSeenId(it) }
            pushNotifier.cancel()

            if (!credentials.webSessionActive || credentials.cookieHeader.isEmpty()) {
                _uiState.update { it.copy(officialUnreadCount = null) }
                unreadNotificationsStore.publishOfficialCount(null, credentials.unreadKey())
                showNotice(
                    "网页登录同一账号后，可同步官网未读数量和全部已读状态。",
                    NotificationNoticeAction.ACCOUNT,
                )
            } else {
                webSessionService.notificationReadState(
                    cookieHeader = credentials.cookieHeader,
                    expectedUsername = member.username,
                ).onSuccess { state ->
                    if (!isCurrent(request, credentials)) return@onSuccess
                    val count = state.unreadCount.coerceAtLeast(0)
                    _uiState.update { it.copy(officialUnreadCount = count) }
                    unreadNotificationsStore.publishOfficialCount(count, credentials.unreadKey())
                }.onFailure { error ->
                    if (!isCurrent(request, credentials)) return@onFailure
                    _uiState.update { it.copy(officialUnreadCount = null) }
                    unreadNotificationsStore.publishOfficialCount(null, credentials.unreadKey())
                    showNotice(
                        error.displayMessage("无法读取官网未读提醒"),
                        NotificationNoticeAction.ACCOUNT,
                    )
                }
            }

            backfillAvatars(request, credentials)
        } catch (error: Throwable) {
            if (!isCurrent(request, credentials)) return
            val message = error.displayMessage("没能读取通知")
            _uiState.update { it.copy(error = message) }
            showNotice("加载失败：$message")
        } finally {
            if (isCurrent(request, credentials)) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun selectFilter(filter: NotificationFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun markAllRead() {
        val credentials = credentialsSnapshot()
        val expectedAccount = account
        val request = generation
        if (_uiState.value.isRefreshing || _uiState.value.isSyncing) return
        if (expectedAccount.isNullOrBlank() || credentials != activeCredentials) {
            showNotice("账号状态已变化，请刷新通知后重试。", NotificationNoticeAction.ACCOUNT)
            return
        }
        if (!credentials.webSessionActive || credentials.cookieHeader.isEmpty()) {
            showNotice("请先登录网页账号，再同步全部已读。", NotificationNoticeAction.ACCOUNT)
            return
        }
        if ((_uiState.value.officialUnreadCount ?: 0) <= 0) return

        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            try {
                val state = webSessionService.markNotificationsRead(
                    cookieHeader = credentials.cookieHeader,
                    expectedUsername = expectedAccount,
                ).getOrThrow()
                if (!isCurrent(request, credentials)) return@launch
                val count = state.unreadCount.coerceAtLeast(0)
                _uiState.update { it.copy(officialUnreadCount = count) }
                unreadNotificationsStore.publishOfficialCount(count, credentials.unreadKey())
                showNotice(
                    if (count == 0) "已同步官网：全部已读" else "官网仍有新提醒，请刷新后重试。",
                )
            } catch (error: Throwable) {
                if (!isCurrent(request, credentials)) return@launch
                // Preserve the last verified count on every failed/ambiguous acknowledgement.
                showNotice(
                    "已读同步失败：${error.displayMessage("请稍后重试")}",
                    NotificationNoticeAction.ACCOUNT,
                )
            } finally {
                if (generation == request) _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun delete(id: Long) {
        val credentials = credentialsSnapshot()
        val request = generation
        if (items.none { it.id == id }) return
        if (credentials.token.isEmpty() || credentials != activeCredentials) {
            showNotice("账号状态已变化，请刷新通知后重试。", NotificationNoticeAction.ACCOUNT)
            return
        }

        viewModelScope.launch {
            try {
                val envelope = apiV2.deleteNotification(id = id, authorization = credentials.authorization)
                check(envelope.success == true) { envelope.message ?: "官网没有确认删除" }
                if (!isCurrent(request, credentials)) return@launch
                items = items.filterNot { it.id == id }
                rebuildRows()
            } catch (error: Throwable) {
                if (!isCurrent(request, credentials)) return@launch
                showNotice("删除失败：${error.displayMessage("请稍后重试")}")
            }
        }
    }

    fun consumeNotice(id: Long) {
        _uiState.update { state -> if (state.notice?.id == id) state.copy(notice = null) else state }
    }

    private fun rebuildRows() {
        val rules = moderationRules ?: return
        val rows = items.asSequence()
            .filterNot { notification -> notification.isHidden(rules) }
            .map { notification ->
                val username = notification.member?.username.orEmpty()
                NotificationRow(
                    id = notification.id,
                    kind = notification.kind,
                    username = username,
                    avatarUrl = notification.member?.avatarUrl ?: avatarCache[username],
                    actionText = htmlToPlainText(notification.text.orEmpty()).ifBlank { "有新动态" },
                    payloadPreview = payloadPreview(notification),
                    createdAt = notification.created,
                    topicId = notification.text?.let { text ->
                        topicLinkRegex.find(text)?.groupValues?.get(1)?.toLongOrNull()
                    },
                )
            }
            .toList()
        _uiState.update { it.copy(rows = rows) }
    }

    private fun Notification.isHidden(rules: NotificationModerationRules): Boolean {
        val username = member?.username.orEmpty().lowercase(Locale.ROOT)
        if (username.isNotEmpty() && username in rules.blockedUsernames) return true
        return (member?.id ?: memberId)?.let { it in rules.unavailableMemberIds } == true
    }

    private fun payloadPreview(notification: Notification): String {
        val rendered = notification.payloadRendered
        if (!rendered.isNullOrBlank()) return htmlToPlainText(rendered)
        return notification.payload.orEmpty().trim()
    }

    private suspend fun backfillAvatars(request: Long, credentials: NotificationCredentials) {
        val missing = items.mapNotNull { it.member }
            .filter { it.avatarUrl == null && it.username.isNotBlank() && avatarCache[it.username] == null }
            .map { it.username }
            .distinct()
        for (username in missing) {
            val url = runCatching { apiV1.showMember(username).avatarUrl }.getOrNull() ?: continue
            if (!isCurrent(request, credentials)) return
            avatarCache[username] = url
            rebuildRows()
        }
    }

    private fun showNotice(message: String, action: NotificationNoticeAction? = null) {
        _uiState.update {
            it.copy(notice = NotificationNotice(id = ++noticeID, message = message, action = action))
        }
    }

    private fun isCurrent(request: Long, credentials: NotificationCredentials): Boolean =
        generation == request && activeCredentials == credentials && credentialsSnapshot() == credentials

    private fun credentialsSnapshot(): NotificationCredentials = NotificationCredentials(
        token = secureStore.personalAccessToken.orEmpty().filterNot(Char::isWhitespace),
        cookieHeader = secureStore.sessionCookieHeader.orEmpty(),
        sessionUsername = secureStore.sessionUsername.orEmpty(),
        webSessionActive = secureStore.isWebSessionActive,
    )

    private fun NotificationCredentials.unreadKey() = NotificationCredentialKey(
        token = token,
        cookieHeader = cookieHeader,
        sessionUsername = sessionUsername,
        webSessionActive = webSessionActive,
    )

    private fun <T> V2Envelope<T>.requireResult(fallback: String): T {
        check(success == true) { message ?: fallback }
        return result ?: error(message ?: fallback)
    }

    private fun Throwable.displayMessage(fallback: String): String =
        message?.trim()?.takeIf(String::isNotEmpty) ?: fallback
}
