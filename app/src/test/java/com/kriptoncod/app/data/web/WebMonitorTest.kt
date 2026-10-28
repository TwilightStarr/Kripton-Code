package com.kriptoncod.app.data.web

import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMonitorTest {

    private class FakeHandle : WebMonitorHandle {
        override fun mount(host: ViewGroup) = Unit
        override fun unmount(host: ViewGroup) = Unit
        override fun currentUrl(): String? = "https://claude.ai/new"
    }

    private var clock = 1_000L
    private fun monitor(keepClosed: Int = 8) = WebMonitor(now = { clock }, keepClosed = keepClosed)
    private fun WebMonitor.first() = entries.value.first()
    private fun WebMonitor.open1(account: Long = 1L) = open(account, "hesap$account", WebProvider.CLAUDE, FakeHandle())

    @Test
    fun open_registers_a_live_entry_and_ready_marks_it_idle() {
        val m = monitor()
        val id = m.open1()
        assertEquals(WebPhase.OPENING, m.first().phase)
        assertTrue(m.first().isLive)
        assertNotNull(m.handle(id))
        m.ready(id)
        assertEquals(WebPhase.READY, m.first().phase)
    }

    @Test
    fun prompt_sent_records_the_turn_and_caps_long_prompts() {
        val m = monitor()
        val id = m.open1()
        val long = "x".repeat(WebMonitor.PROMPT_CAP + 500)
        m.promptSent(id, long)
        val turn = m.first().lastTurn!!
        assertEquals(WebPhase.SENDING, m.first().phase)
        assertEquals(1, turn.number)
        assertEquals(long.length, turn.promptChars)
        assertEquals(WebMonitor.PROMPT_CAP, turn.prompt.length)
        assertFalse(turn.isDone)
        assertTrue(m.first().awaitingReply)
    }

    @Test
    fun live_probe_moves_to_generating_and_tracks_growth() {
        val m = monitor()
        val id = m.open1()
        m.promptSent(id, "merhaba")
        m.live(id, generating = false, chars = 0, tail = "")
        assertEquals(WebPhase.SENDING, m.first().phase)

        clock = 5_000L
        m.live(id, generating = true, chars = 0, tail = "")
        assertEquals(WebPhase.GENERATING, m.first().phase)

        clock = 6_000L
        m.live(id, generating = true, chars = 120, tail = "abc")
        assertEquals(120, m.first().liveChars)
        assertEquals("abc", m.first().liveTail)
        assertEquals(6_000L, m.first().lastGrowthAtMs)

        // Aynı uzunluk: büyüme zamanı değişmez.
        clock = 9_000L
        m.live(id, generating = true, chars = 120, tail = "abc")
        assertEquals(6_000L, m.first().lastGrowthAtMs)
        assertEquals(3_000L, m.first().idleMs(9_000L))
    }

    @Test
    fun stalled_only_after_the_threshold_while_waiting() {
        val m = monitor()
        val id = m.open1()
        m.promptSent(id, "p")
        val e = m.first()
        assertFalse(e.looksStalled(e.lastGrowthAtMs + WebMonitorEntry.STALL_MS - 1))
        assertTrue(e.looksStalled(e.lastGrowthAtMs + WebMonitorEntry.STALL_MS))
        m.replied(id, WebReply(WebReply.Status.OK, text = "bitti"))
        assertFalse(m.first().looksStalled(Long.MAX_VALUE / 2))
    }

    @Test
    fun live_is_ignored_when_no_reply_is_pending() {
        val m = monitor()
        val id = m.open1()
        m.ready(id)
        m.live(id, generating = true, chars = 50, tail = "x")
        assertEquals(WebPhase.READY, m.first().phase)
        assertEquals(0, m.first().liveChars)
    }

    @Test
    fun ok_reply_completes_the_turn_with_a_preview() {
        val m = monitor()
        val id = m.open1()
        m.promptSent(id, "yaz")
        clock = 3_000L
        m.replied(id, WebReply(WebReply.Status.OK, text = "a".repeat(2_000), fileUrls = listOf("u1", "u2")))
        val e = m.first()
        val t = e.lastTurn!!
        assertEquals(WebPhase.REPLIED, e.phase)
        assertTrue(t.isDone)
        assertEquals(2_000, t.replyChars)
        assertEquals(2, t.fileCount)
        assertEquals("OK", t.status)
        assertTrue(t.replyPreview!!.length < 2_000)
        assertEquals(3_000L, t.doneAtMs)
    }

    @Test
    fun problem_reply_is_flagged_with_status_and_reason() {
        val m = monitor()
        val id = m.open1()
        m.promptSent(id, "yaz")
        m.replied(id, WebReply(WebReply.Status.ERROR, reason = "no_input"))
        assertEquals(WebPhase.PROBLEM, m.first().phase)
        assertEquals("ERROR:no_input", m.first().lastTurn!!.status)
        assertNull(m.first().lastTurn!!.replyPreview)
    }

    @Test
    fun peek_turn_is_marked_and_has_no_prompt_text() {
        val m = monitor()
        val id = m.open1()
        m.promptSent(id, "", peek = true)
        val t = m.first().lastTurn!!
        assertTrue(t.peek)
        assertEquals(0, t.promptChars)
        assertEquals(WebPhase.GENERATING, m.first().phase)
    }

    @Test
    fun turn_history_is_capped_and_numbers_keep_counting() {
        val m = monitor()
        val id = m.open1()
        repeat(WebMonitor.TURN_CAP + 3) { i ->
            m.promptSent(id, "p$i")
            m.replied(id, WebReply(WebReply.Status.OK, text = "r$i"))
        }
        val turns = m.first().turns
        assertEquals(WebMonitor.TURN_CAP, turns.size)
        assertEquals(WebMonitor.TURN_CAP + 3, turns.last().number)
        assertEquals("p${WebMonitor.TURN_CAP + 2}", turns.last().prompt)
    }

    @Test
    fun close_drops_the_handle_but_keeps_the_record() {
        val m = monitor()
        val id = m.open1()
        m.promptSent(id, "p")
        clock = 4_000L
        m.close(id)
        val e = m.first()
        assertEquals(WebPhase.CLOSED, e.phase)
        assertFalse(e.isLive)
        assertEquals(4_000L, e.closedAtMs)
        assertNull(m.handle(id))
        assertEquals("p", e.lastTurn!!.prompt)
        // Kapalı kayıt artık değişmez.
        m.live(id, true, 10, "x")
        m.replied(id, WebReply(WebReply.Status.OK, text = "geç"))
        assertEquals(WebPhase.CLOSED, m.first().phase)
    }

    @Test
    fun live_entries_come_first_and_old_closed_ones_are_pruned() {
        val m = monitor(keepClosed = 2)
        val ids = (1L..4L).map { m.open1(it) }
        ids.forEach { m.close(it) }
        val open = m.open1(9)
        val list = m.entries.value
        assertEquals(open, list.first().id)
        assertTrue(list.first().isLive)
        assertEquals(2, list.count { !it.isLive })
        // En yeni kapananlar kalır.
        assertEquals(listOf(ids[3], ids[2]), list.filter { !it.isLive }.map { it.id })
        m.clearClosed()
        assertEquals(listOf(open), m.entries.value.map { it.id })
    }
}
