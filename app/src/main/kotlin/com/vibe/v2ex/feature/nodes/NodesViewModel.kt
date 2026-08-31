package com.vibe.v2ex.feature.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.FollowedNodesStore
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.nodes.NodeCatalog
import com.vibe.v2ex.data.nodes.NodeCategory
import com.vibe.v2ex.data.repository.NodesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NodesUiState(
    val allNodes: List<Node> = emptyList(),
    /** Canonical live node lookup. Follow cards and category pages must not invent metadata. */
    val nodesByName: Map<String, Node> = emptyMap(),
    val titlesByName: Map<String, String> = emptyMap(),
    /** 节点头像（all.json 的 avatar 字段），列表与关注卡展示真实图标用。 */
    val avatarsByName: Map<String, String> = emptyMap(),
    val query: String = "",
    val followedNames: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** Client-side substring filter over the whole directory — no server search exists. */
    val searchResults: List<Node>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return emptyList()
            return allNodes
                .filter { node ->
                    node.title.contains(q, ignoreCase = true) ||
                        node.name.contains(q, ignoreCase = true) ||
                        node.titleAlternative?.contains(q, ignoreCase = true) == true
                }
                .sortedByDescending { it.topics ?: 0 }
                .take(40)
        }

    fun displayTitle(name: String): String =
        titlesByName[name]?.takeIf(String::isNotBlank) ?: NodeCatalog.displayName(name)

    fun avatarUrl(name: String): String? = avatarsByName[name]

    fun node(name: String): Node? = nodesByName[name]

    /**
     * Match iOS: use the live directory whenever it has landed; while loading/offline,
     * preserve the complete built-in category with readable stubs.
     */
    fun nodesIn(category: NodeCategory): List<Node> {
        val names = category.nodeNames.distinct()
        if (nodesByName.isNotEmpty()) return names.mapNotNull(nodesByName::get)
        return names.map { name -> Node(name = name, title = displayTitle(name)) }
    }

    fun countIn(category: NodeCategory): Int =
        if (nodesByName.isEmpty()) category.nodeNames.distinct().size
        else category.nodeNames.distinct().count(nodesByName::containsKey)
}

@HiltViewModel
class NodesViewModel @Inject constructor(
    private val repository: NodesRepository,
    private val followedNodesStore: FollowedNodesStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NodesUiState())
    val uiState: StateFlow<NodesUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            followedNodesStore.names.collect { names ->
                _uiState.update { it.copy(followedNames = names) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.allNodes()
                .onSuccess { nodes ->
                    _uiState.update {
                        val byName = nodes.associateBy(Node::name)
                        it.copy(
                            allNodes = nodes,
                            nodesByName = byName,
                            titlesByName = nodes.associate { node -> node.name to node.title },
                            avatarsByName = nodes.mapNotNull { node ->
                                node.avatarUrl?.let { url -> node.name to url }
                            }.toMap(),
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message?.takeIf(String::isNotBlank) ?: "节点目录加载失败",
                        )
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun removeFollowed(name: String) {
        viewModelScope.launch { followedNodesStore.remove(name) }
    }
}
