package com.vibe.v2ex.feature.nodes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.nodes.NodeCatalog
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.GlassCircleButton
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.cardGroupPosition
import com.vibe.v2ex.designsystem.identityColor
import java.util.Locale

/** Real category destination. The route passes a stable [categoryId], never a first-node shortcut. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeCategoryScreen(
    categoryId: String,
    onBack: () -> Unit,
    onNodeClick: (String) -> Unit,
    viewModel: NodesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val category = NodeCatalog.category(categoryId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            GlassCircleButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    Icons.Filled.ArrowBackIosNew,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = category?.title ?: "节点分类",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (category == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("找不到这个节点分类", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = categoryId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("返回") }
            }
            return@Column
        }

        val nodes = uiState.nodesIn(category)
        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.allNodes.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 36.dp),
            ) {
                item(key = "category-header") {
                    V2Card(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIconTile(
                                iconId = category.icon,
                                size = 54.dp,
                                iconSize = 23.dp,
                                radius = 15.dp,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.title,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = NodeCatalog.subtitle(category, uiState.titlesByName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                when {
                    uiState.isLoading && uiState.allNodes.isEmpty() -> item(key = "directory-loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = "正在同步实时节点资料，当前展示内置目录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }

                    uiState.error != null && uiState.allNodes.isEmpty() -> item(key = "directory-error") {
                        DirectoryFallbackNotice(onRetry = viewModel::refresh)
                    }
                }

                item(key = "nodes-header") {
                    SectionHeader(
                        title = "节点",
                        modifier = Modifier.padding(top = 8.dp),
                        trailing = {
                            Text(
                                text = "${nodes.size} 个",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }

                itemsIndexed(nodes, key = { _, node -> node.name }) { index, node ->
                    CardGroupItem(
                        position = cardGroupPosition(index, nodes.lastIndex),
                        dividerInset = 62.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        CategoryNodeRow(node = node, onClick = { onNodeClick(node.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryFallbackNotice(onRetry: () -> Unit) {
    V2Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "实时目录加载失败，当前展示内置目录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "重试",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun CategoryNodeRow(node: Node, onClick: () -> Unit) {
    val title = node.title.ifBlank { NodeCatalog.displayName(node.name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "打开 $title 节点" }
            .heightIn(min = 58.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NodeIdentitySquare(
            name = node.name,
            title = title,
            avatarUrl = node.avatarUrl,
            size = 34.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = node.path,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        node.topics?.let { topics ->
            Text(
                text = String.format(Locale.US, "%,d", topics),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
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

@Composable
internal fun CategoryIconTile(
    iconId: String,
    size: Dp = 32.dp,
    iconSize: Dp = 16.dp,
    radius: Dp = 9.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = nodeCategoryIcon(iconId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize),
        )
    }
}

internal fun nodeCategoryIcon(id: String): ImageVector = when (id) {
    "code" -> Icons.Outlined.Code
    "lightbulb" -> Icons.Outlined.Lightbulb
    "coffee" -> Icons.Outlined.Coffee
    "games" -> Icons.Outlined.SportsEsports
    "apple" -> Icons.Outlined.PhoneIphone
    "storage" -> Icons.Outlined.Storage
    "work" -> Icons.Outlined.WorkOutline
    "sell" -> Icons.Outlined.Sell
    "help" -> Icons.AutoMirrored.Outlined.HelpOutline
    else -> Icons.Outlined.Tag
}

/** Node artwork with the same non-flashing fallback policy as iOS IdentitySquare. */
@Composable
internal fun NodeIdentitySquare(
    name: String,
    title: String,
    avatarUrl: String?,
    size: Dp,
    transparentFallback: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * 0.29f)
    val fallbackColor = if (transparentFallback) Color.Transparent else identityColor(name)
    val fallbackTextColor = if (transparentFallback) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(fallbackColor)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = nodeInitials(title),
            color = fallbackTextColor,
            fontSize = size.value.times(0.38f).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun nodeInitials(title: String): String {
    val trimmed = title.trim()
    val first = trimmed.firstOrNull() ?: return "?"
    return if (first.code > 0x2E80) first.toString() else trimmed.take(2).lowercase(Locale.ROOT)
}
