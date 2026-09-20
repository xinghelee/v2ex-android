package com.vibe.v2ex.data.push

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vibe.v2ex.data.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "V2exPush"

/**
 * 把「新提醒推送」偏好翻译成 WorkManager 的周期任务。跟着偏好走：开就排期、改间隔就更新、
 * 关就取消。订阅挂在进程级作用域上，用户离开设置页也照样生效。
 */
@Singleton
class NotificationPushScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        settings.notificationPush
            .map { it.enabled to it.intervalMinutes }
            .distinctUntilChanged()
            .onEach { (enabled, interval) -> apply(enabled, interval) }
            .catch { Log.w(TAG, "notification push settings stream failed", it) }
            .launchIn(scope)
    }

    private fun apply(enabled: Boolean, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        // UPDATE：改间隔时沿用原有排期，不会因为每次冷启动都重新入队而把「下次执行」往后推。
        // 周期任务的第一次执行会尽快跑，刚打开开关时就靠它记下基线。
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val PERIODIC_WORK = "notification-poll"
    }
}
