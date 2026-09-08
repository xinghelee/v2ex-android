package com.vibe.v2ex.feature.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.local.ReportDao
import com.vibe.v2ex.data.local.ReportEntity
import com.vibe.v2ex.data.moderation.ModerationStore
import com.vibe.v2ex.data.moderation.WebsiteModerationState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModerationSettingsUiState(
    val usernames: List<String> = emptyList(),
    val unavailableMemberIds: List<Long> = emptyList(),
    val hiddenTopicIds: List<Long> = emptyList(),
    val hiddenReplyIds: List<Long> = emptyList(),
    /** Last 30 reports, newest first (mirrors the iOS 举报记录 cap). */
    val reports: List<ReportEntity> = emptyList(),
    val accountName: String? = null,
    val isWebSessionActive: Boolean = false,
    val isSyncing: Boolean = false,
    val actionUsername: String? = null,
    val message: String? = null,
)

private data class ModerationRules(
    val usernames: List<String>,
    val unavailableMemberIds: List<Long>,
    val hiddenTopicIds: List<Long>,
    val hiddenReplyIds: List<Long>,
)

@HiltViewModel
class ModerationSettingsViewModel @Inject constructor(
    private val moderationStore: ModerationStore,
    reportDao: ReportDao,
) : ViewModel() {
    private val rules = combine(
        moderationStore.blockedUsernames,
        moderationStore.unavailableBlockedMemberIds,
        moderationStore.hiddenTopicIds,
        moderationStore.hiddenReplyIds,
    ) { usernames, unavailableIds, hiddenTopics, hiddenReplies ->
        ModerationRules(usernames, unavailableIds, hiddenTopics, hiddenReplies)
    }

    val uiState: StateFlow<ModerationSettingsUiState> = combine(
        rules,
        reportDao.observeAll(),
        moderationStore.websiteState,
    ) { rules, reports, website -> rules.toUiState(reports, website) }
        // This ViewModel remains on the back stack while Account can switch identities. Keep the
        // lightweight flows hot so an old account's block list is never replayed for one frame.
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModerationSettingsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            moderationStore.syncSessionIdentity()
            if (moderationStore.websiteState.value.isWebSessionActive) {
                moderationStore.refreshWebsiteBlocks()
            }
        }
    }

    fun addUsername(username: String, onSuccess: () -> Unit = {}) {
        val trimmed = username.trim().removePrefix("@")
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            moderationStore.blockUser(trimmed).onSuccess { onSuccess() }
        }
    }

    fun removeUsername(username: String) {
        viewModelScope.launch { moderationStore.unblockUser(username) }
    }

    fun consumeMessage() {
        moderationStore.consumeWebsiteMessage()
    }

    fun unhideTopic(topicId: Long) {
        viewModelScope.launch { moderationStore.unhideTopic(topicId) }
    }

    fun unhideReply(replyId: Long) {
        viewModelScope.launch { moderationStore.unhideReply(replyId) }
    }

    private fun ModerationRules.toUiState(
        reports: List<ReportEntity>,
        website: WebsiteModerationState,
    ) = ModerationSettingsUiState(
        usernames = usernames,
        unavailableMemberIds = unavailableMemberIds,
        hiddenTopicIds = hiddenTopicIds,
        hiddenReplyIds = hiddenReplyIds,
        reports = reports.take(30),
        accountName = website.accountName,
        isWebSessionActive = website.isWebSessionActive,
        isSyncing = website.isSyncing,
        actionUsername = website.actionUsername,
        message = website.message,
    )
}
