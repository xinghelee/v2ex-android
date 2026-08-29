package com.vibe.v2ex.feature.write

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteScreen(onBack: () -> Unit, viewModel: WriteViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.submitted) {
        if (uiState.submitted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.topicId == null) "写新话题" else "回复") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            if (uiState.topicId == null) {
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.nodes.isNotEmpty()) {
                    LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                        items(uiState.nodes.take(30), key = { it.id.takeIf { id -> id != 0L } ?: it.name }) { node ->
                            FilterChip(
                                selected = uiState.selectedNode?.name == node.name,
                                onClick = { viewModel.onNodeSelected(node) },
                                label = { Text(node.title) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.content,
                onValueChange = viewModel::onContentChange,
                label = { Text("正文（支持 Markdown）") },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            Row(modifier = Modifier.padding(top = 8.dp)) {
                FormatButton("B") { viewModel.onContentChange(uiState.content + "**文本**") }
                FormatButton("I") { viewModel.onContentChange(uiState.content + "*文本*") }
                FormatButton("</>") { viewModel.onContentChange(uiState.content + "`文本`") }
                FormatButton("🔗") { viewModel.onContentChange(uiState.content + "[标题](https://)") }
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            if (uiState.topicId != null) {
                Button(
                    onClick = viewModel::submitReply,
                    enabled = !uiState.isSubmitting && uiState.content.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("发送回复") }
            } else {
                Text(
                    "V2EX 没有开放发帖 API，草稿会自动保存；发布需要复制正文到网页完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val context = LocalContext.current
                Button(
                    onClick = { copyAndOpenWebWrite(context, uiState.content, uiState.selectedNode?.name) },
                    enabled = uiState.content.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("复制正文并打开网页发布") }
            }
        }
    }
}

@Composable
private fun FormatButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
}

private fun copyAndOpenWebWrite(context: Context, content: String, nodeName: String?) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("v2ex draft", content))
    val url = "https://www.v2ex.com/write" + (nodeName?.let { "?node=$it" } ?: "")
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
