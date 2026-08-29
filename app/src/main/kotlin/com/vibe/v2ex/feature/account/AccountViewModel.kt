package com.vibe.v2ex.feature.account

import androidx.lifecycle.ViewModel
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.remote.WebSessionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class AccountUiState(
    val webSessionActive: Boolean = false,
    val sessionUsername: String? = null,
    val personalAccessToken: String = "",
    val showWebLogin: Boolean = false,
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AccountUiState(
            webSessionActive = secureStore.isWebSessionActive,
            sessionUsername = secureStore.sessionUsername,
            personalAccessToken = secureStore.personalAccessToken.orEmpty(),
        ),
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

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
        )
    }

    fun signOutWebSession() {
        webSessionService.signOutWebSession()
        _uiState.value = _uiState.value.copy(webSessionActive = false, sessionUsername = null)
    }

    fun onPatChange(value: String) {
        _uiState.value = _uiState.value.copy(personalAccessToken = value)
        secureStore.personalAccessToken = value.ifBlank { null }
    }
}
