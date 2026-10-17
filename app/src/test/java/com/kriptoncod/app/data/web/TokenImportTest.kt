package com.kriptoncod.app.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenImportTest {
    @Test fun parsesJsonWithSurroundingNoise() {
        val m = TokenImport.parse("""kopyalandı: {"access_token":"a.b.c","refresh_token":"x.y.z","empty":""} tamam""")!!
        assertEquals("a.b.c", m["access_token"])
        assertEquals("x.y.z", m["refresh_token"])
        assertEquals(2, m.size)
    }

    @Test fun rejectsGarbageAndEmpty() {
        assertNull(TokenImport.parse("sadece bir metin"))
        assertNull(TokenImport.parse("{}"))
        assertNull(TokenImport.parse("{bozuk"))
    }

    @Test fun bookmarkletStartsWithJavascriptScheme() {
        assertTrue(TokenImport.BOOKMARKLET.startsWith("javascript:"))
    }
}
