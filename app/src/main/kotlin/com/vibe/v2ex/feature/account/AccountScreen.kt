package com.vibe.v2ex.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showWebLogin) {
        WebLoginScreen(
            onCancel = viewModel::dismissWebLogin,
            onSuccess = viewModel::onWebLoginSuccess,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(16.dp)) {
            Text("网页会话", style = MaterialTheme.typography.titleMedium)
            Text(
                "用于在 app 内直接回复、发帖、同步收藏",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            if (uiState.webSessionActive) {
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = uiState.sessionUsername?.take(2)?.lowercase() ?: "?",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(uiState.sessionUsername ?: "已登录", fontWeight = FontWeight.Bold)
                            Text(
                                "网页会话已连接，可在 app 内直接回复",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = viewModel::signOutWebSession,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("退出登录") }
            } else {
                Button(onClick = viewModel::openWebLogin, modifier = Modifier.fillMaxWidth()) {
                    Text("在网页中登录")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Text("Personal Access Token", style = MaterialTheme.typography.titleMedium)
            Text(
                "用于通知、个人资料、长帖分页等 API 2.0 功能，从 v2ex.com/settings/tokens 获取",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = uiState.personalAccessToken,
                onValueChange = viewModel::onPatChange,
                label = { Text("Personal Access Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
