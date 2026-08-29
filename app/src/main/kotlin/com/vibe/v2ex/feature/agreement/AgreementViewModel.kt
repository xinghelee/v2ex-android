package com.vibe.v2ex.feature.agreement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

const val CURRENT_AGREEMENT_VERSION = 1

@HiltViewModel
class AgreementViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    fun accept() {
        viewModelScope.launch { settingsDataStore.setAgreedTermsVersion(CURRENT_AGREEMENT_VERSION) }
    }
}
