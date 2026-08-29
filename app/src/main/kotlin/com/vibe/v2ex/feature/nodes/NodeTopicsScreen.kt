package com.vibe.v2ex.feature.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.CardTopicRow
import com.vibe.v2ex.designsystem.cardGroupPosition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeTopicsScreen(
    onBack: () -> Unit,
    onTopicClick: (Long) -> Unit,
    viewModel: NodeTopicsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.nodeTitle.ifBlank { uiState.nodeName }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
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
                    IconButton(onClick = viewModel::toggleFollow) {
                        Icon(
                            imageVector = if (uiState.isFollowed) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (uiState.isFollowed) "取消关注" else "关注节点",
                            tint = if (uiState.isFollowed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.raw.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.raw.isEmpty() && uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.raw.isEmpty() && uiState.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
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
                            modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp),
                        )
                        Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 12.dp)) {
                            Text("重试")
                        }
                    }
                }
                uiState.raw.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "该节点暂无话题",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    val topics = uiState.topics
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        itemsIndexed(topics, key = { _, topic -> topic.id }) { index, topic ->
                            CardGroupItem(position = cardGroupPosition(index, topics.lastIndex)) {
                                CardTopicRow(topic = topic, onClick = { onTopicClick(topic.id) })
                            }
                        }
                        if (!uiState.reachedEnd) {
                            item(key = "load-more") {
                                // 末行出现即翻页；按 raw.size 触发，配合 VM 的 isLoadingMore 闸门保证每页只请求一次
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
