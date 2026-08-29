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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch

data class MemberUiState(
    val username: String = "",
    val member: Member? = null,
    val topics: List<Topic> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class MemberViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiV1: V2exApiV1,
) : ViewModel() {
    private val username: String = savedStateHandle.toRoute<Route.Member>().username

    private val _uiState = MutableStateFlow(MemberUiState(username = username))
    val uiState: StateFlow<MemberUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val member = runCatching { apiV1.showMember(username) }.getOrNull()
            val topics = runCatching { apiV1.topicsByMember(username) }.getOrDefault(emptyList())
            _uiState.value = MemberUiState(username, member, topics, isLoading = false)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    uiState.member != null -> MemberCard(uiState.member!!)
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
}

@Composable
private fun MemberCard(member: Member) {
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
