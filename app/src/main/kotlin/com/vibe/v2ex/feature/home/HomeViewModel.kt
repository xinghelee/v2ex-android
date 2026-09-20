package com.vibe.v2ex.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.FollowedNodesStore
import com.vibe.v2ex.data.datastore.ReadStateStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.moderation.ModerationStore
import com.vibe.v2ex.data.nodes.NodeCatalog
import com.vibe.v2ex.data.repository.FeedCacheRepository
import com.vibe.v2ex.data.repository.HomeRepository
import com.vibe.v2ex.data.repository.NodesRepository
import com.vibe.v2ex.data.repository.OfflineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Home feed tabs: 全部 / 最热 / 关注 + one chip per followed node (first 8). */
sealed interface HomeFeed {
    val key: String
    val title: String

    data object All : HomeFeed {
        override val key = "all"
        override val title = "全部"
    }

    data object Hot : HomeFeed {
        override val key = "hot"
        override val title = "最热"
    }

    /** 网页端的投票排序，只有网页有；见 HomeRepository.r2Topics。 */
    data object R2 : HomeFeed {
        override val key = "r2"
        override val title = "R2"
    }

    data object Following : HomeFeed {
        override val key = "following"
        override val title = "关注"
    }

    data class Node(val name: String, override val title: String) : HomeFeed {
        override val key: String get() = "node:$name"
    }
}

