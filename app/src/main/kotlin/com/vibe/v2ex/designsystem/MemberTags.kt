package com.vibe.v2ex.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibe.v2ex.data.tags.MemberTagLookup

/**
 * 全局用户标记查表，由 V2exApp 提供。默认 [MemberTagLookup.Disabled]：任何没被包进
 * provider 的预览或页面都画不出标记，和功能不存在时一致。
 */
val LocalMemberTags = staticCompositionLocalOf { MemberTagLookup.Disabled }

/** 当前用户名的标记；开关关闭或没打过标记时为空列表，调用处据此什么都不画。 */
@Composable
fun memberTagsFor(username: String): List<String> = LocalMemberTags.current.tagsFor(username)

/** 一枚灰底小胶囊，和「推广」徽章同一套形态；超长标签省略号截断。 */
@Composable
fun MemberTagChip(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize, lineHeight = fontSize * 1.35f),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 112.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 一行标记，最多 [maxVisible] 枚，其余折成 `+n`；空列表什么都不画。 */
@Composable
fun MemberTagChips(
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3,
    fontSize: TextUnit = 10.sp,
    spacing: androidx.compose.ui.unit.Dp = 4.dp,
) {
    if (tags.isEmpty()) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        tags.take(maxVisible).forEachIndexed { index, tag ->
            MemberTagChip(
                text = tag,
                fontSize = fontSize,
                modifier = Modifier.padding(start = if (index == 0) 0.dp else spacing),
            )
        }
        if (tags.size > maxVisible) {
            MemberTagChip(
                text = "+${tags.size - maxVisible}",
                fontSize = fontSize,
                modifier = Modifier.padding(start = spacing),
            )
        }
    }
}
