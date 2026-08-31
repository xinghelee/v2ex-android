package com.vibe.v2ex.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vibe.v2ex.data.datastore.AppTheme
import com.vibe.v2ex.data.datastore.DarkModePreference
import com.vibe.v2ex.data.datastore.LineSpacingPreference
import com.vibe.v2ex.data.datastore.MonoFontPreference

/**
 * 可选主题色（mirrors iOS ThemePalette）：中性的纸墨底色共享，每套配色拥有
 * 强调色、深强调与次级信号色；EMERALD 保持本项目一直以来的品牌绿。
 */
data class V2Palette(
    val title: String,
    val accentLight: Color,
    val accentDark: Color,
    val accentDeep: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
    val canvasLight: Color,
)

fun paletteFor(theme: AppTheme): V2Palette = when (theme) {
    AppTheme.EMERALD -> V2Palette(
        title = "翡翠绿",
        accentLight = Color(0xFF00734C), accentDark = Color(0xFF2FBF8F), accentDeep = Color(0xFF00543A),
        secondaryLight = Color(0xFFA85C00), secondaryDark = Color(0xFFE8A33C),
        canvasLight = Color(0xFFF2F2F7),
    )
    AppTheme.OCEAN -> V2Palette(
        title = "海洋蓝",
        accentLight = Color(0xFF0F64B0), accentDark = Color(0xFF4AA3E8), accentDeep = Color(0xFF0A4A85),
        secondaryLight = Color(0xFFB06A00), secondaryDark = Color(0xFFE8A33C),
        canvasLight = Color(0xFFF0F3F7),
    )
    AppTheme.CRIMSON -> V2Palette(
        title = "绯红",
        accentLight = Color(0xFFA63D2E), accentDark = Color(0xFFE86A5A), accentDeep = Color(0xFF7E2A20),
        secondaryLight = Color(0xFFA85C00), secondaryDark = Color(0xFFE8A33C),
        canvasLight = Color(0xFFF7F2F1),
    )
    AppTheme.AMBER -> V2Palette(
        title = "琥珀橙",
        accentLight = Color(0xFFB4550A), accentDark = Color(0xFFEFA85C), accentDeep = Color(0xFF8A3D00),
        // 琥珀套的次级信号换成蓝，避免和强调色撞车（mirrors iOS）。
        secondaryLight = Color(0xFF1E5AA8), secondaryDark = Color(0xFF4AA3E8),
        canvasLight = Color(0xFFF7F3EC),
    )
    AppTheme.VIOLET -> V2Palette(
        title = "紫罗兰",
        accentLight = Color(0xFF5A4B8C), accentDark = Color(0xFFA898DD), accentDeep = Color(0xFF433665),
        secondaryLight = Color(0xFFA85C00), secondaryDark = Color(0xFFE8A33C),
        canvasLight = Color(0xFFF3F1F8),
    )
}

/**
 * 品牌视觉 — 源自 claude.ai/design「V2EX iOS」设计稿（iOS 26 Liquid Glass）：
 * 墨绿强调、分组底 #F2F2F7、白卡圆角 22、彩色方形身份标识。
 * 单一品牌配色，浅/深两个模式（设计稿 09 屏只提供 浅色/深色/跟随系统）。
 */
object V2Colors {
    // Mirrors the current iOS Theme.swift values. Keep these aliases for
    // non-composable renderers (for example the share-card canvas); Compose UI
    // should prefer MaterialTheme.colorScheme so the selected palette wins.
    val AccentLight = Color(0xFF00734C)
    val AccentDark = Color(0xFF2FBF8F)
    val AccentDeep = Color(0xFF00543A)

    val CanvasLight = Color(0xFFF2F2F7)
    val CanvasDark = Color(0xFF0A0A0B)
    val CardLight = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF161618)

    val InkLight = Color(0xFF141416)
    val InkDark = Color(0xFFF2F2F4)
    val BodyLight = Color(0xFF2C2C2E)
    val BodyDark = Color(0xFFD1D1D6)
    val SecondaryLabelLight = Color(0xFF3C3C43)
    val MutedLight = Color(0xFF86868B)
    val MutedDark = Color(0xFF8E8E93)
    val TertiaryLight = Color(0xFFAEAEB2)
    val TertiaryDark = Color(0xFF4A4A4F)

    val SeparatorLight = Color(0x12141416) // rgba(20,20,22,0.07)
    val SeparatorDark = Color(0x1AF2F2F4) // rgba(242,242,244,0.10)

    /** 离线标记 / 铜币 橙 */
    val Amber = Color(0xFFC77700)
    val AmberSoftLight = Color(0x24FF9500) // rgba(255,149,0,0.14)

    val UnreadRed = Color(0xFFFF3B30)
    val SearchHighlight = Color(0x59FFCC00) // rgba(255,204,0,0.35)

    /** 未读通知行的底色 */
    val UnreadRowTintLight = Color(0x0B1C7C6B) // rgba(28,124,107,0.045)

    /** 代码块底色 */
    val CodeBgLight = Color(0xFFF7F7F9)
    val CodeBgDark = Color(0xFF2C2C2E)
    val CodePrompt = Color(0xFF8E5A9E)

    fun accentSoft(dark: Boolean): Color =
        (if (dark) AccentDark else AccentLight).copy(alpha = if (dark) 0.18f else 0.11f)
}

