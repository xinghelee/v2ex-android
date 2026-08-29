package com.vibe.v2ex.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.seenNotificationsDataStore by preferencesDataStore(name = "v2ex_seen_notifications")

/**
 * Client-inferred notification read state — the V2EX API exposes no read/unread field
 * at all, so "seen" is whatever the user has tapped or marked locally (the Android
 * counterpart of the iOS `seenNotifications` UserDefaults set).
 */
@Singleton
class SeenNotificationsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val seenKey = stringSetPreferencesKey("seen_notification_ids")

    val seenIds: Flow<Set<Long>> = context.seenNotificationsDataStore.data.map { prefs ->
        prefs[seenKey].orEmpty().mapNotNull(String::toLongOrNull).toSet()
    }

    suspend fun markSeen(id: Long) {
        context.seenNotificationsDataStore.edit { prefs ->
            prefs[seenKey] = prefs[seenKey].orEmpty() + id.toString()
        }
    }

    suspend fun markAllSeen(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        context.seenNotificationsDataStore.edit { prefs ->
            prefs[seenKey] = prefs[seenKey].orEmpty() + ids.map(Long::toString)
        }
    }
}
