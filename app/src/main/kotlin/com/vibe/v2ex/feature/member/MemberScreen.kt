package com.vibe.v2ex.feature.member

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.moderation.ModerationStore
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.ReplyCount
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.cardGroupPosition
import com.vibe.v2ex.designsystem.relativeTimeText
import com.vibe.v2ex.designsystem.topicRowTitle
import com.vibe.v2ex.feature.home.TAB_BAR_CLEARANCE
import com.vibe.v2ex.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemberUiState(
    val username: String = "",
    val member: Member? = null,
    val topics: List<Topic> = emptyList(),
    val isLoading: Boolean = true,
    val isBlocked: Boolean = false,
    val isWebSessionActive: Boolean = false,
    val isBlockSyncing: Boolean = false,
    val message: String? = null,
)

private data class MemberModerationRules(
    val hiddenTopicIds: List<Long> = emptyList(),
    val blockedUsernames: List<String> = emptyList(),
    val unavailableMemberIds: List<Long> = emptyList(),
)

@HiltViewModel
class MemberViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiV1: V2exApiV1,
    private val secureStore: SecureStore,
    private val moderationStore: ModerationStore,
) : ViewModel() {
    private val username: String = savedStateHandle.toRoute<Route.Member>().username

    private val _uiState = MutableStateFlow(
        MemberUiState(username = username, isWebSessionActive = secureStore.isWebSessionActive),
    )
    val uiState: StateFlow<MemberUiState> = _uiState.asStateFlow()
    private var rawTopics: List<Topic> = emptyList()
    private var member: Member? = null
    private var rules: MemberModerationRules? = null

    init {
        viewModelScope.launch {
            combine(
                moderationStore.hiddenTopicIds,
                moderationStore.blockedUsernames,
                moderationStore.unavailableBlockedMemberIds,
            ) { hiddenTopicIds, blockedUsernames, memberIds ->
                MemberModerationRules(hiddenTopicIds, blockedUsernames, memberIds)
            }.collect { next ->
                rules = next
                publishModeratedContent()
            }
        }
        viewModelScope.launch {
            member = runCatching { apiV1.showMember(username) }.getOrNull()
            rawTopics = runCatching { apiV1.topicsByMember(username) }.getOrDefault(emptyList())
            publishModeratedContent()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun toggleBlocked() {
        val state = _uiState.value
        if (state.isBlockSyncing) return
        if (!secureStore.isWebSessionActive) {
            _uiState.update { it.copy(message = "屏蔽用户需要先登录 V2EX 网页账号") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBlockSyncing = true) }
            val result = if (state.isBlocked) {
                moderationStore.unblockUser(username)
            } else {
                moderationStore.blockUser(username)
            }
            result.onFailure { error ->
                _uiState.update { it.copy(message = error.message ?: "官网屏蔽状态更新失败") }
            }
            _uiState.update {
                it.copy(
                    isBlockSyncing = false,
                    isWebSessionActive = secureStore.isWebSessionActive,
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun publishModeratedContent() {
        val currentMember = member
        val activeRules = rules
        if (activeRules == null) {
            _uiState.update { it.copy(member = currentMember, topics = emptyList()) }
            return
        }
        val isBlocked = activeRules.blockedUsernames.any { it.equals(username, ignoreCase = true) } ||
            currentMember?.id?.let(activeRules.unavailableMemberIds::contains) == true
        val topics = rawTopics.filterNot { topic ->
            moderationStore.isTopicHidden(
                topic = topic,
                hiddenIds = activeRules.hiddenTopicIds,
                blockedUsers = activeRules.blockedUsernames,
                blockedMemberIds = activeRules.unavailableMemberIds,
            )
        }
        _uiState.update {
            it.copy(
                member = currentMember,
                topics = topics,
                isBlocked = isBlocked,
                isWebSessionActive = secureStore.isWebSessionActive,
            )
        }
    }
}

@Composable
fun MemberScreen(
    onBack: () -> Unit,
    onTopicClick: (Long) -> Unit,
    viewModel: MemberViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = "返回",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = TAB_BAR_CLEARANCE + 24.dp),
        ) {
            item(key = "profile") {
                when {
                    uiState.member != null -> MemberCard(
                        member = uiState.member!!,
                        isBlocked = uiState.isBlocked,
                        isSyncing = uiState.isBlockSyncing,
                        onToggleBlocked = viewModel::toggleBlocked,
                    )
                    uiState.isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    else -> V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "没有找到这个用户",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
            }
            if (uiState.topics.isNotEmpty()) {
                item(key = "recent-header") {
                    SectionHeader("最近发布", modifier = Modifier.padding(top = 22.dp))
                }
                itemsIndexed(uiState.topics, key = { _, topic -> topic.id }) { index, topic ->
                    CardGroupItem(
                        position = cardGroupPosition(index, uiState.topics.lastIndex),
                        dividerInset = 16.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTopicClick(topic.id) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = topic.title,
                                    style = MaterialTheme.typography.topicRowTitle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = listOf(
                                        topic.nodeTitle,
                                        relativeTimeText(topic.activityTimestamp),
                                    ).filter(String::isNotBlank).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            ReplyCount(topic.replies, modifier = Modifier.padding(start = 10.dp, top = 2.dp))
                        }
                    }
                }
            }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MemberCard(
    member: Member,
    isBlocked: Boolean,
    isSyncing: Boolean,
    onToggleBlocked: () -> Unit,
) {
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(username = member.username, url = member.avatarUrl, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.username,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val joined = member.created?.let { created ->
                        ((System.currentTimeMillis() / 1000 - created) / 86_400).coerceAtLeast(0)
                    }
                    val subtitle = listOfNotNull(
                        member.id?.let { "第 $it 号会员" },
                        joined?.let { "加入 $it 天" },
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                TextButton(onClick = onToggleBlocked, enabled = !isSyncing) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (isBlocked) "取消屏蔽" else "屏蔽")
                    }
                }
            }
            member.tagline?.takeIf(String::isNotBlank)?.let { tagline ->
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            member.bio?.takeIf(String::isNotBlank)?.let { bio ->
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
