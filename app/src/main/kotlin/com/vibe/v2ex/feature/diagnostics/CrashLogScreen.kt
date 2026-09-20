package com.vibe.v2ex.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.diagnostics.CrashLog
import com.vibe.v2ex.feature.settings.InsetDivider
import com.vibe.v2ex.feature.settings.ValueRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志页：给用户一个把 [CrashLog] 报告复制 / 分享出去的入口。
 * 日志只在本机，这里不做任何自动上传。
 */
@Composable
fun CrashLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.latest(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = "返回",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "崩溃日志",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 14.dp),
        )

        val current = report
        if (current == null) {
            V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "没有崩溃记录。应用意外退出后，这里会保存一份日志，方便你反馈给开发者。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            val capturedAt = remember(current.capturedAt) {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(current.capturedAt))
            }
            SectionHeader("记录于 $capturedAt")
            V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                ValueRow(label = "复制到剪贴板", value = "", onClick = {
                    context.getSystemService<ClipboardManager>()
                        ?.setPrimaryClip(ClipData.newPlainText("V2EX Android 崩溃日志", current.text))
                })
                InsetDivider()
                ValueRow(label = "分享", value = "", onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "V2EX Android 崩溃日志")
                        .putExtra(Intent.EXTRA_TEXT, current.text)
                    runCatching { context.startActivity(Intent.createChooser(send, "分享崩溃日志")) }
                })
                InsetDivider()
                ValueRow(label = "清除", value = "", showChevron = false, onClick = {
                    CrashLog.clear(context)
                    report = null
                })
            }
            Text(
                text = "反馈时可以把日志贴到 GitHub Issue 或发邮件给开发者。日志只包含异常堆栈和本应用自己的运行记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
            )
            V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                SelectionContainer {
                    Text(
                        text = current.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** 冷启动发现上次崩溃过时的一次性提示。 */
@Composable
fun CrashPromptDialog(onView: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上次使用时应用意外退出") },
        text = { Text("已保存一份崩溃日志。把它发给开发者（GitHub Issue 或邮件）能帮助尽快定位问题。") },
        confirmButton = { TextButton(onClick = onView) { Text("查看日志") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("忽略") } },
    )
}
