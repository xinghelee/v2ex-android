package com.vibe.v2ex.feature.topic

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.ContentBlock
import com.vibe.v2ex.designsystem.ContentBlocksView
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.V2Colors
import com.vibe.v2ex.designsystem.cardGroupPosition
import com.vibe.v2ex.designsystem.relativeTimeText

/** 未选中 mini pill 的底色 rgba(118,118,128,0.1)（设计稿 04）。 */
private val PillNeutralBg = Color(0x1A767680)

@Composable
fun TopicScreen(
    topicId: Long,
    onBack: () -> Unit,
    onReplyClick: () -> Unit,
    viewModel: TopicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
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
                onBack = onBack,
                onToggleFavorite = viewModel::toggleFavorite,
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = 90.dp + navBarInset,
                        ),
                    ) {
                        if (topic != null) {
                            item(key = "header") { TopicCard(topic, uiState.topicBlocks) }
                            item(key = "reply-header") {
                                ReplyHeaderRow(
                                    count = topic.replies,
                                    onlyPoster = uiState.onlyPoster,
                                    onToggleOnlyPoster = viewModel::toggleOnlyPoster,
                                )
                            }
                        }
                        val visible = uiState.visibleReplies
                        itemsIndexed(visible, key = { _, item -> item.reply.id }) { index, floorReply ->
                            CardGroupItem(
                                position = cardGroupPosition(index, visible.lastIndex),
                                dividerInset = 59.dp,
                            ) {
                                ReplyRow(floorReply)
                            }
                        }
                        if (topic != null && visible.isEmpty() && !uiState.isLoading) {
                            item(key = "empty") {
                                Text(
                                    text = if (uiState.onlyPoster) "楼主还没有回复" else "还没有回复",
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
            onReplyClick = onReplyClick,
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

/** 顶栏：返回 chevron + 节点名面包屑（accent），右侧收藏星。 */
@Composable
private fun TopicTopBar(
    nodeTitle: String,
    favorited: Boolean,
    favoriteSyncing: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = nodeTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(Modifier.weight(1f))
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
    }
}

/** 话题主卡：23sp 标题、30dp 身份方块作者行、0.5 分割线、16sp 正文。 */
@Composable
private fun TopicCard(topic: Topic, blocks: List<ContentBlock>) {
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
                Avatar(username = topic.authorName, url = topic.member?.avatarUrl, size = 30.dp)
                Column(modifier = Modifier.padding(start = 9.dp)) {
                    Text(
                        text = topic.authorName.ifBlank { "匿名" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = relativeTimeText(topic.activityTimestamp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        }
    }
}

/** 回复区头行：`N 条回复` + 「按楼层 / 只看楼主」mini pill。 */
@Composable
private fun ReplyHeaderRow(
    count: Int,
    onlyPoster: Boolean,
    onToggleOnlyPoster: () -> Unit,
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
        MiniPill(text = "按楼层", selected = true, onClick = null)
        Spacer(Modifier.width(6.dp))
        MiniPill(text = "只看楼主", selected = onlyPoster, onClick = onToggleOnlyPoster)
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

@Composable
private fun ReplyRow(floorReply: FloorReply) {
    val reply = floorReply.reply
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Avatar(username = reply.authorName, url = reply.member?.avatarUrl, size = 32.dp)
        Column(modifier = Modifier.padding(start = 11.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (floorReply.isAuthor) AuthorBadge()
                Text(
                    text = relativeTimeText(reply.created),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "#${floorReply.floor}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            floorReply.quoted?.let { QuoteCapsule(it) }
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
private fun QuoteCapsule(quoted: QuotedReply) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        )
        Column(modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp)) {
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
    }
}

/** 底部悬浮回复栏：canvas → 透明的渐变背景 + 玻璃胶囊 + accent 发送圆钮。 */
@Composable
private fun ReplyComposerBar(onReplyClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalV2Dark.current
    val canvas = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to canvas.copy(alpha = 0f),
                    0.5f to canvas.copy(alpha = 0.98f),
                    1f to canvas.copy(alpha = 0.98f),
                ),
            )
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Surface(
            onClick = onReplyClick,
            shape = RoundedCornerShape(24.dp),
            color = if (dark) {
                Color(0xFF1C1C1E).copy(alpha = 0.94f)
            } else {
                Color.White.copy(alpha = 0.94f)
            },
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "写下你的回复…",
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 21.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "回复",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
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
