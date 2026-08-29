package com.vibe.v2ex.feature.write

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.repository.DraftRepository
import com.vibe.v2ex.data.repository.NodesRepository
import com.vibe.v2ex.data.repository.TopicRepository
import com.vibe.v2ex.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WriteUiState(
    val topicId: Long? = null,
    val title: String = "",
    val content: String = "",
    val nodes: List<Node> = emptyList(),
    val selectedNode: Node? = null,
    val isSaving: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WriteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftRepository: DraftRepository,
    private val nodesRepository: NodesRepository,
    private val topicRepository: TopicRepository,
) : ViewModel() {
    private val topicId: Long? = savedStateHandle.toRoute<Route.Write>().topicId

    private val _uiState = MutableStateFlow(WriteUiState(topicId = topicId))
    val uiState: StateFlow<WriteUiState> = _uiState.asStateFlow()

    private var draftId: Long? = null

    init {
        viewModelScope.launch {
            draftRepository.forTopic(topicId)?.let { draft ->
                draftId = draft.id
                _uiState.value = _uiState.value.copy(title = draft.title, content = draft.content)
            }
            if (topicId == null) {
                nodesRepository.allNodes().onSuccess { nodes ->
                    _uiState.value = _uiState.value.copy(nodes = nodes)
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _uiState.value = _uiState.value.copy(title = value)
        autosave()
    }

    fun onContentChange(value: String) {
        _uiState.value = _uiState.value.copy(content = value)
        autosave()
    }

    fun onNodeSelected(node: Node) {
        _uiState.value = _uiState.value.copy(selectedNode = node)
        autosave()
    }

    private fun autosave() {
        val state = _uiState.value
        viewModelScope.launch {
            draftId = draftRepository.save(draftId, topicId, state.title, state.content, state.selectedNode?.name)
        }
    }

    fun submitReply() {
        val state = _uiState.value
        val id = topicId ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isSubmitting = true, error = null)
            topicRepository.postReply(id, state.content)
                .onSuccess { _uiState.value = _uiState.value.copy(isSubmitting = false, submitted = true) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(isSubmitting = false, error = error.message) }
        }
    }
}
