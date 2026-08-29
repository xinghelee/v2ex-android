package com.vibe.v2ex.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibe.v2ex.data.model.Topic

/**
 * 灰底白卡的卡片容器 — 对应 iOS 的 TopicListCard/CardSection。
 * 内容行之间用行内分割线，整卡圆角 20。
 */
@Composable
fun V2Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column { content() }
    }
}

/** 节点标签 pill：主色软底 + 深主色文字。 */
@Composable
fun NodePill(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 回复数小胶囊。 */
@Composable
fun ReplyCountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 信息流顶部的精选头卡 — iOS FeaturedTopicCard 的移植：
 * 徽章（今日最热 / 最新活跃）+ 放大标题 + 摘要 + 作者行。
 */
@Composable
fun FeaturedTopicCard(
    topic: Topic,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                if (topic.nodeTitle.isNotBlank()) NodePill(topic.nodeTitle)
            }
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (topic.excerpt.isNotBlank()) {
                Text(
                    text = topic.excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(username = topic.authorName, url = topic.member?.avatarUrl, size = 24.dp)
                Text(
                    text = listOfNotNull(
                        topic.authorName.ifBlank { null },
                        relativeTimeText(topic.activityTimestamp).ifBlank { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                ReplyCountBadge(topic.replies)
            }
        }
    }
}

/** 懒加载列表里的"分组卡片"行位置 — 首行圆上角、末行圆下角，中间行方角，保持整卡观感又不破坏 LazyColumn 惰性。 */
enum class CardGroupPosition { FIRST, MIDDLE, LAST, SINGLE }

fun cardGroupPosition(index: Int, lastIndex: Int): CardGroupPosition = when {
    lastIndex == 0 -> CardGroupPosition.SINGLE
    index == 0 -> CardGroupPosition.FIRST
    index == lastIndex -> CardGroupPosition.LAST
    else -> CardGroupPosition.MIDDLE
}

@Composable
fun CardGroupItem(
    position: CardGroupPosition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val radius = 20.dp
    val shape = when (position) {
        CardGroupPosition.SINGLE -> RoundedCornerShape(radius)
        CardGroupPosition.FIRST -> RoundedCornerShape(topStart = radius, topEnd = radius)
        CardGroupPosition.LAST -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
        CardGroupPosition.MIDDLE -> RoundedCornerShape(0.dp)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
        if (position == CardGroupPosition.FIRST || position == CardGroupPosition.MIDDLE) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 66.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** 卡片内的标准话题行 — 供 Home / 节点 / 收藏 / 历史等列表共用。 */
@Composable
fun CardTopicRow(
    topic: Topic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(username = topic.authorName, url = topic.member?.avatarUrl, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = topic.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.let {
                    if (dimmed) it.copy(alpha = 0.4f) else it
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (topic.nodeTitle.isNotBlank()) {
                    NodePill(topic.nodeTitle)
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = listOfNotNull(
                        topic.authorName.ifBlank { null },
                        relativeTimeText(topic.activityTimestamp).ifBlank { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ReplyCountBadge(topic.replies, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
