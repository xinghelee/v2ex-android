package com.vibe.v2ex.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.CardTopicRow
import com.vibe.v2ex.designsystem.FeaturedTopicCard
import com.vibe.v2ex.designsystem.GlassCircleButton
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.designsystem.OfflineBadge
import com.vibe.v2ex.designsystem.OfflineNoticeBar
import com.vibe.v2ex.designsystem.PromotionBadge
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.cardGroupPosition
import kotlinx.coroutines.launch

/** 各 tab 列表的底部留白（底栏为常规通栏，由 Scaffold 占位，这里只留呼吸空间）。 */
val TAB_BAR_CLEARANCE = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTopicClick: (Long) -> Unit,
    onComposeClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onNodeClick: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = uiState.currentIndex,
        pageCount = { uiState.feeds.size },
    )
    val railState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectFeed(pagerState.currentPage)
        val target = pagerState.currentPage.coerceIn(0, (uiState.feeds.lastIndex).coerceAtLeast(0))
        railState.animateScrollToItem(target)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // 大标题 + 玻璃搜索圆钮 + accent 发布圆钮（设计稿 01 顶栏）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "V2EX",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            GlassCircleButton(onClick = onSearchClick) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            GlassCircleButton(onClick = onComposeClick, accent = true) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "写新话题",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        FeedChipRail(
            feeds = uiState.feeds,
            selectedIndex = pagerState.currentPage,
            railState = railState,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> uiState.feeds.getOrNull(page)?.key ?: "page-$page" },
        ) { page ->
            val feed = uiState.feeds.getOrNull(page) ?: return@HorizontalPager
            FeedPage(
                topics = uiState.topicsByFeed[feed.key],
                isLoading = feed.key in uiState.loadingFeeds,
                error = uiState.errorsByFeed[feed.key],
                cachedAt = uiState.cachedAtByFeed[feed.key],
                featuredBadge = when (feed) {
                    HomeFeed.Hot -> "今日最热"
                    HomeFeed.R2 -> "R2 排序"
                    else -> "最新活跃"
                },
                readIds = if (uiState.dimReadTopics) uiState.readIds else emptySet(),
                offlineIds = uiState.offlineIds,
                showCommunityPulse = feed == HomeFeed.All && uiState.communityPulseEnabled,
                onRefresh = {
                    if (pagerState.currentPage == page) viewModel.refresh(feed)
                },
                onTopicClick = onTopicClick,
                onNodeClick = onNodeClick,
            )
        }
    }
}

/** 分类 chip 栏：选中 = accent 底白字圆角 16；未选 = 白 80% 底（设计稿）。 */
@Composable
private fun FeedChipRail(
    feeds: List<HomeFeed>,
    selectedIndex: Int,
    railState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (Int) -> Unit,
) {
    val dark = LocalV2Dark.current
    LazyRow(
        state = railState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
    ) {
        itemsIndexed(feeds, key = { _, feed -> feed.key }) { index, feed ->
            val selected = index == selectedIndex
            Text(
                text = feed.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = when {
                    selected -> Color.White
                    dark -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> Color(0xFF3C3C43)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            dark -> Color(0xFF1C1C1E).copy(alpha = 0.9f)
                            else -> Color.White.copy(alpha = 0.85f)
                        },
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 15.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun FeedPage(
    topics: List<Topic>?,
    isLoading: Boolean,
    error: String?,
    /** 非空 = 列表来自本地快照，值是快照时间（毫秒）。 */
    cachedAt: Long?,
    featuredBadge: String,
    readIds: Set<Long> = emptySet(),
    offlineIds: Set<Long> = emptySet(),
    showCommunityPulse: Boolean,
    onRefresh: () -> Unit,
    onTopicClick: (Long) -> Unit,
    onNodeClick: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isLoading && topics != null,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            topics == null && isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            topics == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error ?: "加载失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Button(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) {
                        Text("重试")
                    }
                }
            }
            topics.isEmpty() -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "暂无话题",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            else -> {
                val featured = topics.first()
                val rest = topics.drop(1)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = TAB_BAR_CLEARANCE,
                    ),
                ) {
                    if (cachedAt != null) {
                        item(key = "offline-banner") {
                            OfflineNoticeBar(Modifier.padding(bottom = 10.dp), cachedAt = cachedAt)
                        }
                    }
                    if (showCommunityPulse) {
                        item(key = "community-pulse") {
                            CommunityPulseCard(
                                topics = topics,
                                onNodeClick = onNodeClick,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                    item(key = "featured-${featured.id}") {
                        FeaturedTopicCard(
                            topic = featured,
                            badge = featuredBadge,
                            onClick = { onTopicClick(featured.id) },
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    itemsIndexed(rest, key = { _, topic -> topic.id }) { index, topic ->
                        CardGroupItem(position = cardGroupPosition(index, rest.lastIndex)) {
                            CardTopicRow(
                                topic = topic,
                                onClick = { onTopicClick(topic.id) },
                                dimmed = topic.id in readIds,
                                trailingBadge = when {
                                    topic.id in offlineIds -> ({ OfflineBadge() })
                                    topic.isPromotionNode -> ({ PromotionBadge() })
                                    else -> null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CommunitySignal(
    val name: String,
    val title: String,
    val topicCount: Int,
    val replies: Int,
) {
    val score: Int get() = replies + topicCount * 2
}

/** Public-feed snapshot: where the current conversation is concentrated. */
@Composable
private fun CommunityPulseCard(
    topics: List<Topic>,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sampled = topics.take(30)
    val signals = sampled
        .filter { !it.node?.name.isNullOrBlank() }
        .groupBy { it.node!!.name }
        .map { (name, nodeTopics) ->
            CommunitySignal(
                name = name,
                title = nodeTopics.first().nodeTitle.ifBlank { name },
                topicCount = nodeTopics.size,
                replies = nodeTopics.sumOf { it.replies },
            )
        }
        .sortedWith(compareByDescending<CommunitySignal> { it.score }.thenBy { it.title })
        .take(3)
    if (signals.isEmpty()) return

    val maximum = signals.maxOf { it.score }.coerceAtLeast(1)
    V2Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("社区脉搏", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "当前讨论集中在哪里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${sampled.size} 个话题 · ${sampled.sumOf { it.replies }} 条回复",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            signals.forEach { signal ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNodeClick(signal.name) }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        signal.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.width(76.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(signal.score.toFloat() / maximum)
                                .height(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
                        )
                    }
                    Text(
                        signal.replies.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(38.dp),
                    )
                }
            }
        }
    }
}
