package com.vibe.v2ex.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class FollowedNodesStoreTest {
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(data.value).also { data.value = it } }
    }

    private var session = NodeFollowSession("test-cookie", "alice", 1)
    private fun store(
        update: suspend (String, String, Boolean) -> Result<Unit> = { _, _, _ -> Result.success(Unit) },
        read: suspend (String) -> Result<List<String>> = { Result.success(emptyList()) },
    ) = FollowedNodesStore(MemoryPreferences(), { session }, update, read)

    @Test fun `logged out follows remain local`() = runBlocking {
        session = NodeFollowSession(null, null, 0)
        val store = store(update = { _, _, _ -> error("Must not contact website") })
        assertEquals(FollowedNodesStore.DEFAULT_NODES, store.names.first())
        store.setFollowing("programmer", false).getOrThrow()
        store.setFollowing("kotlin", true).getOrThrow()
        assertFalse("programmer" in store.names.first())
        assertTrue("kotlin" in store.names.first())
        assertTrue(store.updatingNames.value.isEmpty())
    }

    @Test fun `waits for website confirmation and ignores repeated clicks`() = runBlocking {
        val confirmed = CompletableDeferred<Unit>()
        var requests = 0
        val store = store(update = { cookie, name, following ->
            requests++
            assertEquals("test-cookie", cookie)
            assertEquals("kotlin", name)
            assertTrue(following)
            confirmed.await()
            Result.success(Unit)
        })
        val first = async(start = CoroutineStart.UNDISPATCHED) { store.setFollowing("kotlin", true) }
        assertFalse("kotlin" in store.names.first())
        assertEquals(setOf("kotlin"), store.updatingNames.value)
        store.setFollowing("kotlin", true).getOrThrow()
        assertEquals(1, requests)
        confirmed.complete(Unit)
        first.await().getOrThrow()
        assertTrue("kotlin" in store.names.first())
        assertTrue(store.updatingNames.value.isEmpty())
    }

    @Test fun `website failures leave local follows intact and release pending state`() = runBlocking {
        var fail = true
        val store = store(update = { _, _, _ ->
            if (fail) Result.failure(IllegalStateException("Session expired")) else Result.success(Unit)
        })
        assertTrue(store.setFollowing("programmer", false).isFailure)
        assertTrue("programmer" in store.names.first())
        assertTrue(store.updatingNames.value.isEmpty())
        fail = false
        store.setFollowing("programmer", false).getOrThrow()
        assertFalse("programmer" in store.names.first())
    }

    @Test fun `account changes discard mutation result`() = runBlocking {
        val confirmed = CompletableDeferred<Unit>()
        val store = store(update = { _, _, _ -> confirmed.await(); Result.success(Unit) })
        val action = async(start = CoroutineStart.UNDISPATCHED) { store.setFollowing("programmer", false) }
        session = NodeFollowSession("other-cookie", "bob", 2)
        confirmed.complete(Unit)
        assertTrue(action.await().isFailure)
        assertTrue("programmer" in store.names.first())
        assertTrue(store.updatingNames.value.isEmpty())
    }

    @Test fun `cancelled operation cannot write local state or leave button pending`() = runBlocking {
        val store = store(update = { _, _, _ -> CompletableDeferred<Unit>().await(); Result.success(Unit) })
        val action = launch(start = CoroutineStart.UNDISPATCHED) { store.setFollowing("programmer", false) }
        action.cancelAndJoin()
        assertTrue("programmer" in store.names.first())
        assertTrue(store.updatingNames.value.isEmpty())
    }

    @Test fun `in flight import cannot overwrite a manual mutation`() = runBlocking {
        val remote = CompletableDeferred<List<String>>()
        val store = store(read = { Result.success(remote.await()) })
        val sync = launch(start = CoroutineStart.UNDISPATCHED) { store.syncFromRemote() }
        store.setFollowing("programmer", false).getOrThrow()
        remote.complete(listOf("programmer", "kotlin"))
        sync.join()
        assertFalse("programmer" in store.names.first())
        assertFalse("kotlin" in store.names.first())
    }

    @Test fun `account changes discard stale imports`() = runBlocking {
        val remote = CompletableDeferred<List<String>>()
        val store = store(read = { Result.success(remote.await()) })
        val sync = launch(start = CoroutineStart.UNDISPATCHED) { store.syncFromRemote() }
        session = NodeFollowSession("other-cookie", "bob", 2)
        remote.complete(listOf("kotlin"))
        sync.join()
        assertFalse("kotlin" in store.names.first())
    }

    @Test fun `imports are skipped during a pending change`() = runBlocking {
        val confirmed = CompletableDeferred<Unit>()
        val store = store(
            update = { _, _, _ -> confirmed.await(); Result.success(Unit) },
            read = { error("Import should be skipped") },
        )
        val action = async(start = CoroutineStart.UNDISPATCHED) { store.setFollowing("kotlin", true) }
        store.syncFromRemote()
        confirmed.complete(Unit)
        action.await().getOrThrow()
    }

    @Test fun `removed defaults stay removed until explicitly followed again`() = runBlocking {
        val store = store(read = { Result.success(listOf("apple", "programmer", "kotlin")) })
        store.syncFromRemote()
        assertEquals("apple", store.names.first().first())
        store.setFollowing("programmer", false).getOrThrow()
        store.syncFromRemote()
        assertFalse("programmer" in store.names.first())
        store.setFollowing("programmer", true).getOrThrow()
        store.syncFromRemote()
        assertEquals(listOf("apple", "programmer", "kotlin"), store.names.first().take(3))
    }

    @Test fun `invalid node names never send requests`() = runBlocking {
        val store = store(update = { _, _, _ -> error("Must not contact website") })
        for (name in listOf("", "../settings", "a/b", "a?once=1")) {
            assertTrue(store.setFollowing(name, true).isFailure)
        }
        assertEquals(FollowedNodesStore.DEFAULT_NODES, store.names.first())
    }
}
