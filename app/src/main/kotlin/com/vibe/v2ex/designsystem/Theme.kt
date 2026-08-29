package com.vibe.v2ex.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vibe.v2ex.data.datastore.DarkModePreference

/**
 * 品牌视觉 — 源自 claude.ai/design「V2EX iOS」设计稿（iOS 26 Liquid Glass）：
 * 墨绿强调、分组底 #F2F2F7、白卡圆角 22、彩色方形身份标识。
 * 单一品牌配色，浅/深两个模式（设计稿 09 屏只提供 浅色/深色/跟随系统）。
 */
object V2Colors {
    val AccentLight = Color(0xFF1C7C6B)
    val AccentDark = Color(0xFF2A9C87)
    val AccentDeep = Color(0xFF14584D)

    val CanvasLight = Color(0xFFF2F2F7)
    val CanvasDark = Color(0xFF000000)
    val CardLight = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF1C1C1E)

    val InkLight = Color(0xFF000000)
    val InkDark = Color(0xFFFFFFFF)
    val BodyLight = Color(0xFF1C1C1E)
    val BodyDark = Color(0xFFEBEBF5)
    val SecondaryLabelLight = Color(0xFF3C3C43)
    val MutedLight = Color(0xFF8E8E93)
    val MutedDark = Color(0x99EBEBF5) // rgba(235,235,245,0.6)
    val TertiaryLight = Color(0xFFC7C7CC)
    val TertiaryDark = Color(0x4DEBEBF5) // rgba(235,235,245,0.3)

    val SeparatorLight = Color(0x1F3C3C43) // rgba(60,60,67,0.12)
    val SeparatorDark = Color(0xA6545458) // rgba(84,84,88,0.65)

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

private fun scheme(dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = V2Colors.AccentDark,
            onPrimary = Color.White,
            primaryContainer = V2Colors.accentSoft(true).compositeOver(V2Colors.CardDark),
            onPrimaryContainer = V2Colors.AccentDark,
            secondary = V2Colors.AccentDark,
            secondaryContainer = V2Colors.accentSoft(true).compositeOver(V2Colors.CardDark),
            onSecondaryContainer = V2Colors.AccentDark,
            tertiary = V2Colors.Amber,
            tertiaryContainer = V2Colors.AmberSoftLight.compositeOver(V2Colors.CardDark),
            onTertiaryContainer = V2Colors.Amber,
            background = V2Colors.CanvasDark,
            surface = V2Colors.CardDark,
            surfaceVariant = Color(0xFF2C2C2E),
            surfaceContainer = Color(0xFF232326),
            surfaceContainerHigh = Color(0xFF2C2C2E),
            onBackground = V2Colors.InkDark,
            onSurface = V2Colors.InkDark,
            onSurfaceVariant = V2Colors.MutedDark,
            outline = V2Colors.TertiaryDark,
            outlineVariant = V2Colors.SeparatorDark,
            error = V2Colors.UnreadRed,
        )
    } else {
        lightColorScheme(
            primary = V2Colors.AccentLight,
            onPrimary = Color.White,
            primaryContainer = V2Colors.accentSoft(false).compositeOver(V2Colors.CardLight),
            onPrimaryContainer = V2Colors.AccentDeep,
            secondary = V2Colors.AccentLight,
            secondaryContainer = V2Colors.accentSoft(false).compositeOver(V2Colors.CardLight),
            onSecondaryContainer = V2Colors.AccentLight,
            tertiary = V2Colors.Amber,
            tertiaryContainer = V2Colors.AmberSoftLight.compositeOver(V2Colors.CardLight),
            onTertiaryContainer = V2Colors.Amber,
            background = V2Colors.CanvasLight,
            surface = V2Colors.CardLight,
            surfaceVariant = V2Colors.CanvasLight,
            surfaceContainer = Color(0xFFF7F7F9),
            surfaceContainerHigh = V2Colors.CanvasLight,
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
    content: @Composable () -> Unit,
) {
    val dark = when (darkModePreference) {
        DarkModePreference.SYSTEM -> isSystemInDarkTheme()
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalV2Dark provides dark) {
        MaterialTheme(
            colorScheme = scheme(dark),
            typography = V2exTypography,
            content = content,
        )
    }
}
