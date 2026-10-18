package com.kriptoncod.app.data.email

/**
 * Test sahte istemcisi.
 * [shouldFail] true ise login/silentRefresh Error döner.
 * [shouldNeedReauth] true ise NeedsReauth döner.
 */
class FakeEmailAuthClient(
    var shouldFail: Boolean = false,
    var shouldNeedReauth: Boolean = false,
    var fakeToken: String = "fake-session-token",
    var fakeExpiresAtMs: Long = System.currentTimeMillis() + 60 * 60 * 1000L,
) : EmailAuthClient {

    var loginCallCount = 0
    var refreshCallCount = 0
    var revokeCallCount = 0

    override suspend fun login(loginUrl: String, email: String, password: String): EmailAuthResult {
        loginCallCount++
        return when {
            shouldFail -> EmailAuthResult.Error("Sahte giriş hatası")
            shouldNeedReauth -> EmailAuthResult.NeedsReauth
            else -> EmailAuthResult.Success(fakeToken, fakeExpiresAtMs)
        }
    }

    override suspend fun silentRefresh(loginUrl: String, token: String): EmailAuthResult {
        refreshCallCount++
        return when {
            shouldFail -> EmailAuthResult.Error("Sahte yenileme hatası")
            shouldNeedReauth -> EmailAuthResult.NeedsReauth
            else -> EmailAuthResult.Success(fakeToken, fakeExpiresAtMs)
        }
    }

    override suspend fun revoke(revokeUrl: String?, token: String) {
        revokeCallCount++
    }
}
