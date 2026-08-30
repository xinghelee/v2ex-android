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
        val AUTO_OFFLINE_FOLLOWED_NODES = booleanPreferencesKey("auto_offline_followed_nodes")
        val AUTO_SYNC_FOLLOWED_NODES = booleanPreferencesKey("auto_sync_followed_nodes")
        val OFFLINE_ON_WIFI_ONLY = booleanPreferencesKey("offline_on_wifi_only")
        val COMMUNITY_PULSE_ENABLED = booleanPreferencesKey("community_pulse_enabled")
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
    val autoOfflineFollowedNodes: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.AUTO_OFFLINE_FOLLOWED_NODES] ?: true }
    val autoSyncFollowedNodes: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.AUTO_SYNC_FOLLOWED_NODES] ?: true }
    val offlineOnWifiOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.OFFLINE_ON_WIFI_ONLY] ?: true }
    val communityPulseEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.COMMUNITY_PULSE_ENABLED] ?: true }

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
    suspend fun setAutoOfflineFollowedNodes(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.AUTO_OFFLINE_FOLLOWED_NODES] = enabled }
    suspend fun setAutoSyncFollowedNodes(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.AUTO_SYNC_FOLLOWED_NODES] = enabled }
    suspend fun setOfflineOnWifiOnly(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.OFFLINE_ON_WIFI_ONLY] = enabled }
    suspend fun setCommunityPulseEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.COMMUNITY_PULSE_ENABLED] = enabled }
}
