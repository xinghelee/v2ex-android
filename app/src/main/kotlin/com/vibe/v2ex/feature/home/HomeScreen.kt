package com.vibe.v2ex.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.CardTopicRow
import com.vibe.v2ex.designsystem.FeaturedTopicCard
import com.vibe.v2ex.designsystem.cardGroupPosition
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTopicClick: (Long) -> Unit,
    onComposeClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = uiState.currentIndex,
        pageCount = { uiState.feeds.size },
    )
    val railState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 双向同步：滑动 pager → 选中 feed 并让 chip 滚入视野；点 chip → 翻页（见下方 onClick）
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectFeed(pagerState.currentPage)
        val target = pagerState.currentPage.coerceIn(0, (uiState.feeds.lastIndex).coerceAtLeast(0))
        railState.animateScrollToItem(target)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("V2EX", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onComposeClick) {
                Icon(Icons.Filled.Edit, contentDescription = "写新话题")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyRow(
                state = railState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(uiState.feeds, key = { _, feed -> feed.key }) { index, feed ->
                    FilterChip(
                        selected = index == pagerState.currentPage,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        label = { Text(feed.title) },
                    )
                }
            }
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
                    featuredBadge = if (feed == HomeFeed.Hot) "今日最热" else "最新活跃",
                    // 下拉刷新只对当前页生效，避免多个分页容器同时争抢
                    onRefresh = {
                        if (pagerState.currentPage == page) viewModel.refresh(feed)
                    },
                    onTopicClick = onTopicClick,
                )
            }
        }
    }
}

@Composable
private fun FeedPage(
    topics: List<Topic>?,
    isLoading: Boolean,
    error: String?,
    featuredBadge: String,
    onRefresh: () -> Unit,
    onTopicClick: (Long) -> Unit,
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
                // 灰底白卡布局：首条话题渲染为精选头卡，其余合成一张分组卡片
                val featured = topics.first()
                val rest = topics.drop(1)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
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
                            CardTopicRow(topic = topic, onClick = { onTopicClick(topic.id) })
                        }
                    }
                }
            }
        }
    }
}
