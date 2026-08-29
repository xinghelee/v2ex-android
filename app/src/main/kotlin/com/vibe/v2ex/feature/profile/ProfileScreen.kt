package com.vibe.v2ex.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.relativeTimeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onModerationClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // 每次回到本页（含从「账号」返回后）重新检查 Token 并刷新资料。
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "外观与阅读设置")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when {
                uiState.isTokenSet && uiState.member != null -> {
                    MemberCard(member = uiState.member!!)
                }
                uiState.isTokenSet && uiState.isLoading -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("正在加载资料…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                uiState.isTokenSet -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = uiState.error?.let { "资料加载失败：$it" } ?: "资料加载失败，下拉或重进本页重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                else -> {
                    NotConnectedCard(onLoginClick = onLoginClick)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable(onClick = onLoginClick),
            ) {
                ListItem(
                    headlineContent = { Text("账号") },
                    supportingContent = { Text("登录网页会话 · 设置 Personal Access Token") },
                    leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                )
            }

            if (uiState.recentTopics.isNotEmpty()) {
                SectionLabel("最近发布")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        uiState.recentTopics.forEachIndexed { index, topic ->
                            if (index > 0) HorizontalDivider()
                            RecentTopicRow(topic)
                        }
                    }
                }
            }

            SectionLabel("我的内容")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    StatRow(icon = Icons.Filled.Bookmark, label = "收藏", count = uiState.favoriteCount)
                    HorizontalDivider()
                    StatRow(icon = Icons.Filled.History, label = "浏览历史", count = uiState.historyCount)
                    HorizontalDivider()
                    StatRow(
                        icon = Icons.Filled.Shield,
                        label = "内容与屏蔽",
                        count = uiState.moderationCount,
                        onClick = onModerationClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberCard(member: Member) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(username = member.username, url = member.avatarUrl, size = 56.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val subtitle = memberSubtitle(member)
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val bio = listOfNotNull(member.bio, member.tagline).firstOrNull { it.isNotBlank() }
                if (bio != null) {
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** "V2EX 第 {id} 号会员 · 加入 {days} 天" — either clause omitted when its source field is null. */
private fun memberSubtitle(member: Member): String {
    val clauses = buildList {
        member.id?.let { add("V2EX 第 $it 号会员") }
        member.created?.let { created ->
            val days = ((System.currentTimeMillis() / 1000 - created) / 86_400).coerceAtLeast(0)
            add("加入 $days 天")
        }
    }
    return clauses.joinToString(" · ")
}

@Composable
private fun NotConnectedCard(onLoginClick: () -> Unit) {
    Column {
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onLoginClick)) {
            ListItem(
                headlineContent = { Text("未连接账号") },
                supportingContent = { Text("前往 账号 设置 Personal Access Token 后查看资料") },
                leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
            )
        }
        Text(
            text = "登录后可查看通知、个人资料、我的话题，并在 app 内直接回复、收藏",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentTopicRow(topic: Topic) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = topic.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            topic.nodeTitle.ifBlank { null },
            relativeTimeText(topic.activityTimestamp).ifBlank { null },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun StatRow(icon: ImageVector, label: String, count: Int, onClick: (() -> Unit)? = null) {
    val rowModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ListItem(
        modifier = rowModifier,
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (count > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (onClick != null) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}
