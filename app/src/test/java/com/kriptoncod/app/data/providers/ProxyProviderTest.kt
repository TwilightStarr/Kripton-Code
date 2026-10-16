package com.kriptoncod.app.data.providers

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.domain.AuthType
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyProviderTest {
    private fun valid(raw: String): String =
        (ProxyProvider.parseBaseUrl(raw) as ProxyUrlResult.Valid).baseUrl.toString()

    private fun invalid(raw: String): ProxyUrlError =
        (ProxyProvider.parseBaseUrl(raw) as ProxyUrlResult.Invalid).error

    // ---- taban adres normalleştirme

    @Test fun lanHttpIsAcceptedAndV1IsAppended() {
        assertEquals("http://192.168.1.20:8317/v1", valid("http://192.168.1.20:8317"))
        assertEquals("http://192.168.1.20:8317/v1", valid("  http://192.168.1.20:8317/  "))
        assertEquals("http://192.168.1.20:8317/v1", valid("http://192.168.1.20:8317/v1/"))
    }

    @Test fun pastedEndpointPathsAreStrippedBackToBase() {
        assertEquals("http://10.0.0.5:8317/v1", valid("http://10.0.0.5:8317/v1/chat/completions"))
        assertEquals("http://10.0.0.5:8317/v1", valid("http://10.0.0.5:8317/v1/models"))
        assertEquals("http://10.0.0.5:8317/api/v1", valid("http://10.0.0.5:8317/api"))
    }

    @Test fun missingSchemeDefaultsToHttpForLocalAndHttpsForPublic() {
        assertEquals("http://192.168.1.20:8317/v1", valid("192.168.1.20:8317"))
        assertEquals("http://localhost:8317/v1", valid("localhost:8317"))
        assertEquals("https://proxy.example.com/v1", valid("proxy.example.com"))
    }

    @Test fun httpsPublicHostIsAccepted() {
        assertEquals("https://proxy.example.com:8443/v1", valid("https://proxy.example.com:8443"))
    }

    @Test fun ipv6LoopbackIsLocal() {
        assertEquals("http://[::1]:8317/v1", valid("http://[::1]:8317"))
        assertEquals(ProxyUrlError.INSECURE_PUBLIC_HOST, invalid("http://[2001:db8::1]:8317"))
    }

    @Test fun plainHttpToPublicInternetIsRejected() {
        assertEquals(ProxyUrlError.INSECURE_PUBLIC_HOST, invalid("http://proxy.example.com"))
        assertEquals(ProxyUrlError.INSECURE_PUBLIC_HOST, invalid("http://8.8.8.8:8317"))
        assertEquals(ProxyUrlError.INSECURE_PUBLIC_HOST, invalid("http://172.32.0.1:8317")) // 172.16/12 dışı
    }

    @Test fun credentialsQueryAndFragmentAreRejected() {
        assertEquals(ProxyUrlError.HAS_CREDENTIALS, invalid("http://user:pw@192.168.1.5:8317"))
        assertEquals(ProxyUrlError.HAS_QUERY, invalid("https://proxy.example.com/v1?key=abc"))
        assertEquals(ProxyUrlError.HAS_QUERY, invalid("https://proxy.example.com/#x"))
    }

    @Test fun emptyAndMalformedInputs() {
        assertEquals(ProxyUrlError.EMPTY, invalid("   "))
        assertEquals(ProxyUrlError.MALFORMED, invalid("http://"))
        assertEquals(ProxyUrlError.MALFORMED, invalid("ftp://192.168.1.5"))
        assertNull(ProxyProvider.normalizeBaseUrl("ftp://192.168.1.5"))
        assertEquals("http://127.0.0.1:8317/v1", ProxyProvider.normalizeBaseUrl("127.0.0.1:8317"))
    }

    // ---- yerel/özel ağ sınıflaması

    @Test fun privateHostClassification() {
        val privateHosts = listOf(
            "localhost", "127.0.0.1", "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.0.1",
            "169.254.1.1", "100.64.0.1", "100.127.255.255", "mini-pc", "nas.local", "box.lan", "::1", "fd12:3456::1", "fe80::1",
        )
        privateHosts.forEach { assertTrue("$it özel olmalı", ProxyProvider.isPrivateHost(it)) }

        val publicHosts = listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "192.169.0.1", "proxy.example.com", "2001:db8::1")
        publicHosts.forEach { assertFalse("$it açık adres olmalı", ProxyProvider.isPrivateHost(it)) }
    }

    // ---- uç nokta

    @Test fun endpointBuildsOpenAiPathsAndBearerAuth() {
        val e = ProxyProvider.endpoint("http://192.168.1.20:8317", UpstreamProvider.CLAUDE)!!
        assertEquals("claude", e.providerId)
        assertEquals("http://192.168.1.20:8317/v1/models", e.modelsUrl.toString())
        assertEquals("http://192.168.1.20:8317/v1/chat/completions", e.chatUrl.toString())
        assertEquals(e.modelsUrl, e.validationUrl)
        val req = e.authMethod.authorize(Request.Builder().url(e.modelsUrl), "proxy-key").build()
        assertEquals("Bearer proxy-key", req.header("Authorization"))
    }

    @Test fun endpointWithoutUpstreamUsesProxyId() {
        assertEquals(ProxyProvider.ID, ProxyProvider.endpoint("http://localhost:8317")!!.providerId)
    }

    @Test fun invalidBaseUrlGivesNoEndpoint() {
        assertNull(ProxyProvider.endpoint("http://proxy.example.com"))
        assertNull(ProxyProvider.endpoint(""))
    }

    // ---- hesap → uç nokta

    private fun account(auth: AuthType, provider: String, baseUrl: String? = null) = AiAccount(
        providerId = provider, displayName = "h", authType = auth, modelId = "m", powerRank = 1, baseUrl = baseUrl,
    )

    @Test fun proxyAccountUsesItsOwnBaseUrlAndUpstreamProvider() {
        val e = AccountEndpoints.resolve(account(AuthType.PROXY, "gemini", "http://10.0.0.5:8317/v1"))!!
        assertEquals("gemini", e.providerId)
        assertEquals("http://10.0.0.5:8317/v1/chat/completions", e.chatUrl.toString())
    }

    @Test fun twoProxyAccountsOfSameProviderResolveToDifferentServers() {
        val mine = AccountEndpoints.resolve(account(AuthType.PROXY, "claude", "http://192.168.1.20:8317/v1"))!!
        val friend = AccountEndpoints.resolve(account(AuthType.PROXY, "claude", "https://friend.example.com/v1"))!!
        assertEquals(mine.providerId, friend.providerId)
        assertEquals("192.168.1.20", mine.baseUrl.host)
        assertEquals("friend.example.com", friend.baseUrl.host)
    }

    @Test fun apiKeyAccountStillUsesCatalog() {
        val e = AccountEndpoints.resolve(account(AuthType.API_KEY, "groq"))!!
        assertEquals("https://api.groq.com/openai/v1/chat/completions", e.chatUrl.toString())
    }

    @Test fun proxyAccountWithMissingOrUnsafeBaseUrlHasNoEndpoint() {
        assertNull(AccountEndpoints.resolve(account(AuthType.PROXY, "claude", null)))
        assertNull(AccountEndpoints.resolve(account(AuthType.PROXY, "claude", "http://proxy.example.com/v1")))
        assertNotNull(AccountEndpoints.resolve(account(AuthType.PROXY, "claude", "https://proxy.example.com/v1")))
    }

    // ---- sağlayıcı tespiti

    @Test fun detectsUpstreamFromModelId() {
        val expected = mapOf(
            "claude-sonnet-5-5" to UpstreamProvider.CLAUDE,
            "claude-fable-5-1" to UpstreamProvider.CLAUDE,
            "antigravity/claude-opus-5-5-thinking" to UpstreamProvider.CLAUDE,
            "gemini-3.8-flash" to UpstreamProvider.GEMINI,
            "models/gemini-4-argon" to UpstreamProvider.GEMINI,
            "gpt-6-astra" to UpstreamProvider.OPENAI,
            "o3-mini" to UpstreamProvider.OPENAI,
            "codex-mini" to UpstreamProvider.OPENAI,
            "deepseek-v4" to UpstreamProvider.DEEPSEEK,
            "kimi-k3" to UpstreamProvider.KIMI,
            "moonshot-v1-128k" to UpstreamProvider.KIMI,
            "grok-4.7" to UpstreamProvider.OTHER,
            "qwen3-coder" to UpstreamProvider.OTHER,
            "llama-3-70b" to UpstreamProvider.OTHER,
        )
        expected.forEach { (id, want) -> assertEquals(id, want, UpstreamProvider.detect(id)) }
    }

    @Test fun ownedByIsOnlyAFallbackAfterModelId() {
        assertEquals(UpstreamProvider.CLAUDE, UpstreamProvider.detect("custom-model", "anthropic"))
        assertEquals(UpstreamProvider.GEMINI, UpstreamProvider.detect("custom-model", "google"))
        assertEquals(UpstreamProvider.OTHER, UpstreamProvider.detect("glm-5.2", "zhipu"))
        assertEquals(UpstreamProvider.GEMINI, UpstreamProvider.detect("gemini-x", "anthropic")) // model kimliği kazanır
    }

    @Test fun upstreamIdsMatchProviderCatalogIds() {
        assertEquals(ProviderCatalog.CLAUDE_ID, UpstreamProvider.CLAUDE.id)
        assertEquals(ProviderCatalog.GEMINI_ID, UpstreamProvider.GEMINI.id)
        assertEquals(ProviderCatalog.OPENAI_ID, UpstreamProvider.OPENAI.id)
        assertEquals(ProviderCatalog.KIMI_ID, UpstreamProvider.KIMI.id)
        assertEquals("deepseek", UpstreamProvider.DEEPSEEK.id)
        assertEquals(UpstreamProvider.DEEPSEEK, UpstreamProvider.fromId("deepseek"))
        assertNull(UpstreamProvider.fromId("groq"))
    }
}
