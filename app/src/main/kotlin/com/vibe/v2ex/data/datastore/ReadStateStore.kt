package com.vibe.v2ex.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.readStateDataStore by preferencesDataStore(name = "v2ex_read_state")

/**
 * Mirrors the iOS ReadStateStore: which topics were opened (drives 已读置灰) and,
 * separately, the last reply floor seen per topic (drives 记住阅读进度).
 * Read IDs are capped so the store stays small; positions ride along with them.
 */
@Singleton
class ReadStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val READ_IDS = stringSetPreferencesKey("read_topic_ids")
        val POSITIONS = stringPreferencesKey("reading_positions")
    }

    val readIds: Flow<Set<Long>> = context.readStateDataStore.data.map { prefs ->
        prefs[Keys.READ_IDS].orEmpty().mapNotNullTo(mutableSetOf(), String::toLongOrNull)
    }

    suspend fun markRead(topicId: Long) {
        context.readStateDataStore.edit { prefs ->
            val ids = prefs[Keys.READ_IDS].orEmpty()
            if (topicId.toString() in ids) return@edit
            // Same cap as iOS: beyond 2000 entries keep an arbitrary 1500 —
            // read-state is cosmetic, losing old entries is harmless.
            val next = ids + topicId.toString()
            prefs[Keys.READ_IDS] = if (next.size > 2_000) next.drop(next.size - 1_500).toSet() else next
        }
    }

    suspend fun position(topicId: Long): Int? =
        decodePositions(context.readStateDataStore.data.first()[Keys.POSITIONS])[topicId]

    suspend fun rememberPosition(topicId: Long, floor: Int) {
        context.readStateDataStore.edit { prefs ->
            val positions = decodePositions(prefs[Keys.POSITIONS]).toMutableMap()
            if (positions[topicId] == floor) return@edit
            positions[topicId] = floor
            prefs[Keys.POSITIONS] = positions.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    private fun decodePositions(raw: String?): Map<Long, Int> =
        raw.orEmpty().split(',').mapNotNull { pair ->
            val (id, floor) = pair.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val topicId = id.toLongOrNull() ?: return@mapNotNull null
            val floorInt = floor.toIntOrNull() ?: return@mapNotNull null
            topicId to floorInt
        }.toMap()
}
