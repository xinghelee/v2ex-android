package com.vibe.v2ex.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.AppTheme
import com.vibe.v2ex.data.datastore.DarkModePreference
import com.vibe.v2ex.data.datastore.LineSpacingPreference
import com.vibe.v2ex.data.datastore.MonoFontPreference
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.repository.OfflineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val theme: AppTheme = AppTheme.EMERALD,
    val darkMode: DarkModePreference = DarkModePreference.SYSTEM,
    val fontSize: Float = 14f,
    val lineSpacing: LineSpacingPreference = LineSpacingPreference.RELAXED,
    val monoFont: MonoFontPreference = MonoFontPreference.SF_MONO,
    val dimReadTopics: Boolean = false,
    val rememberReadingPosition: Boolean = true,
    val autoSyncFollowedNodes: Boolean = true,
    val autoOfflineFollowedNodes: Boolean = true,
    val offlineOnWifiOnly: Boolean = true,
    val communityPulseEnabled: Boolean = true,
    /** 离线缓存占用（JSON 字节近似值），供「清空缓存」行展示。 */
    val cacheByteSize: Int = 0,
    val isWebSessionActive: Boolean = false,
    val sessionUsername: String? = null,
    val isTokenSet: Boolean = false,
    val isDeepSeekConfigured: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val offlineRepository: OfflineRepository,
    private val secureStore: SecureStore,
) : ViewModel() {
    private data class Appearance(
        val theme: AppTheme,
        val darkMode: DarkModePreference,
        val fontSize: Float,
        val lineSpacing: LineSpacingPreference,
        val monoFont: MonoFontPreference,
    )

    private data class Reading(
        val dimReadTopics: Boolean,
        val rememberReadingPosition: Boolean,
        val autoSync: Boolean,
        val autoOffline: Boolean,
        val wifiOnly: Boolean,
    )

    private val appearance = combine(
        settingsDataStore.theme,
        settingsDataStore.darkMode,
        settingsDataStore.fontSize,
        settingsDataStore.lineSpacing,
        settingsDataStore.monoFont,
    ) { theme, darkMode, fontSize, lineSpacing, monoFont ->
        Appearance(theme, darkMode, fontSize, lineSpacing, monoFont)
    }

    private val reading = combine(
        settingsDataStore.dimReadTopics,
        settingsDataStore.rememberReadingPosition,
        settingsDataStore.autoSyncFollowedNodes,
        settingsDataStore.autoOfflineFollowedNodes,
        settingsDataStore.offlineOnWifiOnly,
    ) { dim, remember, autoSync, autoOffline, wifiOnly ->
        Reading(dim, remember, autoSync, autoOffline, wifiOnly)
    }

    private val refreshSession = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        appearance,
        reading,
        offlineRepository.observeAll().map { bundles -> bundles.sumOf { it.byteSize } },
        settingsDataStore.communityPulseEnabled,
        refreshSession,
    ) { appearance, reading, cacheBytes, communityPulseEnabled, _ ->
        SettingsUiState(
            theme = appearance.theme,
            darkMode = appearance.darkMode,
            fontSize = appearance.fontSize,
            lineSpacing = appearance.lineSpacing,
            monoFont = appearance.monoFont,
            dimReadTopics = reading.dimReadTopics,
            rememberReadingPosition = reading.rememberReadingPosition,
            autoSyncFollowedNodes = reading.autoSync,
            autoOfflineFollowedNodes = reading.autoOffline,
            offlineOnWifiOnly = reading.wifiOnly,
            communityPulseEnabled = communityPulseEnabled,
            cacheByteSize = cacheBytes,
            isWebSessionActive = secureStore.isWebSessionActive,
            sessionUsername = secureStore.sessionUsername,
            isTokenSet = secureStore.isTokenSet,
            isDeepSeekConfigured = secureStore.isDeepSeekConfigured,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** 从「账号」页返回后刷新登录状态展示。 */
    fun refreshSessionState() {
        refreshSession.value++
    }

    fun setTheme(theme: AppTheme) = viewModelScope.launch { settingsDataStore.setTheme(theme) }
    fun setDarkMode(mode: DarkModePreference) = viewModelScope.launch { settingsDataStore.setDarkMode(mode) }
    fun setFontSize(size: Float) = viewModelScope.launch { settingsDataStore.setFontSize(size) }
    fun setLineSpacing(pref: LineSpacingPreference) = viewModelScope.launch { settingsDataStore.setLineSpacing(pref) }
    fun setMonoFont(pref: MonoFontPreference) = viewModelScope.launch { settingsDataStore.setMonoFont(pref) }
    fun setDimReadTopics(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setDimReadTopics(enabled) }
    fun setRememberReadingPosition(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setRememberReadingPosition(enabled) }
    fun setAutoSyncFollowedNodes(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setAutoSyncFollowedNodes(enabled) }
    fun setAutoOfflineFollowedNodes(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setAutoOfflineFollowedNodes(enabled) }
    fun setOfflineOnWifiOnly(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setOfflineOnWifiOnly(enabled) }
    fun setCommunityPulseEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setCommunityPulseEnabled(enabled) }

    fun clearCache() = viewModelScope.launch { offlineRepository.clear() }

    fun saveDeepSeekApiKey(key: String) {
        secureStore.deepSeekApiKey = key
        refreshSession.value++
    }

    fun clearDeepSeekApiKey() {
        secureStore.clearDeepSeekApiKey()
        refreshSession.value++
    }
}
