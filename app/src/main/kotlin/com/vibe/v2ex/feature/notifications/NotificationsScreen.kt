package com.vibe.v2ex.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.relativeTimeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // 每次回到本 tab 都重新拉取，顺带重新检查 Token 是否已经配置。
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(
                        onClick = viewModel::markAllSeen,
                        enabled = uiState.totalUnread > 0,
                    ) {
                        Text("全部已读")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!uiState.isTokenSet) {
            TokenEmptyState(modifier = Modifier.fillMaxSize().padding(innerPadding))
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "filters") {
                    FilterChips(
                        uiState = uiState,
                        onSelect = viewModel::selectFilter,
                    )
                }
                if (uiState.visibleRows.isEmpty() && !uiState.isRefreshing) {
                    item(key = "empty") {
                        Text(
                            text = uiState.error?.let { "加载失败：$it" } ?: "暂无通知",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                        )
                    }
                }
                items(uiState.visibleRows, key = { it.id }) { row ->
                    NotificationRowItem(
                        row = row,
                        onClick = { viewModel.markSeen(row.id) },
                        onMarkSeen = { viewModel.markSeen(row.id) },
                        onDelete = { viewModel.delete(row.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChips(uiState: NotificationsUiState, onSelect: (NotificationFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        NotificationFilter.entries.forEach { filter ->
            val unread = uiState.unreadCount(filter)
            FilterChip(
                selected = uiState.filter == filter,
                onClick = { onSelect(filter) },
                label = { Text(if (unread > 0) "${filter.label} $unread" else filter.label) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun NotificationRowItem(
    row: NotificationRow,
    onClick: () -> Unit,
    onMarkSeen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(username = row.username, url = row.avatarUrl, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.actionText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (row.isUnread) FontWeight.Medium else FontWeight.Normal,
            )
            if (row.payloadPreview.isNotBlank()) {
                Text(
                    text = row.payloadPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val time = relativeTimeText(row.createdAt)
            if (time.isNotBlank()) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RowOverflowMenu(isUnread = row.isUnread, onMarkSeen = onMarkSeen, onDelete = onDelete)
            if (row.isUnread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
            }
        }
    }
}

@Composable
private fun RowOverflowMenu(isUnread: Boolean, onMarkSeen: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "更多操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isUnread) {
                DropdownMenuItem(
                    text = { Text("标记已读") },
                    onClick = {
                        expanded = false
                        onMarkSeen()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun TokenEmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "需要 Personal Access Token",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
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
