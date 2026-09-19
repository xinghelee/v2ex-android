package com.vibe.v2ex.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibe.v2ex.data.remote.WebSessionService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.followedNodesDataStore by preferencesDataStore(name = "v2ex_followed_nodes")

internal data class NodeFollowSession(val cookie: String?, val username: String?, val revision: Long)

/** Logged-in changes are persisted only after the website confirms them. */
@Singleton
class FollowedNodesStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val currentSession: () -> NodeFollowSession,
    private val setRemote: suspend (String, String, Boolean) -> Result<Unit>,
    private val readRemote: suspend (String) -> Result<List<String>>,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        secureStore: SecureStore,
        webSessionService: WebSessionService,
    ) : this(
        context.followedNodesDataStore,
        {
            NodeFollowSession(
                cookie = secureStore.sessionCookieHeader.takeIf { secureStore.isWebSessionActive },
                username = secureStore.sessionUsername,
                revision = secureStore.accountCredentialsRevision.value,
            )
        },
        webSessionService::setFollowNode,
        webSessionService::favoriteNodeNames,
    )

    private object Keys {
        val NAMES = stringPreferencesKey("followed_node_names")
        val REMOVED_FROM_SYNC = stringPreferencesKey("followed_nodes_removed_from_sync")
    }

    private val mutex = Mutex()
    private var revision = 0L
    private val _updatingNames = MutableStateFlow<Set<String>>(emptySet())
    val updatingNames = _updatingNames.asStateFlow()
    val names: Flow<List<String>> = dataStore.data.map { decodeNames(it[Keys.NAMES]) }

    suspend fun setFollowing(name: String, following: Boolean): Result<Unit> {
        if (!NODE_NAME.matches(name)) return Result.failure(IllegalArgumentException("节点名称无效"))
        val session = mutex.withLock {
            if (name in _updatingNames.value) return Result.success(Unit)
            revision++
            _updatingNames.value += name
            currentSession()
        }
        try {
            session.cookie?.let { setRemote(it, name, following).getOrThrow() }
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                dataStore.edit { prefs ->
                    check(currentSession() == session) { "登录账号已更改，请重新操作" }
                    val local = decodeNames(prefs[Keys.NAMES])
                    val removed = decode(prefs[Keys.REMOVED_FROM_SYNC])
                    prefs[Keys.NAMES] = encode(if (following) (local + name).distinct() else local - name)
                    prefs[Keys.REMOVED_FROM_SYNC] = encode(
                        if (following) removed - name else (removed + name).distinct(),
                    )
                }
            }
            return Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return Result.failure(error)
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    _updatingNames.value -= name
                    revision++
                }
            }
        }
    }

    /** Ignore imports that overlap a manual change or a login/account change. */
    suspend fun syncFromRemote() {
        val (session, startedAt) = mutex.withLock {
            val session = currentSession()
            if (session.cookie == null || _updatingNames.value.isNotEmpty()) return
            session to revision
        }
        val remote = readRemote(checkNotNull(session.cookie)).getOrNull()?.takeIf { it.isNotEmpty() } ?: return
        mutex.withLock {
            if (revision != startedAt || _updatingNames.value.isNotEmpty() || currentSession() != session) return
            dataStore.edit { prefs ->
                if (currentSession() != session) return@edit
                val removed = decode(prefs[Keys.REMOVED_FROM_SYNC]).toSet()
                val incoming = remote.filter { NODE_NAME.matches(it) && it !in removed }
                prefs[Keys.NAMES] = encode((incoming + decodeNames(prefs[Keys.NAMES])).distinct())
            }
            revision++
        }
    }

    private fun decodeNames(raw: String?): List<String> = raw?.let(::decode) ?: DEFAULT_NODES
    private fun decode(raw: String?): List<String> =
        raw?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
    private fun encode(names: List<String>): String = names.joinToString(",")

    companion object {
        private val NODE_NAME = Regex("[A-Za-z0-9_-]+")
        val DEFAULT_NODES = listOf("programmer", "create", "apple", "coffee", "autistic")
    }
}
