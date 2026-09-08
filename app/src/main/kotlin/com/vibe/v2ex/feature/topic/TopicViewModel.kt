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
import com.vibe.v2ex.data.moderation.ModerationStore
import com.vibe.v2ex.data.moderation.ReportReason
import com.vibe.v2ex.data.moderation.ReportTargetType
import com.vibe.v2ex.data.remote.TopicAppend
import com.vibe.v2ex.data.remote.WebSessionService
import com.vibe.v2ex.data.repository.DraftRepository
import com.vibe.v2ex.data.repository.FavoritesRepository
import com.vibe.v2ex.data.repository.HistoryRepository
import com.vibe.v2ex.data.repository.OfflineRepository
import com.vibe.v2ex.data.repository.TopicRepository
import com.vibe.v2ex.data.repository.TopicSummaryRepository
import com.vibe.v2ex.designsystem.ContentBlock
import com.vibe.v2ex.designsystem.htmlToPlainText
import com.vibe.v2ex.designsystem.parseContentBlocks
import com.vibe.v2ex.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
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

/** A concrete piece of UGC selected from this topic screen. */
data class TopicModerationTarget(
    val targetType: ReportTargetType,
    val targetId: Long,
    val topicId: Long,
    val author: String,
    val excerpt: String,
) {
    val key: String get() = "${targetType.slug}:$targetId"
    val kindTitle: String
        get() = when (targetType) {
            ReportTargetType.TOPIC -> "这个话题"
            ReportTargetType.REPLY -> "这条回复"
            ReportTargetType.MEMBER -> "这个用户"
        }
}

data class TopicModerationCompletion(
    val targetKey: String,
    val closeTopic: Boolean,
)

data class TopicUiState(
    val topic: Topic? = null,
    /** False until local moderation visibility has emitted, preventing a hidden topic flash. */
    val moderationReady: Boolean = false,
    val isTopicHidden: Boolean = false,
    val topicBlocks: List<ContentBlock> = emptyList(),
    val replies: List<FloorReply> = emptyList(),
    /** Topic can render while this explains that its reply list is partial. */
    val replyWarning: String? = null,
    val appends: List<AppendBlock> = emptyList(),
    /** 网页抓取的浏览数（API 不返回）。 */
    val topicViews: Int? = null,
    /** 本页佩戴 PRO 徽章的用户名（网页抓取，不落盘 — 订阅可能过期）。 */
    val proMembers: Set<String> = emptySet(),
    val onlyPoster: Boolean = false,
    val onlyMine: Boolean = false,
    val currentUsername: String? = null,
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
    val summary: String? = null,
    val isGeneratingSummary: Boolean = false,
    val summaryError: String? = null,
    val isDeepSeekConfigured: Boolean = false,
    /** Used only to keep blocked accounts out of the composer mention suggestions. */
    val blockedUsernames: Set<String> = emptySet(),
    /** Non-null while a report or website block request owns the moderation controls. */
    val moderationActionKey: String? = null,
    /** One-shot UI event: dismiss the sheet and, for a topic target, leave this now-hidden page. */
    val moderationCompletion: TopicModerationCompletion? = null,
) {
    /** Floors are assigned before filtering, so quote references stay valid under every mode. */
    val visibleReplies: List<FloorReply>
        get() = when {
            onlyPoster -> replies.filter { it.isAuthor }
            onlyMine -> replies.relatedTo(currentUsername)
            else -> replies
        }
}

private fun List<FloorReply>.relatedTo(username: String?): List<FloorReply> {
    val name = username?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
    val mention = Regex("(?i)(?<![A-Za-z0-9_-])@${Regex.escape(name)}(?![A-Za-z0-9_-])")
    val primary = filter { item ->
        item.reply.authorName.equals(name, ignoreCase = true) || mention.containsMatchIn(item.reply.content)
    }
    val contextFloors = primary.mapNotNullTo(mutableSetOf()) { it.quoted?.floor }
    return filter { it in primary || it.floor in contextFloors }
}

