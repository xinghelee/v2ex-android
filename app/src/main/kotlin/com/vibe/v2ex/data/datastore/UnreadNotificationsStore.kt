package com.vibe.v2ex.data.datastore

import com.vibe.v2ex.data.remote.V2exApiV2
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * 底部标签「通知」角标的数据源：最近一次拉到的通知 id 列表 × 本地已读集合。
 * 启动时由 AppViewModel 刷新一次；通知页自己的刷新/删除也会更新（mirrors iOS
 * RootView 的 `.badge(notifications.unreadCount)`）。
 */
@Singleton
class UnreadNotificationsStore @Inject constructor(
    private val apiV2: V2exApiV2,
    private val secureStore: SecureStore,
    seenNotificationsStore: SeenNotificationsStore,
) {
    private val latestIds = MutableStateFlow<List<Long>>(emptyList())

    val unreadCount: Flow<Int> = combine(latestIds, seenNotificationsStore.seenIds) { ids, seen ->
        ids.count { it !in seen }
    }

    /** 通知页拉到新列表后同步进来，角标和列表保持一致。 */
    fun publish(ids: List<Long>) {
        latestIds.value = ids
    }

    suspend fun refresh() {
        if (!secureStore.isTokenSet) {
            latestIds.value = emptyList()
            return
        }
        runCatching { apiV2.notifications(page = 1).result }.getOrNull()?.let { items ->
            latestIds.value = items.map { it.id }
        }
    }
}
