package com.vibe.v2ex.feature.nodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.model.Node

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(
    onNodeClick: (String) -> Unit,
    viewModel: NodesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("节点") }) }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("搜索节点") },
                singleLine = true,
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.filteredNodes, key = { it.id.takeIf { id -> id != 0L } ?: it.name }) { node ->
                    NodeRow(node = node, onClick = { onNodeClick(node.name) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun NodeRow(node: Node, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = node.title)
        Text(text = "${node.topics ?: 0} 篇主题", style = MaterialTheme.typography.bodySmall)
    }
}
