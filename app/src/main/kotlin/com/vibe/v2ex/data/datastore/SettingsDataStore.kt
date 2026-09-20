package com.vibe.v2ex.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "v2ex_settings")

/** Names match the iOS `ThemePalette` cases exactly — see designsystem/Theme.kt for the hex values. */
enum class AppTheme { EMERALD, OCEAN, CRIMSON, AMBER, VIOLET }
enum class DarkModePreference { SYSTEM, LIGHT, DARK }
enum class LineSpacingPreference(val multiplier: Float) { TIGHT(1.38f), STANDARD(1.52f), RELAXED(1.68f) }
enum class MonoFontPreference { SF_MONO, MENLO, COURIER }

/**
 * 加密 DNS 的全部偏好，作为一个整体发射：DohDns 任一项变了都要重建解析链并清连接池，
 * 拆成四个 Flow 会让一次「保存自定义线路」触发多轮重建。
 * [resolverKey] 对应 `DohResolver.key`，未知值由解析侧回落「自动」。
 */
data class EncryptedDnsSettings(
    val enabled: Boolean = false,
    val resolverKey: String = "auto",
    val customUrl: String = "",
    val customBootstrap: String = "",
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("app_theme")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val FONT_SIZE = floatPreferencesKey("reading_font_size")
        val LINE_SPACING = stringPreferencesKey("reading_line_spacing")
        val MONO_FONT = stringPreferencesKey("reading_mono_font")
        val AGREED_TERMS_VERSION = intPreferencesKey("agreed_terms_version")
        val REMEMBER_READING_POSITION = booleanPreferencesKey("remember_reading_position")
        val DIM_READ_TOPICS = booleanPreferencesKey("dim_read_topics")
        val SHOW_MEMBER_TAGS = booleanPreferencesKey("show_member_tags")
        val AUTO_OFFLINE_FOLLOWED_NODES = booleanPreferencesKey("auto_offline_followed_nodes")
        val AUTO_SYNC_FOLLOWED_NODES = booleanPreferencesKey("auto_sync_followed_nodes")
        val OFFLINE_ON_WIFI_ONLY = booleanPreferencesKey("offline_on_wifi_only")
        val COMMUNITY_PULSE_ENABLED = booleanPreferencesKey("community_pulse_enabled")
        val LIQUID_GLASS_ENABLED = booleanPreferencesKey("liquid_glass_enabled")
        val APP_ICON = stringPreferencesKey("app_icon")
        val ENCRYPTED_DNS_ENABLED = booleanPreferencesKey("encrypted_dns_enabled")
        val ENCRYPTED_DNS_RESOLVER = stringPreferencesKey("encrypted_dns_resolver")
        val ENCRYPTED_DNS_CUSTOM_URL = stringPreferencesKey("encrypted_dns_custom_url")
        val ENCRYPTED_DNS_CUSTOM_BOOTSTRAP = stringPreferencesKey("encrypted_dns_custom_bootstrap")
    }

    val theme: Flow<AppTheme> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.EMERALD
    }

    val darkMode: Flow<DarkModePreference> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE]?.let { runCatching { DarkModePreference.valueOf(it) }.getOrNull() }
            ?: DarkModePreference.SYSTEM
    }

    val fontSize: Flow<Float> = context.settingsDataStore.data.map { it[Keys.FONT_SIZE] ?: 14f }

    val lineSpacing: Flow<LineSpacingPreference> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.LINE_SPACING]?.let { runCatching { LineSpacingPreference.valueOf(it) }.getOrNull() }
            ?: LineSpacingPreference.RELAXED
    }

    val monoFont: Flow<MonoFontPreference> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.MONO_FONT]?.let { runCatching { MonoFontPreference.valueOf(it) }.getOrNull() }
            ?: MonoFontPreference.SF_MONO
    }

    val agreedTermsVersion: Flow<Int> = context.settingsDataStore.data.map { it[Keys.AGREED_TERMS_VERSION] ?: 0 }
    val rememberReadingPosition: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.REMEMBER_READING_POSITION] ?: true }
    val dimReadTopics: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DIM_READ_TOPICS] ?: false }

    /** 用户标记的显示总开关。关掉后所有标记 UI（含入口）整体消失，界面退回没有这个功能的样子。 */
    val showMemberTags: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.SHOW_MEMBER_TAGS] ?: true }
    val autoOfflineFollowedNodes: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.AUTO_OFFLINE_FOLLOWED_NODES] ?: true }
    val autoSyncFollowedNodes: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.AUTO_SYNC_FOLLOWED_NODES] ?: true }
    val offlineOnWifiOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.OFFLINE_ON_WIFI_ONLY] ?: true }
    val communityPulseEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.COMMUNITY_PULSE_ENABLED] ?: true }

    /** 底栏液态玻璃。默认开，设备不支持（Android 12 以下）时渲染层自己降级，不看这个值。 */
    val liquidGlassEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.LIQUID_GLASS_ENABLED] ?: true }

    /**
     * DoH 解析：开关 + 线路 + 自定义端点，见 [EncryptedDnsSettings]。默认**关**：企业 split-DNS /
     * VPN 私有 DNS 下开启会解析不到内网域名，这个代价只能由主动打开的人承担。
     */
    val encryptedDns: Flow<EncryptedDnsSettings> = context.settingsDataStore.data.map { prefs ->
        EncryptedDnsSettings(
            enabled = prefs[Keys.ENCRYPTED_DNS_ENABLED] ?: false,
            resolverKey = prefs[Keys.ENCRYPTED_DNS_RESOLVER] ?: "auto",
            customUrl = prefs[Keys.ENCRYPTED_DNS_CUSTOM_URL].orEmpty(),
            customBootstrap = prefs[Keys.ENCRYPTED_DNS_CUSTOM_BOOTSTRAP].orEmpty(),
        )
    }

    /** 用户选的桌面图标（AppIcon 枚举名）。真正切 activity-alias 的动作等 App 退到后台再做。 */
    val appIcon: Flow<String?> =
        context.settingsDataStore.data.map { it[Keys.APP_ICON] }

    suspend fun setTheme(theme: AppTheme) = context.settingsDataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setDarkMode(mode: DarkModePreference) = context.settingsDataStore.edit { it[Keys.DARK_MODE] = mode.name }
    suspend fun setFontSize(size: Float) = context.settingsDataStore.edit { it[Keys.FONT_SIZE] = size }
    suspend fun setLineSpacing(pref: LineSpacingPreference) =
        context.settingsDataStore.edit { it[Keys.LINE_SPACING] = pref.name }
    suspend fun setMonoFont(pref: MonoFontPreference) = context.settingsDataStore.edit { it[Keys.MONO_FONT] = pref.name }
    suspend fun setAgreedTermsVersion(version: Int) =
        context.settingsDataStore.edit { it[Keys.AGREED_TERMS_VERSION] = version }
    suspend fun setRememberReadingPosition(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.REMEMBER_READING_POSITION] = enabled }
    suspend fun setDimReadTopics(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.DIM_READ_TOPICS] = enabled }
    suspend fun setShowMemberTags(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.SHOW_MEMBER_TAGS] = enabled }
    suspend fun setAutoOfflineFollowedNodes(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.AUTO_OFFLINE_FOLLOWED_NODES] = enabled }
    suspend fun setAutoSyncFollowedNodes(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.AUTO_SYNC_FOLLOWED_NODES] = enabled }
    suspend fun setOfflineOnWifiOnly(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.OFFLINE_ON_WIFI_ONLY] = enabled }
    suspend fun setCommunityPulseEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.COMMUNITY_PULSE_ENABLED] = enabled }
    suspend fun setLiquidGlassEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.LIQUID_GLASS_ENABLED] = enabled }
    suspend fun setEncryptedDnsEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.ENCRYPTED_DNS_ENABLED] = enabled }
    suspend fun setEncryptedDnsResolver(key: String) =
        context.settingsDataStore.edit { it[Keys.ENCRYPTED_DNS_RESOLVER] = key }

    /** 自定义端点保存即选中：填完地址还要再点一次才生效，是最常见的「设了没反应」来源。 */
    suspend fun setEncryptedDnsCustom(url: String, bootstrap: String, resolverKey: String) =
        context.settingsDataStore.edit {
            it[Keys.ENCRYPTED_DNS_CUSTOM_URL] = url
            it[Keys.ENCRYPTED_DNS_CUSTOM_BOOTSTRAP] = bootstrap
            it[Keys.ENCRYPTED_DNS_RESOLVER] = resolverKey
        }

    suspend fun setAppIcon(name: String) =
        context.settingsDataStore.edit { it[Keys.APP_ICON] = name }
}
