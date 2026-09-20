package com.vibe.v2ex.feature.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.tags.MemberTagStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemberTagSettingsUiState(
    val showMemberTags: Boolean = true,
    val taggedCount: Int = 0,
    val isSyncing: Boolean = false,
    val message: String? = null,
)

/** 设置页「用户标记」分区自己的状态源，不挤进 SettingsViewModel 的 combine 里。 */
@HiltViewModel
class MemberTagSettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val store: MemberTagStore,
) : ViewModel() {
    val uiState: StateFlow<MemberTagSettingsUiState> = combine(
        settings.showMemberTags,
        store.all,
        store.syncState,
    ) { show, records, sync ->
        MemberTagSettingsUiState(
            showMemberTags = show,
            taggedCount = records.size,
            isSyncing = sync.isSyncing,
            message = sync.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemberTagSettingsUiState())

    fun setShowMemberTags(enabled: Boolean) = viewModelScope.launch { settings.setShowMemberTags(enabled) }

    fun pullFromPolish() = store.pullFromPolish()

    fun pushToPolish() = store.pushToPolish()

    fun consumeMessage() = store.consumeMessage()
}
