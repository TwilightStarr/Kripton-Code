package com.kriptoncod.app.data.github

import com.kriptoncod.app.data.google.InMemoryKeyVault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubTokenStoreTest {
    private val vault = InMemoryKeyVault()
    private val store = GitHubTokenStore(vault)
    private val token = "ghp_" + "a".repeat(36)

    @Test fun saveTrimsAndStoresUnderFixedKey() = runBlocking {
        store.save("  $token \n")
        assertEquals(token, store.get())
        assertEquals(listOf("github-token"), vault.listIds())
        assertTrue(store.has())
    }

    @Test fun rejectsImplausibleTokensWithoutLeakingThemInMessage() = runBlocking {
        val bad = "short token with spaces"
        val e = runCatching { store.save(bad) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
        assertFalse(e!!.message.orEmpty().contains(bad))
        assertFalse(store.has())
    }

    @Test fun clearRemovesToken() = runBlocking {
        store.save(token)
        store.clear()
        assertNull(store.get())
        assertFalse(store.has())
    }
}
