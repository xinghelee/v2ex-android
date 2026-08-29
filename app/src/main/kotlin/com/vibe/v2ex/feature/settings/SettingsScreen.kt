package com.vibe.v2ex.feature.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.datastore.DarkModePreference
import com.vibe.v2ex.data.datastore.LineSpacingPreference
import com.vibe.v2ex.data.datastore.MonoFontPreference
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card
import com.vibe.v2ex.designsystem.V2Colors

private val LINE_SPACING_LABELS = mapOf(
    LineSpacingPreference.TIGHT to "紧凑",
    LineSpacingPreference.STANDARD to "标准",
    LineSpacingPreference.RELAXED to "宽松",
)
private val MONO_FONT_LABELS = mapOf(
    MonoFontPreference.SF_MONO to "系统等宽",
    MonoFontPreference.MENLO to "Serif Mono",
    MonoFontPreference.COURIER to "传统等宽",
)

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

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
            text = "外观",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 14.dp),
        )

        SectionHeader("主题")
        ThemeTiles(selected = uiState.darkMode, onSelect = viewModel::setDarkMode)

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
                label = "代码块等宽字体",
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

        Spacer(modifier = Modifier.height(32.dp))
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

/** 值行：17sp 标题 + 右侧值 + chevron，点按轮换。 */
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
