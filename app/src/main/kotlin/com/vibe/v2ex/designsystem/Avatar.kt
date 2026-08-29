package com.vibe.v2ex.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val PALETTE = listOf(
    Color(0xFF0A8F68),
    Color(0xFF0F64B0),
    Color(0xFFA63D2E),
    Color(0xFFB4550A),
    Color(0xFF5A4B8C),
)

private fun colorFor(seed: String) = PALETTE[(seed.hashCode().let { if (it < 0) -it else it }) % PALETTE.size]

@Composable
fun Avatar(username: String, url: String?, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size / 4)
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = username,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(colorFor(username.ifBlank { "?" })),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = username.take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
