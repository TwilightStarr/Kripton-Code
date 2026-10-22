package com.kriptoncod.app.data.run

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveOutputTest {
    private var t = 0L
    private fun live(max: Int = 1000, interval: Long = 100) = LiveOutput(max, interval) { t }

    @Test fun firstDeltaPublishesImmediatelyThenThrottles() {
        val l = live()
        val sink = l.sinkFor(1)
        sink.append("a")
        assertEquals("a", l.current(1))
        t = 50
        sink.append("b") // aralık dolmadı: yayın yok
        assertEquals("a", l.current(1))
        t = 120
        sink.append("c")
        assertEquals("abc", l.current(1)) // birikenin hepsi birlikte yayılır
    }

    @Test fun keepsOnlyTheTailWhenOverLimit() {
        val l = live(max = 10, interval = 0)
        l.sinkFor(1).append("0123456789ABCDEF")
        assertEquals("6789ABCDEF", l.current(1))
    }

    @Test fun resetEmptiesTextAndClearRemovesIt() {
        val l = live(interval = 0)
        val sink = l.sinkFor(7)
        sink.append("abc")
        sink.reset()
        assertEquals("", l.current(7))
        sink.append("x")
        assertEquals("x", l.current(7))
        l.clear(7)
        assertNull(l.current(7))
        assertNull(runBlocking { l.observe(7).first() })
    }

    @Test fun jobsAreIndependent() {
        val l = live(interval = 0)
        l.sinkFor(1).append("bir")
        l.sinkFor(2).append("iki")
        l.clear(1)
        assertNull(l.current(1))
        assertEquals("iki", l.current(2))
    }

    @Test fun lateDeltaAfterClearIsIgnored() {
        val l = live(interval = 0)
        val sink = l.sinkFor(3)
        sink.append("a")
        l.clear(3)
        sink.append("geç")
        assertNull(l.current(3))
    }

    @Test fun failureNoteSurvivesClearUntilForgotten() {
        val l = live(interval = 0)
        l.failure(5, "Son deneme başarısız")
        l.clear(5) // yeniden deneme başlarken canlı metin temizlenir; hata sebebi kalır
        assertEquals("Son deneme başarısız", l.current(5))
        l.forget(5) // düğüm başarıyla bitti
        assertNull(l.current(5))
    }

    @Test fun streamedTextReplacesFailureNoteAndClearRestoresIt() {
        val l = live(interval = 0)
        l.failure(6, "hata")
        val sink = l.sinkFor(6)
        sink.append("yeni çıktı")
        assertEquals("yeni çıktı", l.current(6))
        l.clear(6)
        assertEquals("hata", l.current(6))
    }
}
