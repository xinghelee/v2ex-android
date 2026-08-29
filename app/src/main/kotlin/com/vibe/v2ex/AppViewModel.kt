package com.vibe.v2ex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.AppTheme
import com.vibe.v2ex.data.datastore.DarkModePreference
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.feature.agreement.CURRENT_AGREEMENT_VERSION
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppUiState(
    val theme: AppTheme = AppTheme.EMERALD,
    val darkMode: DarkModePreference = DarkModePreference.SYSTEM,
    val agreementAccepted: Boolean = true,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsDataStore: SettingsDataStore,
) : ViewModel() {
    val uiState: StateFlow<AppUiState> = combine(
        settingsDataStore.theme,
        settingsDataStore.darkMode,
        settingsDataStore.agreedTermsVersion,
    ) { theme, darkMode, agreedVersion ->
        AppUiState(theme, darkMode, agreementAccepted = agreedVersion >= CURRENT_AGREEMENT_VERSION)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())
}