/** 当前是否深色 — 供需要设计稿精确色值（而非 M3 语义色）的组件使用。 */
val LocalV2Dark = staticCompositionLocalOf { false }

/** Reader controls are separate from the app chrome typography. They are
 * consumed by rich topic/reply content so changing the settings has an
 * immediate, observable effect without making navigation labels jump. */
data class ReadingTypography(
    val fontSize: Float = 14f,
    val lineSpacing: LineSpacingPreference = LineSpacingPreference.RELAXED,
    val monoFont: MonoFontPreference = MonoFontPreference.SF_MONO,
)

val LocalReadingTypography = staticCompositionLocalOf { ReadingTypography() }

private fun scheme(dark: Boolean, palette: V2Palette): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = palette.accentDark,
            // Dark palettes use bright signal colours; near-black ink keeps
            // button labels and fallback initials above WCAG contrast targets.
            onPrimary = V2Colors.CanvasDark,
            primaryContainer = palette.accentDark.copy(alpha = 0.18f).compositeOver(V2Colors.CardDark),
            onPrimaryContainer = palette.accentDark,
            secondary = palette.accentDark,
            secondaryContainer = palette.accentDark.copy(alpha = 0.18f).compositeOver(V2Colors.CardDark),
            onSecondaryContainer = palette.accentDark,
            tertiary = palette.secondaryDark,
            tertiaryContainer = palette.secondaryDark.copy(alpha = 0.18f).compositeOver(V2Colors.CardDark),
            onTertiaryContainer = palette.secondaryDark,
            background = V2Colors.CanvasDark,
            surface = V2Colors.CardDark,
            surfaceVariant = Color(0xFF222225),
            surfaceContainer = Color(0xFF1E1E21),
            surfaceContainerHigh = Color(0xFF222225),
            onBackground = V2Colors.InkDark,
            onSurface = V2Colors.InkDark,
            onSurfaceVariant = V2Colors.MutedDark,
            outline = V2Colors.TertiaryDark,
            outlineVariant = V2Colors.SeparatorDark,
            error = V2Colors.UnreadRed,
        )
    } else {
        lightColorScheme(
            primary = palette.accentLight,
            onPrimary = Color.White,
            primaryContainer = palette.accentLight.copy(alpha = 0.11f).compositeOver(V2Colors.CardLight),
            onPrimaryContainer = palette.accentDeep,
            secondary = palette.accentLight,
            secondaryContainer = palette.accentLight.copy(alpha = 0.11f).compositeOver(V2Colors.CardLight),
            onSecondaryContainer = palette.accentLight,
            tertiary = palette.secondaryLight,
            tertiaryContainer = palette.secondaryLight.copy(alpha = 0.14f).compositeOver(V2Colors.CardLight),
            onTertiaryContainer = palette.secondaryLight,
            background = palette.canvasLight,
            surface = V2Colors.CardLight,
            surfaceVariant = palette.canvasLight,
            surfaceContainer = Color(0xFFECECEF),
            surfaceContainerHigh = palette.canvasLight,
            onBackground = V2Colors.InkLight,
            onSurface = V2Colors.InkLight,
            onSurfaceVariant = V2Colors.MutedLight,
            outline = V2Colors.TertiaryLight,
            outlineVariant = V2Colors.SeparatorLight,
            error = V2Colors.UnreadRed,
        )
    }
}

private fun Color.compositeOver(base: Color): Color {
    val a = alpha
    return Color(
        red = red * a + base.red * (1 - a),
        green = green * a + base.green * (1 - a),
        blue = blue * a + base.blue * (1 - a),
    )
}

@Composable
fun V2exTheme(
    darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    appTheme: AppTheme = AppTheme.EMERALD,
    readingFontSize: Float = 14f,
    readingLineSpacing: LineSpacingPreference = LineSpacingPreference.RELAXED,
    readingMonoFont: MonoFontPreference = MonoFontPreference.SF_MONO,
    content: @Composable () -> Unit,
) {
    val dark = when (darkModePreference) {
        DarkModePreference.SYSTEM -> isSystemInDarkTheme()
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalV2Dark provides dark,
        LocalReadingTypography provides ReadingTypography(
            fontSize = readingFontSize.coerceIn(13f, 21f),
            lineSpacing = readingLineSpacing,
            monoFont = readingMonoFont,
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme(dark, paletteFor(appTheme)),
            typography = V2exTypography,
            content = content,
        )
    }
}
