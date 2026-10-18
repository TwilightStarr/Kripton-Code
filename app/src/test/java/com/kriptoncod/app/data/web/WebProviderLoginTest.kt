package com.kriptoncod.app.data.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebProviderLoginTest {

    @Test
    fun claudeNeedsSessionKeyCookie() {
        val anonymous = WebSession(cookies = mapOf("https://claude.ai" to "foo=bar; lastActiveOrg=1"))
        val loggedIn = WebSession(cookies = mapOf("https://claude.ai" to "foo=bar; sessionKey=abc123"))
        assertFalse(WebProvider.CLAUDE.isLoggedIn(anonymous))
        assertTrue(WebProvider.CLAUDE.isLoggedIn(loggedIn))
    }

    @Test
    fun chatGptNeedsSessionTokenCookie() {
        val loggedIn = WebSession(cookies = mapOf("https://chatgpt.com" to "__Secure-next-auth.session-token.0=xyz"))
        assertTrue(WebProvider.CHATGPT.isLoggedIn(loggedIn))
        assertFalse(WebProvider.CHATGPT.isLoggedIn(WebSession(cookies = mapOf("https://chatgpt.com" to "oai-did=1"))))
    }

    @Test
    fun kimiNeedsAccessToken() {
        assertTrue(WebProvider.KIMI.isLoggedIn(WebSession(localStorage = mapOf("access_token" to "t"))))
        assertFalse(WebProvider.KIMI.isLoggedIn(WebSession(localStorage = mapOf("access_token" to ""))))
    }

    @Test
    fun geminiNeverAutoDetects() {
        assertFalse(WebProvider.GEMINI.isLoggedIn(WebSession(cookies = mapOf("https://google.com" to "SID=1"))))
    }

    @Test
    fun emptyValueCookieIsNotLoggedIn() {
        assertFalse(WebProvider.CLAUDE.isLoggedIn(WebSession(cookies = mapOf("https://claude.ai" to "sessionKey="))))
    }
}
