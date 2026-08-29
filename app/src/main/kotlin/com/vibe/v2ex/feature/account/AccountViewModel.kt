package com.vibe.v2ex.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.FollowedNodesStore
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.remote.WebSessionService
import com.vibe.v2ex.data.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val webSessionActive: Boolean = false,
    val sessionUsername: String? = null,
    val personalAccessToken: String = "",
    val showWebLogin: Boolean = false,
    /** true = 存着登录态但服务器已不认（cookie 过期）；null = 尚未校验或无法校验。 */
    val sessionExpired: Boolean? = null,
)

/**
 * Login itself happens inside an embedded WebView (see WebLoginScreen) — the iOS app's
 * scripted username/password/captcha form exists in V2EXClient but is unreachable dead
 * code in the shipping UI; WKWebView (here, android.webkit.WebView) is the real path
 * since it renders V2EX's own captcha/2FA pages without the app reimplementing them.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val webSessionService: WebSessionService,
    private val secureStore: SecureStore,
    private val favoritesRepository: FavoritesRepository,
    private val followedNodesStore: FollowedNodesStore,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AccountUiState(
            webSessionActive = secureStore.isWebSessionActive,
            sessionUsername = secureStore.sessionUsername,
            personalAccessToken = secureStore.personalAccessToken.orEmpty(),
        ),
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        // 打开账号页时校验会话是否仍被服务器承认 —— cookie 过期时给出重新登录提示，
        // 而不是让「已登录」一直挂着但收藏同步/回复默默失败。
        if (secureStore.isWebSessionActive) {
            viewModelScope.launch {
                val valid = runCatching { webSessionService.verifySession() }.getOrNull()
                // 网络失败（null）不下结论，只有明确的 false 才提示过期。
                if (valid != null) {
                    _uiState.value = _uiState.value.copy(sessionExpired = !valid)
                }
            }
        }
    }

    fun openWebLogin() {
        _uiState.value = _uiState.value.copy(showWebLogin = true)
    }

    fun dismissWebLogin() {
        _uiState.value = _uiState.value.copy(showWebLogin = false)
    }

    fun onWebLoginSuccess(cookieHeader: String, username: String) {
        webSessionService.saveWebSession(cookieHeader, username)
        _uiState.value = _uiState.value.copy(
            webSessionActive = true,
            sessionUsername = username.ifBlank { null },
            showWebLogin = false,
            sessionExpired = false,
        )
        // 登录成功立即把网页收藏（话题 + 关注节点）拉回本地，不用等下次启动。
        viewModelScope.launch { favoritesRepository.syncFromRemote() }
        viewModelScope.launch {
            if (!settingsDataStore.autoSyncFollowedNodes.first()) return@launch
            webSessionService.favoriteNodeNames().getOrNull()?.let { followedNodesStore.mergeFromRemote(it) }
        }
    }

    fun signOutWebSession() {
        webSessionService.signOutWebSession()
        _uiState.value = _uiState.value.copy(
            webSessionActive = false,
            sessionUsername = null,
            sessionExpired = null,
        )
    }

    fun onPatChange(value: String) {
        _uiState.value = _uiState.value.copy(personalAccessToken = value)
        secureStore.personalAccessToken = value.ifBlank { null }
    }
}
