package com.vibe.v2ex.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.vibe.v2ex.designsystem.SecureCredentialField
import com.vibe.v2ex.designsystem.V2Card

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showWebLogin) {
        WebLoginScreen(
            onCancel = viewModel::dismissWebLogin,
            onConfirm = viewModel::confirmWebLogin,
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
                                text = if (uiState.sessionExpired == true) {
                                    "会话已失效，请重新登录"
                                } else {
                                    "网页会话已连接，可在 app 内直接回复"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.sessionExpired == true) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                if (uiState.sessionExpired == true) {
                    Button(
                        onClick = viewModel::openWebLogin,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text("重新登录") }
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

            Spacer(modifier = Modifier.height(24.dp))

            V2Card {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Personal Access Token", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "用于通知、个人资料和长帖分页",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (uiState.hasSavedPersonalAccessToken) {
                            Text(
                                "已配置",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    SecureCredentialField(
                        value = uiState.personalAccessToken,
                        onValueChange = viewModel::onPatChange,
                        placeholder = "粘贴从 v2ex.com/settings/tokens 获取的 Token",
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = viewModel::verifyAndSavePat,
                            enabled = uiState.personalAccessToken.isNotBlank() &&
                                !uiState.isValidatingPersonalAccessToken,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (uiState.isValidatingPersonalAccessToken) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text("验证中…", modifier = Modifier.padding(start = 8.dp))
                            } else {
                                Text("验证并保存")
                            }
                        }
                        if (uiState.hasSavedPersonalAccessToken) {
                            OutlinedButton(
                                onClick = viewModel::clearSavedPat,
                                enabled = !uiState.isValidatingPersonalAccessToken,
                                modifier = Modifier.padding(start = 10.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) { Text("清除") }
                        }
                    }
                    uiState.personalAccessTokenStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.personalAccessTokenStatusIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Text(
                        "输入内容只是草稿；验证通过后才会加密保存在本机，不参与系统备份。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}
