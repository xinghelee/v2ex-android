package com.vibe.v2ex.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.datastore.AppTheme
import com.vibe.v2ex.data.datastore.DarkModePreference
import com.vibe.v2ex.data.datastore.LineSpacingPreference
import com.vibe.v2ex.data.datastore.MonoFontPreference

private val THEME_LABELS = mapOf(
    AppTheme.EMERALD to "翡翠绿",
    AppTheme.OCEAN to "海洋蓝",
    AppTheme.CRIMSON to "绯红",
    AppTheme.AMBER to "琥珀橙",
    AppTheme.VIOLET to "紫罗兰",
)
private val DARK_MODE_LABELS = mapOf(
    DarkModePreference.SYSTEM to "跟随系统",
    DarkModePreference.LIGHT to "浅色",
    DarkModePreference.DARK to "深色",
)
private val LINE_SPACING_LABELS = mapOf(
    LineSpacingPreference.TIGHT to "紧凑",
    LineSpacingPreference.STANDARD to "标准",
    LineSpacingPreference.RELAXED to "宽松",
)
private val MONO_FONT_LABELS = mapOf(
    MonoFontPreference.SF_MONO to "SF Mono",
    MonoFontPreference.MENLO to "Menlo",
    MonoFontPreference.COURIER to "Courier",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观与阅读") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionLabel("主题配色")
            ChipRow {
                AppTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = uiState.theme == theme,
                        onClick = { viewModel.setTheme(theme) },
                        label = { Text(THEME_LABELS.getValue(theme)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            SectionLabel("深色模式")
            ChipRow {
                DarkModePreference.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.darkMode == mode,
                        onClick = { viewModel.setDarkMode(mode) },
                        label = { Text(DARK_MODE_LABELS.getValue(mode)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            SectionLabel("正文字号 ${uiState.fontSize.toInt()}sp")
            Slider(
                value = uiState.fontSize,
                onValueChange = viewModel::setFontSize,
                valueRange = 13f..21f,
                steps = 7,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("行距")
            ChipRow {
                LineSpacingPreference.entries.forEach { pref ->
                    FilterChip(
                        selected = uiState.lineSpacing == pref,
                        onClick = { viewModel.setLineSpacing(pref) },
                        label = { Text(LINE_SPACING_LABELS.getValue(pref)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            SectionLabel("等宽字体")
            ChipRow {
                MonoFontPreference.entries.forEach { pref ->
                    FilterChip(
                        selected = uiState.monoFont == pref,
                        onClick = { viewModel.setMonoFont(pref) },
                        label = { Text(MONO_FONT_LABELS.getValue(pref)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            SwitchRow(
                label = "已读话题变暗",
                checked = uiState.dimReadTopics,
                onCheckedChange = viewModel::setDimReadTopics,
            )
            SwitchRow(
                label = "记住阅读进度",
                checked = uiState.rememberReadingPosition,
                onCheckedChange = viewModel::setRememberReadingPosition,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
