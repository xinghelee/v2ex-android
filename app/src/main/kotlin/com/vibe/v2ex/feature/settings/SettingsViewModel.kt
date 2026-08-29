package com.vibe.v2ex.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.DarkModePreference
import com.vibe.v2ex.data.datastore.LineSpacingPreference
import com.vibe.v2ex.data.datastore.MonoFontPreference
import com.vibe.v2ex.data.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val darkMode: DarkModePreference = DarkModePreference.SYSTEM,
    val fontSize: Float = 14f,
    val lineSpacing: LineSpacingPreference = LineSpacingPreference.RELAXED,
    val monoFont: MonoFontPreference = MonoFontPreference.SF_MONO,
    val dimReadTopics: Boolean = false,
    val rememberReadingPosition: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private data class Appearance(
        val darkMode: DarkModePreference,
        val fontSize: Float,
        val lineSpacing: LineSpacingPreference,
        val monoFont: MonoFontPreference,
    )

    private val appearance = combine(
        settingsDataStore.darkMode,
        settingsDataStore.fontSize,
        settingsDataStore.lineSpacing,
        settingsDataStore.monoFont,
    ) { darkMode, fontSize, lineSpacing, monoFont ->
        Appearance(darkMode, fontSize, lineSpacing, monoFont)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        appearance,
        settingsDataStore.dimReadTopics,
        settingsDataStore.rememberReadingPosition,
    ) { appearance, dimReadTopics, rememberReadingPosition ->
        SettingsUiState(
            darkMode = appearance.darkMode,
            fontSize = appearance.fontSize,
            lineSpacing = appearance.lineSpacing,
            monoFont = appearance.monoFont,
            dimReadTopics = dimReadTopics,
            rememberReadingPosition = rememberReadingPosition,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDarkMode(mode: DarkModePreference) = viewModelScope.launch { settingsDataStore.setDarkMode(mode) }
    fun setFontSize(size: Float) = viewModelScope.launch { settingsDataStore.setFontSize(size) }
    fun setLineSpacing(pref: LineSpacingPreference) = viewModelScope.launch { settingsDataStore.setLineSpacing(pref) }
    fun setMonoFont(pref: MonoFontPreference) = viewModelScope.launch { settingsDataStore.setMonoFont(pref) }
    fun setDimReadTopics(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setDimReadTopics(enabled) }
    fun setRememberReadingPosition(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setRememberReadingPosition(enabled) }
}
