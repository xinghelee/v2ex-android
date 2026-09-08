package com.vibe.v2ex.feature.nodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vibe.v2ex.data.datastore.FollowedNodesStore
import com.vibe.v2ex.data.datastore.ReadStateStore
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.moderation.ModerationStore
import com.vibe.v2ex.data.nodes.NodeCatalog
import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.data.remote.WebSessionService
import com.vibe.v2ex.data.repository.FeedCacheRepository
import com.vibe.v2ex.data.repository.NodesRepository
import com.vibe.v2ex.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Client-side sorts over the accumulated list — no re-fetch on switch (mirrors iOS NodeDetail). */
enum class NodeTopicsSort(val label: String) {
    LAST_REPLY("最新回复"),
    NEWEST("最新发布"),
    WEEKLY_HOT("本周最热"),
}

data class NodeTopicsUiState(
    val nodeName: String = "",
    val nodeTitle: String = "",
    /** 节点简介（v1 show.json 的 header，HTML）；无则不显示。 */
    val nodeHeader: String? = null,
    /** Real node artwork from show.json / topic payload; null intentionally uses the accent glyph fallback. */
    val nodeAvatarUrl: String? = null,
    val topicsCount: Int? = null,
    val starsCount: Int? = null,
    val raw: List<Topic> = emptyList(),
    /** [raw] filtered through ModerationStore; content rows must only read this collection. */
    val visibleRaw: List<Topic> = emptyList(),
    val sort: NodeTopicsSort = NodeTopicsSort.LAST_REPLY,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val reachedEnd: Boolean = false,
    /** Advances for every successful page, even when that page only overlaps existing IDs. */
    val paginationToken: Long = 0,
    val isFollowed: Boolean = false,
    /** 非空 = 当前列表来自本地快照（断网），值是快照时间。 */
    val cachedAt: Long? = null,
    val readIds: Set<Long> = emptySet(),
    val dimReadTopics: Boolean = false,
    val error: String? = null,
) {
    val visibleTopics: List<Topic>
        get() = when (sort) {
            NodeTopicsSort.LAST_REPLY -> visibleRaw.sortedByDescending { it.activityTimestamp }
            // Public website rows omit `created`; topic IDs retain publication order.
            NodeTopicsSort.NEWEST -> visibleRaw.sortedByDescending { it.id }
            NodeTopicsSort.WEEKLY_HOT -> {
                val cutoff = System.currentTimeMillis() / 1000 - 7 * 86_400
                val recent = visibleRaw.filter { it.activityTimestamp >= cutoff }
                recent.ifEmpty { visibleRaw }.sortedByDescending { it.replies }
            }
        }

    /** Compatibility name used by the screen; it deliberately exposes only moderated content. */
    val topics: List<Topic> get() = visibleTopics
}

private data class TopicModerationRules(
    val hiddenTopicIds: List<Long>,
    val blockedUsernames: List<String>,
    val unavailableMemberIds: List<Long>,
)

