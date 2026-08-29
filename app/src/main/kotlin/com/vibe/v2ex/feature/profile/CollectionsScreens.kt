package com.vibe.v2ex.feature.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.local.FavoriteTopicEntity
import com.vibe.v2ex.data.local.HistoryEntity
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.data.repository.FavoritesRepository
import com.vibe.v2ex.data.repository.HistoryRepository
import com.vibe.v2ex.data.repository.OfflineBundle
import com.vibe.v2ex.data.repository.OfflineRepository
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.ReplyCount
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.cardGroupPosition
import com.vibe.v2ex.designsystem.relativeTimeText
import com.vibe.v2ex.designsystem.topicRowTitle
import com.vibe.v2ex.feature.home.TAB_BAR_CLEARANCE
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// MARK: - 通用壳：返回行 + 大标题 + 尾随动作

@Composable
private fun CollectionPageScaffold(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = "返回",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 14.dp),
        )
        content()
    }
}

@Composable
private fun EmptyHint(text: String) {
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
    }
}

/** 收藏/历史行：标题 + `节点 · 作者/时间` meta + 右侧回复数；长按弹移除菜单。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionTopicRow(
    title: String,
    meta: String,
    replies: Int = 0,
    onClick: () -> Unit,
    removeLabel: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (onRemove != null) ({ menuExpanded = true }) else null,
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.topicRowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            ReplyCount(replies, modifier = Modifier.padding(start = 10.dp, top = 2.dp))
        }
        if (onRemove != null && removeLabel != null) {
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(removeLabel) },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    },
                )
            }
        }
    }
}

// MARK: - 我的收藏

data class FavoritesUiState(
    val favorites: List<FavoriteTopicEntity> = emptyList(),
    val isSyncing: Boolean = false,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {
    private val syncing = MutableStateFlow(false)

    val uiState: StateFlow<FavoritesUiState> =
        kotlinx.coroutines.flow.combine(favoritesRepository.observeAll(), syncing) { favorites, isSyncing ->
            FavoritesUiState(favorites, isSyncing)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    init {
        // 登录态下拉 V2EX 网页收藏合并进本地（全量，最多 10 页）。
        viewModelScope.launch { favoritesRepository.syncFromRemote() }
    }

    /** 新收藏按时间倒序出现在第一页；完整历史已由 init 的后台任务同步。 */
    fun refresh() {
        viewModelScope.launch {
            syncing.value = true
            favoritesRepository.syncFromRemote(maxPages = 1)
            syncing.value = false
        }
    }

    fun remove(topicId: Long) {
        viewModelScope.launch { favoritesRepository.removeLocal(topicId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onTopicClick: (Long) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    CollectionPageScaffold(title = "我的收藏", onBack = onBack) {
        PullToRefreshBox(
            isRefreshing = uiState.isSyncing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = TAB_BAR_CLEARANCE + 24.dp),
            ) {
                if (uiState.favorites.isEmpty()) {
                    item(key = "empty") { EmptyHint("还没有收藏。在话题页点右上角的星标即可收藏；登录后会自动同步网页收藏。") }
                } else {
                    itemsIndexed(uiState.favorites, key = { _, item -> item.topicId }) { index, favorite ->
                        CardGroupItem(
                            position = cardGroupPosition(index, uiState.favorites.lastIndex),
                            dividerInset = 16.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            CollectionTopicRow(
                                title = favorite.title,
                                meta = listOf(favorite.nodeName, favorite.authorName)
                                    .filter(String::isNotBlank)
                                    .joinToString(" · "),
                                onClick = { onTopicClick(favorite.topicId) },
                                removeLabel = "取消收藏",
                                onRemove = { viewModel.remove(favorite.topicId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 浏览历史

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {
    val entries: StateFlow<List<HistoryEntity>> = historyRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { historyRepository.prune() }
    }

    fun remove(topicId: Long) = viewModelScope.launch { historyRepository.remove(topicId) }
    fun clear() = viewModelScope.launch { historyRepository.clear() }
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onTopicClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空浏览历史？") },
            text = { Text("共 ${entries.size} 条记录，清空后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clear()
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } },
        )
    }

    CollectionPageScaffold(
        title = "浏览历史",
        onBack = onBack,
        trailing = {
            if (entries.isNotEmpty()) {
                Text(
                    text = "清空",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showClearConfirm = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        },
    ) {
        // 按天分组：entries 已按时间倒序，顺序走一遍即可。
        val sections = remember(entries) { groupHistoryByDay(entries) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = TAB_BAR_CLEARANCE + 24.dp),
        ) {
            if (entries.isEmpty()) {
                item(key = "empty") {
                    EmptyHint("还没有浏览记录。读过的话题会出现在这里，保留 ${HistoryRepository.RETENTION_DAYS} 天。")
                }
            } else {
                sections.forEach { (day, dayEntries) ->
                    item(key = "header-$day") {
                        SectionHeader(day, modifier = Modifier.padding(top = 8.dp))
                    }
                    itemsIndexed(dayEntries, key = { _, entry -> entry.topicId }) { index, entry ->
                        CardGroupItem(
                            position = cardGroupPosition(index, dayEntries.lastIndex),
                            dividerInset = 16.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            CollectionTopicRow(
                                title = entry.title,
                                meta = listOf(entry.nodeName, relativeTimeText(entry.viewedAt / 1000))
                                    .filter(String::isNotBlank)
                                    .joinToString(" · "),
                                onClick = { onTopicClick(entry.topicId) },
                                removeLabel = "从历史中移除",
                                onRemove = { viewModel.remove(entry.topicId) },
                            )
                        }
                    }
                }
                item(key = "footer") {
                    Text(
                        text = "记录保留 ${HistoryRepository.RETENTION_DAYS} 天，只存在这台设备上。",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 32.dp, top = 10.dp, end = 16.dp),
                    )
                }
            }
        }
    }
}

private fun groupHistoryByDay(entries: List<HistoryEntity>): List<Pair<String, List<HistoryEntity>>> {
    val formatter = SimpleDateFormat("M 月 d 日", Locale.CHINA)
    val today = Calendar.getInstance()
    val order = mutableListOf<String>()
    val grouped = mutableMapOf<String, MutableList<HistoryEntity>>()
    for (entry in entries) {
        val day = Calendar.getInstance().apply { timeInMillis = entry.viewedAt }
        val title = when {
            sameDay(day, today) -> "今天"
            sameDay(day, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "昨天"
            else -> formatter.format(Date(entry.viewedAt))
        }
        if (grouped[title] == null) order += title
        grouped.getOrPut(title) { mutableListOf() } += entry
    }
    return order.map { it to grouped.getValue(it) }
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

// MARK: - 稍后读 / 离线

@HiltViewModel
class OfflineListViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
) : ViewModel() {
    val bundles: StateFlow<List<OfflineBundle>> = offlineRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(topicId: Long) = viewModelScope.launch { offlineRepository.remove(topicId) }
    fun clear() = viewModelScope.launch { offlineRepository.clear() }
}

@Composable
fun OfflineListScreen(
    onBack: () -> Unit,
    onTopicClick: (Long) -> Unit,
    viewModel: OfflineListViewModel = hiltViewModel(),
) {
    val bundles by viewModel.bundles.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    val formattedSize = remember(bundles) { formatByteSize(bundles.sumOf { it.byteSize }) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部离线内容？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clear()
                }) { Text("清空 $formattedSize", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } },
        )
    }

    CollectionPageScaffold(title = "稍后读 / 离线", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = TAB_BAR_CLEARANCE + 24.dp),
        ) {
            item(key = "summary") {
                V2Card(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${bundles.size} 篇已下载",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "占用 $formattedSize",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (bundles.isNotEmpty()) {
                            Text(
                                text = "清空",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showClearConfirm = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            if (bundles.isEmpty()) {
                item(key = "empty") {
                    EmptyHint("还没有离线内容。在话题页的「⋯」里选择「保存以离线阅读」，整帖和回复都会存到本地。")
                }
            } else {
                itemsIndexed(bundles, key = { _, bundle -> bundle.topic.id }) { index, bundle ->
                    CardGroupItem(
                        position = cardGroupPosition(index, bundles.lastIndex),
                        dividerInset = 16.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        CollectionTopicRow(
                            title = bundle.topic.title,
                            meta = listOf(
                                bundle.topic.nodeTitle,
                                if (bundle.automatic) "自动离线" else "手动保存",
                                relativeTimeText(bundle.cachedAt / 1000),
                            ).filter(String::isNotBlank).joinToString(" · "),
                            replies = bundle.topic.replies,
                            onClick = { onTopicClick(bundle.topic.id) },
                            removeLabel = "删除离线内容",
                            onRemove = { viewModel.remove(bundle.topic.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun formatByteSize(bytes: Int): String = when {
    bytes >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1 shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

// MARK: - 我的话题

data class MyPostsUiState(
    val isTokenSet: Boolean = true,
    val topics: List<Topic> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class MyPostsViewModel @Inject constructor(
    private val apiV2: V2exApiV2,
    private val apiV1: V2exApiV1,
    secureStore: SecureStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyPostsUiState(isTokenSet = secureStore.isTokenSet))
    val uiState: StateFlow<MyPostsUiState> = _uiState.asStateFlow()

    init {
        if (secureStore.isTokenSet) load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // V2EX 只在 API 2.0 暴露当前账号；拿到用户名后走 v1 列它发过的话题。
            val username = runCatching { apiV2.me().result?.username }.getOrNull()
            val topics = username?.let { runCatching { apiV1.topicsByMember(it) }.getOrNull() }.orEmpty()
            _uiState.value = _uiState.value.copy(topics = topics, isLoading = false)
        }
    }
}

@Composable
fun MyPostsScreen(
    onBack: () -> Unit,
    onTopicClick: (Long) -> Unit,
    viewModel: MyPostsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    CollectionPageScaffold(title = "我的话题", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = TAB_BAR_CLEARANCE + 24.dp),
        ) {
            when {
                !uiState.isTokenSet -> item(key = "token") {
                    EmptyHint("需要 Access Token。V2EX 只在 API 2.0 暴露当前账号，到「账号」里填入 Token 后这里会显示你发过的话题。")
                }
                uiState.isLoading -> item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                uiState.topics.isEmpty() -> item(key = "empty") { EmptyHint("还没有发过话题") }
                else -> itemsIndexed(uiState.topics, key = { _, topic -> topic.id }) { index, topic ->
                    CardGroupItem(
                        position = cardGroupPosition(index, uiState.topics.lastIndex),
                        dividerInset = 16.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        CollectionTopicRow(
                            title = topic.title,
                            meta = listOf(
                                topic.nodeTitle,
                                relativeTimeText(topic.activityTimestamp),
                            ).filter(String::isNotBlank).joinToString(" · "),
                            replies = topic.replies,
                            onClick = { onTopicClick(topic.id) },
                        )
                    }
                }
            }
        }
    }
}
