package com.vibe.v2ex.feature.agreement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private data class AgreementSection(val icon: ImageVector, val title: String, val body: String)

private val SECTIONS = listOf(
    AgreementSection(
        Icons.Filled.PanTool,
        "对冒犯性内容零容忍",
        "V2EX 上的内容由用户发布。本应用对骚扰、仇恨言论、色情、暴力等冒犯性内容零容忍。",
    ),
    AgreementSection(Icons.Filled.Flag, "举报", "遇到不当内容可随时举报，内容会立即从你的列表中隐藏。"),
    AgreementSection(Icons.Filled.Block, "屏蔽", "你可以屏蔽特定用户或关键词，其内容将不再出现。"),
    AgreementSection(Icons.Filled.CheckCircle, "24 小时内处理", "所有举报会在 24 小时内由开发者审核处理。"),
    AgreementSection(Icons.Filled.Email, "联系方式", "如有问题请联系 hi@xinghelee.com"),
)

@Composable
fun AgreementScreen(viewModel: AgreementViewModel = hiltViewModel()) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("使用须知", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "V2EX 是一个内容由用户生成的社区，使用前请阅读以下内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
            SECTIONS.forEach { section ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                    Icon(
                        section.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp, end = 16.dp),
                    )
                    Column {
                        Text(section.title, fontWeight = FontWeight.Bold)
                        Text(
                            section.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = viewModel::accept, modifier = Modifier.weight(1f)) {
                    Text("同意并继续")
                }
            }
        }
    }
}
