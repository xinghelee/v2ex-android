package com.vibe.v2ex.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.local.FavoriteTopicDao
import com.vibe.v2ex.data.local.HistoryDao
import com.vibe.v2ex.data.local.HistoryEntity
import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.moderation.ModerationStore
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.V2exApiV2
import com.vibe.v2ex.data.repository.FavoritesRepository
import com.vibe.v2ex.data.repository.OfflineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ProfileHistoryDay(
    val date: LocalDate,
    val count: Int,
) {
    companion object {
        fun emptyWeek(today: LocalDate = LocalDate.now()): List<ProfileHistoryDay> =
            (6 downTo 0).map { distance -> ProfileHistoryDay(today.minusDays(distance.toLong()), 0) }
    }
}

data class ProfileUiState(
    /** PAT 或网页会话任占其一就算已连接账号。 */
    val isConnected: Boolean = false,
    val member: Member? = null,
    /** 我发布的话题（完整列表；「最近发布」只展示前 4 条，计数用全量）。 */
    val recentTopics: List<Topic> = emptyList(),
    val favoriteCount: Int = 0,
    val historyCount: Int = 0,
    /** 最近 7 个自然日内、按最近浏览日归档的唯一话题数。 */
    val weeklyHistory: List<ProfileHistoryDay> = ProfileHistoryDay.emptyWeek(),
    val offlineCount: Int = 0,
    val offlineByteSize: Long = 0,
    val moderationCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val weeklyHistoryCount: Int get() = weeklyHistory.sumOf(ProfileHistoryDay::count)
    val libraryCount: Int get() = favoriteCount + historyCount + offlineCount + recentTopics.size
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val apiV2: V2exApiV2,
    private val apiV1: V2exApiV1,
    private val secureStore: SecureStore,
    private val favoritesRepository: FavoritesRepository,
    favoriteTopicDao: FavoriteTopicDao,
    historyDao: HistoryDao,
    offlineRepository: OfflineRepository,
    moderationStore: ModerationStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState(isConnected = secureStore.isSignedIn))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private var historyEntries: List<HistoryEntity> = emptyList()
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    /** Credentials that produced the currently displayed member; prevents an account-switch flash. */
    private var memberCredentials: ProfileCredentials? = null

    init {
        viewModelScope.launch {
            favoriteTopicDao.observeAll().collect { favorites ->
                _uiState.update { it.copy(favoriteCount = favorites.size) }
            }
        }
        viewModelScope.launch {
            historyDao.observeAll().collect { entries ->
                historyEntries = entries
                _uiState.update {
                    it.copy(
                        historyCount = entries.distinctBy(HistoryEntity::topicId).size,
                        weeklyHistory = buildWeeklyHistory(entries),
                    )
                }
            }
        }
        viewModelScope.launch {
            offlineRepository.observeAll().collect { bundles ->
                _uiState.update {
                    it.copy(
                        offlineCount = bundles.size,
                        offlineByteSize = bundles.sumOf { bundle -> bundle.byteSize.toLong() },
                    )
                }
            }
        }
        viewModelScope.launch {
            moderationStore.moderationCount.collect { count ->
                _uiState.update { it.copy(moderationCount = count) }
            }
        }
        // 登录态下把网页收藏同步进本地 —— 「我的」页的收藏数因此是账号的真实数据。
        viewModelScope.launch { favoritesRepository.syncFromRemote() }
    }

    fun refresh() {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        val credentials = credentialsSnapshot()
        refreshJob = viewModelScope.launch {
            // 页面跨过午夜后即使历史表没有新写入，也要把七日窗口滚到今天。
            _uiState.update { it.copy(weeklyHistory = buildWeeklyHistory(historyEntries)) }
            if (credentials.webSessionActive) favoritesRepository.syncFromRemote(maxPages = 1)
            if (!isCurrentRefresh(generation, credentials)) return@launch
            if (!credentials.isConnected) {
                memberCredentials = null
                _uiState.update {
                    it.copy(
                        isConnected = false,
                        member = null,
                        recentTopics = emptyList(),
                        isLoading = false,
                        error = null,
                    )
                }
                return@launch
            }
            val canKeepExistingMember = memberCredentials == credentials
            _uiState.update {
                it.copy(
                    isConnected = true,
                    member = it.member.takeIf { canKeepExistingMember },
                    recentTopics = it.recentTopics.takeIf { canKeepExistingMember }.orEmpty(),
                    // Keep an existing member card visible, but expose the
                    // in-flight state so ProfileScreen's pull-to-refresh
                    // indicator remains active until this request settles.
                    isLoading = true,
                    error = null,
                )
            }
            val member = try {
                // 有 PAT 就走 API 2.0 的「当前用户」；只有网页会话（cookie）时退回
                // 公开的 v1 接口按用户名查 —— 网页登录是安卓的主力登录方式，不能因为
                // 没配 PAT 就把人当访客。
                when {
                    credentials.token != null -> try {
                        apiV2.me("Bearer ${credentials.token}")
                            .let { it.result ?: error(it.message ?: "接口没有返回内容") }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (tokenError: Throwable) {
                        credentials.sessionUsername?.let { username ->
                            apiV1.showMember(username)
                        } ?: throw tokenError
                    }
                    credentials.sessionUsername != null -> apiV1.showMember(credentials.sessionUsername)
                    else -> error("网页会话缺少账号信息，请重新登录")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (isCurrentRefresh(generation, credentials)) {
                    _uiState.update {
                        it.copy(isLoading = false, error = throwable.message ?: "加载个人资料失败")
                    }
                }
                return@launch
            }

            if (!isCurrentRefresh(generation, credentials)) return@launch
            memberCredentials = credentials
            _uiState.update { state ->
                state.copy(
                    member = member,
                    recentTopics = state.recentTopics.takeIf {
                        state.member?.username.equals(member.username, ignoreCase = true)
                    }.orEmpty(),
                    isLoading = false,
                )
            }
            loadRecentTopics(member.username, generation, credentials)
        }
    }

    private suspend fun loadRecentTopics(
        username: String,
        generation: Long,
        credentials: ProfileCredentials,
    ) {
        if (username.isBlank()) return
        val topics = try {
            apiV1.topicsByMember(username)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return
        }
        if (!isCurrentRefresh(generation, credentials)) return
        _uiState.update { state ->
            if (state.isConnected && state.member?.username.equals(username, ignoreCase = true)) {
                state.copy(recentTopics = topics)
            } else {
                state
            }
        }
    }

    private fun credentialsSnapshot(): ProfileCredentials {
        val token = secureStore.personalAccessToken
        val webSessionActive = secureStore.isWebSessionActive
        return ProfileCredentials(
            token = token,
            webSessionActive = webSessionActive,
            sessionCookie = secureStore.sessionCookieHeader.takeIf { webSessionActive },
            sessionUsername = secureStore.sessionUsername?.takeIf(String::isNotBlank).takeIf { webSessionActive },
        )
    }

    private fun isCurrentRefresh(generation: Long, credentials: ProfileCredentials): Boolean =
        generation == refreshGeneration && credentials == credentialsSnapshot()

    private data class ProfileCredentials(
        val token: String?,
        val webSessionActive: Boolean,
        /** Included only as an opaque identity/version marker; never logged or sent from here. */
        val sessionCookie: String?,
        val sessionUsername: String?,
    ) {
        val isConnected: Boolean get() = token != null || webSessionActive
    }

    /**
     * HistoryEntity 以 topicId 为主键，但这里仍显式去重，确保图表语义是“唯一话题”而非打开次数。
     * viewedAt 是毫秒时间戳，按设备当前时区归入最近 7 个自然日。
     */
    private fun buildWeeklyHistory(
        entries: List<HistoryEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<ProfileHistoryDay> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val firstDay = today.minusDays(6)
        val counts = entries.asSequence()
            .distinctBy(HistoryEntity::topicId)
            .map { entry -> Instant.ofEpochMilli(entry.viewedAt).atZone(zoneId).toLocalDate() }
            .filter { date -> !date.isBefore(firstDay) && !date.isAfter(today) }
            .groupingBy { it }
            .eachCount()

        return (6 downTo 0).map { distance ->
            val date = today.minusDays(distance.toLong())
            ProfileHistoryDay(date = date, count = counts[date] ?: 0)
        }
    }
}
