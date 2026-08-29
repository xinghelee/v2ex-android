package com.vibe.v2ex.feature.topic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.ContentBlock
import com.vibe.v2ex.designsystem.ContentBlocksView
import com.vibe.v2ex.designsystem.relativeTimeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    topicId: Long,
    onBack: () -> Unit,
    onReplyClick: () -> Unit,
    viewModel: TopicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.topic?.nodeTitle?.ifBlank { null } ?: "话题") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    FilterChip(
                        selected = uiState.onlyPoster,
                        onClick = viewModel::toggleOnlyPoster,
                        label = { Text("只看楼主") },
                        leadingIcon = { Icon(Icons.Filled.FilterAlt, contentDescription = null) },
                    )
                    if (uiState.favoriteSyncing) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (uiState.favorited) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (uiState.favorited) "取消收藏" else "收藏",
                                tint = if (uiState.favorited) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onReplyClick) {
                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("回复")
            }
        },
    ) { innerPadding ->
        val topic = uiState.topic
        val error = uiState.error
        when {
            topic == null && uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            topic == null && error != null -> ErrorState(
                message = error,
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (topic != null) {
                    item(key = "header") { TopicHeader(topic, uiState.topicBlocks) }
                    item(key = "header-divider") { HorizontalDivider() }
                }
                items(uiState.visibleReplies, key = { it.reply.id }) { floorReply ->
                    ReplyRow(floorReply)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
                if (topic != null && uiState.visibleReplies.isEmpty() && !uiState.isLoading) {
                    item(key = "empty") {
                        Text(
                            text = if (uiState.onlyPoster) "楼主还没有回复" else "还没有回复",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicHeader(topic: Topic, blocks: List<ContentBlock>) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = topic.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(username = topic.authorName, url = topic.member?.avatarUrl, size = 28.dp)
            Text(
                text = listOfNotNull(
                    topic.authorName.ifBlank { null },
                    relativeTimeText(topic.activityTimestamp).ifBlank { null },
                    "${topic.replies} 回复",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (blocks.isNotEmpty()) {
            ContentBlocksView(
                blocks = blocks,
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ReplyRow(floorReply: FloorReply) {
    val reply = floorReply.reply
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Avatar(username = reply.authorName, url = reply.member?.avatarUrl, size = 32.dp)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (floorReply.isAuthor) AuthorBadge()
                Text(
                    text = relativeTimeText(reply.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "#${floorReply.floor}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            floorReply.quoted?.let { QuoteCapsule(it) }
            ContentBlocksView(
                blocks = floorReply.blocks,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun AuthorBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.padding(start = 6.dp),
    ) {
        Text(
            text = "楼主",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun QuoteCapsule(quoted: QuotedReply) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Text(
            text = buildString {
                append("@").append(quoted.username)
                quoted.floor?.let { append(" #").append(it) }
                if (quoted.excerpt.isNotBlank()) append("：").append(quoted.excerpt)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("重试") }
    }
}
