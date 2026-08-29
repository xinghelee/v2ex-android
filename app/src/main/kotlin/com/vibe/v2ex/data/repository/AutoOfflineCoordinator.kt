package com.vibe.v2ex.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vibe.v2ex.data.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 关注节点自动离线（mirrors iOS AutoOfflineCoordinator）：启动/回前台时把关注
 * 节点的最新话题连回复缓存到本地。上限与节流参数与 iOS 一致。
 */
@Singleton
class AutoOfflineCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val topicRepository: TopicRepository,
    private val homeRepository: HomeRepository,
    private val offlineRepository: OfflineRepository,
) {
    private val mutex = Mutex()
    private var lastSuccessfulRun = 0L

    suspend fun sync(followedNodes: List<String>, force: Boolean = false) {
        if (!settingsDataStore.autoOfflineFollowedNodes.first()) return
        if (followedNodes.isEmpty()) return
        if (!force && System.currentTimeMillis() - lastSuccessfulRun < MIN_RUN_INTERVAL_MS) return
        if (!allowsDownload(settingsDataStore.offlineOnWifiOnly.first())) return
        if (mutex.isLocked) return

        mutex.withLock {
            // 顺序抓取而非并发：v1 有 600 次/小时的共享 IP 配额。
            val merged = followedNodes.take(MAX_NODE_COUNT).flatMap { node ->
                homeRepository.topicsInNode(node).getOrDefault(emptyList())
            }
            val newest = merged
                .distinctBy { it.id }
                .sortedByDescending { it.activityTimestamp }
                .take(MAX_TOPICS_PER_RUN)

            for (topic in newest) {
                if (!offlineRepository.needsAutomaticRefresh(topic)) continue
                // 单条失败不影响其余话题的离线可用性。
                topicRepository.loadTopic(topic.id).onSuccess { detail ->
                    offlineRepository.save(detail.topic, detail.replies, automatic = true)
                }
            }
            lastSuccessfulRun = System.currentTimeMillis()
        }
    }

    private fun allowsDownload(wifiOnly: Boolean): Boolean {
        if (!wifiOnly) return true
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private companion object {
        const val MAX_NODE_COUNT = 6
        const val MAX_TOPICS_PER_RUN = 12
        const val MIN_RUN_INTERVAL_MS = 30 * 60 * 1000L
    }
}
