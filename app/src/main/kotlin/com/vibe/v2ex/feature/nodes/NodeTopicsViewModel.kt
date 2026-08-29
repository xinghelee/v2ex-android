package com.vibe.v2ex.feature.nodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NodeTopicsUiState(
    val nodeName: String = "",
    val topics: List<Topic> = emptyList(),
    val page: Int = 1,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class NodeTopicsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiV2: V2exApiV2,
) : ViewModel() {
    private val nodeName: String = savedStateHandle.toRoute<Route.NodeTopics>().nodeName

    private val _uiState = MutableStateFlow(NodeTopicsUiState(nodeName = nodeName))
    val uiState: StateFlow<NodeTopicsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { apiV2.topicsForNode(nodeName, page = 1).result.orEmpty() }
                .onSuccess { topics -> _uiState.value = _uiState.value.copy(topics = topics, page = 1, isLoading = false) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            val nextPage = _uiState.value.page + 1
            runCatching { apiV2.topicsForNode(nodeName, page = nextPage).result.orEmpty() }
                .onSuccess { more ->
                    _uiState.value = _uiState.value.copy(
                        topics = _uiState.value.topics + more,
                        page = nextPage,
                    )
                }
        }
    }
}
