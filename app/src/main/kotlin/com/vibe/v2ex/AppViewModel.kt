package com.vibe.v2ex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.AppTheme
import com.vibe.v2ex.data.datastore.DarkModePreference
import com.vibe.v2ex.data.datastore.FollowedNodesStore
import com.vibe.v2ex.data.datastore.LineSpacingPreference
import com.vibe.v2ex.data.datastore.MonoFontPreference
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.datastore.UnreadNotificationsStore
import com.vibe.v2ex.data.moderation.ModerationStore
import com.vibe.v2ex.data.remote.WebSessionService
import com.vibe.v2ex.data.repository.AutoOfflineCoordinator
import com.vibe.v2ex.feature.agreement.CURRENT_AGREEMENT_VERSION
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUiState(
    val theme: AppTheme = AppTheme.EMERALD,
    val darkMode: DarkModePreference = DarkModePreference.SYSTEM,
    val readingFontSize: Float = 14f,
    val readingLineSpacing: LineSpacingPreference = LineSpacingPreference.RELAXED,
    val readingMonoFont: MonoFontPreference = MonoFontPreference.SF_MONO,
    val agreementAccepted: Boolean = true,
    val liquidGlassEnabled: Boolean = true,
)

private data class AppearanceState(
    val theme: AppTheme,
    val darkMode: DarkModePreference,
    val fontSize: Float,
    val lineSpacing: LineSpacingPreference,
    val monoFont: MonoFontPreference,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsDataStore: SettingsDataStore,
    private val secureStore: SecureStore,
    private val webSessionService: WebSessionService,
    private val followedNodesStore: FollowedNodesStore,
    private val autoOfflineCoordinator: AutoOfflineCoordinator,
    private val moderationStore: ModerationStore,
    private val unreadNotificationsStore: UnreadNotificationsStore,
) : ViewModel() {
    private val appearance = combine(
        settingsDataStore.theme,
        settingsDataStore.darkMode,
        settingsDataStore.fontSize,
        settingsDataStore.lineSpacing,
        settingsDataStore.monoFont,
    ) { theme, darkMode, fontSize, lineSpacing, monoFont ->
        AppearanceState(theme, darkMode, fontSize, lineSpacing, monoFont)
    }

    val uiState: StateFlow<AppUiState> = combine(
        appearance,
        settingsDataStore.agreedTermsVersion,
        settingsDataStore.liquidGlassEnabled,
    ) { appearance, agreedVersion, liquidGlassEnabled ->
        AppUiState(
            theme = appearance.theme,
            darkMode = appearance.darkMode,
            readingFontSize = appearance.fontSize,
            readingLineSpacing = appearance.lineSpacing,
            readingMonoFont = appearance.monoFont,
            agreementAccepted = agreedVersion >= CURRENT_AGREEMENT_VERSION,
            liquidGlassEnabled = liquidGlassEnabled,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        // 登录后把网页收藏的节点同步到本地（自动同步开关控制，mirrors iOS RootView.task）。
        viewModelScope.launch {
            if (!secureStore.isWebSessionActive) return@launch
            if (!settingsDataStore.autoSyncFollowedNodes.first()) return@launch
            webSessionService.favoriteNodeNames().getOrNull()?.let { remote ->
                followedNodesStore.mergeFromRemote(remote)
            }
        }
        // 关注节点自动离线（开关 / Wi-Fi / 30 分钟节流都在协调器里）。
        viewModelScope.launch {
            autoOfflineCoordinator.sync(followedNodesStore.names.first())
        }
        // 未送达的举报在启动时补发。
        viewModelScope.launch {
            runCatching { moderationStore.flushPending() }
        }
        // 通知角标：启动时刷新一次（之后由通知页自己的刷新维护）。
        viewModelScope.launch { unreadNotificationsStore.refresh() }
    }
}
