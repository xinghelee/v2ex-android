package com.vibe.v2ex.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.remote.SoV2exHit
import com.vibe.v2ex.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** sov2ex 话题/回复共用同一端点，仅 sort 不同（sumup=话题、created=回复）。 */
enum class SearchScope(val label: String, val sort: String) {
    TOPICS("话题", "sumup"),
    REPLIES("回复", "created"),
}

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.TOPICS,
    val results: List<SoV2exHit> = emptyList(),
    /** 内存态最近搜索（大小写不敏感去重、命中移到最前，上限 12）。 */
    val recents: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.update {
            if (query.isBlank()) {
                it.copy(query = query, results = emptyList(), hasSearched = false, error = null)
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
        rememberRecent(query)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
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
    }

    fun searchRecent(query: String) {
        _uiState.update { it.copy(query = query) }
        search()
    }

    fun removeRecent(query: String) {
        _uiState.update { state ->
            state.copy(recents = state.recents.filterNot { it.equals(query, ignoreCase = true) })
        }
    }

    private fun rememberRecent(query: String) {
        _uiState.update { state ->
            val rest = state.recents.filterNot { it.equals(query, ignoreCase = true) }
            state.copy(recents = (listOf(query) + rest).take(MAX_RECENTS))
        }
    }

    private companion object {
        const val MAX_RECENTS = 12
    }
}
