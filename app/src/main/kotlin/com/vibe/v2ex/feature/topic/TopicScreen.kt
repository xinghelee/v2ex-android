package com.vibe.v2ex.feature.topic

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.ContentBlock
import com.vibe.v2ex.designsystem.ContentBlocksView
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.designsystem.OfflineBadge
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.V2Colors
import com.vibe.v2ex.designsystem.cardGroupPosition
import com.vibe.v2ex.designsystem.relativeTimeText
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 未选中 mini pill 的底色 rgba(118,118,128,0.1)（设计稿 04）。 */
private val PillNeutralBg = Color(0x1A767680)

@Composable
fun TopicScreen(
    topicId: Long,
    onBack: () -> Unit,
    onNodeClick: (String) -> Unit = {},
    onMemberClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: TopicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val composerFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var showShareCard by remember { mutableStateOf(false) }
    var showShareText by remember { mutableStateOf(false) }
    var highlightedReplyId by remember { mutableStateOf<Long?>(null) }

    val jumpToFloor: (Int) -> Unit = { floor ->
        val target = uiState.replies.firstOrNull { it.floor == floor }
        if (target != null) {
            val targetReplies = if (uiState.onlyPoster && !target.isAuthor) {
                viewModel.toggleOnlyPoster()
                uiState.replies
            } else {
                uiState.visibleReplies
            }
            val targetIndex = targetReplies.indexOfFirst { it.reply.id == target.reply.id }
            highlightedReplyId = target.reply.id
            scope.launch {
                // A filter change materializes the hidden rows on the next composition.
                delay(40)
                if (targetIndex >= 0) {
                    listState.animateScrollToItem(
                        index = targetIndex + replyListOffset(targetReplies.size),
                        scrollOffset = -120,
                    )
                }
                delay(2_500)
                if (highlightedReplyId == target.reply.id) highlightedReplyId = null
            }
        }
    }

    if (showShareCard) {
        uiState.topic?.let { topic ->
            TopicShareCardSheet(
                topic = topic,
                summary = uiState.summary,
                onDismiss = { showShareCard = false },
            )
        }
    }
    if (showShareText) {
        uiState.topic?.let { topic ->
            TopicShareTextSheet(
                topic = topic,
                summary = uiState.summary,
                replies = uiState.replies,
                onDismiss = { showShareText = false },
            )
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    // 记住阅读进度：回复就绪后恢复上次读到的楼层（header 占 2 个 item）。
    LaunchedEffect(uiState.pendingRestoreFloor, uiState.replies.size) {
        val floor = uiState.pendingRestoreFloor ?: return@LaunchedEffect
        val index = uiState.visibleReplies.indexOfFirst { it.floor == floor }
        if (index >= 0) {
            listState.scrollToItem(index + replyListOffset(uiState.visibleReplies.size))
            viewModel.consumeRestoreFloor()
        }
    }

    // 滚动时上报当前可见楼层（防抖写盘在 VM 内做）。
    LaunchedEffect(listState, uiState.onlyPoster, uiState.onlyMine, uiState.replies.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstIndex ->
                val replyIndex = firstIndex - replyListOffset(uiState.visibleReplies.size)
                uiState.visibleReplies.getOrNull(replyIndex)?.let { viewModel.onFloorVisible(it.floor) }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            TopicTopBar(
                nodeTitle = uiState.topic?.nodeTitle?.ifBlank { null } ?: "话题",
                favorited = uiState.favorited,
                favoriteSyncing = uiState.favoriteSyncing,
                isOfflineSaved = uiState.isOfflineSaved,
                onBack = onBack,
                onNodeClick = { uiState.topic?.node?.name?.let(onNodeClick) },
                onToggleFavorite = viewModel::toggleFavorite,
                onToggleOffline = viewModel::toggleOffline,
                onShareLink = {
                    if (uiState.topic != null) showShareText = true
                },
                onShareCard = { showShareCard = true },
                onOpenInBrowser = {
                    val topic = uiState.topic ?: return@TopicTopBar
                    context.startActivity(Intent(Intent.ACTION_VIEW, topic.webUrl.toUri()))
                },
            )

            val topic = uiState.topic
            val error = uiState.error
            when {
                topic == null && uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                topic == null && error != null -> ErrorState(
                    message = error,
                    onRetry = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> {
                    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        state = listState,
                        // 键盘弹起时列表随之收缩，内容不会被压在键盘后面。
                        modifier = Modifier.fillMaxSize().imePadding(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = 120.dp + navBarInset,
                        ),
                    ) {
                        if (topic != null) {
                            item(key = "header") {
                                TopicCard(
                                    topic = topic,
                                    blocks = uiState.topicBlocks,
                                    appends = uiState.appends,
                                    views = uiState.topicViews,
                                    isPro = topic.authorName in uiState.proMembers,
                                    isOfflineSaved = uiState.isOfflineSaved,
                                    onAuthorClick = onMemberClick,
                                )
                            }
                            item(key = "reply-header") {
                                ReplyHeaderRow(
                                    count = topic.replies,
                                    onlyPoster = uiState.onlyPoster,
                                    onlyMine = uiState.onlyMine,
                                    onToggleOnlyPoster = viewModel::toggleOnlyPoster,
                                    onToggleOnlyMine = viewModel::toggleOnlyMine,
                                )
                            }
                            item(key = "ai-summary") {
                                TopicSummaryCard(
                                    summary = uiState.summary,
                                    isGenerating = uiState.isGeneratingSummary,
                                    error = uiState.summaryError,
                                    configured = uiState.isDeepSeekConfigured,
                                    onGenerate = viewModel::generateSummary,
                                    onConfigure = onSettingsClick,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                            }
                        }
                        val visible = uiState.visibleReplies
                        if (visible.size > 1) {
                            item(key = "discussion-track") {
                                DiscussionTrack(
                                    replies = visible,
                                    onFloorClick = jumpToFloor,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                            }
                        }
                        itemsIndexed(visible, key = { _, item -> item.reply.id }) { index, floorReply ->
                            CardGroupItem(
                                position = cardGroupPosition(index, visible.lastIndex),
                                dividerInset = 59.dp,
                            ) {
                                ReplyRow(
                                    floorReply = floorReply,
                                    isPro = floorReply.reply.authorName in uiState.proMembers,
                                    highlighted = highlightedReplyId == floorReply.reply.id,
                                    onAuthorClick = onMemberClick,
                                    onReplyClick = {
                                        viewModel.prefillMention(
                                            "@${floorReply.reply.authorName} #${floorReply.floor} ",
                                        )
                                        composerFocus.requestFocus()
                                    },
                                    onQuoteClick = jumpToFloor,
                                )
                            }
                        }
                        if (topic != null && visible.isEmpty() && !uiState.isLoading) {
                            item(key = "empty") {
                                Text(
                                    text = when {
                                        uiState.onlyPoster -> "楼主还没有回复"
                                        uiState.onlyMine -> "还没有与你有关的回复"
                                        else -> "还没有回复"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        ReplyComposerBar(
            draft = uiState.replyDraft,
            isSending = uiState.isSendingReply,
            isLoggedIn = uiState.isWebSessionActive,
            mentionCandidates = mentionCandidates(uiState),
            focusRequester = composerFocus,
            onDraftChange = viewModel::onReplyDraftChange,
            onInsertMention = { name ->
                val text = uiState.replyDraft
                val at = text.lastIndexOf('@')
                if (at >= 0) viewModel.onReplyDraftChange(text.take(at) + "@$name ")
            },
            onSend = viewModel::sendReply,
            onOpenWebReply = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://www.v2ex.com/t/$topicId#reply".toUri()),
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 76.dp),
        )
    }
}

private fun replyListOffset(visibleReplyCount: Int): Int = 3 + if (visibleReplyCount > 1) 1 else 0

/**
 * 草稿末尾正在输入的 `@name` 片段 → 本帖参与者中前缀匹配的候选（最近发言优先）。
 * 只看结尾，@ 前必须是空白或行首（否则邮箱地址也会弹列表）。
 */
private fun mentionCandidates(uiState: TopicUiState): List<String> {
    val text = uiState.replyDraft
    val at = text.lastIndexOf('@')
    if (at < 0) return emptyList()
    if (at > 0 && !text[at - 1].isWhitespace()) return emptyList()
    val fragment = text.substring(at + 1)
    if (fragment.any(Char::isWhitespace)) return emptyList()

    val ordered = LinkedHashSet<String>()
    uiState.replies.asReversed().forEach { item ->
        item.reply.authorName.takeIf(String::isNotBlank)?.let(ordered::add)
    }
    uiState.topic?.authorName?.takeIf(String::isNotBlank)?.let(ordered::add)

    val needle = fragment.lowercase()
    return ordered
        .filter { needle.isEmpty() || it.lowercase().startsWith(needle) }
        .take(8)
}

/** 顶栏：返回 chevron + 节点名面包屑（accent，可点进节点页），右侧收藏星 + ⋯ 菜单。 */
@Composable
private fun TopicTopBar(
    nodeTitle: String,
    favorited: Boolean,
    favoriteSyncing: Boolean,
    isOfflineSaved: Boolean,
    onBack: () -> Unit,
    onNodeClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleOffline: () -> Unit,
    onShareLink: () -> Unit,
    onShareCard: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 返回键独立在左、节点名居中（iOS principal 位）—— 两个功能拉开距离，避免误点。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = nodeTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                // 中间标题最多占一半宽度，与左右按钮保持安全距离。
                .fillMaxWidth(0.5f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onNodeClick)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (favoriteSyncing) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (favorited) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (favorited) "取消收藏" else "收藏",
                    tint = if (favorited) V2Colors.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = "更多操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (isOfflineSaved) "移除离线内容" else "保存以离线阅读") },
                    trailingIcon = {
                        if (isOfflineSaved) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        onToggleOffline()
                    },
                )
                DropdownMenuItem(
                    text = { Text("分享文本模板") },
                    onClick = {
                        menuExpanded = false
                        onShareLink()
                    },
                )
                DropdownMenuItem(
                    text = { Text("分享为卡片") },
                    onClick = {
                        menuExpanded = false
                        onShareCard()
                    },
                )
                DropdownMenuItem(
                    text = { Text("在 V2EX 打开") },
                    onClick = {
                        menuExpanded = false
                        onOpenInBrowser()
                    },
                )
            }
        }
        }
    }
}

/** 话题主卡：标题、作者行（可点进用户页 + PRO + 浏览数 + 离线标记）、正文、附言。 */
@Composable
private fun TopicCard(
    topic: Topic,
    blocks: List<ContentBlock>,
    appends: List<AppendBlock>,
    views: Int?,
    isPro: Boolean,
    isOfflineSaved: Boolean,
    onAuthorClick: (String) -> Unit,
) {
    V2Card {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = topic.authorName.isNotBlank()) { onAuthorClick(topic.authorName) },
                ) {
                    Avatar(username = topic.authorName, url = topic.member?.avatarUrl, size = 30.dp)
                    Column(modifier = Modifier.padding(start = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = topic.authorName.ifBlank { "匿名" },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (isPro) ProBadge()
                        }
                        Text(
                            text = relativeTimeText(topic.activityTimestamp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                views?.let { count ->
                    Icon(
                        Icons.Filled.RemoveRedEye,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = String.format(Locale.US, "%,d", count),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
                if (isOfflineSaved) {
                    Spacer(Modifier.width(6.dp))
                    OfflineBadge()
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            if (blocks.isNotEmpty()) {
                ContentBlocksView(
                    blocks = blocks,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            // 楼主附言：左侧 accent 竖线 + 「楼主 … 补充」标头（网页抓取，API 不返回）。
            appends.forEach { append ->
                Row(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .height(IntrinsicSize.Min),
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "楼主 ${append.append.timeLabel.ifBlank { "补充" }} 补充",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (append.blocks.isNotEmpty()) {
                            ContentBlocksView(
                                blocks = append.blocks,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** PRO 会员徽章（网页抓取，仅展示不落盘）。 */
@Composable
private fun ProBadge() {
    Text(
        text = "PRO",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(PillNeutralBg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** DeepSeek is opt-in: content is sent only after an explicit tap and successful results are cached. */
@Composable
private fun TopicSummaryCard(
    summary: String?,
    isGenerating: Boolean,
    error: String?,
    configured: Boolean,
    onGenerate: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    V2Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "✦ 讨论摘要",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "DeepSeek",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(V2Colors.accentSoft(LocalV2Dark.current))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            when {
                summary != null -> {
                    Text(
                        text = summaryMarkdown(summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "AI 生成，可能不准确",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryAction("重新生成", enabled = !isGenerating, onClick = onGenerate)
                    }
                }
                isGenerating -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "正在通过 DeepSeek 阅读这段讨论…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                configured -> {
                    Text(
                        "生成时会把本帖正文和前 60 条回复发送给 DeepSeek；结果缓存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SummaryAction("生成摘要", enabled = true, onClick = onGenerate)
                }
                else -> {
                    Text(
                        "配置 DeepSeek API Key 后，可手动提炼核心内容、主要观点与讨论分歧。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SummaryAction("前往设置", enabled = true, onClick = onConfigure)
                }
            }
            if (!error.isNullOrBlank()) {
                Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SummaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(V2Colors.accentSoft(LocalV2Dark.current))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    )
}

/** Small Markdown subset produced by the prompt: headings, bullets and bold spans. */
internal fun summaryMarkdown(source: String) = buildAnnotatedString {
    val normalized = source.lineSequence().joinToString("\n") { raw ->
        val trimmed = raw.trimStart()
        when {
            Regex("^#{1,6}\\s+").containsMatchIn(trimmed) ->
                "**${trimmed.replaceFirst(Regex("^#{1,6}\\s+"), "")}**"
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") ->
                "• ${trimmed.drop(2)}"
            else -> raw
        }
    }
    var index = 0
    while (index < normalized.length) {
        val start = normalized.indexOf("**", index)
        if (start < 0) {
            append(normalized.substring(index))
            break
        }
        append(normalized.substring(index, start))
        val end = normalized.indexOf("**", start + 2)
        if (end < 0) {
            append(normalized.substring(start))
            break
        }
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(normalized.substring(start + 2, end))
        pop()
        index = end + 2
    }
}

/** 回复区头行：`N 条回复` + 「按楼层 / 只看楼主」mini pill。 */
@Composable
private fun ReplyHeaderRow(
    count: Int,
    onlyPoster: Boolean,
    onlyMine: Boolean,
    onToggleOnlyPoster: () -> Unit,
    onToggleOnlyMine: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count 条回复",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MiniPill(
            text = "按楼层",
            selected = !onlyPoster && !onlyMine,
            onClick = {
                when {
                    onlyPoster -> onToggleOnlyPoster()
                    onlyMine -> onToggleOnlyMine()
                }
            },
        )
        Spacer(Modifier.width(6.dp))
        MiniPill(text = "只看楼主", selected = onlyPoster, onClick = { if (!onlyPoster) onToggleOnlyPoster() })
        Spacer(Modifier.width(6.dp))
        MiniPill(text = "与我有关", selected = onlyMine, onClick = { if (!onlyMine) onToggleOnlyMine() })
    }
}

@Composable
private fun MiniPill(text: String, selected: Boolean, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(shape)
            .background(if (selected) V2Colors.accentSoft(LocalV2Dark.current) else PillNeutralBg)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

/** Compact map of a long discussion. First/last are retained and intermediate floors are sampled evenly. */
@Composable
private fun DiscussionTrack(
    replies: List<FloorReply>,
    onFloorClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maximum = 8
    val sampled = if (replies.size <= maximum) {
        replies
    } else {
        (0 until maximum).map { position ->
            val index = ((position.toDouble() / (maximum - 1)) * (replies.size - 1)).toInt()
            replies[index]
        }.distinctBy { it.reply.id }
    }

    V2Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "讨论轨道",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${replies.size} 层",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 5.dp, start = 16.dp, end = 16.dp),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    sampled.forEach { item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .clickable { onFloorClick(item.floor) }
                                .padding(vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (item.isAuthor) 10.dp else 9.dp)
                                    .clip(if (item.isAuthor) RoundedCornerShape(2.dp) else CircleShape)
                                    .background(
                                        if (item.isAuthor) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface,
                                    )
                                    .let { dot ->
                                        if (item.isAuthor) dot else dot.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            CircleShape,
                                        )
                                    },
                            )
                            Text(
                                item.floor.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (item.isAuthor) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(
    floorReply: FloorReply,
    isPro: Boolean,
    highlighted: Boolean,
    onAuthorClick: (String) -> Unit,
    onReplyClick: () -> Unit,
    onQuoteClick: (Int) -> Unit,
) {
    val reply = floorReply.reply
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlighted) V2Colors.accentSoft(LocalV2Dark.current)
                else Color.Transparent,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable(enabled = reply.authorName.isNotBlank()) { onAuthorClick(reply.authorName) },
        ) {
            Avatar(username = reply.authorName, url = reply.member?.avatarUrl, size = 32.dp)
        }
        Column(modifier = Modifier.padding(start = 11.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 左侧信息打包吃满剩余宽度，让楼层号 + 回复按钮固定钉在右缘对齐。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = reply.authorName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(enabled = reply.authorName.isNotBlank()) {
                                onAuthorClick(reply.authorName)
                            },
                    )
                    if (floorReply.isAuthor) AuthorBadge()
                    if (isPro) ProBadge()
                    Text(
                        text = relativeTimeText(reply.created),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Text(
                    text = "#${floorReply.floor}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "回复 ${reply.authorName}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onReplyClick)
                        .padding(6.dp),
                )
            }
            floorReply.quoted?.let { quoted ->
                QuoteCapsule(quoted = quoted, onClick = quoted.floor?.let { { onQuoteClick(it) } })
            }
            ContentBlocksView(
                blocks = floorReply.blocks,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** 楼主徽章：11sp/600 accent 字 accentSoft 底 圆角 5。 */
@Composable
private fun AuthorBadge() {
    Text(
        text = "楼主",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(V2Colors.accentSoft(LocalV2Dark.current))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** 引用链胶囊：左竖线 2.5dp accent 35%，`user #N` accent + 一行摘要 muted。 */
@Composable
private fun QuoteCapsule(quoted: QuotedReply, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(IntrinsicSize.Min)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        )
        Column(modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp).weight(1f)) {
            Text(
                text = buildString {
                    append(quoted.username)
                    quoted.floor?.let { append(" #").append(it) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (quoted.excerpt.isNotBlank()) {
                Text(
                    text = quoted.excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = "跳到被引用的回复",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp).size(14.dp),
            )
        }
    }
}

/**
 * 底部回复条 — 安卓常规形态：实心底、顶部 0.5dp 分割线、贴底通栏（不悬浮）。
 * 登录态是真输入框（草稿自动保存、@ 补全、直接发送）；未登录时点击打开网页版。
 */
@Composable
private fun ReplyComposerBar(
    draft: String,
    isSending: Boolean,
    isLoggedIn: Boolean,
    mentionCandidates: List<String>,
    focusRequester: FocusRequester,
    onDraftChange: (String) -> Unit,
    onInsertMention: (String) -> Unit,
    onSend: () -> Unit,
    onOpenWebReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalV2Dark.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (isLoggedIn && mentionCandidates.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                itemsIndexed(mentionCandidates, key = { _, name -> name }) { _, name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (dark) Color(0xFF2C2C2E).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f),
                            )
                            .clickable { onInsertMention(name) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    // inset 加在 Surface 内部，白底才能铺满键盘/导航条上方，
                    // 不会露出后面的列表；union 取两者较大值，避免双重叠加。
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // 输入区：surfaceVariant 圆角底，安卓输入条惯用形态。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .let { if (!isLoggedIn) it.clickable(onClick = onOpenWebReply) else it }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (isLoggedIn) {
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (draft.isEmpty()) {
                                        Text(
                                            text = "写下你的回复…",
                                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 21.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    } else {
                        Text(
                            text = "写下你的回复…（未登录将打开网页版）",
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 21.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, bottom = 1.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLoggedIn && draft.isBlank()) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        .clickable(enabled = !isSending) {
                            if (isLoggedIn) onSend() else onOpenWebReply()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "发送回复",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("重试") }
    }
}
