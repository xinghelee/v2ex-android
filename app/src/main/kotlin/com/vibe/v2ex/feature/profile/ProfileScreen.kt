package com.vibe.v2ex.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.repository.HistoryRepository
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.GlassCircleButton
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.relativeTimeText
import com.vibe.v2ex.designsystem.topicRowTitle
import com.vibe.v2ex.feature.home.TAB_BAR_CLEARANCE
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max

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

    // 每次回到本页（含从「账号」返回后）重新检查凭证并刷新资料。
    LaunchedEffect(Unit) { viewModel.refresh() }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            ProfileTopBar(onSettingsClick)

            Spacer(Modifier.height(2.dp))
            ProfileAccountSection(
                uiState = uiState,
                onLoginClick = onLoginClick,
                onRetry = viewModel::refresh,
            )

            Spacer(Modifier.height(16.dp))
            WeeklyFootprintSection(uiState.weeklyHistory)

            Spacer(Modifier.height(16.dp))
            LibrarySection(
                uiState = uiState,
                onFavoritesClick = onFavoritesClick,
                onHistoryClick = onHistoryClick,
                onOfflineClick = onOfflineClick,
                onMyPostsClick = onMyPostsClick,
            )

            Spacer(Modifier.height(16.dp))
            ManagementSection(
                moderationCount = uiState.moderationCount,
                onModerationClick = onModerationClick,
            )

            if (uiState.recentTopics.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                RecentTopicsSection(
                    topics = uiState.recentTopics.take(RECENT_TOPICS_LIMIT),
                    onTopicClick = onTopicClick,
                )
            }

            Spacer(Modifier.height(TAB_BAR_CLEARANCE))
        }
    }
}

@Composable
private fun ProfileTopBar(onSettingsClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center),
        )
        GlassCircleButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "外观与阅读设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun ProfileAccountSection(
    uiState: ProfileUiState,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        uiState.isConnected && uiState.member != null -> MemberHeaderCard(uiState.member)
        uiState.isConnected && uiState.isLoading -> ProfileLoadingCard()
        uiState.isConnected && uiState.error != null -> ProfileErrorCard(uiState.error, onRetry)
        uiState.isConnected -> ProfileLoadingCard()
        else -> GuestCard(onLoginClick)
    }
}

