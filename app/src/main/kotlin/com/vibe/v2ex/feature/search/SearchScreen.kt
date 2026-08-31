package com.vibe.v2ex.feature.search

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.nodes.NodeCatalog
import com.vibe.v2ex.data.remote.SoV2exHit
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.CardGroupItem
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.V2Colors
import com.vibe.v2ex.designsystem.cardGroupPosition
import com.vibe.v2ex.designsystem.htmlToPlainText
import com.vibe.v2ex.designsystem.topicRowTitle

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onTopicClick: (Long) -> Unit,
    onNodeClick: (String) -> Unit = {},
    onMemberClick: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                onSearch = {
                    viewModel.search()
                    keyboard?.hide()
                },
                onClear = viewModel::clearQuery,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "取消",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onBack),
            )
        }

        ScopeChipsRow(
            selected = uiState.scope,
            onSelect = viewModel::setScope,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            when {
                uiState.isLoading -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                uiState.error != null -> {
                    item(key = "error") {
                        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = uiState.error.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                }
                uiState.scope == SearchScope.MEMBERS && uiState.memberResult != null -> {
                    item(key = "member") {
                        val member = uiState.memberResult!!
                        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMemberClick(member.username) }
                                    .padding(16.dp),
                            ) {
                                Avatar(username = member.username, url = member.avatarUrl, size = 48.dp)
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(
                                        text = member.username,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    member.tagline?.takeIf(String::isNotBlank)?.let { tagline ->
                                        Text(
                                            text = tagline,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                uiState.scope == SearchScope.NODES && uiState.nodeResults.isNotEmpty() -> {
                    val nodes = uiState.nodeResults.take(40)
                    itemsIndexed(nodes, key = { _, node -> node.id.takeIf { it != 0L } ?: node.name }) { index, node ->
                        CardGroupItem(
                            position = cardGroupPosition(index, nodes.lastIndex),
                            dividerInset = 16.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNodeClick(node.name) }
                                    .heightIn(min = 54.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = node.title.ifBlank { node.name },
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
                                node.topics?.takeIf { it > 0 }?.let { topics ->
                                    Text(
                                        text = "$topics",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                uiState.results.isNotEmpty() &&
                    (uiState.scope == SearchScope.TOPICS || uiState.scope == SearchScope.REPLIES) -> {
                    itemsIndexed(uiState.results, key = { _, hit -> hit.source.id }) { index, hit ->
                        CardGroupItem(
                            position = cardGroupPosition(index, uiState.results.lastIndex),
                            dividerInset = 16.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            SearchResultRow(hit = hit, onClick = { onTopicClick(hit.source.id) })
                        }
                    }
                }
                uiState.hasSearched && uiState.query.isNotBlank() -> {
                    item(key = "no-results") {
                        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = "没有找到相关内容",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                }
            }
            if (uiState.recents.isNotEmpty() && !uiState.hasSearched) {
                item(key = "recents") {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        SectionHeader(
                            title = "最近搜索",
                            trailing = {
                                Text(
                                    text = "清空",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable(onClick = viewModel::clearRecents),
                                )
                            },
                        )
                        RecentSearchesCard(
                            recents = uiState.recents,
                            onSearch = { query ->
                                viewModel.searchRecent(query)
                                keyboard?.hide()
                            },
                            onRemove = viewModel::removeRecent,
                        )
                    }
                }
            }
        }
    }
}

/** 设计稿搜索框：rgba(118,118,128,0.12) 底圆角 12、放大镜、非空时尾随 ✕ 清除圆钮。 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val dark = LocalV2Dark.current
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
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
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
                            text = "话题、回复、用户或节点",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline)
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清空",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        },
    )
}

/** 范围 chip 行：话题/回复（sov2ex）、用户（v1 精确查询）、节点（本地过滤）。 */
@Composable
private fun ScopeChipsRow(
    selected: SearchScope,
    onSelect: (SearchScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalV2Dark.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchScope.entries.forEach { scope ->
            val isSelected = scope == selected
            Text(
                text = scope.label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    dark -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> V2Colors.SecondaryLabelLight
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            dark -> Color(0xFF1C1C1E).copy(alpha = 0.9f)
                            else -> Color.White.copy(alpha = 0.8f)
                        },
                    )
                    .clickable { onSelect(scope) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** 结果行：高亮标题、两行内容预览、`节点(accent) · 作者 · N 回复` meta。 */
@Composable
private fun SearchResultRow(hit: SoV2exHit, onClick: () -> Unit) {
    val source = hit.source
    val title = hit.highlight?.title?.firstOrNull()?.let(::highlightFragment)
        ?: AnnotatedString(source.title)
    val preview = hit.highlight?.content?.firstOrNull()?.let(::highlightFragment)
        ?: AnnotatedString(htmlToPlainText(source.content).take(120))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.topicRowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (preview.isNotBlank()) {
            Text(
                text = preview,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            source.nodeName?.takeIf(String::isNotBlank)?.let { node ->
                Text(
                    text = NodeCatalog.displayName(node),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = listOfNotNull(
                    source.member?.takeIf(String::isNotBlank),
                    "${source.replies} 回复",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentSearchesCard(
    recents: List<String>,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        recents.forEachIndexed { index, query ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearch(query) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = query,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除 $query",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onRemove(query) }
                        .padding(4.dp)
                        .size(14.dp),
                )
            }
            if (index != recents.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 42.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

private val EM_TAG = Regex("<em>(.*?)</em>", RegexOption.DOT_MATCHES_ALL)

/** sov2ex 高亮片段：`<em>…</em>` 标记 → SearchHighlight 底色 span，其余文本解实体。 */
private fun highlightFragment(fragment: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    EM_TAG.findAll(fragment).forEach { match ->
        append(decodeEntities(fragment.substring(cursor, match.range.first)))
        withStyle(SpanStyle(background = V2Colors.SearchHighlight)) {
            append(decodeEntities(match.groupValues[1]))
        }
        cursor = match.range.last + 1
    }
    append(decodeEntities(fragment.substring(cursor)))
}

private fun decodeEntities(text: String): String = text
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
