package com.vibe.v2ex.feature.write

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.V2Colors
import com.vibe.v2ex.designsystem.identityColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteScreen(
    onBack: () -> Unit,
    onPublished: (Long) -> Unit = {},
    viewModel: WriteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isReply = uiState.topicId != null
    var showNodePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.submitted) {
        if (uiState.submitted) onBack()
    }
    LaunchedEffect(uiState.publishedTopicId) {
        uiState.publishedTopicId?.let(onPublished)
    }

    // 草稿时间 — 首帧（回显已有草稿）不算，之后每次输入更新一次。
    var draftTime by remember { mutableStateOf<String?>(null) }
    var isFirstDraftEmission by remember { mutableStateOf(true) }
    LaunchedEffect(uiState.title, uiState.content) {
        if (isFirstDraftEmission) {
            isFirstDraftEmission = false
        } else {
            draftTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // 顶栏：取消 / 标题 / 发布胶囊（设计稿 05）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "取消",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(2.dp),
            )
            Text(
                text = if (isReply) "回复" else "新话题",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center),
            )
            // mirrors iOS：标题或正文任一非空即可点发布，标题缺失由校验给出明确报错。
            val submitEnabled = if (isReply) {
                !uiState.isSubmitting && uiState.content.isNotBlank()
            } else {
                !uiState.isSubmitting && (uiState.content.isNotBlank() || uiState.title.isNotBlank())
            }
            Text(
                text = when {
                    uiState.isSubmitting -> "发布中…"
                    isReply -> "发送"
                    else -> "发布"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (submitEnabled) 1f else 0.4f))
                    .clickable(enabled = submitEnabled) {
                        when {
                            isReply -> viewModel.submitReply()
                            // 未登录时没有会话 cookie，表单提交必然失败，直接走网页。
                            uiState.isWebSessionActive -> viewModel.submitTopic()
                            else -> copyAndOpenWebWrite(context, uiState.content, uiState.selectedNode?.name)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!isReply) {
                NodeSelectorCard(
                    node = uiState.selectedNode,
                    onClick = { showNodePicker = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // 标题 + 正文卡
            V2Card(modifier = Modifier.padding(horizontal = 16.dp).weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!isReply) {
                        BorderlessField(
                            value = uiState.title,
                            onValueChange = viewModel::onTitleChange,
                            placeholder = "标题",
                            textStyle = TextStyle(
                                fontSize = 21.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 28.sp,
                                letterSpacing = (-0.4).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            singleLine = true,
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    BorderlessField(
                        value = uiState.content,
                        onValueChange = viewModel::onContentChange,
                        placeholder = "正文（支持 Markdown）",
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            color = if (LocalV2Dark.current) V2Colors.BodyDark else V2Colors.BodyLight,
                        ),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }

            // 格式工具条
            Column {
                SectionHeader("格式")
                V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FormatSquare(label = "B", fontWeight = FontWeight.Bold) {
                            viewModel.onContentChange(uiState.content + "**文本**")
                        }
                        FormatSquare(label = "I", fontStyle = FontStyle.Italic) {
                            viewModel.onContentChange(uiState.content + "*文本*")
                        }
                        FormatSquare(label = "</>", fontSize = 13.sp, fontFamily = FontFamily.Monospace) {
                            viewModel.onContentChange(uiState.content + "`文本`")
                        }
                        FormatSquare(label = "🔗", fontSize = 14.sp) {
                            viewModel.onContentChange(uiState.content + "[标题](https://)")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "支持 Markdown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 草稿状态 / 错误
            Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = draftTime?.let { "草稿已自动保存 · $it" } ?: "草稿已自动保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!isReply) {
                    Text(
                        text = if (uiState.isWebSessionActive) {
                            "将以你的网页会话直接发布，成功后自动打开新帖"
                        } else {
                            "未登录网页会话：「发布」会复制正文并打开网页完成"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // 发布被拒后的逃生口（mirrors iOS「改用网页发布」）：
                    // 正文复制到剪贴板，网页里长按粘贴即可。
                    if (!isReply) {
                        Text(
                            text = "改用网页发布（正文已复制）",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    copyAndOpenWebWrite(context, uiState.content, uiState.selectedNode?.name)
                                }
                                .padding(vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }

    if (showNodePicker) {
        NodePickerSheet(
            nodes = uiState.nodes,
            followedNames = uiState.followedNames,
            onSelect = { node ->
                viewModel.onNodeSelected(node)
                showNodePicker = false
            },
            onDismiss = { showNodePicker = false },
        )
    }
}

/** 节点选择行（设计稿 05 首卡）：30dp 身份方块 + 节点名 + 「更换节点」。 */
@Composable
private fun NodeSelectorCard(node: Node?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    V2Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 54.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NodeSquare(node = node)
            Text(
                text = node?.title ?: "选择节点",
                style = MaterialTheme.typography.titleSmall,
                color = if (node != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "更换节点",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun NodeSquare(node: Node?, size: androidx.compose.ui.unit.Dp = 30.dp) {
    if (node?.avatarUrl != null) {
        AsyncImage(
            model = node.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
        )
        return
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(node?.let { identityColor(it.name) } ?: MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = node?.title?.take(1) ?: "?",
            color = if (node != null) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 无边框输入框：白卡内直接排版（设计稿 05），带 placeholder。 */
@Composable
private fun BorderlessField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = singleLine,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** 32dp 格式按钮方块：canvas 底圆角 9。 */
@Composable
private fun FormatSquare(
    label: String,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    fontFamily: FontFamily = FontFamily.Default,
    onClick: () -> Unit,
) {
    val dark = LocalV2Dark.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (dark) Color(0xFF2C2C2E) else V2Colors.CanvasLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontFamily = fontFamily,
            color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else V2Colors.SecondaryLabelLight,
        )
    }
}

/** 节点选择弹层：无搜索词时默认列关注的节点，搜索时过滤全量（mirrors iOS NodePicker）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodePickerSheet(
    nodes: List<Node>,
    followedNames: List<String>,
    onSelect: (Node) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(nodes, followedNames, query) {
        if (query.isBlank()) {
            val byName = nodes.associateBy { it.name }
            followedNames.mapNotNull { byName[it] }.ifEmpty { nodes }
        } else {
            nodes.filter {
                it.title.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Text(
                text = "选择节点",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            // 搜索框（设计稿 07 形态）
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1F767680))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "搜索节点",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id.takeIf { id -> id != 0L } ?: it.name }) { node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(node) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NodeSquare(node = node)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyAndOpenWebWrite(context: Context, content: String, nodeName: String?) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("v2ex draft", content))
    val url = "https://www.v2ex.com/write" + (nodeName?.let { "?node=$it" } ?: "")
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
