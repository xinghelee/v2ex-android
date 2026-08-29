package com.vibe.v2ex.feature.topic

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.designsystem.V2Colors
import com.vibe.v2ex.designsystem.relativeTimeText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - 卡片数据

/** 卡片要画的所有东西的扁平快照（mirrors iOS TopicShareCardData）。 */
private data class ShareCardData(
    val title: String,
    val author: String,
    val nodeTitle: String,
    val timeLabel: String,
    val excerpt: String,
    /** 正文超过上限被截断时为 true — 卡片会明说，而不是没头没尾地断句。 */
    val isTruncated: Boolean,
    val replies: Int,
    val url: String,
    val avatarUrl: String?,
) {
    val shortUrl: String
        get() = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")

    companion object {
        fun from(topic: Topic): ShareCardData {
            val (excerpt, truncated) = excerpt(topic.content.orEmpty())
            return ShareCardData(
                title = topic.title,
                author = topic.authorName,
                nodeTitle = topic.nodeTitle,
                timeLabel = relativeTimeText(topic.activityTimestamp),
                excerpt = excerpt,
                isTruncated = truncated,
                replies = topic.replies,
                url = topic.webUrl,
                avatarUrl = topic.member?.avatarUrl,
            )
        }

        /**
         * 卡片随正文长度生长，普通帖子完整分享。上限只因为产物是图片：
         * 太长的 PNG 会被聊天工具缩略到不可读（mirrors iOS excerpt 清洗规则）。
         */
        private fun excerpt(content: String, limit: Int = 1_000): Pair<String, Boolean> {
            var text = content
            text = text.replace(Regex("```[\\s\\S]*?```"), "「代码」")
            text = text.replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "「图片」")
            text = text.replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
            text = text.replace(Regex("(?m)^#{1,6}\\s*"), "")
            text = text.replace(Regex("[*_`>]"), "")
            text = text.replace(Regex("[ \\t]+"), " ")
            text = text.replace(Regex("\\n{2,}"), "\n")
            text = text.trim()

            if (text.length <= limit) return text to false
            // 退到最后一个句读处，让截断落在自然的位置。
            val head = text.take(limit)
            val breaks = charArrayOf('。', '！', '？', '\n', '.', '!', '?')
            val cut = head.lastIndexOfAny(breaks)
            val kept = if (cut >= 0) head.take(cut + 1) else head
            return (kept.trim() + "…") to true
        }
    }
}

// MARK: - 分享弹层

/**
 * 「分享为卡片」预览弹层：展示卡片 + 分享/取消。点分享时把卡片的绘制层
 * 录制成 PNG，经 FileProvider 交给系统分享面板。
 */
@Composable
fun TopicShareCardSheet(topic: Topic, onDismiss: () -> Unit) {
    val data = remember(topic) { ShareCardData.from(topic) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var isSharing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Box(
                // 录制的是这个 Box 的内容 — 卡片自带圆角，背景保持透明。
                modifier = Modifier.drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                },
            ) {
                ShareCardContent(data)
            }
            Row(
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = !isSharing) {
                            isSharing = true
                            scope.launch {
                                shareBitmap(context, topic.id, graphicsLayer.toImageBitmap())
                                isSharing = false
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = "分享图片",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private suspend fun shareBitmap(context: Context, topicId: Long, image: ImageBitmap) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "v2ex-topic-$topicId.png")
        file.outputStream().use { stream ->
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "分享话题卡片"))
}

// MARK: - 卡片本体（mirrors iOS TopicShareCard：340dp 宽，随内容生长）

@Composable
private fun ShareCardContent(data: ShareCardData) {
    val dark = LocalV2Dark.current
    val accent = MaterialTheme.colorScheme.primary
    val card = if (dark) V2Colors.CardDark else V2Colors.CardLight
    Column(
        modifier = Modifier
            .width(340.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(card)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
    ) {
        // 顶部 accent 渐变条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(listOf(accent, lerp(accent, Color.Black, 0.3f))),
                ),
        )
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 节点胶囊 + V2EX 字标
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (data.nodeTitle.isNotBlank()) {
                    Text(
                        text = data.nodeTitle,
                        style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(V2Colors.accentSoft(dark))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "V2EX",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            // 标题
            Text(
                text = data.title,
                style = TextStyle(
                    fontSize = 21.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            // 作者行
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShareAvatar(name = data.author, url = data.avatarUrl)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = data.author.ifBlank { "匿名" },
                    style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (data.timeLabel.isNotBlank()) {
                    Text(
                        text = " · ${data.timeLabel}",
                        style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            // 正文摘录
            if (data.excerpt.isNotBlank()) {
                Text(
                    text = data.excerpt,
                    style = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                    color = if (dark) V2Colors.BodyDark else V2Colors.BodyLight,
                )
                if (data.isTruncated) {
                    Text(
                        text = "正文较长，已截断",
                        style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            // 页脚：二维码 + 扫码提示 + 回复数
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrTile(text = data.url)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "扫码阅读全文",
                        style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = data.shortUrl,
                        style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${data.replies}",
                        style = TextStyle(fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
                        color = accent,
                    )
                    Text(
                        text = "条回复",
                        style = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 30dp 头像：accent 渐变字母底 + 已加载的头像图覆盖（在线渲染，直接用 Coil）。 */
@Composable
private fun ShareAvatar(name: String, url: String?) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.92f), accent))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().let { trimmed ->
                val first = trimmed.firstOrNull() ?: '?'
                if (first.code > 0x2E80) first.toString() else trimmed.take(2).lowercase()
            },
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(30.dp).clip(shape),
            )
        }
    }
}

/**
 * 二维码：深色模式也保持黑码白底 —— 反色码许多扫描器不认，
 * 而它的全部职责就是被另一台手机拍下来。
 */
@Composable
private fun QrTile(text: String) {
    val bitmap = remember(text) { generateQr(text) }
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "话题链接二维码",
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

private fun generateQr(text: String): ImageBitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.MARGIN to 0,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        ),
    )
    val size = matrix.width
    val pixels = IntArray(size * size) { i ->
        if (matrix.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()
