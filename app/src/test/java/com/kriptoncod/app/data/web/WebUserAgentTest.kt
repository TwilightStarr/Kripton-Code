package com.kriptoncod.app.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUserAgentTest {

    @Test
    fun major_is_read_from_the_real_webview_user_agent() {
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/151.0.7000.12 Mobile Safari/537.36"
        assertEquals(151, WebUserAgent.majorOf(ua))
    }

    @Test
    fun unreadable_or_ancient_versions_fall_back_to_a_modern_one() {
        assertEquals(WebUserAgent.FALLBACK_MAJOR, WebUserAgent.majorOf(null))
        assertEquals(WebUserAgent.FALLBACK_MAJOR, WebUserAgent.majorOf("Mozilla/5.0 Firefox/120.0"))
        assertEquals(WebUserAgent.FALLBACK_MAJOR, WebUserAgent.majorOf("... Chrome/90.0.0.0 ..."))
    }

    @Test
    fun desktop_user_agent_carries_the_given_version() {
        assertTrue(WebUserAgent.desktopUa(150).contains("Chrome/150.0.0.0"))
    }
}
