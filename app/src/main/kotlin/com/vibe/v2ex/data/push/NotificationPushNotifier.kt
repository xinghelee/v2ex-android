package com.vibe.v2ex.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vibe.v2ex.MainActivity
import com.vibe.v2ex.R
import com.vibe.v2ex.data.model.Notification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 把新提醒发成一条系统通知；点开直接落到「通知」Tab。 */
@Singleton
class NotificationPushNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notify(items: List<Notification>) {
        if (items.isEmpty() || !canPost()) return
        ensureChannel()
        val lines = items.map(NotificationPushPlanner::plainText)
        val title = if (items.size == 1) "V2EX 新提醒" else "V2EX · ${items.size} 条新提醒"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.take(MAX_LINES).forEach(style::addLine) })
            .setNumber(items.size)
            .setContentIntent(openNotificationsTab())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
        // canPost 已检查权限；这里再兜一层，系统在两次调用之间撤销权限时不会抛到 Worker 外面。
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }

    /** 用户已经在 App 里看过通知页，把还挂着的系统通知收掉。 */
    fun cancel() {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "新提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "V2EX 上有人回复、提到或感谢你时提醒"
            },
        )
    }

    private fun openNotificationsTab(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOTIFICATIONS
            putExtra(EXTRA_OPEN_TAB, TAB_NOTIFICATIONS)
            // MainActivity 是 singleTop：已在前台就走 onNewIntent，否则冷启动后再切 Tab。
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_OPEN_TAB = "com.vibe.v2ex.extra.OPEN_TAB"
        const val TAB_NOTIFICATIONS = "notifications"
        private const val ACTION_OPEN_NOTIFICATIONS = "com.vibe.v2ex.action.OPEN_NOTIFICATIONS"
        private const val CHANNEL_ID = "new_notifications"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_LINES = 5
    }
}
