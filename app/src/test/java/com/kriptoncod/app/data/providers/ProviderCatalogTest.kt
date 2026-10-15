package com.kriptoncod.app.data.providers

import com.kriptoncod.app.domain.AuthType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {
    private fun p(id: String) = ProviderCatalog.byId(id)!!

    @Test fun containsAllSevenProvidersWithUniqueIds() {
        assertEquals(
            listOf("gemini", "groq", "openrouter", "mistral", "cerebras", "deepseek", "custom"),
            ProviderCatalog.all.map { it.id },
        )
        assertEquals(7, ProviderCatalog.all.map { it.nameRes }.toSet().size)
        assertTrue(ProviderCatalog.all.all { it.nameRes != 0 })
        assertNull(ProviderCatalog.byId("yok"))
    }

    @Test fun fixedProvidersUseHttpsAndApiKeyAuth() {
        ProviderCatalog.all.filterNot { it.isCustom }.forEach {
            assertTrue(it.id, it.baseUrl!!.startsWith("https://"))
            assertTrue(it.id, it.keyUrl!!.startsWith("https://"))
            assertEquals(AuthType.API_KEY, it.authMethod.type)
            assertTrue(it.authMethod.isAvailable)
        }
    }

    @Test fun freeTierLabels() {
        assertTrue(p("gemini").hasFreeTier)
        assertTrue(p("groq").hasFreeTier)
        assertTrue(p("openrouter").hasFreeTier)
        assertFalse(p("deepseek").hasFreeTier)
    }

    @Test fun onlyCustomProviderNeedsBaseUrl() {
        assertEquals(listOf("custom"), ProviderCatalog.all.filter { it.isCustom }.map { it.id })
        assertNull(p("custom").keyUrl)
        assertNull(p("custom").resolve())
    }

    @Test fun endpointsAreBuiltCorrectly() {
        assertEquals("https://api.groq.com/openai/v1/models", p("groq").resolve()!!.modelsUrl.toString())
        assertEquals("https://api.groq.com/openai/v1/chat/completions", p("groq").resolve()!!.chatUrl.toString())
        assertEquals("https://api.deepseek.com/chat/completions", p("deepseek").resolve()!!.chatUrl.toString())
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/models",
            p("gemini").resolve()!!.modelsUrl.toString(),
        )
    }

    @Test fun openRouterValidatesWithKeyEndpointAndPrefersFreeModels() {
        val e = p("openrouter").resolve()!!
        assertEquals("https://openrouter.ai/api/v1/key", e.validationUrl.toString())
        assertEquals("https://openrouter.ai/api/v1/models", e.modelsUrl.toString())
        assertEquals(":free", e.freeModelMarker)
        val groq = p("groq").resolve()!!
        assertEquals(groq.modelsUrl, groq.validationUrl)
    }

    @Test fun customBaseUrlIsNormalizedAndValidated() {
        val c = p("custom")
        assertEquals(
            "https://llm.example.com/v1/chat/completions",
            c.resolve("  https://llm.example.com/v1/  ")!!.chatUrl.toString(),
        )
        assertNull(c.resolve("http://llm.example.com/v1"))
        assertNull(c.resolve("https://kullanici:sifre@llm.example.com/v1"))
        assertNull(c.resolve("https://llm.example.com/v1?key=1"))
        assertNull(c.resolve("bu bir adres değil"))
        assertNull(c.resolve(""))
    }

    @Test fun fixedProviderIgnoresCustomBaseUrl() {
        val e = p("groq").resolve("https://evil.example.com")
        assertNotNull(e)
        assertEquals("api.groq.com", e!!.baseUrl.host)
    }
}
