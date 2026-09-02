package com.vibe.v2ex.designsystem

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.Shadow

/**
 * 玻璃效果的设备门槛：模糊要 RenderEffect（Android 12），折射要 AGSL（Android 13）。
 * Android 12 以下连模糊都没有，剩下的只是一块半透明色板 —— 那还不如走原来的实心底栏。
 */
val isLiquidGlassSupported: Boolean get() = isRenderEffectSupported()

/**
 * 内容底部要为悬浮底栏让出的留白。玻璃底栏浮在内容之上，列表得自己滚过它下面；
 * 实心底栏由 Scaffold 占位，这里只留呼吸空间。V2exApp 按当前底栏形态提供实际值。
 */
val LocalTabBarClearance = compositionLocalOf { 16.dp }

/** 悬浮玻璃底栏的胶囊高度与四周留白，同时用于算 [LocalTabBarClearance]。 */
val GlassBarHeight = 56.dp
val GlassBarMargin = 8.dp

/**
 * iOS 26 的那层玻璃：模糊打底 + 边缘折射 + 高光描边 + 投影。
 * [lens] 与 [blur] 内部各自按系统版本 no-op，低版本会自动退化成纯色板。
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(8.dp.toPx())
        lens(20.dp.toPx(), 24.dp.toPx())
    },
    highlight = { Highlight.Default },
    shadow = { Shadow.Default },
    onDrawSurface = { drawRect(tint) },
)
