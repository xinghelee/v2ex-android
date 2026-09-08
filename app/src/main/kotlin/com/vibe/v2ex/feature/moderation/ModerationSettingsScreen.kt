package com.vibe.v2ex.feature.moderation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.local.ReportEntity
import com.vibe.v2ex.designsystem.relativeTimeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationSettingsScreen(
    onBack: () -> Unit,
    onAccountClick: () -> Unit = {},
    viewModel: ModerationSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var usernameInput by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("内容与屏蔽") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.isWebSessionActive) {
                        IconButton(onClick = viewModel::refresh, enabled = !uiState.isSyncing) {
                            Icon(Icons.Filled.Refresh, contentDescription = "同步官网屏蔽名单")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isSyncing,
            onRefresh = {
                if (uiState.isWebSessionActive) viewModel.refresh() else onAccountClick()
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                item(key = "usernames_header") {
                    SectionHeader("官网屏蔽用户")
                    if (uiState.isWebSessionActive) {
                        Text(
                            text = listOfNotNull(
                                uiState.accountName?.let { "@$it" },
                                "${uiState.usernames.size + uiState.unavailableMemberIds.size} 位用户",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        AddEntryRow(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            placeholder = "输入 V2EX 用户名",
                            enabled = !uiState.isSyncing,
                            onAdd = {
                                viewModel.addUsername(usernameInput) { usernameInput = "" }
                            },
                        )
                    } else {
                        EmptyHint("屏蔽名单与 V2EX 官网同步，需要先登录网页账号。")
                        TextButton(onClick = onAccountClick) { Text("登录 V2EX 网页账号") }
                    }
                }
                if (uiState.isWebSessionActive && uiState.usernames.isEmpty() && uiState.unavailableMemberIds.isEmpty()) {
                    item(key = "usernames_empty") { EmptyHint("官网屏蔽名单为空") }
                }
                items(uiState.usernames, key = { "username_${it.lowercase()}" }) { username ->
                    BlockedEntryRow(
                        text = "@$username",
                        deleting = uiState.actionMatches(username),
                        enabled = !uiState.isSyncing,
                        onDelete = { viewModel.removeUsername(username) },
                    )
                }
                items(uiState.unavailableMemberIds, key = { "unavailable_$it" }) { memberId ->
                    Text(
                        text = "用户 #$memberId",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }

                item(key = "official_hint") {
                    EmptyHint("这里只显示官网账号的屏蔽名单；旧版仅存于本机的关键词和用户名规则不会上传。")
                }

                item(key = "hidden_header") { SectionHeader("已隐藏的内容") }
                if (uiState.hiddenTopicIds.isEmpty() && uiState.hiddenReplyIds.isEmpty()) {
                    item(key = "hidden_empty") { EmptyHint("暂无因举报隐藏的内容") }
                }
                items(uiState.hiddenTopicIds, key = { "hidden_topic_$it" }) { topicId ->
                    HiddenEntryRow(label = "话题 #$topicId", onRestore = { viewModel.unhideTopic(topicId) })
                }
                items(uiState.hiddenReplyIds, key = { "hidden_reply_$it" }) { replyId ->
                    HiddenEntryRow(label = "回复 #$replyId", onRestore = { viewModel.unhideReply(replyId) })
                }

                item(key = "reports_header") { SectionHeader("举报记录") }
                if (uiState.reports.isEmpty()) {
                    item(key = "reports_empty") { EmptyHint("暂无举报记录") }
                }
                items(uiState.reports, key = { "report_${it.id}" }) { report ->
                    ReportRow(report)
                }
            }
        }
    }
}

private fun ModerationSettingsUiState.actionMatches(username: String): Boolean =
    actionUsername?.equals(username, ignoreCase = true) == true

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun AddEntryRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd, enabled = enabled && value.isNotBlank()) {
            Icon(Icons.Filled.Add, contentDescription = "添加")
        }
    }
}

@Composable
private fun BlockedEntryRow(
    text: String,
    deleting: Boolean,
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, enabled = enabled) {
            if (deleting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "取消屏蔽 $text",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HiddenEntryRow(label: String, onRestore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestore) { Text("恢复显示") }
    }
}

@Composable
private fun ReportRow(report: ReportEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = report.reasonTitle, style = MaterialTheme.typography.bodyMedium)
            val meta = listOfNotNull(
                kindLabel(report.kind),
                targetLabel(report),
                relativeTimeText(report.createdAt / 1000).ifBlank { null },
            ).joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        DeliveryBadge(delivered = report.deliveredAt != null)
    }
}

@Composable
private fun DeliveryBadge(delivered: Boolean) {
    Surface(
        color = if (delivered) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = if (delivered) "已送达" else "待送达",
            style = MaterialTheme.typography.labelSmall,
            color = if (delivered) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "block" -> "屏蔽"
    else -> "举报"
}

private fun targetLabel(report: ReportEntity): String = when (report.targetType) {
    "topic" -> "话题 #${report.targetId}"
    "reply" -> "回复 #${report.targetId}"
    "member" -> "用户 @${report.targetId}"
    else -> report.targetId
}