data class HomeUiState(
    val feeds: List<HomeFeed> = listOf(HomeFeed.All, HomeFeed.Hot, HomeFeed.R2, HomeFeed.Following),
    val currentIndex: Int = 0,
    val topicsByFeed: Map<String, List<Topic>> = emptyMap(),
    val loadingFeeds: Set<String> = emptySet(),
    val loadingMoreFeeds: Set<String> = emptySet(),
    val errorsByFeed: Map<String, String> = emptyMap(),
    val loadMoreErrorsByFeed: Map<String, String> = emptyMap(),
    val hasMoreByFeed: Map<String, Boolean> = emptyMap(),
    /** Changes after every successful page, including an overlap-only page. */
    val paginationTokensByFeed: Map<String, Long> = emptyMap(),
    /** 该 feed 当前展示的是本地快照（断网 / 还没刷新成功），值是快照时间。 */
    val cachedAtByFeed: Map<String, Long> = emptyMap(),
    /** 已读话题（配合 dimReadTopics 置灰）+ 已离线话题（行尾徽章）。 */
    val readIds: Set<Long> = emptySet(),
    val dimReadTopics: Boolean = false,
    val offlineIds: Set<Long> = emptySet(),
    val communityPulseEnabled: Boolean = true,
) {
    val currentFeed: HomeFeed get() = feeds.getOrElse(currentIndex) { HomeFeed.All }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val feedCacheRepository: FeedCacheRepository,
    private val nodesRepository: NodesRepository,
    private val followedNodesStore: FollowedNodesStore,
    private val moderationStore: ModerationStore,
    readStateStore: ReadStateStore,
    settingsDataStore: SettingsDataStore,
    offlineRepository: OfflineRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private data class FeedSnapshot(
        val topics: List<Topic>,
        val page: Int,
        /** Followed-node sources that still advertise a later page. */
        val sources: List<String>,
        val hasMore: Boolean,
    )

    private data class HomeModerationRules(
        val hiddenTopicIds: List<Long>,
        val blockedUsernames: List<String>,
        val unavailableMemberIds: List<Long>,
    )

    private val snapshots = mutableMapOf<String, FeedSnapshot>()
    /** Raw live or disk-backed rows currently represented in [HomeUiState.topicsByFeed]. */
    private val displayedRawTopics = mutableMapOf<String, List<Topic>>()
    private val requestGenerations = mutableMapOf<String, Long>()
    private var followedNames: List<String> = emptyList()
    private var receivedFollowedNames = false
    private var moderationRules: HomeModerationRules? = null

    init {
        viewModelScope.launch {
            combine(
                moderationStore.hiddenTopicIds,
                moderationStore.blockedUsernames,
                moderationStore.unavailableBlockedMemberIds,
            ) { hiddenTopicIds, blockedUsernames, unavailableMemberIds ->
                HomeModerationRules(hiddenTopicIds, blockedUsernames, unavailableMemberIds)
            }.collect { rules ->
                moderationRules = rules
                _uiState.update { state ->
                    state.copy(
                        topicsByFeed = state.topicsByFeed.mapValues { (feedKey, topics) ->
                            displayedRawTopics[feedKey]?.let(::visibleTopics) ?: topics
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            readStateStore.readIds.collect { ids -> _uiState.update { it.copy(readIds = ids) } }
        }
        viewModelScope.launch {
            settingsDataStore.dimReadTopics.collect { dim -> _uiState.update { it.copy(dimReadTopics = dim) } }
        }
        viewModelScope.launch {
            settingsDataStore.communityPulseEnabled.collect { enabled ->
                _uiState.update { it.copy(communityPulseEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            offlineRepository.observeSummaries()
                .map { summaries -> summaries.mapTo(mutableSetOf()) { it.topicId } }
                .collect { ids -> _uiState.update { it.copy(offlineIds = ids) } }
        }
        viewModelScope.launch {
            followedNodesStore.names.collect { names ->
                val changed = receivedFollowedNames && followedNames != names
                receivedFollowedNames = true
                followedNames = names
                val liveTitles = nodesRepository.cachedTitleMap()
                val nodeFeeds = names.take(MAX_NODE_CHIPS)
                    .map { HomeFeed.Node(it, NodeCatalog.displayName(it, liveTitles)) }
                _uiState.update { state ->
                    val feeds = listOf(HomeFeed.All, HomeFeed.Hot, HomeFeed.R2, HomeFeed.Following) + nodeFeeds
                    val selectedKey = state.currentFeed.key
                    val selectedIndex = feeds.indexOfFirst { it.key == selectedKey }
                    state.copy(
                        feeds = feeds,
                        currentIndex = if (selectedIndex >= 0) {
                            selectedIndex
                        } else {
                            state.currentIndex.coerceIn(0, feeds.lastIndex)
                        },
                    )
                }
                if (changed) invalidateFollowingFeed()
                loadIfNeeded(_uiState.value.currentFeed)
            }
        }
    }

    fun selectFeed(index: Int) {
        val state = _uiState.value
        if (index !in state.feeds.indices) return
        if (index != state.currentIndex) {
            invalidateRequest(state.currentFeed.key)
            _uiState.update { it.copy(currentIndex = index) }
        }
        loadIfNeeded(state.feeds[index])
    }

    fun refresh(feed: HomeFeed) {
        val state = _uiState.value
        if (state.currentFeed.key != feed.key) return
        if (feed.key in state.loadingFeeds || feed.key in state.loadingMoreFeeds) return
        load(feed, force = true)
    }

    private fun loadIfNeeded(feed: HomeFeed) {
        snapshots[feed.key]?.let { snapshot ->
            publishSnapshot(feed.key, snapshot)
            return
        }
        load(feed, force = false)
    }

    private fun load(feed: HomeFeed, force: Boolean) {
        val state = _uiState.value
        if (feed.key in state.loadingFeeds || feed.key in state.loadingMoreFeeds) return
        if (!force) {
            snapshots[feed.key]?.let { snapshot ->
                publishSnapshot(feed.key, snapshot)
                return
            }
        }
        val request = nextGeneration(feed.key)
        val sources = if (feed == HomeFeed.Following) followedNames else emptyList()
        val diskKey = diskCacheKey(feed, sources)
        _uiState.update {
            it.copy(
                loadingFeeds = it.loadingFeeds + feed.key,
                errorsByFeed = it.errorsByFeed - feed.key,
                loadMoreErrorsByFeed = it.loadMoreErrorsByFeed - feed.key,
            )
        }
        viewModelScope.launch {
            // 断网冷启动时先把上次的快照放出来 —— 正文早已离线，缺的只是能点进去的列表。
            if (!force) hydrateFromCache(feed, request, diskKey)

            val result = runCatching { fetch(feed = feed, page = 1, sources = sources) }
            if (!isCurrent(feed.key, request) || _uiState.value.currentFeed.key != feed.key) return@launch
            result.onSuccess { fetched ->
                val snapshot = fetched
                snapshots[feed.key] = snapshot
                displayedRawTopics[feed.key] = snapshot.topics
                // Only a live response owns a cursor. A disk snapshot remains display-only until this succeeds.
                _uiState.update {
                    it.copy(
                        topicsByFeed = it.topicsByFeed + (feed.key to visibleTopics(snapshot.topics)),
                        loadingFeeds = it.loadingFeeds - feed.key,
                        cachedAtByFeed = it.cachedAtByFeed - feed.key,
                        errorsByFeed = it.errorsByFeed - feed.key,
                        hasMoreByFeed = it.hasMoreByFeed + (feed.key to snapshot.hasMore),
                        paginationTokensByFeed = it.paginationTokensByFeed +
                            (feed.key to ((it.paginationTokensByFeed[feed.key] ?: 0L) + 1L)),
                    )
                }
                runCatching { feedCacheRepository.save(diskKey, snapshot.topics) }
            }.onFailure {
                val message = result.exceptionOrNull()?.message ?: "加载失败"
                _uiState.update {
                    it.copy(
                        loadingFeeds = it.loadingFeeds - feed.key,
                        // 有快照就继续显示快照 + 顶部离线提示，不要退回整页报错。
                        errorsByFeed = if (feed.key in it.topicsByFeed) {
                            it.errorsByFeed
                        } else {
                            it.errorsByFeed + (feed.key to message)
                        },
                        hasMoreByFeed = snapshots[feed.key]?.let { snapshot ->
                            it.hasMoreByFeed + (feed.key to snapshot.hasMore)
                        } ?: (it.hasMoreByFeed - feed.key),
                    )
                }
            }
        }
    }

    fun loadMore(feed: HomeFeed) {
        val state = _uiState.value
        val previous = snapshots[feed.key] ?: return
        if (state.currentFeed.key != feed.key || !previous.hasMore) return
        if (feed.key in state.loadingFeeds || feed.key in state.loadingMoreFeeds) return

        val request = nextGeneration(feed.key)
        val diskKey = diskCacheKey(feed, followedNames)
        _uiState.update {
            it.copy(
                loadingMoreFeeds = it.loadingMoreFeeds + feed.key,
                loadMoreErrorsByFeed = it.loadMoreErrorsByFeed - feed.key,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                fetch(feed = feed, page = previous.page + 1, sources = previous.sources)
            }
            if (!isCurrent(feed.key, request) || _uiState.value.currentFeed.key != feed.key) return@launch
            result.onSuccess { fetched ->
                val known = previous.topics.mapTo(HashSet()) { it.id }
                val unique = fetched.topics.filter { known.add(it.id) }
                val snapshot = fetched.copy(topics = previous.topics + unique)
                snapshots[feed.key] = snapshot
                displayedRawTopics[feed.key] = snapshot.topics
                _uiState.update {
                    it.copy(
                        topicsByFeed = it.topicsByFeed + (feed.key to visibleTopics(snapshot.topics)),
                        loadingMoreFeeds = it.loadingMoreFeeds - feed.key,
                        loadMoreErrorsByFeed = it.loadMoreErrorsByFeed - feed.key,
                        cachedAtByFeed = it.cachedAtByFeed - feed.key,
                        hasMoreByFeed = it.hasMoreByFeed + (feed.key to snapshot.hasMore),
                        // Do not key pagination to topic count: a valid page may contain only duplicates.
                        paginationTokensByFeed = it.paginationTokensByFeed +
                            (feed.key to ((it.paginationTokensByFeed[feed.key] ?: 0L) + 1L)),
                    )
                }
                runCatching { feedCacheRepository.save(diskKey, snapshot.topics) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loadingMoreFeeds = it.loadingMoreFeeds - feed.key,
                        loadMoreErrorsByFeed = it.loadMoreErrorsByFeed +
                            (feed.key to (error.message?.takeIf(String::isNotBlank)
                                ?: "加载更多失败，请重试")),
                    )
                }
            }
        }
    }

    private suspend fun fetch(feed: HomeFeed, page: Int, sources: List<String>): FeedSnapshot = when (feed) {
        HomeFeed.Hot -> FeedSnapshot(
            topics = repository.hotTopics().getOrThrow(),
            page = 1,
            sources = emptyList(),
            hasMore = false,
        )
        HomeFeed.R2 -> FeedSnapshot(
            topics = repository.r2Topics().getOrThrow(),
            page = 1,
            sources = emptyList(),
            hasMore = false,
        )
        HomeFeed.All -> repository.publicTopicPage(nodeName = null, page = page).getOrThrow().let {
            FeedSnapshot(topics = it.topics, page = page, sources = emptyList(), hasMore = it.hasMore)
        }
        is HomeFeed.Node -> repository.publicTopicPage(nodeName = feed.name, page = page).getOrThrow().let {
            FeedSnapshot(topics = it.topics, page = page, sources = emptyList(), hasMore = it.hasMore)
        }
        HomeFeed.Following -> {
            if (sources.isEmpty()) {
                repository.publicTopicPage(nodeName = null, page = page).getOrThrow().let {
                    FeedSnapshot(topics = it.topics, page = page, sources = emptyList(), hasMore = it.hasMore)
                }
            } else {
                val merged = ArrayList<Topic>()
                val remaining = ArrayList<String>()
                // One failed source fails the page so the complete cursor can be retried unchanged.
                for (nodeName in sources) {
                    val result = repository.publicTopicPage(nodeName = nodeName, page = page).getOrThrow()
                    merged += result.topics
                    if (result.hasMore) remaining += nodeName
                }
                FeedSnapshot(
                    topics = merged.distinctBy { it.id }.sortedByDescending { it.activityTimestamp },
                    page = page,
                    sources = remaining,
                    hasMore = remaining.isNotEmpty(),
                )
            }
        }
    }

    private suspend fun hydrateFromCache(feed: HomeFeed, request: Long, diskKey: String) {
        if (feed.key in _uiState.value.topicsByFeed) return
        val cached = feedCacheRepository.load(diskKey) ?: return
        if (!isCurrent(feed.key, request) || _uiState.value.currentFeed.key != feed.key) return
        displayedRawTopics[feed.key] = cached.topics
        _uiState.update { state ->
            if (feed.key in state.topicsByFeed) {
                state
            } else {
                state.copy(
                    topicsByFeed = state.topicsByFeed + (feed.key to visibleTopics(cached.topics)),
                    cachedAtByFeed = state.cachedAtByFeed + (feed.key to cached.updatedAt),
                )
            }
        }
    }

    private fun publishSnapshot(feedKey: String, snapshot: FeedSnapshot) {
        displayedRawTopics[feedKey] = snapshot.topics
        _uiState.update {
            it.copy(
                topicsByFeed = it.topicsByFeed + (feedKey to visibleTopics(snapshot.topics)),
                hasMoreByFeed = it.hasMoreByFeed + (feedKey to snapshot.hasMore),
                cachedAtByFeed = it.cachedAtByFeed - feedKey,
                loadMoreErrorsByFeed = it.loadMoreErrorsByFeed - feedKey,
            )
        }
    }

    private fun nextGeneration(feedKey: String): Long {
        val next = (requestGenerations[feedKey] ?: 0L) + 1L
        requestGenerations[feedKey] = next
        return next
    }

    private fun isCurrent(feedKey: String, request: Long): Boolean =
        requestGenerations[feedKey] == request

    private fun invalidateRequest(feedKey: String) {
        nextGeneration(feedKey)
        _uiState.update {
            it.copy(
                loadingFeeds = it.loadingFeeds - feedKey,
                loadingMoreFeeds = it.loadingMoreFeeds - feedKey,
            )
        }
    }

    private fun invalidateFollowingFeed() {
        val key = HomeFeed.Following.key
        snapshots.remove(key)
        displayedRawTopics.remove(key)
        invalidateRequest(key)
        _uiState.update {
            it.copy(
                topicsByFeed = it.topicsByFeed - key,
                errorsByFeed = it.errorsByFeed - key,
                loadMoreErrorsByFeed = it.loadMoreErrorsByFeed - key,
                hasMoreByFeed = it.hasMoreByFeed - key,
                cachedAtByFeed = it.cachedAtByFeed - key,
            )
        }
    }

    /** Followed-feed disk snapshots are source-specific so an edited node list never reuses stale rows. */
    private fun diskCacheKey(feed: HomeFeed, sources: List<String>): String =
        if (feed == HomeFeed.Following) "${feed.key}:${sources.joinToString(",")}" else feed.key

    /** Cosmetic home-feed-only ad filter (hides entirely) — same keyword list as iOS. */
    private fun isPromotion(topic: Topic): Boolean {
        val haystack = "${topic.title} ${topic.authorName}".lowercase(Locale.ROOT)
        return PROMOTION_KEYWORDS.any { haystack.contains(it) }
    }

    private fun visibleTopics(raw: List<Topic>): List<Topic> {
        val rules = moderationRules ?: return emptyList()
        return raw.filterNot { topic ->
            isPromotion(topic) || moderationStore.isTopicHidden(
                topic = topic,
                hiddenIds = rules.hiddenTopicIds,
                blockedUsers = rules.blockedUsernames,
                keywords = emptyList(),
                blockedMemberIds = rules.unavailableMemberIds,
            )
        }
    }

    private companion object {
        const val MAX_NODE_CHIPS = 8
        val PROMOTION_KEYWORDS = listOf(
            "邀请码", "免费送", "动态住宅", "住宅 ip", "住宅ip", "流量用不完", "注册送", "返利",
        )
    }
}
