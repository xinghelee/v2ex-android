package com.vibe.v2ex.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vibe.v2ex.data.datastore.AppTheme
import com.vibe.v2ex.data.datastore.DarkModePreference

/** One palette's accent/secondary pair, light and dark variants — exact hex values from the iOS DesignSystem/Theme.swift. */
private data class PaletteColors(
    val accentLight: Color,
    val accentDark: Color,
    val accentDeepLight: Color,
    val accentDeepDark: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
    val canvasLight: Color,
)

private val PALETTES = mapOf(
    AppTheme.EMERALD to PaletteColors( // 翡翠绿
        accentLight = Color(0xFF00734C), accentDark = Color(0xFF2FBF8F),
        accentDeepLight = Color(0xFF00543A), accentDeepDark = Color(0xFF1E9C72),
        secondaryLight = Color(0xFFA85C00), secondaryDark = Color(0xFFE8A33C),
        canvasLight = Color(0xFFF2F2F7),
    ),
    AppTheme.OCEAN to PaletteColors( // 海洋蓝
        accentLight = Color(0xFF0F64B0), accentDark = Color(0xFF4AA3E8),
        accentDeepLight = Color(0xFF0A4A85), accentDeepDark = Color(0xFF2F87C8),
        secondaryLight = Color(0xFFB06A00), secondaryDark = Color(0xFFE8A33C),
        canvasLight = Color(0xFFF0F3F7),
    ),
    AppTheme.CRIMSON to PaletteColors( // 绯红
        accentLight = Color(0xFFA63D2E), accentDark = Color(0xFFE86A5A),
        accentDeepLight = Color(0xFF7E2A20), accentDeepDark = Color(0xFFD14A3A),
        secondaryLight = Color(0xFFA85C00), secondaryDark = Color(0xFFE8A33C),
        canvasLight = Color(0xFFF7F2F1),
    ),
    AppTheme.AMBER to PaletteColors( // 琥珀橙 — secondary deliberately swaps to blue since accent is already orange-family
        accentLight = Color(0xFFB4550A), accentDark = Color(0xFFEFA85C),
        accentDeepLight = Color(0xFF8A3D00), accentDeepDark = Color(0xFFD97C34),
        secondaryLight = Color(0xFF1E5AA8), secondaryDark = Color(0xFF4AA3E8),
        canvasLight = Color(0xFFF7F3EC),
    ),
    AppTheme.VIOLET to PaletteColors( // 紫罗兰
        accentLight = Color(0xFF5A4B8C), accentDark = Color(0xFFA898DD),
        accentDeepLight = Color(0xFF433665), accentDeepDark = Color(0xFF8A78C6),
        secondaryLight = Color(0xFFA85C00), secondaryDark = Color(0xFFE8A33C),
        canvasLight = Color(0xFFF3F1F8),
    ),
)

// Palette-independent neutral ramp, light/dark.
private val CARD_LIGHT = Color(0xFFFFFFFF); private val CARD_DARK = Color(0xFF161618)
private val INK_LIGHT = Color(0xFF141416); private val INK_DARK = Color(0xFFF2F2F4)
private val BODY_LIGHT = Color(0xFF2C2C2E); private val BODY_DARK = Color(0xFFD1D1D6)
private val CANVAS_DARK = Color(0xFF0A0A0B) // fixed across all palettes

private fun colorScheme(theme: AppTheme, dark: Boolean): ColorScheme {
    val p = PALETTES.getValue(theme)
    // accentSoft/accentWash from the iOS spec: accent at 10%/16% (light/dark) over the canvas.
    return if (dark) {
        val accentSoft = p.accentDark.copy(alpha = 0.16f).compositeOverColor(CARD_DARK)
        darkColorScheme(
            primary = p.accentDark,
            onPrimary = Color.White,
            primaryContainer = accentSoft,
            onPrimaryContainer = p.accentDark,
            secondary = p.secondaryDark,
            secondaryContainer = accentSoft,
            onSecondaryContainer = p.accentDark,
            tertiary = p.accentDeepDark,
            background = CANVAS_DARK,
            surface = CARD_DARK,
            surfaceVariant = Color(0xFF1E1E21),
            surfaceContainer = Color(0xFF1A1A1D),
            surfaceContainerHigh = Color(0xFF222225),
            onBackground = INK_DARK,
            onSurface = INK_DARK,
            onSurfaceVariant = BODY_DARK,
            outlineVariant = INK_DARK.copy(alpha = 0.10f).compositeOverColor(CARD_DARK),
        )
    } else {
        val accentSoft = p.accentLight.copy(alpha = 0.10f).compositeOverColor(CARD_LIGHT)
        lightColorScheme(
            primary = p.accentLight,
            onPrimary = Color.White,
            primaryContainer = accentSoft,
            onPrimaryContainer = p.accentDeepLight,
            secondary = p.secondaryLight,
            secondaryContainer = accentSoft,
            onSecondaryContainer = p.accentDeepLight,
            tertiary = p.accentDeepLight,
            background = p.canvasLight,
            surface = CARD_LIGHT,
            surfaceVariant = Color(0xFFECECEF),
            surfaceContainer = Color(0xFFF4F4F6),
            surfaceContainerHigh = Color(0xFFECECEF),
            onBackground = INK_LIGHT,
            onSurface = INK_LIGHT,
            onSurfaceVariant = BODY_LIGHT,
            outlineVariant = INK_LIGHT.copy(alpha = 0.07f).compositeOverColor(CARD_LIGHT),
        )
    }
}

/** Flattens a translucent color over an opaque base so M3 containers stay opaque. */
private fun Color.compositeOverColor(base: Color): Color {
    val a = alpha
    return Color(
        red = red * a + base.red * (1 - a),
        green = green * a + base.green * (1 - a),
        blue = blue * a + base.blue * (1 - a),
    )
}

@Composable
fun V2exTheme(
    appTheme: AppTheme = AppTheme.EMERALD,
    darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (darkModePreference) {
        DarkModePreference.SYSTEM -> isSystemInDarkTheme()
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
    }
    MaterialTheme(
        colorScheme = colorScheme(appTheme, dark),
        typography = V2exTypography,
        content = content,
    )
}
