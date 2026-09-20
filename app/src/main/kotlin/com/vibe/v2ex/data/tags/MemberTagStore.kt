package com.vibe.v2ex.data.tags

import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.local.MemberTagDao
import com.vibe.v2ex.data.local.MemberTagEntity
import com.vibe.v2ex.data.remote.PolishMemberTag
import com.vibe.v2ex.data.remote.PolishSettingsNoteParser
import com.vibe.v2ex.data.remote.WebSessionService
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 与 V2EX Polish 同步的进行态和一次性结果文案，设置页据此显示进度和提示。 */
data class MemberTagSyncState(
    val isSyncing: Boolean = false,
    val message: String? = null,
)

/**
 * 用户标记的唯一入口：本地 Room 表 + 与浏览器插件 V2EX Polish 的双向同步。
 *
 * 同步走用户的 V2EX 记事本（插件自己的备份机制，见 [PolishSettingsNoteParser]），所以需要
 * 网页登录态。同步跑在进程级 scope 里，离开设置页不会中断；结果通过 [syncState] 回传。
 */
@Singleton
class MemberTagStore @Inject constructor(
    private val dao: MemberTagDao,
    private val settings: SettingsDataStore,
    private val secureStore: SecureStore,
    private val webSessionService: WebSessionService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val _syncState = MutableStateFlow(MemberTagSyncState())
    val syncState: StateFlow<MemberTagSyncState> = _syncState.asStateFlow()

    /** 全部标记，最近改动的在前。 */
    val all: Flow<List<MemberTagRecord>> = dao.observeAll().map { rows -> rows.map { it.toRecord() } }

    /** 界面查表：开关关闭时恒为 [MemberTagLookup.Disabled]，所有标记 UI 随之整体消失。 */
    val lookup: Flow<MemberTagLookup> = combine(settings.showMemberTags, all) { show, records ->
        if (show) MemberTagLookup.of(records) else MemberTagLookup.Disabled
    }

    fun observe(username: String): Flow<MemberTagRecord?> =
        dao.observe(username.key()).map { it?.toRecord() }

    suspend fun tagsFor(username: String): List<String> = dao.get(username.key())?.toRecord()?.tags.orEmpty()

    /** 空列表等于删除；头像只在拿得到时更新，拿不到保留原值（插件那边会显示它）。 */
    suspend fun setTags(username: String, tags: List<String>, avatarUrl: String? = null) {
        val name = username.trim()
        if (name.isEmpty()) return
        val cleaned = normalizeMemberTags(tags)
        if (cleaned.isEmpty()) {
            dao.delete(name.key())
            return
        }
        val existing = dao.get(name.key())
        dao.upsert(
            MemberTagRecord(
                username = existing?.username ?: name,
                tags = cleaned,
                avatarUrl = avatarUrl?.takeIf(String::isNotBlank) ?: existing?.avatarUrl,
            ).toEntity(System.currentTimeMillis()),
        )
    }

    suspend fun remove(username: String) = dao.delete(username.key())

    /** 拉取：记事本里的标记并入本地（只增不删）。 */
    fun pullFromPolish() = launchSync { cookie ->
        val note = webSessionService.polishSettingsNote(cookie).getOrThrow()
            ?: syncFailure("没有找到 V2EX Polish 的备份记事本，请先在浏览器插件里完成一次备份")
        val remote = note.memberTags.toRecords()
        if (remote.isEmpty()) syncFailure("V2EX Polish 备份里还没有用户标签")
        val local = dao.all().map { it.toRecord() }
        val changed = applyMerged(local, mergeMemberTags(local, remote))
        buildString {
            append("已同步 ${remote.size} 位用户的标记")
            if (changed > 0) append("，本地新增或更新 $changed 位") else append("，本地已是最新")
        }
    }

    /** 上传：本地标记并入记事本（同样只增不删），写回后本地也补齐远端有而本地没有的。 */
    fun pushToPolish() = launchSync { cookie ->
        val local = dao.all().map { it.toRecord() }
        val note = webSessionService.polishSettingsNote(cookie).getOrThrow()
        val remote = note?.memberTags?.toRecords().orEmpty()
        if (local.isEmpty() && remote.isEmpty()) syncFailure("本地还没有任何用户标记，先给用户加个标记再上传")
        val merged = mergeMemberTags(remote, local)
        if (note != null && merged.sameAs(remote)) {
            applyMerged(local, merged)
            return@launchSync "V2EX Polish 已经有全部标记，没有需要上传的内容"
        }
        val polishTags = merged.toPolishMap()
        val content = PolishSettingsNoteParser.buildContent(
            root = note?.root,
            memberTags = polishTags,
            version = (note?.syncVersion ?: 0) + 1,
            nowMillis = System.currentTimeMillis(),
        )
        webSessionService.writePolishSettingsNote(cookie, note?.noteId, content).getOrThrow()
        applyMerged(local, merged)
        buildString {
            append(if (note == null) "已新建备份记事本并上传 " else "已上传 ")
            append("${merged.size} 位用户的标记，浏览器插件会在下次检查时自动同步")
            val bytes = PolishSettingsNoteParser.memberTagsSyncBytes(polishTags)
            if (bytes > PolishSettingsNoteParser.SYNC_ITEM_QUOTA_BYTES) {
                append("。注意：标记数据约 ${bytes / 1024} KB，超过了插件 8 KB 的存储上限，浏览器端可能无法完整保存")
            }
        }
    }

    fun consumeMessage() = _syncState.update { it.copy(message = null) }

    private fun launchSync(block: suspend (cookieHeader: String) -> String) {
        if (syncMutex.isLocked) return
        scope.launch {
            syncMutex.withLock {
                _syncState.update { it.copy(isSyncing = true, message = null) }
                val message = try {
                    val cookie = secureStore.sessionCookieHeader
                        ?.takeIf { secureStore.isWebSessionActive && it.isNotBlank() }
                        ?: syncFailure("同步需要网页登录，请先到「我 → 账号」完成登录")
                    block(cookie)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    error.message?.takeIf(String::isNotBlank) ?: "同步失败，请检查网络后重试"
                }
                _syncState.update { MemberTagSyncState(isSyncing = false, message = message) }
            }
        }
    }

    /** 只写有变化的行，返回改动条数；没变的行不动 updatedAt，管理页的排序才不会被同步打乱。 */
    private suspend fun applyMerged(local: List<MemberTagRecord>, merged: List<MemberTagRecord>): Int {
        val localByKey = local.associateBy { it.username.key() }
        val now = System.currentTimeMillis()
        val changed = merged.filter { record ->
            val before = localByKey[record.username.key()]
            before == null || before.tags != record.tags || (before.avatarUrl == null && record.avatarUrl != null)
        }
        if (changed.isNotEmpty()) dao.upsertAll(changed.map { it.toEntity(now) })
        return changed.size
    }

    private fun List<MemberTagRecord>.sameAs(other: List<MemberTagRecord>): Boolean {
        if (size != other.size) return false
        val otherByKey = other.associateBy { it.username.key() }
        return all { record -> otherByKey[record.username.key()]?.tags == record.tags }
    }

    private fun Map<String, PolishMemberTag>.toRecords(): List<MemberTagRecord> =
        map { (username, entry) -> MemberTagRecord(username, entry.tags, entry.avatar) }

    private fun List<MemberTagRecord>.toPolishMap(): Map<String, PolishMemberTag> =
        associate { it.username to PolishMemberTag(it.tags, it.avatarUrl) }

    private fun syncFailure(message: String): Nothing = throw IllegalStateException(message)

    private fun String.key(): String = trim().lowercase(Locale.ROOT)

    private fun MemberTagRecord.toEntity(updatedAt: Long) = MemberTagEntity(
        usernameKey = username.key(),
        username = username,
        tagsJson = memberTagJson.encodeToString(TAGS_SERIALIZER, tags),
        avatarUrl = avatarUrl,
        updatedAt = updatedAt,
    )
}

private val memberTagJson = Json { ignoreUnknownKeys = true }
private val TAGS_SERIALIZER = ListSerializer(String.serializer())

/** 坏掉的 JSON 当作没有标签，而不是让整张表读不出来。 */
private fun MemberTagEntity.toRecord() = MemberTagRecord(
    username = username,
    tags = runCatching { memberTagJson.decodeFromString(TAGS_SERIALIZER, tagsJson) }
        .getOrDefault(emptyList())
        .let(::normalizeMemberTags),
    avatarUrl = avatarUrl,
)
