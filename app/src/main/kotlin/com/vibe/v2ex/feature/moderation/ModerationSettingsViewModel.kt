package com.vibe.v2ex.feature.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.local.ReportDao
import com.vibe.v2ex.data.local.ReportEntity
import com.vibe.v2ex.data.moderation.ModerationStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModerationSettingsUiState(
    val keywords: List<String> = emptyList(),
    val usernames: List<String> = emptyList(),
    val hiddenTopicIds: List<Long> = emptyList(),
    val hiddenReplyIds: List<Long> = emptyList(),
    /** Last 30 reports, newest first (mirrors the iOS 举报记录 cap). */
    val reports: List<ReportEntity> = emptyList(),
)

@HiltViewModel
class ModerationSettingsViewModel @Inject constructor(
    private val moderationStore: ModerationStore,
    reportDao: ReportDao,
) : ViewModel() {
    val uiState: StateFlow<ModerationSettingsUiState> = combine(
        moderationStore.blockedKeywords,
        moderationStore.blockedUsernames,
        moderationStore.hiddenTopicIds,
        moderationStore.hiddenReplyIds,
        reportDao.observeAll(),
    ) { keywords, usernames, hiddenTopics, hiddenReplies, reports ->
        ModerationSettingsUiState(
            keywords = keywords,
            usernames = usernames,
            hiddenTopicIds = hiddenTopics,
            hiddenReplyIds = hiddenReplies,
            reports = reports.take(30),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModerationSettingsUiState())

    fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { moderationStore.blockKeyword(trimmed) }
    }

    fun removeKeyword(keyword: String) {
        viewModelScope.launch { moderationStore.unblockKeyword(keyword) }
    }

    fun addUsername(username: String) {
        val trimmed = username.trim().removePrefix("@")
        if (trimmed.isEmpty()) return
        viewModelScope.launch { moderationStore.blockUser(trimmed) }
    }

    fun removeUsername(username: String) {
        viewModelScope.launch { moderationStore.unblockUser(username) }
    }

    fun unhideTopic(topicId: Long) {
        viewModelScope.launch { moderationStore.unhideTopic(topicId) }
    }

    fun unhideReply(replyId: Long) {
        viewModelScope.launch { moderationStore.unhideReply(replyId) }
    }
}
