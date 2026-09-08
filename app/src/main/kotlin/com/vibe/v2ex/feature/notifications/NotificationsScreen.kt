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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    onAccountClick: () -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 进入本 tab 或从后台返回时重新读取列表和官网计数。
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val notice = uiState.notice
    LaunchedEffect(notice?.id) {
        if (notice == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = notice.message,
            actionLabel = if (notice.action == NotificationNoticeAction.ACCOUNT) "账号" else null,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        viewModel.consumeNotice(notice.id)
        if (result == SnackbarResult.ActionPerformed && notice.action == NotificationNoticeAction.ACCOUNT) {
            onAccountClick()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // 大标题 + 官网账号级「全部已读」。
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
                TextButton(
                    onClick = viewModel::markAllRead,
                    enabled = uiState.canMarkAllRead,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text(
                        text = if (uiState.isSyncing) "正在同步…" else "全部已读",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (!uiState.isTokenSet) {
                TokenEmptyState(onAccountClick = onAccountClick, modifier = Modifier.fillMaxSize())
            } else {
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
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(
                                            text = uiState.error?.let { "加载失败：$it" } ?: "没有新通知",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (uiState.error != null) {
                                            TextButton(
                                                onClick = viewModel::refresh,
                                                modifier = Modifier.padding(top = 4.dp).heightIn(min = 44.dp),
                                            ) {
                                                Text("重试")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        itemsIndexed(uiState.visibleRows, key = { _, row -> row.id }) { index, row ->
                            CardGroupItem(position = cardGroupPosition(index, uiState.visibleRows.lastIndex)) {
                                NotificationRowItem(
                                    row = row,
                                    onClick = { row.topicId?.let(onTopicClick) },
                                    onDelete = { viewModel.delete(row.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TAB_BAR_CLEARANCE),
        )
    }
}

/** 分类 chip 只表达筛选，不虚构 API 并不存在的分类已读计数。 */
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
            Text(
                text = filter.label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
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
                    .heightIn(min = 44.dp)
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    }
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
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
private fun TokenEmptyState(onAccountClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        V2Card(
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = "前往账号设置", onClick = onAccountClick),
        ) {
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
                Text(
                    text = "去填写",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp).heightIn(min = 44.dp),
                )
            }
        }
    }
}
