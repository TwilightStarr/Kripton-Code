package com.kriptoncod.app.data.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivProtocolTest {
    @Test fun `entry round trip`() {
        val line = PrivProtocol.formatEntry("shared_prefs", true, 123, 99L)
        assertEquals(listOf(PrivEntry("shared_prefs", true, 0L, 99L)), PrivProtocol.parseEntries(line))
        assertEquals(listOf(PrivEntry("a b.txt", false, 5L, 7L)), PrivProtocol.parseEntries(PrivProtocol.formatEntry("a b.txt", false, 5, 7)))
    }

    @Test fun `bozuk satirlar atlanir`() {
        assertEquals(emptyList<PrivEntry>(), PrivProtocol.parseEntries("x\ty\n\nd\t1\t2\t\nzzz"))
    }

    @Test fun `hata kodlari`() {
        assertEquals(PrivError.DENIED, PrivProtocol.parseError(PrivProtocol.error(PrivError.DENIED)))
        assertEquals(PrivError.UNKNOWN, PrivProtocol.parseError("ERR:???"))
        assertEquals(PrivError.IO, PrivProtocol.parseError(null))
        assertNull(PrivProtocol.parseError("d\t0\t1\tx"))
    }

    @Test fun `siralama klasor once, gizliler filtrelenir`() {
        val l = listOf(
            PrivEntry("b.txt", false, 1, 1),
            PrivEntry(".hid", false, 1, 1),
            PrivEntry("Zed", true, 0, 1),
            PrivEntry("alpha", true, 0, 1),
        )
        assertEquals(listOf("alpha", "Zed", "b.txt"), PrivProtocol.arrange(l, false).map { it.name })
        assertEquals(listOf("alpha", "Zed", ".hid", "b.txt"), PrivProtocol.arrange(l, true).map { it.name })
    }

    @Test fun `paket ayristirma`() {
        val text = "package:com.b.app\npackage:com.a.app\ngarbage line\ncom.a.app\nERR:IO"
        assertEquals(listOf("com.a.app", "com.b.app"), PrivProtocol.parsePackages(text))
    }

    @Test fun `uygulama veri yolu moda gore`() {
        assertEquals("/data/data/com.x.y", PrivProtocol.appDataPath("com.x.y", 0, "/storage/emulated/0"))
        assertEquals("/storage/emulated/0/Android/data/com.x.y", PrivProtocol.appDataPath("com.x.y", 2000, "/storage/emulated/0/"))
        assertEquals("/storage/emulated/0/Android/data/com.x.y", PrivProtocol.appDataPath("com.x.y", -1, "/storage/emulated/0"))
    }

    @Test fun `mod`() {
        assertEquals(PrivMode.ROOT, PrivProtocol.modeOf(0))
        assertEquals(PrivMode.ADB, PrivProtocol.modeOf(2000))
        assertEquals(PrivMode.UNKNOWN, PrivProtocol.modeOf(null))
        assertEquals(PrivMode.OTHER, PrivProtocol.modeOf(10123))
    }
}
