package com.vibe.v2ex.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.RecentSearchStore
import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.remote.SoV2exHit
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.repository.NodesRepository
import com.vibe.v2ex.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 话题/回复走 sov2ex 全文索引（仅 sort 不同：sumup=话题、created=回复）；
 * 用户走 v1 members/show.json 精确查询；节点在 all.json 里本地过滤（mirrors iOS）。
 */
enum class SearchScope(val label: String, val sort: String) {
    TOPICS("话题", "sumup"),
    REPLIES("回复", "created"),
    MEMBERS("用户", ""),
    NODES("节点", ""),
}

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.TOPICS,
    val results: List<SoV2exHit> = emptyList(),
    val memberResult: Member? = null,
    val nodeResults: List<Node> = emptyList(),
    /** 持久化最近搜索（大小写不敏感去重、命中移到最前，上限 12）。 */
    val recents: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val recentSearchStore: RecentSearchStore,
    private val nodesRepository: NodesRepository,
    private val apiV1: V2exApiV1,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recentSearchStore.queries.collect { recents ->
                _uiState.update { it.copy(recents = recents) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update {
            if (query.isBlank()) {
                it.copy(
                    query = query,
                    results = emptyList(),
                    memberResult = null,
                    nodeResults = emptyList(),
                    hasSearched = false,
                    error = null,
                )
            } else {
                it.copy(query = query, error = null)
            }
        }
    }

    fun clearQuery() = onQueryChange("")

    fun setScope(scope: SearchScope) {
        val state = _uiState.value
        if (scope == state.scope) return
        _uiState.update { it.copy(scope = scope) }
        if (state.query.isNotBlank() && state.hasSearched) search()
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        viewModelScope.launch { recentSearchStore.record(query) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (_uiState.value.scope) {
                SearchScope.TOPICS, SearchScope.REPLIES -> searchFullText(query)
                SearchScope.MEMBERS -> searchMember(query)
                SearchScope.NODES -> searchNodes(query)
            }
        }
    }

    private suspend fun searchFullText(query: String) {
        repository.search(query, sort = _uiState.value.scope.sort)
            .onSuccess { hits ->
                _uiState.update { it.copy(results = hits, isLoading = false, hasSearched = true) }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, hasSearched = true, error = error.message ?: "搜索失败")
                }
            }
    }

    private suspend fun searchMember(query: String) {
        val member = runCatching { apiV1.showMember(query) }.getOrNull()
        _uiState.update {
            it.copy(
                memberResult = member,
                isLoading = false,
                hasSearched = true,
                error = if (member == null) "没有找到用户 $query" else null,
            )
        }
    }

    private suspend fun searchNodes(query: String) {
        val nodes = nodesRepository.allNodes().getOrDefault(emptyList())
            .filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.titleAlternative?.contains(query, ignoreCase = true) == true
            }
            .sortedByDescending { it.topics ?: 0 }
        _uiState.update { it.copy(nodeResults = nodes, isLoading = false, hasSearched = true) }
    }

    fun searchRecent(query: String) {
        _uiState.update { it.copy(query = query) }
        search()
    }

    fun removeRecent(query: String) {
        viewModelScope.launch { recentSearchStore.remove(query) }
    }

    fun clearRecents() {
        viewModelScope.launch { recentSearchStore.clear() }
    }
}
