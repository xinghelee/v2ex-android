package com.vibe.v2ex.feature.write

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vibe.v2ex.data.datastore.FollowedNodesStore
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.WebSessionService
import com.vibe.v2ex.data.repository.DraftRepository
import com.vibe.v2ex.data.repository.NodesRepository
import com.vibe.v2ex.data.repository.TopicRepository
import com.vibe.v2ex.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WriteUiState(
    val topicId: Long? = null,
    val title: String = "",
    val content: String = "",
    val nodes: List<Node> = emptyList(),
    /** 关注的节点名（有序）— 节点选择器无搜索词时默认只列这些（mirrors iOS NodePicker）。 */
    val followedNames: List<String> = emptyList(),
    val selectedNode: Node? = null,
    val isSaving: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    /** 新话题发布成功后拿到的帖子 id — 界面直接跳进去，省得再回首页找。 */
    val publishedTopicId: Long? = null,
    val isWebSessionActive: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WriteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftRepository: DraftRepository,
    private val nodesRepository: NodesRepository,
    private val topicRepository: TopicRepository,
    private val webSessionService: WebSessionService,
    private val secureStore: SecureStore,
    private val apiV1: V2exApiV1,
    followedNodesStore: FollowedNodesStore,
) : ViewModel() {
    private val topicId: Long? = savedStateHandle.toRoute<Route.Write>().topicId

    private val _uiState = MutableStateFlow(
        WriteUiState(topicId = topicId, isWebSessionActive = secureStore.isWebSessionActive),
    )
    val uiState: StateFlow<WriteUiState> = _uiState.asStateFlow()

    private var draftId: Long? = null

    init {
        if (topicId == null) {
            viewModelScope.launch {
                followedNodesStore.names.collect { names ->
                    _uiState.value = _uiState.value.copy(followedNames = names)
                }
            }
        }
        viewModelScope.launch {
            val draft = draftRepository.forTopic(topicId)
            draft?.let {
                draftId = it.id
                _uiState.value = _uiState.value.copy(title = it.title, content = it.content)
            }
            if (topicId == null) {
                nodesRepository.allNodes().onSuccess { nodes ->
                    // 恢复草稿选过的节点；否则默认「分享创造」（mirrors iOS 默认草稿）。
                    val restored = nodes.firstOrNull { it.name == draft?.nodeName }
                        ?: nodes.firstOrNull { it.name == "create" }
                    _uiState.value = _uiState.value.copy(
                        nodes = nodes,
                        selectedNode = _uiState.value.selectedNode ?: restored,
                    )
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

    /** 登录态下走网页表单直接发布（mirrors iOS createTopic）。 */
    fun submitTopic() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val node = state.selectedNode
        if (node == null) {
            _uiState.value = state.copy(error = "请先选择节点")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            webSessionService.createTopic(
                title = state.title,
                content = state.content,
                nodeName = node.name,
                recentTopicIdByTitle = ::recentTopicIdByTitle,
            ).onSuccess { newTopicId ->
                // 只有确认拿到新帖 id 才删草稿 —— 任何不确定的结果都必须把用户写的字留在原地。
                draftRepository.forTopic(null)?.let { draftRepository.delete(it) }
                draftId = null
                _uiState.value = _uiState.value.copy(isSubmitting = false, publishedTopicId = newTopicId)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isSubmitting = false, error = error.message)
            }
        }
    }

    /** 发布未确认时的兜底：V2EX 是否已把这个标题列进我的最近话题。 */
    private suspend fun recentTopicIdByTitle(title: String): Long? {
        val username = secureStore.sessionUsername?.takeIf(String::isNotBlank) ?: return null
        return runCatching { apiV1.topicsByMember(username) }.getOrNull()
            ?.firstOrNull { it.title.trim() == title }
            ?.id
    }
}
