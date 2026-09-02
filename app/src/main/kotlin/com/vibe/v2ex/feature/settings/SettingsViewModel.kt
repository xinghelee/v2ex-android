package com.vibe.v2ex.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.vibe.v2ex.data.datastore.AppTheme
import com.vibe.v2ex.data.datastore.DarkModePreference
import com.vibe.v2ex.data.datastore.FollowedNodesStore
import com.vibe.v2ex.data.datastore.LineSpacingPreference
import com.vibe.v2ex.data.datastore.MonoFontPreference
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.repository.AutoOfflineCoordinator
import com.vibe.v2ex.data.repository.FeedCacheRepository
import com.vibe.v2ex.data.repository.OfflineRepository
import com.vibe.v2ex.data.repository.OfflineSyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val liquidGlassEnabled: Boolean = true,
    val appIcon: AppIcon = AppIcon.BUBBLES,
    /** 离线缓存占用：正文快照（JSON 字节近似值）+ 图片磁盘缓存，供「清空缓存」行展示。 */
    val cacheByteSize: Long = 0,
    /** 已离线的话题数，「立即缓存」行的副标题。 */
    val offlineTopicCount: Int = 0,
    /** 非空 = 正在下载离线内容。 */
    val offlineProgress: OfflineSyncProgress? = null,
    val offlineMessage: String? = null,
    val isWebSessionActive: Boolean = false,
    val sessionUsername: String? = null,
    val isTokenSet: Boolean = false,
    val isDeepSeekConfigured: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val offlineRepository: OfflineRepository,
    private val feedCacheRepository: FeedCacheRepository,
    private val autoOfflineCoordinator: AutoOfflineCoordinator,
    private val followedNodesStore: FollowedNodesStore,
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

    private data class Offline(
        val topicBytes: Long,
        val topicCount: Int,
        val progress: OfflineSyncProgress?,
        val message: String?,
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

    private val offline = combine(
        offlineRepository.observeAll(),
        autoOfflineCoordinator.progress,
        autoOfflineCoordinator.result,
    ) { bundles, progress, message ->
        Offline(
            topicBytes = bundles.sumOf { it.byteSize.toLong() },
            topicCount = bundles.size,
            progress = progress,
            message = message,
        )
    }

    private data class Toggles(
        val communityPulse: Boolean,
        val liquidGlass: Boolean,
        val appIcon: AppIcon,
    )

    private val toggles = combine(
        settingsDataStore.communityPulseEnabled,
        settingsDataStore.liquidGlassEnabled,
        settingsDataStore.appIcon,
    ) { communityPulse, liquidGlass, appIcon ->
        Toggles(communityPulse, liquidGlass, AppIcon.fromName(appIcon))
    }

    private val refreshSession = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        appearance,
        reading,
        offline,
        toggles,
        refreshSession,
    ) { appearance, reading, offline, toggles, _ ->
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
            communityPulseEnabled = toggles.communityPulse,
            liquidGlassEnabled = toggles.liquidGlass,
            appIcon = toggles.appIcon,
            cacheByteSize = offline.topicBytes + imageCacheBytes(),
            offlineTopicCount = offline.topicCount,
            offlineProgress = offline.progress,
            offlineMessage = offline.message,
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
    fun setLiquidGlassEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setLiquidGlassEnabled(enabled) }

    /** 只记录选择；真正切 alias 在 MainActivity.onStop（见 [AppIcon]）。 */
    fun setAppIcon(icon: AppIcon) =
        viewModelScope.launch { settingsDataStore.setAppIcon(icon.name) }

    /** 登机前手动把最新话题连回复下载下来；下载本身跑在协调器里，离开本页也不会中断。 */
    fun prefetchOffline() {
        viewModelScope.launch {
            autoOfflineCoordinator.prefetchNow(followedNodesStore.names.first())
        }
    }

    fun consumeOfflineMessage() = autoOfflineCoordinator.consumeResult()

    fun clearCache() = viewModelScope.launch {
        offlineRepository.clear()
        feedCacheRepository.clear()
        withContext(Dispatchers.IO) {
            SingletonImageLoader.get(context).apply {
                memoryCache?.clear()
                diskCache?.clear()
            }
        }
        refreshSession.value++
    }

    private fun imageCacheBytes(): Long =
        runCatching { SingletonImageLoader.get(context).diskCache?.size ?: 0L }.getOrDefault(0L)

    fun saveDeepSeekApiKey(key: String) {
        secureStore.deepSeekApiKey = key
        refreshSession.value++
    }

    fun clearDeepSeekApiKey() {
        secureStore.clearDeepSeekApiKey()
        refreshSession.value++
    }
}
