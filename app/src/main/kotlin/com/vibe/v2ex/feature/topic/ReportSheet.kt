package com.vibe.v2ex.feature.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibe.v2ex.data.moderation.ReportReason

private const val REPORT_NOTE_LIMIT = 1_000

/** Report form shared by topic and reply menu entries. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicReportSheet(
    target: TopicModerationTarget,
    isSubmitting: Boolean,
    canBlockAuthor: Boolean,
    blockHint: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: ReportReason, note: String, alsoBlockAuthor: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedReason by remember(target.key) { mutableStateOf<ReportReason?>(null) }
    var note by remember(target.key) { mutableStateOf("") }
    var alsoBlockAuthor by remember(target.key) { mutableStateOf(false) }

    LaunchedEffect(canBlockAuthor) {
        if (!canBlockAuthor) alsoBlockAuthor = false
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("取消") }
                Text(
                    text = "举报${target.kindTitle}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // Balances the cancel action so the title stays visually centred.
                Spacer(modifier = Modifier.width(64.dp))
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                item(key = "explanation") {
                    Text(
                        text = "举报后，${target.kindTitle}会立即从 App 中隐藏，并发送给开发者核实。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                item(key = "reason-title") {
                    Text(
                        text = "举报理由",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(ReportReason.entries, key = ReportReason::slug) { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSubmitting) { selectedReason = reason }
                            .padding(horizontal = 20.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = reason.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = null,
                            enabled = !isSubmitting,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
                }
                item(key = "note") {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { value -> note = value.take(REPORT_NOTE_LIMIT) },
                        label = { Text("补充说明（可选）") },
                        placeholder = { Text("再多说两句，帮助开发者判断…") },
                        minLines = 3,
                        maxLines = 6,
                        enabled = !isSubmitting,
                        supportingText = {
                            if (note.length > REPORT_NOTE_LIMIT - 200) {
                                Text("${note.length} / $REPORT_NOTE_LIMIT")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
                if (target.author.isNotBlank()) {
                    item(key = "also-block") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = canBlockAuthor && !isSubmitting) {
                                    alsoBlockAuthor = !alsoBlockAuthor
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "同时屏蔽 @${target.author}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = blockHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = alsoBlockAuthor,
                                onCheckedChange = null,
                                enabled = canBlockAuthor && !isSubmitting,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    selectedReason?.let { reason -> onSubmit(reason, note.trim(), alsoBlockAuthor) }
                },
                enabled = selectedReason != null && !isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isSubmitting) "正在提交…" else "提交举报")
            }
        }
    }
}
