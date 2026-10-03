package com.vibe.v2ex.navigation

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import com.vibe.v2ex.MainActivity

/**
 * 明确要进浏览器的网址（「在 V2EX 打开」、网页回复）。用户在系统里让本 App 接管 v2ex.com 链接后，
 * 普通的 ACTION_VIEW 会被转回 App 自己，同一篇话题原地不动，按钮等于没反应（issue #4）。
 *
 * 默认浏览器拿一个不归任何 App 接管的网址去问系统；问到的必须在「能打开普通网页」的名单里，
 * 因为没设默认时系统回的是选择框本身。没有默认浏览器就弹选择框，并把自己排除掉。
 * 别改成 selector + CATEGORY_APP_BROWSER：没设默认浏览器时系统会把设置、相机之类一起列进选择框。
 */
internal fun Context.openInBrowser(url: String) {
    val view = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val probe = Intent(Intent.ACTION_VIEW, "https://example.com/".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val browsers = packageManager.queryIntentActivities(probe, 0)
        .map { it.activityInfo.packageName }
        .filterTo(HashSet()) { it != packageName }
    val defaultBrowser = packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName?.takeIf { it in browsers }
    val intent = if (defaultBrowser != null) {
        view.setPackage(defaultBrowser)
    } else {
        Intent.createChooser(view, null)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, MainActivity::class.java)))
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // 设备上连浏览器都没有，没有可退的地方。
    }
}
