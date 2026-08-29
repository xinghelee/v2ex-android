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
        }
    }

    suspend fun toggle(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        context.followedNodesDataStore.edit { prefs ->
            val current = decode(prefs[Keys.NAMES])
            val next = if (trimmed in current) current - trimmed else current + trimmed
            prefs[Keys.NAMES] = encode(next)
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
