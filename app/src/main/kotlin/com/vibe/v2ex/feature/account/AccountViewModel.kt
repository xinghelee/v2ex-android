package com.vibe.v2ex.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.FollowedNodesStore
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.data.remote.WebSessionService
import com.vibe.v2ex.data.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

/** 网页登录候选会话的确认结果。 */
enum class WebLoginConfirmation {
    /** 服务端认这份 cookie，已落盘。 */
    ACCEPTED,

    /** 服务端明确不认（还没登录完，或两步验证没走完）—— 登录页留在原地等用户继续。 */
    NOT_SIGNED_IN,

    /** 网络没给出结论。同样不落盘：与其存一份可能是匿名的会话，不如让用户重试。 */
    UNVERIFIED,
}

data class AccountUiState(
    val webSessionActive: Boolean = false,
    val sessionUsername: String? = null,
    /** 输入框只编辑这份内存草稿，验证成功前绝不会写入 SecureStore。 */
    val personalAccessToken: String = "",
    val hasSavedPersonalAccessToken: Boolean = false,
    val isValidatingPersonalAccessToken: Boolean = false,
    val personalAccessTokenStatus: String? = null,
    val personalAccessTokenStatusIsError: Boolean = false,
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
    private val apiV2: V2exApiV2,
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
            hasSavedPersonalAccessToken = secureStore.isTokenSet,
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

    /**
     * WebView 报上来的候选会话。页面长得像已登录不算数 —— 必须拿这份 cookie 请求
     * `/settings` 确认，只有明确通过才落盘。
     *
     * 刮用户名的兜底选择器会匹配到列表里任意一个楼主，所以未登录时逛首页也会产生
     * 候选；网络失败就放行等于把这种匿名会话存下来，因此网络无结论时宁可让用户重试。
     * cookie 刚写入偶尔还没生效，只对「没结论」重试，明确的不通过立即返回（未登录
     * 浏览时每次翻页都要重试三次纯属浪费）。
     */
    suspend fun confirmWebLogin(cookieHeader: String, username: String): WebLoginConfirmation {
        repeat(VERIFY_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(VERIFY_RETRY_DELAY_MS)
            when (webSessionService.verifyCookie(cookieHeader)) {
                true -> {
                    onWebLoginSuccess(cookieHeader, username)
                    return WebLoginConfirmation.ACCEPTED
                }
                false -> return WebLoginConfirmation.NOT_SIGNED_IN
                null -> Unit
            }
        }
        return WebLoginConfirmation.UNVERIFIED
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
        _uiState.value = _uiState.value.copy(
            personalAccessToken = value,
            personalAccessTokenStatus = null,
            personalAccessTokenStatusIsError = false,
        )
    }

    fun verifyAndSavePat() {
        val current = _uiState.value
        if (current.isValidatingPersonalAccessToken) return

        val candidate = current.personalAccessToken.filterNot(Char::isWhitespace)
        if (candidate.isBlank()) {
            _uiState.value = current.copy(
                personalAccessTokenStatus = "请先输入 Personal Access Token",
                personalAccessTokenStatusIsError = true,
            )
            return
        }

        _uiState.value = current.copy(
            isValidatingPersonalAccessToken = true,
            personalAccessTokenStatus = "正在验证 Token…",
            personalAccessTokenStatusIsError = false,
        )

        viewModelScope.launch {
            val result = runCatching { apiV2.me(authorization = "Bearer $candidate") }

            // 验证期间如果草稿已被编辑，旧请求的结果不得覆盖新草稿或已存 Token。
            if (_uiState.value.personalAccessToken.filterNot(Char::isWhitespace) != candidate) {
                _uiState.value = _uiState.value.copy(
                    isValidatingPersonalAccessToken = false,
                    personalAccessTokenStatus = "Token 已更改，请重新验证",
                    personalAccessTokenStatusIsError = true,
                )
                return@launch
            }

            result.onSuccess { envelope ->
                val member = envelope.result
                if (envelope.success == true && member != null && member.username.isNotBlank()) {
                    // 这是候选 Token 唯一的持久化点：服务端验证通过后才替换旧值。
                    secureStore.personalAccessToken = candidate
                    _uiState.value = _uiState.value.copy(
                        personalAccessToken = candidate,
                        hasSavedPersonalAccessToken = true,
                        isValidatingPersonalAccessToken = false,
                        personalAccessTokenStatus = "已验证并保存，连接为 ${member.username}",
                        personalAccessTokenStatusIsError = false,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isValidatingPersonalAccessToken = false,
                        personalAccessTokenStatus = "Token 无效或已失效，未更改已保存的 Token",
                        personalAccessTokenStatusIsError = true,
                    )
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isValidatingPersonalAccessToken = false,
                    personalAccessTokenStatus = patValidationErrorMessage(error),
                    personalAccessTokenStatusIsError = true,
                )
            }
        }
    }

    fun clearSavedPat() {
        if (_uiState.value.isValidatingPersonalAccessToken) return
        secureStore.clearToken()
        _uiState.value = _uiState.value.copy(
            personalAccessToken = "",
            hasSavedPersonalAccessToken = false,
            personalAccessTokenStatus = "已清除保存在本机的 Token",
            personalAccessTokenStatusIsError = false,
        )
    }

    /** 只返回可控文案，不向 UI 传递可能包含请求细节的底层异常信息。 */
    private fun patValidationErrorMessage(error: Throwable): String = when (error) {
        is IOException -> "网络连接失败，未更改已保存的 Token，请检查网络后重试"
        is HttpException -> when (error.code()) {
            401, 403 -> "Token 无效或权限不足，未更改已保存的 Token"
            else -> "服务器暂时无法验证 Token，未更改已保存的 Token"
        }
        else -> "Token 验证失败，未更改已保存的 Token，请稍后重试"
    }

    private companion object {
        const val VERIFY_ATTEMPTS = 3
        const val VERIFY_RETRY_DELAY_MS = 800L
    }
}
