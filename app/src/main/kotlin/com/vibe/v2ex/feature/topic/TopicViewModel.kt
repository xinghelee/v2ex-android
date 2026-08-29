package com.vibe.v2ex.feature.topic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vibe.v2ex.data.datastore.ReadStateStore
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.model.Reply
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.remote.TopicAppend
import com.vibe.v2ex.data.remote.WebSessionService
import com.vibe.v2ex.data.repository.DraftRepository
import com.vibe.v2ex.data.repository.FavoritesRepository
import com.vibe.v2ex.data.repository.HistoryRepository
import com.vibe.v2ex.data.repository.OfflineRepository
import com.vibe.v2ex.data.repository.TopicRepository
import com.vibe.v2ex.designsystem.ContentBlock
import com.vibe.v2ex.designsystem.htmlToPlainText
import com.vibe.v2ex.designsystem.parseContentBlocks
import com.vibe.v2ex.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [floor] is null when the mention couldn't be resolved to an earlier floor — UI shows a bare @username. */
data class QuotedReply(val username: String, val floor: Int?, val excerpt: String)

/** [floor] is 1-based over replies sorted ascending by id — client-derived, not part of the API payload. */
data class FloorReply(
    val reply: Reply,
    val floor: Int,
    val isAuthor: Boolean,
    val quoted: QuotedReply?,
    val blocks: List<ContentBlock>,
)

/** 附言 + 已解析好的富文本块。 */
data class AppendBlock(val append: TopicAppend, val blocks: List<ContentBlock>)

data class TopicUiState(
    val topic: Topic? = null,
    val topicBlocks: List<ContentBlock> = emptyList(),
    val replies: List<FloorReply> = emptyList(),
    val appends: List<AppendBlock> = emptyList(),
    /** 网页抓取的浏览数（API 不返回）。 */
    val topicViews: Int? = null,
    /** 本页佩戴 PRO 徽章的用户名（网页抓取，不落盘 — 订阅可能过期）。 */
    val proMembers: Set<String> = emptySet(),
    val onlyPoster: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val favorited: Boolean = false,
    val favoriteSyncing: Boolean = false,
    /** 用户手动保存的离线内容（自动缓存不算）。 */
    val isOfflineSaved: Boolean = false,
    val loadedFromOffline: Boolean = false,
    /** 行内回复草稿与发送态。 */
    val replyDraft: String = "",
    val isSendingReply: Boolean = false,
    /** 记住阅读进度：回复就绪后待恢复的楼层，UI 消费一次后置空。 */
    val pendingRestoreFloor: Int? = null,
    val isWebSessionActive: Boolean = false,
    val message: String? = null,
) {
    /** Floors are assigned before filtering, so quote references stay valid under 只看楼主. */
    val visibleReplies: List<FloorReply>
        get() = if (onlyPoster) replies.filter { it.isAuthor } else replies
}

// Strips a leading `@user` / `@user #N` (plain or anchor-wrapped) off the rendered HTML while
// keeping remaining markup intact, so later links in the same reply stay tappable.
private val LEADING_MENTION_REGEX =
    Regex("""^\s*@\s*(?:<a\b[^>]*>[^<]*</a>|[A-Za-z0-9_-]+)\s*(?:#\d+)?\s*""")

private const val QUOTE_EXCERPT_LIMIT = 40

