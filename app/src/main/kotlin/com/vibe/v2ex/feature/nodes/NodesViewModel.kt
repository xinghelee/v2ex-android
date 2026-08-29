package com.vibe.v2ex.feature.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.repository.NodesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NodesUiState(
    val allNodes: List<Node> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val filteredNodes: List<Node>
        get() = if (query.isBlank()) {
            allNodes
        } else {
            allNodes.filter { it.title.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
        }
}

@HiltViewModel
class NodesViewModel @Inject constructor(
    private val repository: NodesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NodesUiState())
    val uiState: StateFlow<NodesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.allNodes()
                .onSuccess { nodes -> _uiState.value = _uiState.value.copy(allNodes = nodes, isLoading = false) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }
}
