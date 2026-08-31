package com.vibe.v2ex.feature.nodes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.nodes.NodeCatalog
import com.vibe.v2ex.data.nodes.NodeCategory
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.cardGroupPosition
import com.vibe.v2ex.designsystem.htmlToPlainText
import com.vibe.v2ex.feature.home.TAB_BAR_CLEARANCE
import java.util.Locale

@Composable
fun NodesScreen(
    onNodeClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    viewModel: NodesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var editMode by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "节点",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { editMode = !editMode }) {
                Icon(
                    imageVector = if (editMode) Icons.Filled.Check else Icons.Filled.Edit,
                    contentDescription = if (editMode) "完成编辑" else "编辑关注节点",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        NodeSearchField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = uiState.allNodes.size
                .takeIf { it > 0 }
                ?.let { "搜索 ${String.format(Locale.US, "%,d", it)} 个节点" }
                ?: "搜索节点",
            focusRequester = searchFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = TAB_BAR_CLEARANCE),
        ) {
            if (uiState.query.isNotBlank()) {
                searchDirectoryContent(
                    uiState = uiState,
                    onNodeClick = onNodeClick,
                    onRetry = viewModel::refresh,
                )
            } else {
                item(key = "followed-header") {
                    SectionHeader(
                        title = "我关注的",
                        trailing = uiState.followedNames.size.takeIf { it > 0 }?.let { count ->
                            {
                                Text(
                                    text = "$count 个",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
                item(key = "followed-grid") {
                    FollowedNodesGrid(
                        uiState = uiState,
                        editMode = editMode,
                        onRemoveFollowed = viewModel::removeFollowed,
                        onNodeClick = onNodeClick,
                        onAddClick = {
                            viewModel.onQueryChange("")
                            searchFocusRequester.requestFocus()
                        },
                    )
                }

                if (uiState.isLoading && uiState.allNodes.isEmpty()) {
                    item(key = "directory-loading") { DirectoryLoadingRow() }
                } else if (uiState.error != null && uiState.allNodes.isEmpty()) {
                    item(key = "directory-error") {
                        DirectoryErrorCard(
                            message = "实时节点资料加载失败，分类仍可浏览",
                            onRetry = viewModel::refresh,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                item(key = "categories-header") {
                    SectionHeader(title = "全部分类", modifier = Modifier.padding(top = 22.dp))
                }
                item(key = "categories-card") {
                    CategoriesCard(
                        uiState = uiState,
                        onCategoryClick = onCategoryClick,
                    )
                }
            }
        }
    }
}

/** iOS-style search field: subtle neutral fill, 12dp radius and no visible outline. */
@Composable
private fun NodeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val dark = LocalV2Dark.current
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 16.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier
            .focusRequester(focusRequester)
            .semantics { contentDescription = "搜索节点" },
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (dark) Color(0x3D767680) else Color(0x1F767680))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(7.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun FollowedNodesGrid(
    uiState: NodesUiState,
    editMode: Boolean,
    onRemoveFollowed: (String) -> Unit,
    onNodeClick: (String) -> Unit,
    onAddClick: () -> Unit,
) {
    val showAddCard = !editMode || uiState.followedNames.isEmpty()
    val cellCount = uiState.followedNames.size + if (showAddCard) 1 else 0
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val columnCount = if (maxWidth >= 600.dp) 4 else 2
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (rowStart in 0 until cellCount step columnCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(columnCount) { column ->
                        val index = rowStart + column
                        Box(modifier = Modifier.weight(1f)) {
                            when {
                                index < uiState.followedNames.size -> {
                                    val name = uiState.followedNames[index]
                                    key("followed-$name") {
                                        FollowedNodeCard(
                                            name = name,
                                            title = uiState.displayTitle(name),
                                            node = uiState.node(name),
                                            editMode = editMode,
                                            onRemove = { onRemoveFollowed(name) },
                                            onClick = { onNodeClick(name) },
                                        )
                                    }
                                }

                                index == uiState.followedNames.size && showAddCard -> {
                                    key("add-followed-node") {
                                        AddFollowedNodeCard(onClick = onAddClick)
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

@Composable
private fun FollowedNodeCard(
    name: String,
    title: String,
    node: Node?,
    editMode: Boolean,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val summary = node?.header
        ?.let(::htmlToPlainText)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: "/go/$name"
    val topicSummary = node?.topics?.let { "${String.format(Locale.US, "%,d", it)} 个话题" }
        ?: "正在同步资料"
    val clickModifier = if (editMode) {
        Modifier
    } else {
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "打开 $title 节点，$summary，$topicSummary" }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 142.dp)
            .then(clickModifier),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                NodeIdentitySquare(
                    name = name,
                    title = title,
                    avatarUrl = node?.avatarUrl,
                    size = 34.dp,
                    transparentFallback = true,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (editMode) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RemoveCircle,
                            contentDescription = "取消关注 $title",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (node?.topics != null) Icons.Filled.ChatBubbleOutline else Icons.Filled.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = topicSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                if (!editMode) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFollowedNodeCard(onClick: () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 142.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "添加关注节点，聚焦节点搜索"
            }
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    ),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                )
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "添加关注",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "搜索全部节点",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DirectoryLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = "正在同步实时节点资料",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun DirectoryErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    V2Card(modifier = modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onRetry, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun CategoriesCard(
    uiState: NodesUiState,
    onCategoryClick: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnCount = if (maxWidth >= 600.dp) 2 else 1
        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            for (rowStart in NodeCatalog.categories.indices step columnCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    repeat(columnCount) { column ->
                        val index = rowStart + column
                        Box(modifier = Modifier.weight(1f)) {
                            NodeCatalog.categories.getOrNull(index)?.let { category ->
                                CategoryRow(
                                    category = category,
                                    uiState = uiState,
                                    onClick = { onCategoryClick(category.id) },
                                )
                            }
                        }
                        if (
                            column < columnCount - 1 &&
                            rowStart + column + 1 < NodeCatalog.categories.size
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(0.5.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                    }
                }
                if (rowStart + columnCount < NodeCatalog.categories.size) {
                    HorizontalDivider(
                        modifier = if (columnCount == 1) Modifier.padding(start = 60.dp) else Modifier,
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: NodeCategory,
    uiState: NodesUiState,
    onClick: () -> Unit,
) {
    val dark = LocalV2Dark.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "打开${category.title}分类" }
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CategoryIconTile(iconId = category.icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = NodeCatalog.subtitle(category, uiState.titlesByName),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${uiState.countIn(category)}",
            fontSize = 15.sp,
            color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0x803C3C43),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
        )
    }
}

private fun LazyListScope.searchDirectoryContent(
    uiState: NodesUiState,
    onNodeClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        uiState.allNodes.isEmpty() && uiState.isLoading -> {
            item(key = "search-loading") { DirectoryLoadingRow() }
        }

        uiState.allNodes.isEmpty() && uiState.error != null -> {
            item(key = "search-error") {
                DirectoryErrorCard(message = "节点目录加载失败", onRetry = onRetry)
            }
        }

        else -> {
            item(key = "search-result-header") {
                SectionHeader(title = "${uiState.searchResults.size} 个结果")
            }
            searchResultItems(results = uiState.searchResults, onNodeClick = onNodeClick)
        }
    }
}

private fun LazyListScope.searchResultItems(
    results: List<Node>,
    onNodeClick: (String) -> Unit,
) {
    if (results.isEmpty()) {
        item(key = "search-empty") {
            V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "没有匹配的节点",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
        }
        return
    }
    itemsIndexed(results, key = { _, node -> node.id.takeIf { it != 0L } ?: node.name }) { index, node ->
        CardGroupItem(
            position = cardGroupPosition(index, results.lastIndex),
            dividerInset = 58.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            SearchResultNodeRow(node = node, onClick = { onNodeClick(node.name) })
        }
    }
}

@Composable
private fun SearchResultNodeRow(node: Node, onClick: () -> Unit) {
    val dark = LocalV2Dark.current
    val title = node.title.ifBlank { node.name }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "打开 $title 节点" }
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        NodeIdentitySquare(
            name = node.name,
            title = title,
            avatarUrl = node.avatarUrl,
            size = 30.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = node.path,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        node.topics?.let { topics ->
            Text(
                text = String.format(Locale.US, "%,d", topics),
                fontSize = 15.sp,
                color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0x803C3C43),
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
        )
    }
}