/** 当前 iOS 账号卡：64dp 身份方块、连接状态、会员信息，以及用发丝线隔开的完整简介。 */
@Composable
private fun MemberHeaderCard(member: Member) {
    val usesLargeText = LocalDensity.current.fontScale >= LARGE_TEXT_FONT_SCALE
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Avatar(
                    username = member.username,
                    url = member.avatarUrl,
                    size = 64.dp,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (usesLargeText) {
                        Text(
                            text = member.username,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ConnectedLabel()
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                text = member.username,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            ConnectedLabel()
                        }
                    }
                    Text(
                        text = membershipLine(member),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            profileBio(member)?.let { bio ->
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConnectedLabel() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "已连接",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun membershipLine(member: Member): String {
    val formatter = NumberFormat.getIntegerInstance()
    val clauses = buildList {
        member.id?.let { add("第 ${formatter.format(it)} 号会员") }
        member.created?.let { created ->
            val days = ((System.currentTimeMillis() / 1_000 - created) / 86_400).coerceAtLeast(0)
            add("加入 ${formatter.format(days)} 天")
        }
    }
    return clauses.ifEmpty { listOf("V2EX 会员") }.joinToString(" · ")
}

private fun profileBio(member: Member): String? =
    listOfNotNull(member.bio, member.tagline)
        .map { it.trim() }
        .firstOrNull(String::isNotEmpty)

@Composable
private fun GuestCard(onLoginClick: () -> Unit) {
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = "连接 V2EX 账号",
                    onClick = onLoginClick,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "访客模式，连接 V2EX 账号。打开账号设置，选择网页登录或 Access Token"
                }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "访客模式",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "浏览不受影响；连接账号后可按需启用回复、通知与个人内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "连接 V2EX 账号",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "→",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ProfileLoadingCard() {
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "正在加载个人资料…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileErrorCard(error: String?, onRetry: () -> Unit) {
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "没能加载个人资料",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "网络或接口暂时不可用，你的登录状态没有改变。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "重试加载个人资料",
                        onClick = onRetry,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "重试",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun WeeklyFootprintSection(days: List<ProfileHistoryDay>) {
    val normalizedDays = days.takeIf { it.size == 7 } ?: ProfileHistoryDay.emptyWeek()
    val weeklyCount = normalizedDays.sumOf(ProfileHistoryDay::count)
    SectionHeader(
        title = "本周社区足迹",
        trailing = {
            if (weeklyCount > 0) {
                Text(
                    text = "$weeklyCount 个话题",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WeeklyHistorySummary(weeklyCount)
            WeeklyHistoryChart(normalizedDays)
            Text(
                text = "同一话题按最近一次阅读日期计入，仅使用保存在本机的浏览历史。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeeklyHistorySummary(count: Int) {
    val usesLargeText = LocalDensity.current.fontScale >= LARGE_TEXT_FONT_SCALE
    if (usesLargeText) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "$count 个话题",
                style = MaterialTheme.typography.headlineMedium,
                color = if (count == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            HistoryPeriodLabel(styleForLargeText = true)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = "$count",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (count == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "个话题",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            HistoryPeriodLabel(styleForLargeText = false)
        }
    }
}

@Composable
private fun HistoryPeriodLabel(styleForLargeText: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarToday,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (styleForLargeText) 18.dp else 14.dp),
        )
        Text(
            text = "过去 7 天",
            style = if (styleForLargeText) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeeklyHistoryChart(days: List<ProfileHistoryDay>) {
    val usesLargeText = LocalDensity.current.fontScale >= LARGE_TEXT_FONT_SCALE
    val today = LocalDate.now()
    val accessibilityValue = days.joinToString("，") { day ->
        val date = if (day.date == today) {
            "今天"
        } else {
            "${day.date.monthValue}月${day.date.dayOfMonth}日${weekdayWide(day.date)}"
        }
        "$date ${day.count} 个话题"
    }
    val chartModifier = Modifier
        .fillMaxWidth()
        .clearAndSetSemantics {
            contentDescription = "过去七天阅读足迹。$accessibilityValue"
        }

    if (usesLargeText) {
        Column(modifier = chartModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { day ->
                val isToday = day.date == today
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (isToday) "今天" else weekdayWide(day.date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${day.count} 个话题",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (day.count == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    } else {
        val maximum = max(days.maxOfOrNull(ProfileHistoryDay::count) ?: 0, 1)
        Row(
            modifier = chartModifier.height(96.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEach { day ->
                val isToday = day.date == today
                val barHeight = if (day.count == 0) {
                    4.dp
                } else {
                    max(8f, 48f * day.count / maximum).dp
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${day.count}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (day.count == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    day.count == 0 -> MaterialTheme.colorScheme.outlineVariant
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
                                },
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = weekdayNarrow(day.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySection(
    uiState: ProfileUiState,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onOfflineClick: () -> Unit,
    onMyPostsClick: () -> Unit,
) {
    SectionHeader(
        title = "我的空间",
        trailing = {
            if (uiState.libraryCount > 0) {
                Text(
                    text = "${uiState.libraryCount} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        LibraryRow(
            icon = Icons.Outlined.StarOutline,
            count = uiState.favoriteCount,
            title = "收藏",
            caption = "喜欢的话题",
            onClick = onFavoritesClick,
        )
        InsetDivider(68.dp)
        LibraryRow(
            icon = Icons.Outlined.History,
            count = uiState.historyCount,
            title = "浏览历史",
            caption = "最近 ${HistoryRepository.RETENTION_DAYS} 天",
            onClick = onHistoryClick,
        )
        InsetDivider(68.dp)
        LibraryRow(
            icon = Icons.Outlined.BookmarkBorder,
            count = uiState.offlineCount,
            title = "稍后读",
            caption = if (uiState.offlineCount == 0) "离线资料库" else formatByteSize(uiState.offlineByteSize),
            onClick = onOfflineClick,
        )
        InsetDivider(68.dp)
        LibraryRow(
            icon = Icons.AutoMirrored.Outlined.Article,
            count = uiState.recentTopics.size,
            title = "我的话题",
            caption = if (uiState.isConnected) "最近发布" else "连接后查看",
            onClick = onMyPostsClick,
        )
    }
}

@Composable
private fun LibraryRow(
    icon: ImageVector,
    count: Int,
    title: String,
    caption: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title，$count 项，$caption"
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (count == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ManagementSection(
    moderationCount: Int,
    onModerationClick: () -> Unit,
) {
    SectionHeader(title = "管理")
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "内容与屏蔽",
                    onClick = onModerationClick,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = if (moderationCount == 0) {
                        "内容与屏蔽，关键词、用户与举报记录"
                    } else {
                        "内容与屏蔽，$moderationCount 条规则正在生效"
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "内容与屏蔽",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (moderationCount == 0) {
                        "关键词、用户与举报记录"
                    } else {
                        "$moderationCount 条规则正在生效"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RecentTopicsSection(
    topics: List<Topic>,
    onTopicClick: (Long) -> Unit,
) {
    SectionHeader(
        title = "最近发布",
        trailing = {
            Text(
                text = "${topics.size} 篇",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        topics.forEachIndexed { index, topic ->
            if (index > 0) InsetDivider(16.dp)
            RecentTopicRow(topic, onClick = { onTopicClick(topic.id) })
        }
    }
}

@Composable
private fun InsetDivider(start: Dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun RecentTopicRow(topic: Topic, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "打开话题", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            text = topic.title,
            style = MaterialTheme.typography.topicRowTitle,
            maxLines = 3,
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

private fun weekdayNarrow(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

private fun weekdayWide(date: LocalDate): String = "星期${weekdayNarrow(date)}"

private fun formatByteSize(bytes: Long): String {
    val locale = Locale.getDefault()
    return when {
        bytes >= GIB -> String.format(locale, "%.1f GB", bytes / GIB)
        bytes >= MIB -> String.format(locale, "%.1f MB", bytes / MIB)
        bytes >= KIB -> String.format(locale, "%.1f KB", bytes / KIB)
        else -> "$bytes B"
    }
}

private const val LARGE_TEXT_FONT_SCALE = 1.3f
private const val RECENT_TOPICS_LIMIT = 4
private const val KIB = 1_024.0
private const val MIB = 1_048_576.0
private const val GIB = 1_073_741_824.0
