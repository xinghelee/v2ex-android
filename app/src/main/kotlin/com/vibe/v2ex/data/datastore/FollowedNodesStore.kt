package com.vibe.v2ex.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.followedNodesDataStore by preferencesDataStore(name = "v2ex_followed_nodes")

/**
 * Ordered list of followed node names — purely local, mirroring the iOS `FollowedNodesStore`
 * (V2EX has no push endpoint for node follows). Seeded with the same defaults as iOS on
 * first read; an absent key means "never touched" while an empty string means "user removed all".
 */
@Singleton
class FollowedNodesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val NAMES = stringPreferencesKey("followed_node_names")

        /**
         * 本地取消关注过的节点（含删掉的默认种子）。远端同步时排除这些，
         * 否则 app 里删掉的下一次自动同步又会被拉回来（mirrors iOS removedFromSync）。
         */
        val REMOVED_FROM_SYNC = stringPreferencesKey("followed_nodes_removed_from_sync")
    }

    val names: Flow<List<String>> = context.followedNodesDataStore.data.map { prefs ->
        decode(prefs[Keys.NAMES])
    }

    suspend fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        context.followedNodesDataStore.edit { prefs ->
            val current = decode(prefs[Keys.NAMES])
            if (trimmed !in current) prefs[Keys.NAMES] = encode(current + trimmed)
        }
    }

    suspend fun remove(name: String) {
        context.followedNodesDataStore.edit { prefs ->
            prefs[Keys.NAMES] = encode(decode(prefs[Keys.NAMES]) - name)
            prefs[Keys.REMOVED_FROM_SYNC] = encode((decode(prefs[Keys.REMOVED_FROM_SYNC]) + name).distinct())
        }
    }

    suspend fun toggle(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        context.followedNodesDataStore.edit { prefs ->
            val current = decode(prefs[Keys.NAMES])
            if (trimmed in current) {
                prefs[Keys.NAMES] = encode(current - trimmed)
                prefs[Keys.REMOVED_FROM_SYNC] = encode((decode(prefs[Keys.REMOVED_FROM_SYNC]) + trimmed).distinct())
            } else {
                prefs[Keys.NAMES] = encode(current + trimmed)
                prefs[Keys.REMOVED_FROM_SYNC] = encode(decode(prefs[Keys.REMOVED_FROM_SYNC]) - trimmed)
            }
        }
    }

    /**
     * 合并网页「我收藏的节点」：远程（按网页顺序）在前，本地独有的保留在末尾；
     * 本地明确删除过的不再拉回。空远程列表直接跳过（抓取失败或未登录）。
     */
    suspend fun mergeFromRemote(remote: List<String>) {
        if (remote.isEmpty()) return
        context.followedNodesDataStore.edit { prefs ->
            val removed = decode(prefs[Keys.REMOVED_FROM_SYNC]).toSet()
            val incoming = remote.filterNot { it in removed }
            val local = decode(prefs[Keys.NAMES])
            prefs[Keys.NAMES] = encode((incoming + local.filterNot { it in incoming }).distinct())
        }
    }

    /** null (key never written) → seed defaults; "" (user removed everything) → empty list. */
    private fun decode(raw: String?): List<String> =
        raw?.split(SEPARATOR)?.map(String::trim)?.filter(String::isNotEmpty) ?: DEFAULT_NODES

    private fun encode(names: List<String>): String = names.joinToString(SEPARATOR)

    companion object {
        // Node names are [A-Za-z0-9_-], so a comma separator can never collide.
        private const val SEPARATOR = ","
        val DEFAULT_NODES = listOf("programmer", "create", "apple", "coffee", "autistic")
    }
}
