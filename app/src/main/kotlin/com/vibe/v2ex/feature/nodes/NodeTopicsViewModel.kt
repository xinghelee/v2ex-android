package com.vibe.v2ex.feature.nodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vibe.v2ex.data.datastore.FollowedNodesStore
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.nodes.NodeCatalog
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.data.repository.NodesRepository
import com.vibe.v2ex.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val topicsCount: Int? = null,
    val starsCount: Int? = null,
    val raw: List<Topic> = emptyList(),
    val sort: NodeTopicsSort = NodeTopicsSort.LAST_REPLY,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val reachedEnd: Boolean = false,
    val isFollowed: Boolean = false,
    val error: String? = null,
) {
    val topics: List<Topic>
        get() = when (sort) {
            NodeTopicsSort.LAST_REPLY -> raw.sortedByDescending { it.activityTimestamp }
            NodeTopicsSort.NEWEST -> raw.sortedByDescending { it.created ?: 0 }
            NodeTopicsSort.WEEKLY_HOT -> {
                val cutoff = System.currentTimeMillis() / 1000 - 7 * 86_400
                val recent = raw.filter { it.activityTimestamp >= cutoff }
                recent.ifEmpty { raw }.sortedByDescending { it.replies }
            }
        }
}

@HiltViewModel
class NodeTopicsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiV1: V2exApiV1,
    private val apiV2: V2exApiV2,
    private val followedNodesStore: FollowedNodesStore,
    private val nodesRepository: NodesRepository,
) : ViewModel() {
    private val nodeName: String = savedStateHandle.toRoute<Route.NodeTopics>().nodeName

    private val _uiState = MutableStateFlow(
        NodeTopicsUiState(nodeName = nodeName, nodeTitle = NodeCatalog.displayName(nodeName)),
    )
    val uiState: StateFlow<NodeTopicsUiState> = _uiState.asStateFlow()

    private var page = 1

    /** v1 is a single unpaginated batch, so there is never a "next page" in fallback mode. */
    private var usingV1Fallback = false

    init {
        refresh()
        loadNodeInfo()
        viewModelScope.launch {
            followedNodesStore.names.collect { names ->
                _uiState.update { it.copy(isFollowed = nodeName in names) }
            }
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
                        topicsCount = node.topics,
                        starsCount = node.stars,
                    )
                }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // v2 分页接口需要 PAT；失败时退回 v1 的单批不分页结果
            val v2 = runCatching { fetchPageV2(1) }
            val result = v2.recoverCatching { apiV1.topicsInNode(nodeName) }
            result
                .onSuccess { topics ->
                    usingV1Fallback = v2.isFailure
                    page = 1
                    _uiState.update { state ->
                        state.copy(
                            raw = topics,
                            isLoading = false,
                            reachedEnd = usingV1Fallback || topics.isEmpty(),
                            nodeTitle = topics.firstOrNull()?.node?.title
                                ?.takeIf(String::isNotBlank) ?: state.nodeTitle,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "加载失败") }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.reachedEnd || usingV1Fallback) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val nextPage = page + 1
            runCatching { fetchPageV2(nextPage) }
                .onSuccess { more ->
                    page = nextPage
                    _uiState.update { current ->
                        val known = current.raw.mapTo(HashSet()) { it.id }
                        current.copy(
                            raw = current.raw + more.filter { it.id !in known },
                            isLoadingMore = false,
                            reachedEnd = more.isEmpty(),
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    fun setSort(sort: NodeTopicsSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun toggleFollow() {
        viewModelScope.launch { followedNodesStore.toggle(nodeName) }
    }

    private suspend fun fetchPageV2(page: Int): List<Topic> {
        val envelope = apiV2.topicsForNode(nodeName, page = page)
        if (envelope.success == false || envelope.result == null) {
            error(envelope.message ?: "接口没有返回内容")
        }
        return envelope.result.orEmpty()
    }
}
