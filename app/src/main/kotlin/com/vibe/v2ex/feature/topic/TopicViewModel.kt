package com.vibe.v2ex.feature.topic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vibe.v2ex.data.model.Reply
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.repository.TopicRepository
import com.vibe.v2ex.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [floor] is 1-based over replies sorted ascending by id — client-derived, not part of the API payload. */
data class FloorReply(val reply: Reply, val floor: Int)

data class TopicUiState(
    val topic: Topic? = null,
    val replies: List<Reply> = emptyList(),
    val onlyPoster: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    private val floored: List<FloorReply>
        get() = replies.sortedBy { it.id }.mapIndexed { index, reply -> FloorReply(reply, index + 1) }

    val visibleReplies: List<FloorReply>
        get() {
            val authorName = topic?.authorName
            return if (onlyPoster) floored.filter { it.reply.authorName == authorName } else floored
        }
}

@HiltViewModel
class TopicViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TopicRepository,
) : ViewModel() {
    private val topicId: Long = savedStateHandle.toRoute<Route.Topic>().topicId

    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.loadTopic(topicId)
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        topic = detail.topic,
                        replies = detail.replies,
                        isLoading = false,
                    )
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) }
        }
    }

    fun toggleOnlyPoster() {
        _uiState.value = _uiState.value.copy(onlyPoster = !_uiState.value.onlyPoster)
    }
}
