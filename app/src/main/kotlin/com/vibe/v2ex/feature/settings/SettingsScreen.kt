package com.vibe.v2ex.feature.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.datastore.AppTheme
import com.vibe.v2ex.data.datastore.DarkModePreference
import com.vibe.v2ex.data.datastore.LineSpacingPreference
import com.vibe.v2ex.data.datastore.MonoFontPreference
import com.vibe.v2ex.data.repository.OfflineSyncProgress
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.SecureCredentialField
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.V2Colors
import com.vibe.v2ex.designsystem.paletteFor
import kotlinx.coroutines.delay

private val LINE_SPACING_LABELS = mapOf(
    LineSpacingPreference.TIGHT to "紧凑",
    LineSpacingPreference.STANDARD to "标准",
    LineSpacingPreference.RELAXED to "宽松",
)
private val MONO_FONT_LABELS = mapOf(
    MonoFontPreference.SF_MONO to "系统等宽",
    MonoFontPreference.MENLO to "紧凑等宽",
    MonoFontPreference.COURIER to "经典宽体",
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAccountClick: () -> Unit = {},
    onModerationClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var deepSeekKeyDraft by remember { mutableStateOf("") }

    // 从「账号」页返回后刷新登录状态展示。
    LaunchedEffect(Unit) { viewModel.refreshSessionState() }

    // 下载结果只是一句一次性反馈，几秒后退回常驻副标题。
    LaunchedEffect(uiState.offlineMessage) {
        if (uiState.offlineMessage != null) {
            delay(4_000)
            viewModel.consumeOfflineMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // 返回行 + 大标题（设计稿 09 顶部）
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
            text = "设置",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 14.dp),
        )

        // 账号排最前：它是这页唯一有「状态」的东西，也是出问题时用户来找的答案。
        SectionHeader("账号")
        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            ValueRow(
                label = "账号与登录",
                value = when {
                    uiState.isWebSessionActive ->
                        uiState.sessionUsername?.takeIf(String::isNotBlank)?.let { "$it · 已登录" } ?: "已登录"
                    uiState.isTokenSet -> "仅 Token"
                    else -> "未登录"
                },
                onClick = onAccountClick,
            )
            InsetDivider()
            SwitchRow(
                label = "自动同步关注节点",
                subtitle = if (uiState.isWebSessionActive) {
                    "登录后自动同步网页收藏的节点"
                } else {
                    "登录 V2EX 后自动同步网页收藏的节点"
                },
                checked = uiState.autoSyncFollowedNodes,
                enabled = uiState.isWebSessionActive,
                onCheckedChange = viewModel::setAutoSyncFollowedNodes,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader("主题")
        ThemeTiles(selected = uiState.darkMode, onSelect = viewModel::setDarkMode)

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader("主题色")
        PaletteRow(selected = uiState.theme, onSelect = viewModel::setTheme)

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader("正文")
        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 正文字号 + Slider
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "正文字号",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${uiState.fontSize.toInt()} pt",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "A", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = uiState.fontSize,
                        onValueChange = viewModel::setFontSize,
                        valueRange = 13f..21f,
                        steps = 7,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    Text(text = "A", fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            InsetDivider()
            ValueRow(
                label = "行距",
                value = LINE_SPACING_LABELS.getValue(uiState.lineSpacing),
                onClick = { viewModel.setLineSpacing(uiState.lineSpacing.next()) },
            )
            InsetDivider()
            ValueRow(
                label = "代码块等宽样式",
                value = MONO_FONT_LABELS.getValue(uiState.monoFont),
                onClick = { viewModel.setMonoFont(uiState.monoFont.next()) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader("阅读")
        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            SwitchRow(
                label = "记住阅读进度",
                checked = uiState.rememberReadingPosition,
                onCheckedChange = viewModel::setRememberReadingPosition,
            )
            InsetDivider()
            SwitchRow(
                label = "标记已读的话题变灰",
                checked = uiState.dimReadTopics,
                onCheckedChange = viewModel::setDimReadTopics,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader("离线与缓存")
        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            OfflinePrefetchRow(
                progress = uiState.offlineProgress,
                message = uiState.offlineMessage,
                topicCount = uiState.offlineTopicCount,
                onClick = viewModel::prefetchOffline,
            )
            InsetDivider()
            SwitchRow(
                label = "自动离线关注节点",
                subtitle = if (uiState.offlineOnWifiOnly) "仅 Wi-Fi 下载" else "使用任意网络下载",
                checked = uiState.autoOfflineFollowedNodes,
                onCheckedChange = viewModel::setAutoOfflineFollowedNodes,
            )
            InsetDivider()
            SwitchRow(
                label = "仅在 Wi-Fi 下载",
                checked = uiState.offlineOnWifiOnly,
                onCheckedChange = viewModel::setOfflineOnWifiOnly,
            )
            InsetDivider()
            ValueRow(
                label = "清空缓存",
                value = formatCacheSize(uiState.cacheByteSize),
                showChevron = false,
                onClick = viewModel::clearCache,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader("AI 摘要")
        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DeepSeek API Key", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (uiState.isDeepSeekConfigured) "已安全保存在本机" else "用于手动生成话题摘要",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (uiState.isDeepSeekConfigured) {
                        Text(
                            "已配置",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                SecureCredentialField(
                    value = deepSeekKeyDraft,
                    onValueChange = { deepSeekKeyDraft = it },
                    placeholder = if (uiState.isDeepSeekConfigured) "输入新 Key 可替换" else "sk-…",
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.isDeepSeekConfigured) {
                        TextButton(
                            onClick = {
                                    viewModel.clearDeepSeekApiKey()
                                    deepSeekKeyDraft = ""
                            },
                        ) { Text("移除", color = MaterialTheme.colorScheme.error) }
                    }
                    FilledTonalButton(
                        onClick = {
                            viewModel.saveDeepSeekApiKey(deepSeekKeyDraft)
                            deepSeekKeyDraft = ""
                        },
                        enabled = deepSeekKeyDraft.isNotBlank(),
                        modifier = Modifier.padding(start = 6.dp),
                    ) { Text(if (uiState.isDeepSeekConfigured) "更新" else "保存") }
                }
                Text(
                    "仅在点击“生成摘要”时，帖子正文和部分回复才会发送给 DeepSeek。Key 不参与备份。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader("实验性功能")
        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            SwitchRow(
                label = "社区脉搏",
                subtitle = "在首页显示活跃节点与回复分布",
                checked = uiState.communityPulseEnabled,
                onCheckedChange = viewModel::setCommunityPulseEnabled,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader("关于")
        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            ValueRow(label = "隐私政策", value = "", onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://xinghelee.github.io/v2ex/privacy.html".toUri()),
                )
            })
            InsetDivider()
            ValueRow(label = "内容与屏蔽", value = "", onClick = onModerationClick)
            InsetDivider()
            ValueRow(label = "联系开发者", value = "hi@xinghelee.com", onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, "mailto:hi@xinghelee.com".toUri()))
            })
            InsetDivider()
            ValueRow(label = "V2EX API 文档", value = "", onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.v2ex.com/help/api".toUri()))
            })
            InsetDivider()
            ValueRow(label = "版本", value = appVersionName(context), showChevron = false, onClick = null)
        }

        Text(
            text = "数据来自 V2EX 开放 API（api/v1 公开，api/v2 需要 Token）。全文搜索由社区项目 sov2ex 提供。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun appVersionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: ""

private fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** 主题色圆点行（mirrors iOS paletteSection）：46dp 渐变圆 + 选中外扩描边。 */
@Composable
private fun PaletteRow(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppTheme.entries.forEach { theme ->
            val palette = paletteFor(theme)
            val isSelected = theme == selected
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(palette.accentLight, palette.accentDeep)),
                        )
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            shape = CircleShape,
                        )
                        .clickable { onSelect(theme) },
                )
                Text(
                    text = palette.title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun LineSpacingPreference.next(): LineSpacingPreference =
    LineSpacingPreference.entries[(ordinal + 1) % LineSpacingPreference.entries.size]

private fun MonoFontPreference.next(): MonoFontPreference =
    MonoFontPreference.entries[(ordinal + 1) % MonoFontPreference.entries.size]

/** 主题预览瓦片（设计稿 09）：浅色 / 深色 / 跟随系统，选中加 2dp accent 边框。 */
@Composable
private fun ThemeTiles(selected: DarkModePreference, onSelect: (DarkModePreference) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemeTile(
            label = "浅色",
            selected = selected == DarkModePreference.LIGHT,
            onClick = { onSelect(DarkModePreference.LIGHT) },
            modifier = Modifier.weight(1f),
        ) { LightPreview() }
        ThemeTile(
            label = "深色",
            selected = selected == DarkModePreference.DARK,
            onClick = { onSelect(DarkModePreference.DARK) },
            modifier = Modifier.weight(1f),
        ) { DarkPreview() }
        ThemeTile(
            label = "跟随系统",
            selected = selected == DarkModePreference.SYSTEM,
            onClick = { onSelect(DarkModePreference.SYSTEM) },
            modifier = Modifier.weight(1f),
        ) { SplitPreview() }
    }
}

@Composable
private fun ThemeTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(shape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = shape,
                )
                .clickable(onClick = onClick),
        ) { preview() }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun LightPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(V2Colors.CanvasLight)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(8.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFC7C7CC)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White),
        )
    }
}

@Composable
private fun DarkPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(8.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF48484A)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1C1C1E)),
        )
    }
}

@Composable
private fun SplitPreview() {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(V2Colors.CanvasLight))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.Black))
    }
}

/** 值行：17sp 标题 + 右侧值（+ 可选 chevron），点按触发动作。 */
@Composable
private fun ValueRow(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showChevron) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 「立即缓存最新话题」：登机前一键把正文和回复拉到本地。 */
@Composable
private fun OfflinePrefetchRow(
    progress: OfflineSyncProgress?,
    message: String?,
    topicCount: Int,
    onClick: () -> Unit,
) {
    val subtitle = when {
        progress != null -> "正在下载 ${progress.completed}/${progress.total}"
        message != null -> message
        topicCount > 0 -> "已离线 $topicCount 篇，飞行模式下可直接阅读"
        else -> "把最新话题连回复存到本机，断网也能看"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = progress == null, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "立即缓存最新话题", style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (progress != null) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
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
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
