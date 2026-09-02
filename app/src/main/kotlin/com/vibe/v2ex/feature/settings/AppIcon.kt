package com.vibe.v2ex.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.vibe.v2ex.MainActivity
import com.vibe.v2ex.R

/**
 * 可选的桌面图标。每个值对应 AndroidManifest 里一个 activity-alias，切换 = 启用新别名、停用其余别名。
 *
 * 停用当前任务栈的根 alias 会让系统直接结束这个任务（DONT_KILL_APP 拦不住），所以设置页只记录选择，
 * 真正的切换放在 MainActivity.onStop —— 用户已经离开了，App 被收掉也看不见。
 * 启动器可能把指向旧别名的桌面快捷方式移除，这是系统行为，UI 里要提醒。
 */
enum class AppIcon(
    /** activity-alias 的类名后缀（`.icon.Xxx`）。 */
    private val alias: String,
    val label: String,
    @DrawableRes val foreground: Int,
) {
    BUBBLES("Bubbles", "双气泡", R.drawable.ic_launcher_fg_bubbles),
    DOTS("Dots", "三点", R.drawable.ic_launcher_fg_dots),
    V("V", "镂空 V", R.drawable.ic_launcher_fg_v),
    BOLD_V("BoldV", "粗 V", R.drawable.ic_launcher_fg_boldv),
    ;

    private fun component(context: Context) =
        ComponentName(context.packageName, "${MainActivity::class.java.packageName}.icon.$alias")

    companion object {
        /** 与 ic_launcher_background.xml 同一组渐变，设置页里画预览用。 */
        val backgroundGradient = listOf(Color(0xFF0E9463), Color(0xFF005538))

        fun fromName(name: String?): AppIcon = entries.firstOrNull { it.name == name } ?: BUBBLES

        /** 当前启用的别名；没人被显式启用时就是 manifest 里默认开着的 [BUBBLES]。 */
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull { icon ->
                pm.getComponentEnabledSetting(icon.component(context)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: BUBBLES
        }

        /** 把 PackageManager 里的状态对齐到用户的选择；已经一致就什么都不做。 */
        fun sync(context: Context, icon: AppIcon) {
            if (current(context) != icon) apply(context, icon)
        }

        /** 先启用新的再停用旧的，中间任何时刻都至少有一个启动器入口。 */
        private fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach { other ->
                pm.setComponentEnabledSetting(
                    other.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
