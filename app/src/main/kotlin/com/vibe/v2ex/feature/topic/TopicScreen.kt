package com.vibe.v2ex.feature.topic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.designsystem.Avatar
import com.vibe.v2ex.designsystem.htmlToPlainText
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

    Scaffold(
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
                        modifier = Modifier.padding(end = 8.dp),
                    )
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
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (topic != null) {
                item { TopicHeader(topic) }
                item { HorizontalDivider() }
            }
            items(uiState.visibleReplies, key = { it.reply.id }) { floorReply ->
                ReplyRow(floorReply)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TopicHeader(topic: Topic) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = topic.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Text(
            text = htmlToPlainText(topic.contentRendered?.ifBlank { topic.content.orEmpty() } ?: topic.content.orEmpty()),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ReplyRow(floorReply: FloorReply) {
    val reply = floorReply.reply
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Avatar(username = reply.authorName, url = reply.member?.avatarUrl, size = 32.dp)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = " #${floorReply.floor}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = htmlToPlainText(reply.contentRendered.ifBlank { reply.content }),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
