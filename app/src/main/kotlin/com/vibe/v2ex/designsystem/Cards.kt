package com.vibe.v2ex.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibe.v2ex.data.model.Topic

/** 白卡容器 — 设计稿圆角 22。 */
@Composable
fun V2Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column { content() }
    }
}

/** 懒列表分组卡片的行位置：首行圆上角、末行圆下角，保持整卡观感不破坏惰性。 */
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
    dividerInset: androidx.compose.ui.unit.Dp = 62.dp,
    content: @Composable () -> Unit,
) {
    val radius = 22.dp
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
            HorizontalDivider(
                modifier = Modifier.padding(start = dividerInset),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** 精选卡上的节点 pill：accent 文字 + accentSoft 底，圆角 7。 */
@Composable
fun NodePill(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(V2Colors.accentSoft(LocalV2Dark.current))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 列表行 meta：`节点名(accent) · 作者 · 时间`，节点为纯彩色文字（设计稿列表行形态）。 */
@Composable
fun TopicMetaLine(topic: Topic, modifier: Modifier = Modifier, showAuthor: Boolean = true) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (topic.nodeTitle.isNotBlank()) {
            Text(
                text = topic.nodeTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            MetaDot()
        }
        Text(
            text = listOfNotNull(
                topic.authorName.takeIf { showAuthor && it.isNotBlank() },
                relativeTimeText(topic.activityTimestamp).ifBlank { null },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetaDot() {
    Text(
        text = " · ",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
    )
}

/** 回复数：纯数字，14sp/600 muted，右对齐（设计稿不用胶囊）。 */
@Composable
fun ReplyCount(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Text(
        text = "$count",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * 精选头卡（设计稿 01 首页首条）：节点 pill + 徽章文字、21sp 标题、摘要、
 * 24dp 身份方块作者行、右下角回复数。
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
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (topic.nodeTitle.isNotBlank()) {
                    NodePill(topic.nodeTitle)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
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
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(username = topic.authorName, url = topic.member?.avatarUrl, size = 24.dp)
                Text(
                    text = topic.authorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = relativeTimeText(topic.activityTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                ReplyCount(topic.replies)
            }
        }
    }
}

/**
 * 分组卡片内的标准话题行（设计稿 01/03）：34dp 身份方块、16sp/500 标题、
 * accent 节点文字 meta、右侧纯数字回复数。分割线由 CardGroupItem 提供（inset 62）。
 */
@Composable
fun CardTopicRow(
    topic: Topic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    trailingBadge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(username = topic.authorName, url = topic.member?.avatarUrl, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = topic.title,
                style = MaterialTheme.typography.topicRowTitle,
                color = MaterialTheme.colorScheme.onSurface.let {
                    if (dimmed) it.copy(alpha = 0.4f) else it
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopicMetaLine(topic, modifier = Modifier.weight(1f, fill = false), showAuthor = false)
                trailingBadge?.let {
                    Spacer(Modifier.width(6.dp))
                    it()
                }
            }
        }
        ReplyCount(topic.replies, modifier = Modifier.padding(start = 10.dp, top = 2.dp))
    }
}

/** 分区小标题（rgba(60,60,67,0.6)、13sp、左缩进 32）。 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 推广标记：发在 promotions 节点 = 站方已声明的商业内容，标记而不隐藏。 */
@Composable
fun PromotionBadge(modifier: Modifier = Modifier) {
    Text(
        text = "推广",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 离线标记胶囊：`↓ 已离线`，橙字橙soft底。 */
@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    Text(
        text = "↓ 已离线",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 「你看到的是本地快照」提示条。断网时列表和正文照样在，但回复数、楼层都是旧的 —
 * 不说清楚就会被当成 bug 报上来。[cachedAt] 是毫秒时间戳，null 表示不显示时间。
 */
@Composable
fun OfflineNoticeBar(
    modifier: Modifier = Modifier,
    cachedAt: Long? = null,
    hint: String = "下拉刷新可更新",
) {
    val stamp = cachedAt?.let { relativeTimeText(it / 1000) }.orEmpty()
    Text(
        text = if (stamp.isBlank()) "离线内容 · $hint" else "离线内容 · 更新于 $stamp",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(vertical = 7.dp),
    )
}

/** 玻璃圆钮（顶栏 38dp 圆）：白 90% 底 + 细阴影；主操作形态为纯 accent 底。 */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = LocalV2Dark.current
    Surface(
        onClick = onClick,
        modifier = modifier.size(38.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = when {
            accent -> MaterialTheme.colorScheme.primary
            dark -> Color(0xFF2C2C2E).copy(alpha = 0.92f)
            else -> Color.White.copy(alpha = 0.92f)
        },
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
