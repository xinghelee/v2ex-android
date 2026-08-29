package com.vibe.v2ex.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * 身份方块调色板 — 设计稿的 5 色：蓝 / 墨绿 / 紫 / 橙 / 灰黑。
 * 节点分类的彩色方块也从这里取色。
 */
val IdentityPalette = listOf(
    Color(0xFF3D5A80),
    Color(0xFF1C7C6B),
    Color(0xFF8E5A9E),
    Color(0xFFC77700),
    Color(0xFF4A4A52),
)

fun identityColor(seed: String): Color =
    IdentityPalette[(seed.hashCode().let { if (it < 0) -it else it }) % IdentityPalette.size]

/**
 * 身份方块（设计稿的 avatar 形态）：圆角≈尺寸×0.29 的方形。
 * 有头像图时同形状裁切；无图时显示用户名前 2 字符小写、白字 600。
 */
@Composable
fun Avatar(username: String, url: String?, size: Dp = 34.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.29f)
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = username,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(identityColor(username.ifBlank { "?" })),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = username.take(2).lowercase().ifBlank { "?" },
                color = Color.White,
                style = TextStyle(
                    fontSize = (size.value * 0.35f).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}
