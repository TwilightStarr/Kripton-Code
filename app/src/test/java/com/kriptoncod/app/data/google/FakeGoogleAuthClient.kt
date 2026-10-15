package com.kriptoncod.app.data.google

import android.app.Activity

/**
 * Test için sahte [GoogleAuthClient].
 * [authorizeResult] ve [silentResult] dışarıdan kontrol edilir.
 */
class FakeGoogleAuthClient(
    var authorizeResult: GoogleAuthResult = GoogleAuthResult.Success("fake-token", Long.MAX_VALUE),
    var silentResult: GoogleAuthResult = GoogleAuthResult.Success("fake-token", Long.MAX_VALUE),
) : GoogleAuthClient {
    var authorizeCallCount = 0
    var silentRefreshCallCount = 0
    var revokeCallCount = 0

    override suspend fun authorize(activity: Activity): GoogleAuthResult {
        authorizeCallCount++
        return authorizeResult
    }

    override suspend fun silentRefresh(): GoogleAuthResult {
        silentRefreshCallCount++
        return silentResult
    }

    override suspend fun revoke() {
        revokeCallCount++
    }
}
