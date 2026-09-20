package com.vibe.v2ex.data.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.moderation.ModerationStore
import com.vibe.v2ex.data.remote.V2exApiV2
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.Locale

/**
 * 定时拉一页通知，比基线新的就推。只走 API 2.0（需要 Token）：通知列表本来就只有 API 有，
 * 网页会话只能给未读数，给不出内容。
 */
@HiltWorker
class NotificationPollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsDataStore,
    private val secureStore: SecureStore,
    private val apiV2: V2exApiV2,
    private val moderationStore: ModerationStore,
    private val notifier: NotificationPushNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = settings.notificationPush.first()
        if (!prefs.enabled) return Result.success()
        val token = secureStore.personalAccessToken ?: return Result.success()

        val envelope = try {
            apiV2.notifications(page = 1, authorization = "Bearer $token")
        } catch (error: IOException) {
            // 网络抖动退避重试；连续失败就等下个周期，不把一次离线变成无限重试。
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (error: Exception) {
            return Result.failure()
        }
        // Token 失效、被限流：接口会明确说不成功，这种情况等用户处理，不重试也不推。
        val notifications = envelope.result.takeIf { envelope.success == true } ?: return Result.success()

        val blocked = moderationStore.blockedUsernames.value
            .mapTo(HashSet()) { it.trim().lowercase(Locale.ROOT) }
        val plan = NotificationPushPlanner.plan(prefs.lastSeenId, notifications, blocked)
        if (plan.newLastSeenId > prefs.lastSeenId) settings.raiseNotificationPushLastSeenId(plan.newLastSeenId)
        if (plan.toNotify.isNotEmpty()) notifier.notify(plan.toNotify)
        return Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
