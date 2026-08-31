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
import com.vibe.v2ex.data.repository.FavoritesRepository
import com.vibe.v2ex.data.repository.OfflineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    /** PAT 或网页会话任占其一就算已连接账号。 */
    val isConnected: Boolean = false,
    val member: Member? = null,
    /** 我发布的话题（完整列表；「最近发布」只展示前 5 条，计数用全量）。 */
    val recentTopics: List<Topic> = emptyList(),
    val favoriteCount: Int = 0,
    val historyCount: Int = 0,
    val offlineCount: Int = 0,
    val moderationCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val apiV2: V2exApiV2,
    private val apiV1: V2exApiV1,
    private val secureStore: SecureStore,
    private val favoritesRepository: FavoritesRepository,
    favoriteTopicDao: FavoriteTopicDao,
    historyDao: HistoryDao,
    offlineRepository: OfflineRepository,
    moderationStore: ModerationStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState(isConnected = secureStore.isSignedIn))
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
            offlineRepository.observeAll().collect { bundles ->
                _uiState.value = _uiState.value.copy(offlineCount = bundles.size)
            }
        }
        viewModelScope.launch {
            moderationStore.moderationCount.collect { count ->
                _uiState.value = _uiState.value.copy(moderationCount = count)
            }
        }
        // 登录态下把网页收藏同步进本地 —— 「我的」页的收藏数因此是账号的真实数据。
        viewModelScope.launch { favoritesRepository.syncFromRemote() }
    }

    fun refresh() {
        viewModelScope.launch {
            if (secureStore.isWebSessionActive) favoritesRepository.syncFromRemote(maxPages = 1)
            val hasToken = secureStore.isTokenSet
            val sessionUsername = secureStore.sessionUsername?.takeIf(String::isNotBlank)
            if (!hasToken && sessionUsername == null) {
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    member = null,
                    recentTopics = emptyList(),
                    isLoading = false,
                    error = null,
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                isConnected = true,
                isLoading = _uiState.value.member == null,
                error = null,
            )
            runCatching {
                // 有 PAT 就走 API 2.0 的「当前用户」；只有网页会话（cookie）时退回
                // 公开的 v1 接口按用户名查 —— 网页登录是安卓的主力登录方式，不能因为
                // 没配 PAT 就把人当访客。
                when {
                    hasToken -> apiV2.me().let { it.result ?: error(it.message ?: "接口没有返回内容") }
                    sessionUsername != null -> apiV1.showMember(sessionUsername)
                    else -> error("未连接账号")
                }
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
            runCatching { apiV1.topicsByMember(username) }
                .onSuccess { topics -> _uiState.value = _uiState.value.copy(recentTopics = topics) }
        }
    }
}
