package com.vibe.v2ex.feature.nodes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.vibe.v2ex.designsystem.V2Colors
import com.vibe.v2ex.designsystem.cardGroupPosition
import com.vibe.v2ex.designsystem.identityColor
import com.vibe.v2ex.feature.home.TAB_BAR_CLEARANCE
import java.util.Locale

@Composable
fun NodesScreen(
    onNodeClick: (String) -> Unit,
    viewModel: NodesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var editMode by remember { mutableStateOf(false) }
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }
    val searchFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Text(
            text = "节点",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = TAB_BAR_CLEARANCE),
        ) {
            if (uiState.query.isNotBlank()) {
                searchResultItems(results = uiState.searchResults, onNodeClick = onNodeClick)
            } else {
                item(key = "followed-header") {
                    SectionHeader(
                        title = "我关注的",
                        trailing = {
                            Text(
                                text = if (editMode) "完成" else "编辑",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { editMode = !editMode },
                            )
                        },
                    )
                }
                item(key = "followed-chips") {
                    FollowedNodesCard(
                        uiState = uiState,
                        editMode = editMode,
                        onRemoveFollowed = viewModel::removeFollowed,
                        onNodeClick = onNodeClick,
                        onAddClick = { searchFocusRequester.requestFocus() },
                    )
                }
                item(key = "categories-header") {
                    SectionHeader(title = "全部分类", modifier = Modifier.padding(top = 22.dp))
                }
                item(key = "categories-card") {
                    CategoriesCard(
                        uiState = uiState,
                        expandedCategories = expandedCategories,
                        onToggleCategory = { title ->
                            expandedCategories =
                                if (title in expandedCategories) expandedCategories - title
                                else expandedCategories + title
                        },
                        onNodeClick = onNodeClick,
                    )
                }
            }
        }
    }
}

/** 设计稿搜索框：rgba(118,118,128,0.12) 底、圆角 12、放大镜 + 16sp 文字，无外框。 */
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
        modifier = modifier.focusRequester(focusRequester),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowedNodesCard(
    uiState: NodesUiState,
    editMode: Boolean,
    onRemoveFollowed: (String) -> Unit,
    onNodeClick: (String) -> Unit,
    onAddClick: () -> Unit,
) {
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (uiState.followedNames.isEmpty()) {
            Text(
                text = "还没有关注的节点，点下方分类或搜索添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.followedNames.forEach { name ->
                    FollowedNodeChip(
                        name = name,
                        title = uiState.displayTitle(name),
                        editMode = editMode,
                        onClick = {
                            if (editMode) onRemoveFollowed(name) else onNodeClick(name)
                        },
                    )
                }
                AddNodeChip(onClick = onAddClick)
            }
        }
    }
}

/** 关注 chip：canvas 底圆角 13，20dp 身份方块 + 14sp 标题；编辑态尾随小 ✕。 */
@Composable
private fun FollowedNodeChip(
    name: String,
    title: String,
    editMode: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 11.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(identityColor(name)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(1),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (editMode) {
            Spacer(Modifier.width(5.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = "取消关注 $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** 「+ 添加」chip：1dp 虚线边、透明底、muted 文字，点击聚焦搜索框。 */
@Composable
private fun AddNodeChip(onClick: () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    ),
                    cornerRadius = CornerRadius(13.dp.toPx()),
                )
            }
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            text = "+ 添加",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoriesCard(
    uiState: NodesUiState,
    expandedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onNodeClick: (String) -> Unit,
) {
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        val categories = NodeCatalog.categories
        categories.forEachIndexed { index, category ->
            val expanded = category.title in expandedCategories
            CategoryRow(
                category = category,
                uiState = uiState,
                expanded = expanded,
                onClick = { onToggleCategory(category.title) },
            )
            if (expanded) {
                CategoryNodesFlow(category = category, uiState = uiState, onNodeClick = onNodeClick)
            }
            if (index != categories.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 58.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

/** iOS 列表行：30dp 彩色方块、17sp 标题、成员摘要、右侧节点数 + chevron。 */
@Composable
private fun CategoryRow(
    category: NodeCategory,
    uiState: NodesUiState,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val dark = LocalV2Dark.current
    val memberNames = category.nodeNames.distinct()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(identityColor(category.title)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = memberNames.take(3).joinToString(" · ") { uiState.displayTitle(it) },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${memberNames.size}",
            fontSize = 15.sp,
            color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0x803C3C43),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryNodesFlow(
    category: NodeCategory,
    uiState: NodesUiState,
    onNodeClick: (String) -> Unit,
) {
    val dark = LocalV2Dark.current
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        category.nodeNames.distinct().forEach { name ->
            Text(
                text = uiState.displayTitle(name),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(V2Colors.accentSoft(dark))
                    .clickable { onNodeClick(name) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
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

/** 搜索结果行：iOS 列表行形态 — 30dp 身份方块、17sp 标题、右侧话题数 + chevron。 */
@Composable
private fun SearchResultNodeRow(node: Node, onClick: () -> Unit) {
    val dark = LocalV2Dark.current
    val title = node.title.ifBlank { node.name }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(identityColor(node.name)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(1),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
                text = node.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        node.topics?.takeIf { it > 0 }?.let { topics ->
            Text(
                text = String.format(Locale.US, "%,d", topics),
                fontSize = 15.sp,
                color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0x803C3C43),
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}
