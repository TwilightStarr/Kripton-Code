package com.kriptoncod.app.data.providers

import com.kriptoncod.app.domain.AuthType
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /**
     * Google artık gerçek uygulama — isAvailable = true, Bearer başlığı ekler.
     * Somut uygulama [GeminiGoogleAuth]'da; bu test statik nesneyi doğrular.
     */
    @Test fun googleAuthMethodIsAvailableAndAddsBearer() {
        val google: AuthMethod = Google
        assertEquals(AuthType.GOOGLE, google.type)
        assertTrue(google.isAvailable)
        val req = google.authorize(builder(), "my-access-token").build()
        assertEquals("Bearer my-access-token", req.header("Authorization"))
    }

    @Test fun geminiProviderHasGoogleAuthMethod() {
        val gemini = ProviderCatalog.byId(ProviderCatalog.GEMINI_ID)
        assertTrue("Gemini supportsGoogleAuth olmalı", gemini?.supportsGoogleAuth == true)
        assertEquals(AuthType.GOOGLE, gemini?.authMethod?.type)
    }

    @Test fun nonGeminiProvidersDoNotSupportGoogleAuth() {
        val nonGemini = ProviderCatalog.all.filter { it.id != ProviderCatalog.GEMINI_ID && !it.isCustom }
        nonGemini.forEach { p ->
            assertFalse("${p.id} supportsGoogleAuth olmamalı", p.supportsGoogleAuth)
            assertEquals("${p.id} API_KEY kullanmalı", AuthType.API_KEY, p.authMethod.type)
        }
    }
}
