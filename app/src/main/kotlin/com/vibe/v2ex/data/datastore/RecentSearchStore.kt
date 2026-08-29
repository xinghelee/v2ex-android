package com.vibe.v2ex.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentSearchDataStore by preferencesDataStore(name = "v2ex_recent_searches")

/** Persistent最近搜索（大小写不敏感去重、命中移到最前、上限 12）— 之前只存内存，进程被杀就丢。 */
@Singleton
class RecentSearchStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val QUERIES = stringPreferencesKey("queries")
    }

    val queries: Flow<List<String>> = context.recentSearchDataStore.data.map { decode(it[Keys.QUERIES]) }

    suspend fun record(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        context.recentSearchDataStore.edit { prefs ->
            val rest = decode(prefs[Keys.QUERIES]).filterNot { it.equals(trimmed, ignoreCase = true) }
            prefs[Keys.QUERIES] = encode((listOf(trimmed) + rest).take(12))
        }
    }

    suspend fun remove(query: String) {
        context.recentSearchDataStore.edit { prefs ->
            prefs[Keys.QUERIES] = encode(decode(prefs[Keys.QUERIES]).filterNot { it.equals(query, ignoreCase = true) })
        }
    }

    suspend fun clear() {
        context.recentSearchDataStore.edit { it[Keys.QUERIES] = "" }
    }

    // 搜索词可以包含逗号，所以用不可见分隔符。
    private fun decode(raw: String?): List<String> =
        raw.orEmpty().split(SEPARATOR).map(String::trim).filter(String::isNotEmpty)

    private fun encode(queries: List<String>): String = queries.joinToString(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
