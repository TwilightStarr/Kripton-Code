package com.kriptoncod.app.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLiveProbeTest {

    @Test
    fun parses_plain_json() {
        val p = WebLiveProbe.parse("""{"g":true,"n":3,"c":1234,"t":"son satır"}""")!!
        assertTrue(p.generating)
        assertEquals(3, p.messages)
        assertEquals(1234, p.chars)
        assertEquals("son satır", p.tail)
    }

    @Test
    fun parses_json_quoted_by_evaluateJavascript() {
        val raw = "\"{\\\"g\\\":false,\\\"n\\\":1,\\\"c\\\":10,\\\"t\\\":\\\"x\\\"}\""
        val p = WebLiveProbe.parse(raw)!!
        assertFalse(p.generating)
        assertEquals(10, p.chars)
        assertEquals("x", p.tail)
    }

    @Test
    fun missing_fields_default_to_empty() {
        val p = WebLiveProbe.parse("{}")!!
        assertFalse(p.generating)
        assertEquals(0, p.chars)
        assertEquals("", p.tail)
    }

    @Test
    fun garbage_and_empty_return_null() {
        assertNull(WebLiveProbe.parse(null))
        assertNull(WebLiveProbe.parse(""))
        assertNull(WebLiveProbe.parse("null"))
        assertNull(WebLiveProbe.parse("bozuk {"))
    }

    @Test
    fun script_embeds_the_stop_and_assistant_selectors() {
        val sel = WebSelectors.defaults(WebProvider.CLAUDE)
        val js = WebLiveProbe.script(sel)
        assertNotNull(js)
        assertTrue(js.contains("assistant-message"))
        assertTrue(js.contains("Stop response"))
        // Yalnız okur: sohbet durumuna yazan hiçbir şey içermemeli.
        assertFalse(js.contains("__kr"))
        assertFalse(js.contains(".click("))
    }
}
