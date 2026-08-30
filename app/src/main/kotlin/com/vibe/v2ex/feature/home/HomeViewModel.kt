package com.vibe.v2ex.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.FollowedNodesStore
import com.vibe.v2ex.data.datastore.ReadStateStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.nodes.NodeCatalog
import com.vibe.v2ex.data.repository.HomeRepository
import com.vibe.v2ex.data.repository.NodesRepository
import com.vibe.v2ex.data.repository.OfflineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    data object Following : HomeFeed {
        override val key = "following"
        override val title = "关注"
    }

    data class Node(val name: String, override val title: String) : HomeFeed {
        override val key: String get() = "node:$name"
    }
}

data class HomeUiState(
    val feeds: List<HomeFeed> = listOf(HomeFeed.All, HomeFeed.Hot, HomeFeed.Following),
    val currentIndex: Int = 0,
    val topicsByFeed: Map<String, List<Topic>> = emptyMap(),
    val loadingFeeds: Set<String> = emptySet(),
    val errorsByFeed: Map<String, String> = emptyMap(),
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
    private val nodesRepository: NodesRepository,
    private val followedNodesStore: FollowedNodesStore,
    readStateStore: ReadStateStore,
    settingsDataStore: SettingsDataStore,
    offlineRepository: OfflineRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var followedNames: List<String> = emptyList()

    init {
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
            offlineRepository.observeAll()
                .map { bundles -> bundles.mapTo(mutableSetOf()) { it.topic.id } }
                .collect { ids -> _uiState.update { it.copy(offlineIds = ids) } }
        }
        viewModelScope.launch {
            followedNodesStore.names.collect { names ->
                followedNames = names
                val liveTitles = nodesRepository.cachedTitleMap()
                val nodeFeeds = names.take(MAX_NODE_CHIPS)
                    .map { HomeFeed.Node(it, NodeCatalog.displayName(it, liveTitles)) }
                _uiState.update { state ->
                    val feeds = listOf(HomeFeed.All, HomeFeed.Hot, HomeFeed.Following) + nodeFeeds
                    state.copy(
                        feeds = feeds,
                        currentIndex = state.currentIndex.coerceIn(0, feeds.lastIndex),
                    )
                }
                loadIfNeeded(_uiState.value.currentFeed)
            }
        }
    }

    fun selectFeed(index: Int) {
        val state = _uiState.value
        if (index !in state.feeds.indices) return
        if (index != state.currentIndex) {
            _uiState.update { it.copy(currentIndex = index) }
        }
        loadIfNeeded(state.feeds[index])
    }

    fun refresh(feed: HomeFeed) = load(feed, force = true)

    private fun loadIfNeeded(feed: HomeFeed) {
        if (feed.key in _uiState.value.topicsByFeed) return
        load(feed, force = false)
    }

    private fun load(feed: HomeFeed, force: Boolean) {
        val state = _uiState.value
        if (feed.key in state.loadingFeeds) return
        if (!force && feed.key in state.topicsByFeed) return
        _uiState.update {
            it.copy(loadingFeeds = it.loadingFeeds + feed.key, errorsByFeed = it.errorsByFeed - feed.key)
        }
        viewModelScope.launch {
            val result = when (feed) {
                HomeFeed.All -> repository.latestTopics()
                HomeFeed.Hot -> repository.hotTopics()
                HomeFeed.Following -> repository.followingTopics(followedNames)
                is HomeFeed.Node -> repository.topicsInNode(feed.name)
            }
            // 用户已切走该 feed 时丢弃过期的在途结果，避免旧响应覆盖当前页
            if (_uiState.value.currentFeed.key != feed.key) {
                _uiState.update { it.copy(loadingFeeds = it.loadingFeeds - feed.key) }
                return@launch
            }
            result
                .onSuccess { topics ->
                    _uiState.update {
                        it.copy(
                            topicsByFeed = it.topicsByFeed + (feed.key to topics.filterNot(::isPromotion)),
                            loadingFeeds = it.loadingFeeds - feed.key,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loadingFeeds = it.loadingFeeds - feed.key,
                            errorsByFeed = it.errorsByFeed + (feed.key to (error.message ?: "加载失败")),
                        )
                    }
                }
        }
    }

    /** Cosmetic home-feed-only ad filter (hides entirely) — same keyword list as iOS. */
    private fun isPromotion(topic: Topic): Boolean {
        val haystack = "${topic.title} ${topic.authorName}".lowercase()
        return PROMOTION_KEYWORDS.any { haystack.contains(it) }
    }

    private companion object {
        const val MAX_NODE_CHIPS = 8
        val PROMOTION_KEYWORDS = listOf(
            "邀请码", "免费送", "动态住宅", "住宅 ip", "住宅ip", "流量用不完", "注册送", "返利",
        )
    }
}