@HiltViewModel
class TopicViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TopicRepository,
    private val secureStore: SecureStore,
    private val webSessionService: WebSessionService,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val offlineRepository: OfflineRepository,
    private val readStateStore: ReadStateStore,
    private val settingsDataStore: SettingsDataStore,
    private val draftRepository: DraftRepository,
) : ViewModel() {
    private val topicId: Long = savedStateHandle.toRoute<Route.Topic>().topicId

    private val _uiState = MutableStateFlow(TopicUiState(isWebSessionActive = secureStore.isWebSessionActive))
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    private var rawReplies: List<Reply> = emptyList()
    private var replyDraftId: Long? = null
    private var draftSaveJob: Job? = null
    private var positionSaveJob: Job? = null

    init {
        viewModelScope.launch { readStateStore.markRead(topicId) }
        viewModelScope.launch {
            favoritesRepository.observeIds().collect { ids ->
                _uiState.update { it.copy(favorited = topicId in ids) }
            }
        }
        viewModelScope.launch {
            offlineRepository.observeManualIds().collect { ids ->
                _uiState.update { it.copy(isOfflineSaved = topicId in ids) }
            }
        }
        viewModelScope.launch {
            draftRepository.forTopic(topicId)?.let { draft ->
                replyDraftId = draft.id
                _uiState.update { it.copy(replyDraft = draft.content) }
            }
        }
        hydrateFromCacheThenRefresh()
    }

    /** 先用离线/缓存快照立即出内容，再走网络刷新（stale-while-revalidate，mirrors iOS）。 */
    private fun hydrateFromCacheThenRefresh() {
        viewModelScope.launch {
            val cached = offlineRepository.bundle(topicId)
            if (cached != null && _uiState.value.topic == null) {
                applyDetail(cached.topic, cached.replies, loadedFromOffline = !cached.automatic)
            }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.loadTopic(topicId)
                .onSuccess { detail ->
                    // 旧接口对新帖可能返回不完整的回复列表 —— 不要用它把更全的缓存挤回去。
                    val looksIncomplete = rawReplies.isNotEmpty() &&
                        detail.replies.size < rawReplies.size &&
                        detail.topic.replies >= rawReplies.size
                    applyDetail(
                        topic = detail.topic,
                        replies = if (looksIncomplete) rawReplies else detail.replies,
                        loadedFromOffline = false,
                    )
                    _uiState.update { it.copy(isLoading = false) }
                    historyRepository.record(detail.topic)
                    // 已离线的话题顺手把快照刷新到最新（保持原 manual/automatic 身份）。
                    offlineRepository.bundle(topicId)?.let { saved ->
                        offlineRepository.save(detail.topic, rawReplies, automatic = saved.automatic)
                    }
                    restoreReadingPosition()
                    syncFavoriteState()
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        if (state.topic != null) {
                            state.copy(isLoading = false)
                        } else {
                            state.copy(isLoading = false, error = error.message ?: "加载失败")
                        }
                    }
                }

            // 浏览数 / 附言 / PRO 徽章都来自同一次网页抓取；失败静默（返回空 extras）。
            val extras = webSessionService.topicPageExtras(topicId)
            if (extras.views != null || extras.appends.isNotEmpty() || extras.proMembers.isNotEmpty()) {
                val appendBlocks = withContext(Dispatchers.Default) {
                    extras.appends.map { AppendBlock(it, parseContentBlocks(it.contentHtml)) }
                }
                _uiState.update {
                    it.copy(
                        topicViews = extras.views ?: it.topicViews,
                        appends = appendBlocks.ifEmpty { it.appends },
                        proMembers = extras.proMembers.ifEmpty { it.proMembers },
                    )
                }
            }
        }
    }

    private suspend fun applyDetail(topic: Topic, replies: List<Reply>, loadedFromOffline: Boolean) {
        val (topicBlocks, threaded) = withContext(Dispatchers.Default) {
            val bodyHtml = topic.contentRendered.orEmpty().ifBlank { topic.content.orEmpty() }
            parseContentBlocks(bodyHtml) to threadReplies(replies, topic.authorName)
        }
        rawReplies = replies
        _uiState.update {
            it.copy(
                topic = topic,
                topicBlocks = topicBlocks,
                replies = threaded,
                loadedFromOffline = loadedFromOffline,
            )
        }
    }

    // MARK: 阅读进度

    private suspend fun restoreReadingPosition() {
        if (!settingsDataStore.rememberReadingPosition.first()) return
        val floor = readStateStore.position(topicId) ?: return
        if (floor > 1 && _uiState.value.replies.any { it.floor == floor }) {
            _uiState.update { it.copy(pendingRestoreFloor = floor) }
        }
    }

    fun consumeRestoreFloor() {
        _uiState.update { it.copy(pendingRestoreFloor = null) }
    }

    /** 列表滚动时上报可见楼层，防抖后写盘。 */
    fun onFloorVisible(floor: Int) {
        positionSaveJob?.cancel()
        positionSaveJob = viewModelScope.launch {
            if (!settingsDataStore.rememberReadingPosition.first()) return@launch
            delay(350)
            readStateStore.rememberPosition(topicId, floor)
        }
    }

    fun toggleOnlyPoster() {
        _uiState.update { it.copy(onlyPoster = !it.onlyPoster) }
    }

    // MARK: 收藏

    fun toggleFavorite() {
        val state = _uiState.value
        val topic = state.topic ?: return
        if (state.favoriteSyncing) return
        val target = !state.favorited

        viewModelScope.launch {
            // 本地列表先行（未登录也能收藏，mirrors iOS）；登录态下再同步 V2EX。
            if (target) favoritesRepository.addLocal(topic) else favoritesRepository.removeLocal(topicId)
            if (!secureStore.isWebSessionActive) return@launch

            _uiState.update { it.copy(favoriteSyncing = true) }
            repository.setFavorite(topicId, target)
                .onSuccess { _uiState.update { it.copy(favoriteSyncing = false) } }
                .onFailure {
                    // 服务器同步失败：回滚本地，保持两边一致。
                    if (target) favoritesRepository.removeLocal(topicId) else favoritesRepository.addLocal(topic)
                    _uiState.update {
                        it.copy(
                            favoriteSyncing = false,
                            message = if (target) "收藏失败，请稍后再试" else "取消收藏失败，请稍后再试",
                        )
                    }
                }
        }
    }

    /** Server-authoritative initial state — a blind toggle would pick the wrong action link on already-favorited topics. */
    private fun syncFavoriteState() {
        if (!secureStore.isWebSessionActive) return
        viewModelScope.launch {
            val remote = repository.fetchFavoriteState(topicId) ?: return@launch
            val topic = _uiState.value.topic ?: return@launch
            if (remote != _uiState.value.favorited) {
                if (remote) favoritesRepository.addLocal(topic) else favoritesRepository.removeLocal(topicId)
            }
        }
    }

    // MARK: 离线

    fun toggleOffline() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isOfflineSaved) {
                offlineRepository.remove(topicId)
                _uiState.update { it.copy(message = "已移除离线内容") }
            } else {
                val topic = state.topic ?: return@launch
                offlineRepository.save(topic, rawReplies, automatic = false)
                _uiState.update { it.copy(message = "已保存，可离线阅读") }
            }
        }
    }

    // MARK: 行内回复

    fun onReplyDraftChange(text: String) {
        _uiState.update { it.copy(replyDraft = text) }
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(500)
            persistDraft(text)
        }
    }

    private suspend fun persistDraft(text: String) {
        replyDraftId = draftRepository.save(replyDraftId, topicId, title = "", content = text, nodeName = null)
    }

    /** 每条回复行的「回复」按钮：预填 `@user #floor `。 */
    fun prefillMention(mention: String) {
        onReplyDraftChange(mention)
    }

    fun sendReply() {
        val state = _uiState.value
        val content = state.replyDraft.trim()
        if (content.isEmpty() || state.isSendingReply) return
        if (!secureStore.isWebSessionActive) {
            _uiState.update { it.copy(message = "回复需要先在「账号」中登录网页会话") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingReply = true) }
            repository.postReply(topicId, content)
                .onSuccess {
                    draftSaveJob?.cancel()
                    draftRepository.forTopic(topicId)?.let { draftRepository.delete(it) }
                    replyDraftId = null
                    _uiState.update { it.copy(isSendingReply = false, replyDraft = "") }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSendingReply = false, message = error.message ?: "回复失败，请稍后重试")
                    }
                }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Floors are 1-based over id-ascending replies. [floorsByAuthor] is built incrementally,
     * so a reply can only quote something earlier in the thread.
     */
    private fun threadReplies(replies: List<Reply>, authorName: String): List<FloorReply> {
        val sorted = replies.sortedBy { it.id }
        val floorsByAuthor = mutableMapOf<String, MutableList<Int>>()
        val result = ArrayList<FloorReply>(sorted.size)
        sorted.forEachIndexed { index, reply ->
            val floor = index + 1
            val quoted = resolveQuote(reply.content, floorsByAuthor, result)
            val html = reply.contentRendered.ifBlank { reply.content }
            val cleaned = if (quoted != null) LEADING_MENTION_REGEX.replaceFirst(html, "") else html
            result += FloorReply(
                reply = reply,
                floor = floor,
                isAuthor = authorName.isNotEmpty() && reply.authorName == authorName,
                quoted = quoted,
                blocks = parseContentBlocks(cleaned),
            )
            floorsByAuthor.getOrPut(reply.authorName) { mutableListOf() } += floor
        }
        return result
    }

    private fun resolveQuote(
        rawContent: String,
        floorsByAuthor: Map<String, List<Int>>,
        earlier: List<FloorReply>,
    ): QuotedReply? {
        var i = 0
        // A markdown-blockquote `>` prefix is tolerated; the quote must otherwise lead the reply.
        while (i < rawContent.length && (rawContent[i].isWhitespace() || rawContent[i] == '>')) i++
        if (i >= rawContent.length || rawContent[i] != '@') return null
        i++
        val nameStart = i
        while (i < rawContent.length && rawContent[i].isUsernameChar()) i++
        val username = rawContent.substring(nameStart, i)
        if (username.length < 2) return null
        while (i < rawContent.length && rawContent[i] == ' ') i++
        var explicitFloor: Int? = null
        if (i < rawContent.length && rawContent[i] == '#') {
            i++
            val digitsStart = i
            while (i < rawContent.length && rawContent[i] in '0'..'9') i++
            if (i > digitsStart) explicitFloor = rawContent.substring(digitsStart, i).toIntOrNull()
        }
        val resolvedFloor = explicitFloor ?: floorsByAuthor[username]?.lastOrNull()
        if (resolvedFloor != null) {
            val quotedReply = earlier.getOrNull(resolvedFloor - 1)
            if (quotedReply != null) {
                val plain = htmlToPlainText(
                    quotedReply.reply.contentRendered.ifBlank { quotedReply.reply.content },
                )
                val excerpt = if (plain.length > QUOTE_EXCERPT_LIMIT) {
                    plain.take(QUOTE_EXCERPT_LIMIT) + "…"
                } else {
                    plain
                }
                return QuotedReply(username, resolvedFloor, excerpt)
            }
        }
        return QuotedReply(username, null, "")
    }

    private fun Char.isUsernameChar(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '_' || this == '-'
}
