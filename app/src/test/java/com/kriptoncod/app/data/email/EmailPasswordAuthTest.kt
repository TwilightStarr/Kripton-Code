package com.kriptoncod.app.data.email

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmailPasswordAuthTest {

    private lateinit var vault: InMemoryKeyVault
    private lateinit var tokenStore: EmailTokenStore
    private lateinit var client: FakeEmailAuthClient

    private val accountId = 42L
    private val loginUrl = "https://api.anthropic.com/v1/auth/login"

    private fun makeAuth(
        client: FakeEmailAuthClient = this.client,
        revokeUrl: String? = null,
    ) = EmailPasswordAuth(tokenStore, client, accountId, loginUrl, revokeUrl)

    @Before
    fun setUp() {
        vault = InMemoryKeyVault()
        tokenStore = EmailTokenStore(vault)
        client = FakeEmailAuthClient()
    }

    // ── Başarılı giriş ───────────────────────────────────────────────────────

    @Test
    fun `signIn success stores token and returns Valid`() = runTest {
        val auth = makeAuth()
        val result = auth.signIn("test@example.com", "secret")

        assertTrue(result is EmailAuthState.Valid)
        assertEquals(client.fakeToken, (result as EmailAuthState.Valid).sessionToken)
        assertNotNull(tokenStore.getValid(accountId))
        assertEquals(1, client.loginCallCount)
    }

    // ── Başarısız giriş ───────────────────────────────────────────────────────

    @Test
    fun `signIn failure returns Error and does not store token`() = runTest {
        client.shouldFail = true
        val auth = makeAuth()
        val result = auth.signIn("test@example.com", "wrong")

        assertTrue(result is EmailAuthState.Error)
        assertNull(tokenStore.getValid(accountId))
    }

    // ── Token yenileme ────────────────────────────────────────────────────────

    @Test
    fun `silentRefreshIfNeeded returns Valid when token still in store`() = runTest {
        // Önce oturum aç
        makeAuth().signIn("test@example.com", "secret")

        val auth = makeAuth()
        val result = auth.silentRefreshIfNeeded()
        assertTrue(result is EmailAuthState.Valid)
        // Kasa kullanıldı, client çağrılmadı
        assertEquals(0, client.refreshCallCount)
    }

    @Test
    fun `silentRefreshIfNeeded returns NeedsReauth when no token in store`() = runTest {
        // Token yok; refresh NeedsReauth dönsün
        client.shouldNeedReauth = true
        val auth = makeAuth()
        val result = auth.silentRefreshIfNeeded()
        // Kasada hiç kayıt yoksa doğrudan NeedsReauth
        assertTrue(result is EmailAuthState.NeedsReauth)
    }

    // ── Süre dolumu ────────────────────────────────────────────────────────────

    @Test
    fun `getValid returns null for expired token`() = runTest {
        // Süresi geçmiş token kaydet
        tokenStore.save(accountId, "expired-token", System.currentTimeMillis() - 1_000)
        val info = tokenStore.getValid(accountId)
        assertNull(info)
    }

    // ── İptal ────────────────────────────────────────────────────────────────

    @Test
    fun `signOut clears token and calls revoke`() = runTest {
        makeAuth().signIn("test@example.com", "secret")
        assertNotNull(tokenStore.getValid(accountId))

        val revokeUrl = "https://api.anthropic.com/v1/auth/revoke"
        makeAuth(revokeUrl = revokeUrl).signOut()

        assertNull(tokenStore.getValid(accountId))
        assertEquals(1, client.revokeCallCount)
    }

    // ── loadToken ────────────────────────────────────────────────────────────

    @Test
    fun `loadToken returns null before sign-in`() = runTest {
        val auth = makeAuth()
        assertNull(auth.loadToken())
    }

    @Test
    fun `loadToken returns token after successful sign-in`() = runTest {
        val auth = makeAuth()
        auth.signIn("test@example.com", "secret")
        assertEquals(client.fakeToken, auth.loadToken())
    }
}