@HiltViewModel
class NodeTopicsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiV2: V2exApiV2,
    private val webSessionService: WebSessionService,
    private val secureStore: SecureStore,
    private val feedCacheRepository: FeedCacheRepository,
    private val followedNodesStore: FollowedNodesStore,
    private val nodesRepository: NodesRepository,
    private val moderationStore: ModerationStore,
    readStateStore: ReadStateStore,
    settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private val nodeName: String = savedStateHandle.toRoute<Route.NodeTopics>().nodeName

    private val _uiState = MutableStateFlow(
        NodeTopicsUiState(nodeName = nodeName, nodeTitle = NodeCatalog.displayName(nodeName)),
    )
    val uiState: StateFlow<NodeTopicsUiState> = _uiState.asStateFlow()

    /** 与 HomeFeed.Node.key 同格式 —— 首页节点 chip 与本页共用同一份快照。 */
    private val feedKey = "node:$nodeName"

    private var page = 1
    private var usingPublicWebsite = true
    private var hasLiveCursor = false
    private var generation = 0L

    private data class TopicPage(val topics: List<Topic>, val hasMore: Boolean)

    /** Null until all three Room-backed rule streams have emitted, preventing blocked content from flashing. */
    private var moderationRules: TopicModerationRules? = null

    init {
        viewModelScope.launch {
            combine(
                moderationStore.hiddenTopicIds,
                moderationStore.blockedUsernames,
                moderationStore.unavailableBlockedMemberIds,
            ) { hiddenTopicIds, blockedUsernames, unavailableMemberIds ->
                TopicModerationRules(hiddenTopicIds, blockedUsernames, unavailableMemberIds)
            }.collect { rules ->
                moderationRules = rules
                _uiState.update(::applyModeration)
            }
        }
        refresh()
        loadNodeInfo()
        viewModelScope.launch {
            followedNodesStore.names.collect { names ->
                _uiState.update { it.copy(isFollowed = nodeName in names) }
            }
        }
        viewModelScope.launch {
            readStateStore.readIds.collect { ids -> _uiState.update { it.copy(readIds = ids) } }
        }
        viewModelScope.launch {
            settingsDataStore.dimReadTopics.collect { dim -> _uiState.update { it.copy(dimReadTopics = dim) } }
        }
    }

    /** 节点详情（简介 + 话题/关注数）— 失败静默，头卡只是少一段文案。 */
    private fun loadNodeInfo() {
        viewModelScope.launch {
            nodesRepository.node(nodeName).onSuccess { node ->
                _uiState.update { state ->
                    state.copy(
                        nodeTitle = node.title.takeIf(String::isNotBlank) ?: state.nodeTitle,
                        nodeHeader = node.header?.takeIf(String::isNotBlank),
                        nodeAvatarUrl = node.avatarUrl ?: state.nodeAvatarUrl,
                        topicsCount = node.topics,
                        starsCount = node.stars,
                    )
                }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore) return
        val request = ++generation
        val usePublicWebsite = !secureStore.isTokenSet
        _uiState.update { it.copy(isLoading = true, error = null, loadMoreError = null) }
        viewModelScope.launch {
            // 断网时先把上次的快照放出来，列表里的帖子正文多半也已经离线了。
            if (_uiState.value.raw.isEmpty()) {
                feedCacheRepository.load(feedKey)?.let { cached ->
                    _uiState.update { state ->
                        if (state.raw.isEmpty()) {
                            applyModeration(state.copy(raw = cached.topics, cachedAt = cached.updatedAt))
                        } else {
                            state
                        }
                    }
                }
            }

            val result = runCatching { fetchPage(page = 1, publicWebsite = usePublicWebsite) }
            if (generation != request) return@launch
            result.onSuccess { fetched ->
                usingPublicWebsite = usePublicWebsite
                hasLiveCursor = true
                page = 1
                _uiState.update { state ->
                    applyModeration(state.copy(
                        raw = fetched.topics,
                        isLoading = false,
                        cachedAt = null,
                        reachedEnd = !fetched.hasMore,
                        paginationToken = state.paginationToken + 1,
                        nodeTitle = fetched.topics.firstOrNull()?.node?.title
                            // Public `/go/` rows may only carry the injected slug; never let that
                            // overwrite richer live/catalog metadata that arrived concurrently.
                            ?.takeIf { it.isNotBlank() && it != nodeName } ?: state.nodeTitle,
                        nodeAvatarUrl = fetched.topics.firstOrNull()?.node?.avatarUrl ?: state.nodeAvatarUrl,
                    ))
                }
                runCatching { feedCacheRepository.save(feedKey, fetched.topics) }
            }.onFailure { error ->
                _uiState.update {
                    // A disk snapshot has no trustworthy cursor; keep it readable but never page from it.
                    it.copy(
                        isLoading = false,
                        reachedEnd = if (hasLiveCursor) it.reachedEnd else true,
                        error = if (it.raw.isEmpty()) error.message ?: "加载失败" else null,
                    )
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.reachedEnd || !hasLiveCursor) return
        val request = generation
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            val nextPage = page + 1
            runCatching { fetchPage(page = nextPage, publicWebsite = usingPublicWebsite) }
                .onSuccess { fetched ->
                    if (generation != request) return@onSuccess
                    val current = _uiState.value
                    val known = current.raw.mapTo(HashSet()) { it.id }
                    val merged = current.raw + fetched.topics.filter { known.add(it.id) }
                    page = nextPage
                    _uiState.update { latest ->
                        applyModeration(latest.copy(
                            raw = merged,
                            isLoadingMore = false,
                            loadMoreError = null,
                            reachedEnd = !fetched.hasMore,
                            // A page can be non-empty but contribute zero unique topics.
                            paginationToken = latest.paginationToken + 1,
                        ))
                    }
                    runCatching { feedCacheRepository.save(feedKey, merged) }
                }
                .onFailure { error ->
                    if (generation != request) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            loadMoreError = error.message
                                ?.takeIf(String::isNotBlank)
                                ?: "加载更多失败，请重试",
                        )
                    }
                }
        }
    }

    fun setSort(sort: NodeTopicsSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun toggleFollow() {
        viewModelScope.launch { followedNodesStore.toggle(nodeName) }
    }

    private fun applyModeration(state: NodeTopicsUiState): NodeTopicsUiState {
        val rules = moderationRules
        val visible = if (rules == null) {
            emptyList()
        } else {
            state.raw.filterNot { topic ->
                moderationStore.isTopicHidden(
                    topic = topic,
                    hiddenIds = rules.hiddenTopicIds,
                    blockedUsers = rules.blockedUsernames,
                    keywords = emptyList(),
                    blockedMemberIds = rules.unavailableMemberIds,
                )
            }
        }
        return state.copy(visibleRaw = visible)
    }

    private suspend fun fetchPage(page: Int, publicWebsite: Boolean): TopicPage {
        if (publicWebsite) {
            return webSessionService.publicTopicPage(nodeName = nodeName, page = page)
                .getOrThrow()
                .let { TopicPage(topics = it.topics, hasMore = it.hasMore) }
        }

        val envelope = apiV2.topicsForNode(nodeName, page = page)
        if (envelope.success == false || envelope.result == null) {
            error(envelope.message ?: "接口没有返回内容")
        }
        val topics = envelope.result.orEmpty()
        // API v2 has no reliable total-page cursor; a non-empty page means probe the next one.
        return TopicPage(topics = topics, hasMore = topics.isNotEmpty())
    }
}
