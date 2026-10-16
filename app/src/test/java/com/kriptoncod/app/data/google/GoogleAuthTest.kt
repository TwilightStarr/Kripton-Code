package com.kriptoncod.app.data.google

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoogleAuthTest {

    private lateinit var vault: InMemoryKeyVault
    private lateinit var tokenStore: GoogleTokenStore
    private lateinit var fakeClient: FakeGoogleAuthClient

    private val accountId = 42L

    private fun makeAuth(id: Long = accountId) = GeminiGoogleAuth(tokenStore, fakeClient, id)

    @Before
    fun setUp() {
        vault = InMemoryKeyVault()
        tokenStore = GoogleTokenStore(vault)
        fakeClient = FakeGoogleAuthClient()
    }

    // ── GoogleTokenStore ──────────────────────────────────────────────────────

    @Test
    fun `tokenStore returns valid token when not expired`() = runTest {
        val expiresAt = System.currentTimeMillis() + 10 * 60 * 1_000
        tokenStore.save(accountId, "tok", expiresAt)
        val info = tokenStore.getValid(accountId)
        assertNotNull(info)
        assertEquals("tok", info!!.accessToken)
    }

    @Test
    fun `tokenStore returns null when token expired`() = runTest {
        // Süresi dolmuş: geçmişte bir zaman
        val expiresAt = System.currentTimeMillis() - 1_000
        tokenStore.save(accountId, "old-tok", expiresAt)
        assertNull(tokenStore.getValid(accountId))
    }

    @Test
    fun `tokenStore returns null when buffer margin applies`() = runTest {
        // Buffer (5 dk) içinde kalıyorsa geçersiz sayılır
        val expiresAt = System.currentTimeMillis() + 2 * 60 * 1_000 // 2 dk kaldı, 5 dk buffer
        tokenStore.save(accountId, "almost-done", expiresAt)
        assertNull(tokenStore.getValid(accountId, bufferMs = 5 * 60 * 1_000))
    }

    @Test
    fun `tokenStore hasToken false when nothing saved`() = runTest {
        assertFalse(tokenStore.hasToken(accountId))
    }

    @Test
    fun `tokenStore hasToken true after save`() = runTest {
        tokenStore.save(accountId, "t", Long.MAX_VALUE)
        assertTrue(tokenStore.hasToken(accountId))
    }

    @Test
    fun `tokenStore clear removes entry`() = runTest {
        tokenStore.save(accountId, "t", Long.MAX_VALUE)
        tokenStore.clear(accountId)
        assertFalse(tokenStore.hasToken(accountId))
    }

    @Test
    fun `tokenStore isolates different accountIds`() = runTest {
        tokenStore.save(1L, "a", Long.MAX_VALUE)
        tokenStore.save(2L, "b", Long.MAX_VALUE)
        assertEquals("a", tokenStore.getValid(1L)?.accessToken)
        assertEquals("b", tokenStore.getValid(2L)?.accessToken)
        tokenStore.clear(1L)
        assertNull(tokenStore.getValid(1L))
        assertNotNull(tokenStore.getValid(2L))
    }

    // ── GeminiGoogleAuth.silentRefreshIfNeeded ────────────────────────────────

    @Test
    fun `silentRefreshIfNeeded returns Valid when cached token exists`() = runTest {
        tokenStore.save(accountId, "cached", Long.MAX_VALUE)
        val auth = makeAuth()
        val result = auth.silentRefreshIfNeeded()
        assertTrue(result is GoogleAuthState.Valid)
        assertEquals("cached", (result as GoogleAuthState.Valid).accessToken)
        // Sessiz yenileme çağrılmamalı: kasada geçerli token var
        assertEquals(0, fakeClient.silentRefreshCallCount)
    }

    @Test
    fun `silentRefreshIfNeeded delegates to client when no cached token`() = runTest {
        fakeClient.silentResult = GoogleAuthResult.Success("fresh", System.currentTimeMillis() + 10 * 60 * 1_000)
        val auth = makeAuth()
        val result = auth.silentRefreshIfNeeded()
        assertTrue(result is GoogleAuthState.Valid)
        assertEquals("fresh", (result as GoogleAuthState.Valid).accessToken)
        assertEquals(1, fakeClient.silentRefreshCallCount)
        // Yeni token kasaya yazılmalı
        assertNotNull(tokenStore.getValid(accountId))
    }

    @Test
    fun `silentRefreshIfNeeded returns NeedsReauth when client says so`() = runTest {
        fakeClient.silentResult = GoogleAuthResult.NeedsReauth
        val result = makeAuth().silentRefreshIfNeeded()
        assertTrue(result is GoogleAuthState.NeedsReauth)
        // Token kasaya yazılmamalı
        assertFalse(tokenStore.hasToken(accountId))
    }

    @Test
    fun `silentRefreshIfNeeded returns Error when client errors`() = runTest {
        fakeClient.silentResult = GoogleAuthResult.Error("net error")
        val result = makeAuth().silentRefreshIfNeeded()
        assertTrue(result is GoogleAuthState.Error)
    }

    // ── Token süresi dolunca yenileme ─────────────────────────────────────────

    @Test
    fun `expired cached token triggers silent refresh`() = runTest {
        // Süresi dolmuş token kasaya yaz
        tokenStore.save(accountId, "expired", System.currentTimeMillis() - 1_000)
        fakeClient.silentResult = GoogleAuthResult.Success("new-token", Long.MAX_VALUE)
        val auth = makeAuth()
        val result = auth.silentRefreshIfNeeded()
        assertTrue(result is GoogleAuthState.Valid)
        assertEquals("new-token", (result as GoogleAuthState.Valid).accessToken)
        assertEquals(1, fakeClient.silentRefreshCallCount)
    }

    // ── İptal (revoke) ────────────────────────────────────────────────────────

    @Test
    fun `signOut clears token and calls revoke`() = runTest {
        tokenStore.save(accountId, "tok", Long.MAX_VALUE)
        val auth = makeAuth()
        auth.signOut()
        assertFalse(tokenStore.hasToken(accountId))
        assertEquals(1, fakeClient.revokeCallCount)
    }

    @Test
    fun `signOut on account with no token still calls revoke`() = runTest {
        val auth = makeAuth()
        auth.signOut()
        assertEquals(1, fakeClient.revokeCallCount)
    }

    // ── loadToken ─────────────────────────────────────────────────────────────

    @Test
    fun `loadToken returns null when no token`() = runTest {
        assertNull(makeAuth().loadToken())
    }

    @Test
    fun `loadToken returns token when valid`() = runTest {
        tokenStore.save(accountId, "t", Long.MAX_VALUE)
        assertEquals("t", makeAuth().loadToken())
    }

    @Test
    fun `loadToken returns null for expired token`() = runTest {
        tokenStore.save(accountId, "t", System.currentTimeMillis() - 1)
        assertNull(makeAuth().loadToken())
    }

    // ── AuthMethod ────────────────────────────────────────────────────────────

    @Test
    fun `GeminiGoogleAuth is available and type is GOOGLE`() {
        val auth = makeAuth()
        assertTrue(auth.isAvailable)
        assertEquals(com.kriptoncod.app.domain.AuthType.GOOGLE, auth.type)
    }

    @Test
    fun `authorize adds Bearer header`() {
        val auth = makeAuth()
        val request = okhttp3.Request.Builder().url("https://api.example.com/")
        val built = auth.authorize(request, "access-token-xyz").build()
        assertEquals("Bearer access-token-xyz", built.header("Authorization"))
    }

    // ── Google AuthMethod (static) ─────────────────────────────────────────────

    @Test
    fun `Google authMethod authorize adds bearer header`() {
        val google = com.kriptoncod.app.data.providers.Google
        val req = google.authorize(
            okhttp3.Request.Builder().url("https://example.com/"),
            "my-token",
        ).build()
        assertEquals("Bearer my-token", req.header("Authorization"))
        assertTrue(google.isAvailable)
        assertEquals(com.kriptoncod.app.domain.AuthType.GOOGLE, google.type)
    }
}