// Strips a leading `@user` / `@user #N` (plain or anchor-wrapped) off the rendered HTML while
// keeping remaining markup intact, so later links in the same reply stay tappable.
private val LEADING_MENTION_REGEX =
    Regex("""^\s*@\s*(?:<a\b[^>]*>[^<]*</a>|[A-Za-z0-9_-]+)\s*(?:#\d+)?\s*""")

private const val QUOTE_EXCERPT_LIMIT = 40
private const val MODERATION_EXCERPT_LIMIT = 500
private val SENSITIVE_ERROR_PATTERN = Regex("(?i)token|authorization|bearer|cookie")

private data class ReplyModerationRules(
    val hiddenTopicIds: List<Long> = emptyList(),
    val hiddenReplyIds: List<Long> = emptyList(),
    val blockedUsernames: List<String> = emptyList(),
    val unavailableMemberIds: List<Long> = emptyList(),
)

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
    private val topicSummaryRepository: TopicSummaryRepository,
    private val moderationStore: ModerationStore,
) : ViewModel() {
    private val route: Route.Topic = savedStateHandle.toRoute()
    private val topicId: Long = route.topicId
    private val initialFloor: Int? = route.initialFloor

    private val _uiState = MutableStateFlow(
        TopicUiState(
            isWebSessionActive = secureStore.isWebSessionActive,
            isDeepSeekConfigured = topicSummaryRepository.isConfigured,
            currentUsername = secureStore.sessionUsername,
        ),
    )
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    private var rawReplies: List<Reply> = emptyList()
    private var threadedReplies: List<FloorReply> = emptyList()
    /** Null until all rule flows emit, so blocked replies never flash during initial load. */
    private var moderationRules: ReplyModerationRules? = null
    private var moderationRevision = 0L
    private var detailRevision = 0L
    private var refreshGeneration = 0L
    private var refreshJob: Job? = null
    private var summaryGeneration = 0L
    private var summaryJob: Job? = null
    private var summaryRequestSource: String? = null
    /** Identifies the source represented by [TopicUiState.summary]. */
    private var displayedSummarySource: String? = null
    private var replyDraftId: Long? = null
    private var draftSaveJob: Job? = null
    private var positionSaveJob: Job? = null
    private var initialFloorHandled = false

    init {
        viewModelScope.launch {
            combine(
                moderationStore.hiddenTopicIds,
                moderationStore.hiddenReplyIds,
                moderationStore.blockedUsernames,
                moderationStore.unavailableBlockedMemberIds,
            ) { hiddenTopicIds, hiddenReplyIds, blockedUsernames, unavailableMemberIds ->
                ReplyModerationRules(hiddenTopicIds, hiddenReplyIds, blockedUsernames, unavailableMemberIds)
            }.collectLatest { rules ->
                moderationRules = rules
                moderationRevision += 1
                applyModeration(rules, moderationRevision)
            }
        }
        viewModelScope.launch {
            moderationStore.websiteState.collect { website ->
                _uiState.update {
                    it.copy(
                        isWebSessionActive = website.isWebSessionActive,
                        currentUsername = website.accountName,
                    )
                }
            }
        }
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
        startRefresh(hydrateFromCache = true)
    }

    fun refresh() {
        startRefresh(hydrateFromCache = false)
    }

    private fun startRefresh(hydrateFromCache: Boolean) {
        refreshGeneration += 1
        val generation = refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (hydrateFromCache) {
                val cached = offlineRepository.bundle(topicId)
                if (generation != refreshGeneration) return@launch
                if (cached != null && _uiState.value.topic == null) {
                    // 快照先上屏，刷新成功后再落回 false —— 网络失败时这个标记留着，
                    // 顶部提示会告诉用户「看的是离线内容」。
                    val applied = applyDetail(
                        cached.topic,
                        cached.replies,
                        loadedFromOffline = true,
                        expectedRefreshGeneration = generation,
                    )
                    if (!applied || generation != refreshGeneration) return@launch
                }
            }
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = repository.loadTopic(topicId)
            if (generation != refreshGeneration) return@launch
            if (result.isSuccess) {
                val detail = result.getOrThrow()
                // 旧接口对新帖可能返回不完整的回复列表 —— 不要用它把更全的缓存挤回去。
                val looksIncomplete = rawReplies.isNotEmpty() &&
                    detail.replies.size < rawReplies.size &&
                    detail.topic.replies >= rawReplies.size
                val replies = if (looksIncomplete) rawReplies else detail.replies
                val applied = applyDetail(
                    topic = detail.topic,
                    replies = replies,
                    loadedFromOffline = false,
                    replyWarning = if (looksIncomplete) {
                        "网络返回的回复少于本地快照，已保留本地较完整版本"
                    } else {
                        detail.replyWarning
                    },
                    expectedRefreshGeneration = generation,
                )
                if (!applied || generation != refreshGeneration) return@launch
                _uiState.update { it.copy(isLoading = false) }
                historyRepository.record(detail.topic)
                if (generation != refreshGeneration) return@launch
                // 打开过就缓存下来 —— 「上飞机前刷一遍首页」靠的就是这条，
                // 手动保存过的条目由 OfflineRepository.save 保住 manual 身份。
                offlineRepository.save(detail.topic, replies, automatic = true)
                if (generation != refreshGeneration) return@launch
                restoreReadingPosition()
                if (generation != refreshGeneration) return@launch
                syncFavoriteState()
            } else {
                val error = result.exceptionOrNull()
                _uiState.update { state ->
                    if (state.topic != null) {
                        state.copy(isLoading = false)
                    } else {
                        state.copy(isLoading = false, error = error?.message ?: "加载失败")
                    }
                }
            }

            // 浏览数 / 附言 / PRO 徽章都来自同一次网页抓取；失败静默（返回空 extras）。
            if (generation != refreshGeneration) return@launch
            val extras = webSessionService.topicPageExtras(topicId)
            if (generation != refreshGeneration) return@launch
            if (extras.views != null || extras.appends.isNotEmpty() || extras.proMembers.isNotEmpty()) {
                val appendBlocks = withContext(Dispatchers.Default) {
                    extras.appends.map { AppendBlock(it, parseContentBlocks(it.contentHtml)) }
                }
                if (generation != refreshGeneration) return@launch
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

    private suspend fun applyDetail(
        topic: Topic,
        replies: List<Reply>,
        loadedFromOffline: Boolean,
        replyWarning: String? = null,
        expectedRefreshGeneration: Long? = null,
    ): Boolean {
        val (topicBlocks, threaded) = withContext(Dispatchers.Default) {
            val bodyHtml = topic.contentRendered.orEmpty().ifBlank { topic.content.orEmpty() }
            parseContentBlocks(bodyHtml) to threadReplies(replies, topic.authorName)
        }
        if (expectedRefreshGeneration != null && expectedRefreshGeneration != refreshGeneration) return false
        rawReplies = replies
        threadedReplies = threaded
        detailRevision += 1
        val appliedDetailRevision = detailRevision
        val rules = moderationRules
        val appliedModerationRevision = moderationRevision
        val moderated = rules?.let { moderatedReplies(threaded, it) }.orEmpty()
        val topicHidden = topic.id in rules?.hiddenTopicIds.orEmpty()
        val source = rules
            ?.takeUnless { topicHidden }
            ?.let { summarySource(topic, moderated) }
        cancelSummaryIfSourceChanged(source)
        val keepSummary = source != null && displayedSummarySource == source
        if (!keepSummary) displayedSummarySource = null
        val restoreFloor = initialFloor?.takeIf { floor ->
            !initialFloorHandled && floor > 0 && moderated.any { it.floor == floor }
        }
        _uiState.update {
            it.copy(
                topic = topic,
                moderationReady = rules != null,
                isTopicHidden = topicHidden,
                topicBlocks = topicBlocks,
                replies = moderated,
                blockedUsernames = rules?.blockedUsernames.orEmpty()
                    .mapTo(mutableSetOf()) { username -> username.lowercase(Locale.ROOT) },
                replyWarning = replyWarning,
                loadedFromOffline = loadedFromOffline,
                summary = if (keepSummary) it.summary else null,
                isGeneratingSummary = if (source != null && summaryRequestSource == source) {
                    it.isGeneratingSummary
                } else {
                    false
                },
                summaryError = null,
                pendingRestoreFloor = restoreFloor ?: it.pendingRestoreFloor,
            )
        }
        if (restoreFloor != null) initialFloorHandled = true
        if (source == null || summaryRequestSource == source) return true
        val expectedSummaryGeneration = summaryGeneration
        val cachedSummary = topicSummaryRepository.cached(topicId, source)
        if (
            (expectedRefreshGeneration != null && expectedRefreshGeneration != refreshGeneration) ||
            detailRevision != appliedDetailRevision ||
            moderationRevision != appliedModerationRevision ||
            moderationRules != rules ||
            summaryGeneration != expectedSummaryGeneration ||
            currentSummarySource() != source
        ) {
            return true
        }
        displayedSummarySource = source.takeIf { cachedSummary != null }
        _uiState.update { it.copy(summary = cachedSummary, summaryError = null) }
        return true
    }

    /**
     * Floors and quote targets are resolved over the complete server thread first. Filtering then
     * preserves those floor numbers, and a quote preview is removed whenever its target is hidden.
     */
    private fun moderatedReplies(
        replies: List<FloorReply>,
        rules: ReplyModerationRules,
    ): List<FloorReply> {
        val hiddenFloors = replies.asSequence()
            .filter { item ->
                moderationStore.isReplyHidden(
                    reply = item.reply,
                    hiddenIds = rules.hiddenReplyIds,
                    blockedUsers = rules.blockedUsernames,
                    blockedMemberIds = rules.unavailableMemberIds,
                )
            }
            .mapTo(mutableSetOf(), FloorReply::floor)
        val blockedNames = rules.blockedUsernames.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }

        return replies.asSequence()
            .filterNot { it.floor in hiddenFloors }
            .map { item ->
                val quote = item.quoted
                if (
                    quote != null &&
                    (
                        quote.floor?.let(hiddenFloors::contains) == true ||
                            quote.username.lowercase(Locale.ROOT) in blockedNames
                    )
                ) {
                    item.copy(quoted = null)
                } else {
                    item
                }
            }
            .toList()
    }

    private suspend fun applyModeration(rules: ReplyModerationRules, rulesRevision: Long) {
        if (moderationRules != rules || moderationRevision != rulesRevision) return
        val appliedDetailRevision = detailRevision
        val topic = _uiState.value.topic
        val moderated = moderatedReplies(threadedReplies, rules)
        val topicHidden = topicId in rules.hiddenTopicIds
        val source = topic
            ?.takeUnless { topicHidden }
            ?.let { summarySource(it, moderated) }
        cancelSummaryIfSourceChanged(source)
        val keepSummary = source != null && displayedSummarySource == source
        if (!keepSummary) displayedSummarySource = null
        val restoreFloor = initialFloor?.takeIf { floor ->
            !initialFloorHandled && floor > 0 && moderated.any { it.floor == floor }
        }
        _uiState.update {
            it.copy(
                moderationReady = true,
                isTopicHidden = topicHidden,
                replies = moderated,
                blockedUsernames = rules.blockedUsernames
                    .mapTo(mutableSetOf()) { username -> username.lowercase(Locale.ROOT) },
                summary = if (keepSummary) it.summary else null,
                isGeneratingSummary = if (source != null && summaryRequestSource == source) {
                    it.isGeneratingSummary
                } else {
                    false
                },
                summaryError = null,
                pendingRestoreFloor = restoreFloor ?: it.pendingRestoreFloor?.takeIf { floor ->
                    moderated.any { reply -> reply.floor == floor }
                },
            )
        }
        if (restoreFloor != null) initialFloorHandled = true
        if (source == null || summaryRequestSource == source) return
        val expectedSummaryGeneration = summaryGeneration
        val cachedSummary = topicSummaryRepository.cached(topicId, source)
        if (
            moderationRules != rules ||
            moderationRevision != rulesRevision ||
            detailRevision != appliedDetailRevision ||
            summaryGeneration != expectedSummaryGeneration ||
            currentSummarySource() != source
        ) {
            return
        }
        displayedSummarySource = source.takeIf { cachedSummary != null }
        _uiState.update { it.copy(summary = cachedSummary, summaryError = null) }
    }

    fun generateSummary() {
        val state = _uiState.value
        val topic = state.topic ?: return
        if (!state.moderationReady || state.isTopicHidden) return
        if (state.isGeneratingSummary || summaryJob?.isActive == true) return
        if (!topicSummaryRepository.isConfigured) {
            _uiState.update { it.copy(summaryError = "请先在设置中配置 DeepSeek API Key") }
            return
        }
        val source = summarySource(topic, state.replies)
        summaryGeneration += 1
        val generation = summaryGeneration
        summaryRequestSource = source
        _uiState.update {
            it.copy(isGeneratingSummary = true, summaryError = null, isDeepSeekConfigured = true)
        }
        summaryJob = viewModelScope.launch {
            try {
                val summary = topicSummaryRepository.generate(topicId, source)
                if (
                    generation != summaryGeneration ||
                    summaryRequestSource != source ||
                    currentSummarySource() != source
                ) {
                    return@launch
                }
                displayedSummarySource = source
                _uiState.update {
                    it.copy(summary = summary, isGeneratingSummary = false, summaryError = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (
                    generation == summaryGeneration &&
                    summaryRequestSource == source &&
                    currentSummarySource() == source
                ) {
                    _uiState.update {
                        it.copy(
                            isGeneratingSummary = false,
                            summaryError = error.message ?: "摘要生成失败，请稍后重试",
                        )
                    }
                }
            } finally {
                if (generation == summaryGeneration) {
                    summaryRequestSource = null
                    summaryJob = null
                    _uiState.update { current ->
                        if (current.isGeneratingSummary) {
                            current.copy(isGeneratingSummary = false)
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    private fun summarySource(topic: Topic, replies: List<FloorReply>): String {
        val body = htmlToPlainText(topic.contentRendered.orEmpty().ifBlank { topic.content.orEmpty() })
        val discussion = buildString {
            append("标题：").append(topic.title).append('\n')
            append("作者：").append(topic.authorName).append('\n')
            append("正文：").append(body).append("\n\n回复：\n")
            replies.sortedBy { it.reply.id }.take(60).forEach { item ->
                val reply = item.reply
                append('#').append(item.floor).append(' ')
                    .append(reply.authorName).append("：")
                    .append(htmlToPlainText(reply.contentRendered.ifBlank { reply.content }))
                    .append('\n')
            }
        }
        // Bound request cost while keeping the beginning of the discussion deterministic for caching.
        return discussion.take(24_000)
    }

    private fun currentSummarySource(): String? {
        val state = _uiState.value
        val topic = state.topic ?: return null
        if (!state.moderationReady || state.isTopicHidden) return null
        return summarySource(topic, state.replies)
    }

    private fun cancelSummaryIfSourceChanged(source: String?) {
        val activeSource = summaryRequestSource ?: return
        if (activeSource == source) return
        summaryGeneration += 1
        summaryRequestSource = null
        summaryJob?.cancel()
        summaryJob = null
        _uiState.update { state ->
            if (state.isGeneratingSummary) state.copy(isGeneratingSummary = false) else state
        }
    }

    // MARK: 阅读进度

    private suspend fun restoreReadingPosition() {
        // 外部链接的 #replyN 是用户本次明确意图，优先级高于本地保存的阅读进度。
        if (initialFloor != null) return
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
        _uiState.update { it.copy(onlyPoster = !it.onlyPoster, onlyMine = false) }
    }

    fun toggleOnlyMine() {
        _uiState.update { state ->
            if (state.currentUsername.isNullOrBlank()) {
                state.copy(message = "“只看与我有关”需要先连接 V2EX 网页账号")
            } else {
                state.copy(onlyMine = !state.onlyMine, onlyPoster = false)
            }
        }
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

    // MARK: 举报与屏蔽

    /**
     * Reporting is local-first: the selected row is hidden and an outbox item is written before
     * this action completes. An optional website block is attempted afterwards, just like iOS.
     */
    fun report(
        requestedTarget: TopicModerationTarget,
        reason: ReportReason,
        note: String,
        alsoBlockAuthor: Boolean,
    ) {
        val target = canonicalTarget(requestedTarget) ?: run {
            _uiState.update { it.copy(message = "内容已更新，请重新选择举报对象") }
            return
        }
        if (_uiState.value.moderationActionKey != null) return
        val actionKey = "report:${target.key}"
        _uiState.update { it.copy(moderationActionKey = actionKey, moderationCompletion = null) }
        viewModelScope.launch {
            val wasAlreadyHidden = when (target.targetType) {
                ReportTargetType.TOPIC -> target.targetId in moderationRules?.hiddenTopicIds.orEmpty()
                ReportTargetType.REPLY -> target.targetId in moderationRules?.hiddenReplyIds.orEmpty()
                ReportTargetType.MEMBER -> false
            }
            val reportResult = runCatching {
                when (target.targetType) {
                    ReportTargetType.TOPIC -> moderationStore.reportTopic(
                        topicId = target.targetId,
                        author = target.author,
                        excerpt = target.excerpt,
                        reason = reason,
                        note = note.trim().take(1_000).ifBlank { null },
                    )
                    ReportTargetType.REPLY -> moderationStore.reportReply(
                        replyId = target.targetId,
                        topicId = target.topicId,
                        author = target.author,
                        excerpt = target.excerpt,
                        reason = reason,
                        note = note.trim().take(1_000).ifBlank { null },
                    )
                    ReportTargetType.MEMBER -> error("话题页不支持用户级举报")
                }
            }
            if (reportResult.isFailure) {
                // A partial Room failure must not leave content hidden without its outbox record.
                if (!wasAlreadyHidden) {
                    runCatching {
                        when (target.targetType) {
                            ReportTargetType.TOPIC -> moderationStore.unhideTopic(target.targetId)
                            ReportTargetType.REPLY -> moderationStore.unhideReply(target.targetId)
                            ReportTargetType.MEMBER -> Unit
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        moderationActionKey = null,
                        message = "举报保存失败，内容未隐藏，请稍后重试",
                    )
                }
                return@launch
            }

            val blockResult = if (alsoBlockAuthor) {
                moderationStore.blockUserAndReport(
                    username = target.author,
                    targetType = target.targetType,
                    targetId = target.targetId.toString(),
                    topicId = target.topicId,
                    excerpt = target.excerpt,
                )
            } else {
                null
            }
            val message = when {
                blockResult == null -> "已举报，内容已隐藏"
                blockResult.isSuccess -> "已举报，并在官网屏蔽 @${target.author}"
                else -> "已举报并隐藏；${blockResult.exceptionOrNull().safeMessage("官网屏蔽失败，请稍后重试")}"
            }
            _uiState.update {
                it.copy(
                    moderationActionKey = null,
                    moderationCompletion = TopicModerationCompletion(
                        targetKey = target.key,
                        closeTopic = target.targetType == ReportTargetType.TOPIC,
                    ),
                    message = message,
                )
            }
        }
    }

    /** Website confirmation is authoritative; no local filtering happens on a failed block. */
    fun blockAuthor(requestedTarget: TopicModerationTarget) {
        val target = canonicalTarget(requestedTarget) ?: run {
            _uiState.update { it.copy(message = "内容已更新，请重新选择屏蔽对象") }
            return
        }
        if (_uiState.value.moderationActionKey != null) return
        blockPrecondition(target)?.let { message ->
            _uiState.update { it.copy(message = message) }
            return
        }

        val actionKey = "block:${target.key}"
        _uiState.update { it.copy(moderationActionKey = actionKey, moderationCompletion = null) }
        viewModelScope.launch {
            moderationStore.blockUserAndReport(
                username = target.author,
                targetType = target.targetType,
                targetId = target.targetId.toString(),
                topicId = target.topicId,
                excerpt = target.excerpt,
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            moderationActionKey = null,
                            moderationCompletion = TopicModerationCompletion(
                                targetKey = target.key,
                                closeTopic = target.targetType == ReportTargetType.TOPIC,
                            ),
                            message = "已在官网屏蔽 @${target.author}",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            moderationActionKey = null,
                            message = error.safeMessage("官网屏蔽失败，内容未隐藏，请稍后重试"),
                        )
                    }
                },
            )
        }
    }

    fun consumeModerationCompletion() {
        _uiState.update { it.copy(moderationCompletion = null) }
    }

    fun restoreHiddenTopic() {
        if (_uiState.value.moderationActionKey != null) return
        val actionKey = "unhide:topic:$topicId"
        _uiState.update { it.copy(moderationActionKey = actionKey) }
        viewModelScope.launch {
            runCatching { moderationStore.unhideTopic(topicId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            moderationActionKey = null,
                            message = "已恢复本机显示，并删除本地举报记录",
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            moderationActionKey = null,
                            message = "恢复显示失败，请稍后重试",
                        )
                    }
                }
        }
    }

    private fun canonicalTarget(requested: TopicModerationTarget): TopicModerationTarget? =
        when (requested.targetType) {
            ReportTargetType.TOPIC -> _uiState.value.topic
                ?.takeIf { it.id == requested.targetId && it.id == topicId }
                ?.let { topic ->
                    val body = topic.content.orEmpty().ifBlank {
                        htmlToPlainText(topic.contentRendered.orEmpty())
                    }
                    TopicModerationTarget(
                        targetType = ReportTargetType.TOPIC,
                        targetId = topic.id,
                        topicId = topic.id,
                        author = topic.authorName,
                        excerpt = "${topic.title}\n$body".trim().take(MODERATION_EXCERPT_LIMIT),
                    )
                }
            ReportTargetType.REPLY -> threadedReplies
                .firstOrNull { it.reply.id == requested.targetId }
                ?.reply
                ?.let { reply ->
                    val body = reply.content.ifBlank { htmlToPlainText(reply.contentRendered) }
                    TopicModerationTarget(
                        targetType = ReportTargetType.REPLY,
                        targetId = reply.id,
                        topicId = topicId,
                        author = reply.authorName,
                        excerpt = body.take(MODERATION_EXCERPT_LIMIT),
                    )
                }
            ReportTargetType.MEMBER -> null
        }

    private fun blockPrecondition(target: TopicModerationTarget): String? {
        if (target.author.isBlank()) return "无法识别这条内容的作者"
        if (!secureStore.isWebSessionActive || secureStore.sessionUsername.isNullOrBlank()) {
            return "屏蔽作者需要先在「我的」中登录 V2EX 网页账号；仅填写 Token 不够"
        }
        if (target.author.equals(secureStore.sessionUsername, ignoreCase = true)) return "不能屏蔽自己"
        if (target.author.lowercase(Locale.ROOT) in _uiState.value.blockedUsernames) {
            return "@${target.author} 已在官网屏蔽名单中"
        }
        return null
    }

    private fun Throwable?.safeMessage(fallback: String): String {
        val candidate = this?.message?.trim().orEmpty()
        return candidate.takeIf {
            it.isNotEmpty() && it.length <= 200 && !SENSITIVE_ERROR_PATTERN.containsMatchIn(it)
        } ?: fallback
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
