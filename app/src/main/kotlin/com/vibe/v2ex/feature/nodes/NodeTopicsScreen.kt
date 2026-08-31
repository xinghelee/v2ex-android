package com.vibe.v2ex.feature.nodes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.GlassCircleButton
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.designsystem.OfflineNoticeBar
import com.vibe.v2ex.designsystem.ReplyCount
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.V2Colors
import com.vibe.v2ex.designsystem.cardGroupPosition
import com.vibe.v2ex.designsystem.htmlToPlainText
import com.vibe.v2ex.designsystem.relativeTimeText
import com.vibe.v2ex.designsystem.topicRowTitle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeTopicsScreen(
    onBack: () -> Unit,
    onTopicClick: (Long) -> Unit,
    viewModel: NodeTopicsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // 顶栏：玻璃返回圆钮 + ⋯ 圆钮（设计稿 03）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircleButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBackIosNew,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box {
                GlassCircleButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    NodeTopicsSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label) },
                            trailingIcon = {
                                if (sort == uiState.sort) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                            onClick = {
                                viewModel.setSort(sort)
                                sortMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.raw.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item(key = "node-header") {
                    NodeHeaderCard(
                        uiState = uiState,
                        onToggleFollow = viewModel::toggleFollow,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                    )
                }
                item(key = "sort-chips") {
                    SortChipsRow(
                        selected = uiState.sort,
                        onSelect = viewModel::setSort,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                uiState.cachedAt?.let { cachedAt ->
                    item(key = "offline-banner") {
                        OfflineNoticeBar(Modifier.padding(bottom = 10.dp), cachedAt = cachedAt)
                    }
                }
                val visibleTopics = uiState.visibleTopics
                when {
                    uiState.raw.isEmpty() && uiState.isLoading -> {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    uiState.raw.isEmpty() && uiState.error != null -> {
                        item(key = "error") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "加载失败",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = uiState.error.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
                                )
                                Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 12.dp)) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                    visibleTopics.isEmpty() -> {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (uiState.raw.isEmpty()) "该节点暂无话题" else "没有可显示的话题",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    else -> {
                        val topics = visibleTopics
                        itemsIndexed(topics, key = { _, topic -> topic.id }) { index, topic ->
                            CardGroupItem(position = cardGroupPosition(index, topics.lastIndex)) {
                                NodeTopicRow(
                                    topic = topic,
                                    dimmed = uiState.dimReadTopics && topic.id in uiState.readIds,
                                    onClick = { onTopicClick(topic.id) },
                                )
                            }
                        }
                        if (!uiState.reachedEnd) {
                            item(key = "load-more") {
                                val loadMoreError = uiState.loadMoreError
                                if (loadMoreError != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = loadMoreError,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Button(onClick = viewModel::loadMore) { Text("重试加载更多") }
                                    }
                                } else {
                                    // 末行出现即翻页；错误态切换为显式重试，不会按相同 key 无限请求。
                                    LaunchedEffect(uiState.raw.size) { viewModel.loadMore() }
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (uiState.isLoadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 节点头卡：56dp 真实节点头像、24sp 标题 + /go/ 路径、关注 pill、简介、统计行。 */
@Composable
private fun NodeHeaderCard(
    uiState: NodeTopicsUiState,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalV2Dark.current
    val title = uiState.nodeTitle.ifBlank { uiState.nodeName }
    V2Card(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NodeIdentitySquare(
                    name = uiState.nodeName,
                    title = title,
                    avatarUrl = uiState.nodeAvatarUrl,
                    size = 56.dp,
                    transparentFallback = true,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "/go/${uiState.nodeName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (uiState.isFollowed) "已关注" else "关注",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (uiState.isFollowed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (uiState.isFollowed) V2Colors.accentSoft(dark)
                            else MaterialTheme.colorScheme.primary,
                        )
                        .clickable(role = Role.Button, onClick = onToggleFollow)
                        .semantics {
                            role = Role.Button
                            contentDescription = if (uiState.isFollowed) "取消关注 $title" else "关注 $title"
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            uiState.nodeHeader?.let { header ->
                val plain = htmlToPlainText(header)
                if (plain.isNotBlank()) {
                    Text(
                        text = plain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else V2Colors.SecondaryLabelLight,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            uiState.topicsCount?.let { topics ->
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    StatItem(number = topics, label = "话题")
                    uiState.starsCount?.takeIf { it > 0 }?.let { stars ->
                        StatItem(number = stars, label = "关注者")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(number: Int, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = String.format(Locale.US, "%,d", number),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 排序 chip 行：选中 accent 底白字圆角 15；未选 #3C3C43 字白 80% 底。 */
@Composable
private fun SortChipsRow(
    selected: NodeTopicsSort,
    onSelect: (NodeTopicsSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalV2Dark.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NodeTopicsSort.entries.forEach { sort ->
            val isSelected = sort == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            dark -> Color(0xFF1C1C1E).copy(alpha = 0.9f)
                            else -> Color.White.copy(alpha = 0.8f)
                        },
                    )
                    .clickable(role = Role.Button) { onSelect(sort) }
                    .semantics {
                        role = Role.Button
                        this.selected = isSelected
                        contentDescription = "按${sort.label}排序"
                    }
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = sort.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        dark -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> V2Colors.SecondaryLabelLight
                    },
                )
            }
        }
    }
}

/** 节点内话题行：34dp 头像、16/500 标题、`作者 · 时间` meta（不再重复节点名）、右侧回复数。 */
@Composable
private fun NodeTopicRow(topic: Topic, onClick: () -> Unit, dimmed: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // v2 节点话题接口不带 member — 没有作者数据时不渲染头像位，避免整列 "?" 方块
        if (topic.authorName.isNotBlank()) {
            Avatar(username = topic.authorName, url = topic.member?.avatarUrl, size = 34.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = topic.title,
                style = MaterialTheme.typography.topicRowTitle,
                color = MaterialTheme.colorScheme.onSurface.let { if (dimmed) it.copy(alpha = 0.4f) else it },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    topic.authorName.takeIf(String::isNotBlank),
                    relativeTimeText(topic.activityTimestamp).ifBlank { null },
                ).joinToString(" · "),
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
