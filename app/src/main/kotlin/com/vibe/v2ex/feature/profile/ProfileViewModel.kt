package com.vibe.v2ex.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.local.FavoriteTopicDao
import com.vibe.v2ex.data.local.HistoryDao
import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.moderation.ModerationStore
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.V2exApiV2
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isTokenSet: Boolean = false,
    val member: Member? = null,
    /** 最近发布 preview — first 5 topics by the current member, plain rows. */
    val recentTopics: List<Topic> = emptyList(),
    val favoriteCount: Int = 0,
    val historyCount: Int = 0,
    val moderationCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val apiV2: V2exApiV2,
    private val apiV1: V2exApiV1,
    private val secureStore: SecureStore,
    favoriteTopicDao: FavoriteTopicDao,
    historyDao: HistoryDao,
    moderationStore: ModerationStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState(isTokenSet = secureStore.isTokenSet))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            favoriteTopicDao.observeAll().collect { favorites ->
                _uiState.value = _uiState.value.copy(favoriteCount = favorites.size)
            }
        }
        viewModelScope.launch {
            historyDao.observeAll().collect { entries ->
                _uiState.value = _uiState.value.copy(historyCount = entries.size)
            }
        }
        viewModelScope.launch {
            moderationStore.moderationCount.collect { count ->
                _uiState.value = _uiState.value.copy(moderationCount = count)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (!secureStore.isTokenSet) {
                _uiState.value = _uiState.value.copy(
                    isTokenSet = false,
                    member = null,
                    recentTopics = emptyList(),
                    isLoading = false,
                    error = null,
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                isTokenSet = true,
                isLoading = _uiState.value.member == null,
                error = null,
            )
            runCatching {
                val envelope = apiV2.me()
                envelope.result ?: error(envelope.message ?: "接口没有返回内容")
            }.onSuccess { member ->
                _uiState.value = _uiState.value.copy(member = member, isLoading = false)
                loadRecentTopics(member.username)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = throwable.message)
            }
        }
    }

    private fun loadRecentTopics(username: String) {
        if (username.isBlank()) return
        viewModelScope.launch {
            runCatching { apiV1.topicsByMember(username).take(5) }
                .onSuccess { topics -> _uiState.value = _uiState.value.copy(recentTopics = topics) }
        }
    }
}
