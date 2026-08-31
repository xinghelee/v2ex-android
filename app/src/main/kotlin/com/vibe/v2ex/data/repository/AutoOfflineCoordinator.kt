package com.vibe.v2ex.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.model.Topic
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 正在下载的进度，供设置页「立即缓存」显示 x/y。null = 空闲。 */
data class OfflineSyncProgress(val completed: Int, val total: Int)

/**
 * 关注节点自动离线（mirrors iOS AutoOfflineCoordinator）：启动/回前台时把关注
 * 节点的最新话题连回复缓存到本地。上限与节流参数与 iOS 一致。
 *
 * 两个入口：[sync] 是后台的，受开关 / Wi-Fi / 节流约束；[prefetchNow] 是设置页
 * 「立即缓存」，用户明确要的，什么都不看。一个节点都没关注时两者都退回「全部」
 * 列表 —— 否则这个功能对新用户等于不存在。
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

    // 下载跑在协调器自己的 scope 上：用户点完「立即缓存」多半会退出设置页去干别的，
    // 挂在 ViewModel 上会被一起取消，缓存只下到一半。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null

    private val _progress = MutableStateFlow<OfflineSyncProgress?>(null)
    val progress: StateFlow<OfflineSyncProgress?> = _progress.asStateFlow()

    /** 「立即缓存」的一次性结果文案，UI 展示后调 [consumeResult] 清掉。 */
    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    /** 后台同步：开关、Wi-Fi、30 分钟节流三道闸门都要过。 */
    suspend fun sync(followedNodes: List<String>, force: Boolean = false) {
        if (!settingsDataStore.autoOfflineFollowedNodes.first()) return
        if (!force && System.currentTimeMillis() - lastSuccessfulRun < MIN_RUN_INTERVAL_MS) return
        if (!allowsDownload(settingsDataStore.offlineOnWifiOnly.first())) return
        // 后台跑的这一趟可有可无：已有下载在跑就直接让位，别排队堆积。
        if (mutex.isLocked) return
        download(followedNodes, MAX_TOPICS_PER_RUN)
    }

    /**
     * 设置页「立即缓存」：用户在登机口明确点的，开关 / Wi-Fi / 节流一概不看，
     * 额度也比后台同步大。即发即忘，进度看 [progress]，结果看 [result]。
     */
    fun prefetchNow(followedNodes: List<String>) {
        if (prefetchJob?.isActive == true) return
        prefetchJob = scope.launch {
            _result.value = null
            runCatching { download(followedNodes, MAX_TOPICS_PER_PREFETCH) }
                .onSuccess { saved ->
                    _result.value = if (saved > 0) "已缓存 $saved 篇新话题" else "已是最新，无需重新下载"
                }
                .onFailure { _result.value = it.message ?: "下载失败，请检查网络" }
        }
    }

    fun consumeResult() {
        _result.value = null
    }

    private suspend fun download(followedNodes: List<String>, limit: Int): Int = mutex.withLock {
        val newest = collectCandidates(followedNodes)
            .distinctBy { it.id }
            .sortedByDescending { it.activityTimestamp }
            .take(limit)
        var saved = 0
        _progress.value = OfflineSyncProgress(0, newest.size)
        try {
            newest.forEachIndexed { index, topic ->
                if (offlineRepository.needsAutomaticRefresh(topic)) {
                    // 单条失败不影响其余话题的离线可用性。
                    topicRepository.loadTopic(topic.id).onSuccess { detail ->
                        offlineRepository.save(detail.topic, detail.replies, automatic = true)
                        saved++
                    }
                }
                _progress.value = OfflineSyncProgress(index + 1, newest.size)
            }
        } finally {
            _progress.value = null
        }
        if (newest.isNotEmpty()) lastSuccessfulRun = System.currentTimeMillis()
        saved
    }

    /** 顺序抓取而非并发：v1 有 600 次/小时的共享 IP 配额。 */
    private suspend fun collectCandidates(followedNodes: List<String>): List<Topic> {
        val nodes = followedNodes.take(MAX_NODE_COUNT)
        // 一个节点都没关注的用户照样会上飞机 —— 退回「全部」列表，别让离线是空的。
        if (nodes.isEmpty()) return homeRepository.latestTopics().getOrDefault(emptyList())
        return nodes.flatMap { homeRepository.topicsInNode(it).getOrDefault(emptyList()) }
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
        const val MAX_TOPICS_PER_PREFETCH = 30
        const val MIN_RUN_INTERVAL_MS = 30 * 60 * 1000L
    }
}
