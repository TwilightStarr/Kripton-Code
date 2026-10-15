package com.kriptoncod.app.data.providers

import com.kriptoncod.app.domain.AuthType
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthMethodTest {
    private fun builder() = Request.Builder().url("https://example.com/")

    @Test fun apiKeyDefaultsToBearerHeader() {
        val r = ApiKey().authorize(builder(), "abc123").build()
        assertEquals("Bearer abc123", r.header("Authorization"))
        assertEquals(AuthType.API_KEY, ApiKey().type)
        assertTrue(ApiKey().isAvailable)
    }

    @Test fun apiKeyCanUseCustomHeaderWithoutScheme() {
        val r = ApiKey(headerName = "x-api-key", scheme = null).authorize(builder(), "k").build()
        assertEquals("k", r.header("x-api-key"))
        assertEquals(null, r.header("Authorization"))
    }

    @Test fun toStringDoesNotContainCredential() {
        ApiKey().authorize(builder(), "SECRET-VALUE")
        assertFalse(ApiKey().toString().contains("SECRET-VALUE"))
    }

    @Test fun googleIsInterfaceOnlyForNow() {
        val google: AuthMethod = Google
        assertEquals(AuthType.GOOGLE, google.type)
        assertFalse(google.isAvailable)
        try {
            google.authorize(builder(), "token")
            fail("UnsupportedOperationException beklenirdi")
        } catch (_: UnsupportedOperationException) {
        }
    }
}
