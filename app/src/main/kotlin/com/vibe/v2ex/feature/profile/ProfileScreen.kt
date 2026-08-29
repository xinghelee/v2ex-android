package com.vibe.v2ex.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.GlassCircleButton
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.V2Colors
import com.vibe.v2ex.designsystem.relativeTimeText
import com.vibe.v2ex.designsystem.topicRowTitle
import com.vibe.v2ex.feature.home.TAB_BAR_CLEARANCE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onModerationClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onOfflineClick: () -> Unit = {},
    onMyPostsClick: () -> Unit = {},
    onTopicClick: (Long) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // 每次回到本页（含从「账号」返回后）重新检查 Token 并刷新资料。
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // 大标题 + 玻璃设置圆钮（设计稿 08 顶栏）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            GlassCircleButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "外观与阅读设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                uiState.isTokenSet && uiState.member != null -> {
                    MemberHeaderCard(
                        member = uiState.member!!,
                        uiState = uiState,
                        onFavoritesClick = onFavoritesClick,
                        onHistoryClick = onHistoryClick,
                        onModerationClick = onModerationClick,
                    )
                }
                uiState.isTokenSet && uiState.isLoading -> {
                    V2Card {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("正在加载资料…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                uiState.isTokenSet -> {
                    V2Card {
                        Text(
                            text = uiState.error?.let { "资料加载失败：$it" } ?: "资料加载失败，下拉或重进本页重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
                else -> {
                    NotConnectedCard(onLoginClick = onLoginClick)
                }
            }

            // iOS 分组列表（设计稿 08 第二张卡 + iOS collectionsGrid 的全部入口）
            V2Card {
                SettingsListRow(
                    iconColor = Color(0xFF1C7C6B),
                    icon = Icons.Filled.Person,
                    title = "账号",
                    onClick = onLoginClick,
                    showChevron = true,
                )
                RowDivider()
                SettingsListRow(
                    iconColor = V2Colors.Amber,
                    icon = Icons.Filled.Star,
                    title = "我的收藏",
                    detail = "${uiState.favoriteCount}",
                    onClick = onFavoritesClick,
                    showChevron = true,
                )
                RowDivider()
                SettingsListRow(
                    iconColor = Color(0xFF5A7A9E),
                    icon = Icons.Filled.History,
                    title = "浏览历史",
                    detail = "${uiState.historyCount}",
                    onClick = onHistoryClick,
                    showChevron = true,
                )
                RowDivider()
                SettingsListRow(
                    iconColor = Color(0xFFC77700),
                    icon = Icons.Filled.Download,
                    title = "稍后读 / 离线",
                    detail = "${uiState.offlineCount}",
                    onClick = onOfflineClick,
                    showChevron = true,
                )
                RowDivider()
                SettingsListRow(
                    iconColor = Color(0xFF3A8E5A),
                    icon = Icons.AutoMirrored.Filled.Article,
                    title = "我的话题",
                    detail = "${uiState.recentTopics.size}",
                    onClick = onMyPostsClick,
                    showChevron = true,
                )
                RowDivider()
                SettingsListRow(
                    iconColor = Color(0xFF8E5A9E),
                    icon = Icons.Filled.Block,
                    title = "屏蔽的关键词与用户",
                    detail = "${uiState.moderationCount}",
                    onClick = onModerationClick,
                    showChevron = true,
                )
            }
        }

        if (uiState.recentTopics.isNotEmpty()) {
            Spacer(modifier = Modifier.height(21.dp))
            SectionHeader("最近发布")
            V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                uiState.recentTopics.take(5).forEachIndexed { index, topic ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    RecentTopicRow(topic, onClick = { onTopicClick(topic.id) })
                }
            }
        }

        Spacer(modifier = Modifier.height(TAB_BAR_CLEARANCE))
    }
}

/** 会员头卡（设计稿 08）：60dp 渐变身份方块 + 名字 + 简介 + 三块统计瓦片（可点进对应页）。 */
@Composable
private fun MemberHeaderCard(
    member: Member,
    uiState: ProfileUiState,
    onFavoritesClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onModerationClick: () -> Unit = {},
) {
    val dark = LocalV2Dark.current
    V2Card {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (member.avatarUrl != null) {
                    Avatar(username = member.username, url = member.avatarUrl, size = 60.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1C7C6B), Color(0xFF14584D)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = member.username.take(2).lowercase().ifBlank { "?" },
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.username,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle = memberSubtitle(member)
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
            val bio = listOfNotNull(member.bio, member.tagline).firstOrNull { it.isNotBlank() }
            if (bio != null) {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else V2Colors.SecondaryLabelLight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    number = "${uiState.favoriteCount}",
                    label = "收藏",
                    modifier = Modifier.weight(1f),
                    onClick = onFavoritesClick,
                )
                StatTile(
                    number = "${uiState.historyCount}",
                    label = "历史",
                    modifier = Modifier.weight(1f),
                    onClick = onHistoryClick,
                )
                StatTile(
                    number = "${uiState.moderationCount}",
                    label = "屏蔽",
                    numberColor = V2Colors.Amber,
                    modifier = Modifier.weight(1f),
                    onClick = onModerationClick,
                )
            }
        }
    }
}

/** 统计瓦片：canvas 底 圆角 14，数字 19sp/700 + 12sp muted 标签。 */
@Composable
private fun StatTile(
    number: String,
    label: String,
    modifier: Modifier = Modifier,
    numberColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    val dark = LocalV2Dark.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) Color(0xFF2C2C2E) else V2Colors.CanvasLight)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = number,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = numberColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    V2Card {
        Column(
            modifier = Modifier
                .clickable(onClick = onLoginClick)
                .padding(18.dp),
        ) {
            Text(
                text = "未连接账号",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "前往 账号 设置 Personal Access Token 后查看资料。登录后可查看通知、个人资料、我的话题，并在 app 内直接回复、收藏。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** iOS 设置行：30dp 彩色圆角 8 图标方块 + 17sp 标题 + 右 detail/chevron，min 52dp。 */
@Composable
private fun SettingsListRow(
    iconColor: Color,
    icon: ImageVector,
    title: String,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
) {
    val rowModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) {
            Text(
                text = detail,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 行间分割线（inset 58 = 16 + 30 + 12）。 */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun RecentTopicRow(topic: Topic, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            text = topic.title,
            style = MaterialTheme.typography.topicRowTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            topic.nodeTitle.ifBlank { null },
            relativeTimeText(topic.activityTimestamp).ifBlank { null },
            topic.replies.takeIf { it > 0 }?.let { "$it 回复" },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
