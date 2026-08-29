package com.vibe.v2ex.feature.notifications

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.V2Colors
import com.vibe.v2ex.designsystem.cardGroupPosition
import com.vibe.v2ex.designsystem.relativeTimeText
import com.vibe.v2ex.feature.home.TAB_BAR_CLEARANCE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onTopicClick: (Long) -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // 每次回到本 tab 都重新拉取，顺带重新检查 Token 是否已经配置。
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // 大标题 + 全部已读（设计稿 06 顶栏）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "通知",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            val markAllEnabled = uiState.totalUnread > 0
            Text(
                text = "全部已读",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (markAllEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = markAllEnabled, onClick = viewModel::markAllSeen)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }

        if (!uiState.isTokenSet) {
            TokenEmptyState(modifier = Modifier.fillMaxSize())
            return@Column
        }

        FilterChips(uiState = uiState, onSelect = viewModel::selectFilter)

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = TAB_BAR_CLEARANCE),
            ) {
                if (uiState.visibleRows.isEmpty() && !uiState.isRefreshing) {
                    item(key = "empty") {
                        V2Card {
                            Text(
                                text = uiState.error?.let { "加载失败：$it" } ?: "暂无通知",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                }
                itemsIndexed(uiState.visibleRows, key = { _, row -> row.id }) { index, row ->
                    CardGroupItem(position = cardGroupPosition(index, uiState.visibleRows.lastIndex)) {
                        NotificationRowItem(
                            row = row,
                            // 点击 = 标记已读 + 跳到对应帖子（mirrors iOS 的通知行 NavigationLink）。
                            onClick = {
                                viewModel.markSeen(row.id)
                                row.topicId?.let(onTopicClick)
                            },
                            onMarkSeen = { viewModel.markSeen(row.id) },
                            onDelete = { viewModel.delete(row.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 分类 chip：选中 = accent 底白字圆角 15 附未读数；未选 = 白 80% 底（设计稿 06）。 */
@Composable
private fun FilterChips(uiState: NotificationsUiState, onSelect: (NotificationFilter) -> Unit) {
    val dark = LocalV2Dark.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NotificationFilter.entries.forEach { filter ->
            val selected = uiState.filter == filter
            val unread = uiState.unreadCount(filter)
            Text(
                text = if (unread > 0) "${filter.label} $unread" else filter.label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = when {
                    selected -> Color.White
                    dark -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> Color(0xFF3C3C43)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            dark -> Color(0xFF1C1C1E).copy(alpha = 0.9f)
                            else -> Color.White.copy(alpha = 0.8f)
                        },
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationRowItem(
    row: NotificationRow,
    onClick: () -> Unit,
    onMarkSeen: () -> Unit,
    onDelete: () -> Unit,
) {
    val dark = LocalV2Dark.current
    var menuExpanded by remember { mutableStateOf(false) }
    // actionText 通常以用户名开头（htmlToPlainText 之后），拆出来做「粗名字 + 弱动作」的组合。
    val action = if (row.username.isNotBlank() && row.actionText.startsWith(row.username)) {
        row.actionText.removePrefix(row.username).trim().ifBlank { row.actionText }
    } else {
        row.actionText
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (row.isUnread) {
                    if (dark) V2Colors.accentSoft(true).copy(alpha = 0.08f) else V2Colors.UnreadRowTintLight
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(username = row.username, url = row.avatarUrl, size = 34.dp)
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.username.isNotBlank()) {
                        Text(
                            text = row.username,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = action,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val time = relativeTimeText(row.createdAt)
                    if (time.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.payloadPreview.isNotBlank()) {
                    Text(
                        text = row.payloadPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) V2Colors.BodyDark else V2Colors.BodyLight,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
        if (row.isUnread) {
            Box(
                modifier = Modifier
                    .offset(x = 6.dp, y = 18.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (row.isUnread) {
                DropdownMenuItem(
                    text = { Text("标记已读") },
                    onClick = {
                        menuExpanded = false
                        onMarkSeen()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun TokenEmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        V2Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "需要 Personal Access Token",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "通知功能依赖 V2EX API 2.0。请前往 我 → 账号，粘贴在 v2ex.com/settings/tokens 创建的 Personal Access Token。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
