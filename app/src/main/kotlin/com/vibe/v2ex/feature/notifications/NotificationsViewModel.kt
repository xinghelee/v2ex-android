package com.vibe.v2ex.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SeenNotificationsStore
import com.vibe.v2ex.data.datastore.UnreadNotificationsStore
import com.vibe.v2ex.data.model.Notification
import com.vibe.v2ex.data.model.NotificationKind
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.designsystem.htmlToPlainText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** First `/t/<id>` link in the notification text — navigation target once wired. */
    val topicId: Long?,
    val isUnread: Boolean,
)

data class NotificationsUiState(
    val isTokenSet: Boolean = true,
    val rows: List<NotificationRow> = emptyList(),
    val filter: NotificationFilter = NotificationFilter.REPLY,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) {
    val visibleRows: List<NotificationRow> get() = rows.filter { filter.matches(it.kind) }
    val totalUnread: Int get() = rows.count { it.isUnread }
    fun unreadCount(filter: NotificationFilter): Int = rows.count { filter.matches(it.kind) && it.isUnread }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val apiV2: V2exApiV2,
    private val apiV1: V2exApiV1,
    private val secureStore: SecureStore,
    private val seenStore: SeenNotificationsStore,
    private val unreadNotificationsStore: UnreadNotificationsStore,
) : ViewModel() {
    private val topicLinkRegex = Regex("""/t/(\d+)""")

    private var items: List<Notification> = emptyList()

    /** username → avatar URL; ViewModel-lifetime so avatars survive pull-to-refresh (not app relaunch). */
    private val avatarCache = mutableMapOf<String, String>()
    private var seenIds: Set<Long> = emptySet()

    private val _uiState = MutableStateFlow(NotificationsUiState(isTokenSet = secureStore.isTokenSet))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            seenStore.seenIds.collect { seen ->
                seenIds = seen
                rebuildRows()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (!secureStore.isTokenSet) {
                items = emptyList()
                _uiState.value = _uiState.value.copy(isTokenSet = false, rows = emptyList(), isRefreshing = false)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isTokenSet = true, isRefreshing = true, error = null)
            runCatching {
                val envelope = apiV2.notifications(page = 1)
                envelope.result ?: error(envelope.message ?: "接口没有返回内容")
            }.onSuccess { fetched ->
                items = fetched
                unreadNotificationsStore.publish(fetched.map { it.id })
                _uiState.value = _uiState.value.copy(isRefreshing = false)
                rebuildRows()
                backfillAvatars()
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = throwable.message)
            }
        }
    }

    fun selectFilter(filter: NotificationFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun markSeen(id: Long) {
        viewModelScope.launch { seenStore.markSeen(id) }
    }

    fun markAllSeen() {
        viewModelScope.launch { seenStore.markAllSeen(items.map { it.id }) }
    }

    /** Deletes remotely best-effort (errors ignored, matching the iOS `try?`) and drops the row locally. */
    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { apiV2.deleteNotification(id) }
            items = items.filterNot { it.id == id }
            unreadNotificationsStore.publish(items.map { it.id })
            rebuildRows()
        }
    }

    private fun rebuildRows() {
        val rows = items.map { notification ->
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
                isUnread = notification.id !in seenIds,
            )
        }
        _uiState.value = _uiState.value.copy(rows = rows)
    }

    private fun payloadPreview(notification: Notification): String {
        val rendered = notification.payloadRendered
        if (!rendered.isNullOrBlank()) return htmlToPlainText(rendered)
        return notification.payload.orEmpty().trim()
    }

    /**
     * API 2.0 notification members carry only `username`. Two passes per refresh (mirrors iOS):
     * the cache reapply already happened in [rebuildRows]; here each still-missing unique
     * username costs one v1 `members/show.json` call, then every matching row is patched.
     */
    private suspend fun backfillAvatars() {
        val missing = items.mapNotNull { it.member }
            .filter { it.avatarUrl == null && it.username.isNotBlank() && avatarCache[it.username] == null }
            .map { it.username }
            .distinct()
        for (username in missing) {
            val url = runCatching { apiV1.showMember(username).avatarUrl }.getOrNull() ?: continue
            avatarCache[username] = url
            rebuildRows()
        }
    }
}
