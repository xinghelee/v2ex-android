package com.vibe.v2ex.feature.nodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.nodes.NodeCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(
    onNodeClick: (String) -> Unit,
    viewModel: NodesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var editMode by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("节点") }) }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("搜索节点") },
                singleLine = true,
            )
            if (uiState.query.isNotBlank()) {
                SearchResultList(results = uiState.searchResults, onNodeClick = onNodeClick)
            } else {
                NodeDirectory(
                    uiState = uiState,
                    editMode = editMode,
                    onToggleEdit = { editMode = !editMode },
                    onRemoveFollowed = viewModel::removeFollowed,
                    onNodeClick = onNodeClick,
                )
            }
        }
    }
}

@Composable
private fun SearchResultList(results: List<Node>, onNodeClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results, key = { it.id.takeIf { id -> id != 0L } ?: it.name }) { node ->
            NodeRow(node = node, onClick = { onNodeClick(node.name) })
            HorizontalDivider()
        }
        if (results.isEmpty()) {
            item {
                Text(
                    text = "没有匹配的节点",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeDirectory(
    uiState: NodesUiState,
    editMode: Boolean,
    onToggleEdit: () -> Unit,
    onRemoveFollowed: (String) -> Unit,
    onNodeClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "followed-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("我关注的节点")
                Spacer(modifier = Modifier.weight(1f))
                if (uiState.followedNames.isNotEmpty()) {
                    TextButton(onClick = onToggleEdit) {
                        Text(if (editMode) "完成" else "编辑")
                    }
                }
            }
        }
        item(key = "followed-chips") {
            if (uiState.followedNames.isEmpty()) {
                Text(
                    text = "还没有关注的节点，点下方分类或搜索添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                ChipCloud {
                    uiState.followedNames.forEach { name ->
                        NodeChip(
                            title = uiState.displayTitle(name),
                            removable = editMode,
                            onClick = {
                                if (editMode) onRemoveFollowed(name) else onNodeClick(name)
                            },
                        )
                    }
                }
            }
        }
        NodeCatalog.categories.forEach { category ->
            item(key = "category-${category.title}") {
                Column {
                    SectionTitle(
                        title = category.title,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                    ChipCloud {
                        category.nodeNames.distinct().forEach { name ->
                            NodeChip(
                                title = uiState.displayTitle(name),
                                removable = false,
                                onClick = { onNodeClick(name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipCloud(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun NodeChip(title: String, removable: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(title) },
        trailingIcon = if (removable) {
            {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "取消关注 $title",
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun NodeRow(node: Node, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = node.title.ifBlank { node.name })
        Text(
            text = "${node.topics ?: 0} 篇主题",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
