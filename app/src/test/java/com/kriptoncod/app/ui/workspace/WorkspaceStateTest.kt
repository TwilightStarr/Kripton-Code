package com.kriptoncod.app.ui.workspace

import com.kriptoncod.app.data.web.WebPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceStateTest {
    private val sample = WorkspaceSnapshot(
        open = listOf(3L, 7L, 9L),
        weights = mapOf(3L to PaneWeight(0.5f, 2f), 7L to PaneWeight(1.25f, 0.75f)),
        pinned = 7L,
        preset = LayoutPreset.SIDEBAR,
        growOnFocus = false,
    )

    @Test fun snapshotRoundTrips() {
        assertEquals(sample, WorkspaceCodec.decode(WorkspaceCodec.encode(sample)))
    }

    @Test fun emptySnapshotRoundTrips() {
        val empty = WorkspaceSnapshot()
        assertEquals(empty, WorkspaceCodec.decode(WorkspaceCodec.encode(empty)))
    }

    @Test fun unreadableTextGivesEmptyLayout() {
        assertEquals(WorkspaceSnapshot(), WorkspaceCodec.decode(null))
        assertEquals(WorkspaceSnapshot(), WorkspaceCodec.decode(""))
        assertEquals(WorkspaceSnapshot(), WorkspaceCodec.decode("garbage"))
        assertEquals(WorkspaceSnapshot(), WorkspaceCodec.decode("v9;open=1,2"))
    }

    @Test fun badFieldsAreSkippedNotFatal() {
        val s = WorkspaceCodec.decode("v1;preset=zzz;open=1,x,2,2;w=1:a:1,2:1.5:0,2:2.0:3.0,3:1:1;pin=abc;;novalue;grow=1")
        assertEquals(listOf(1L, 2L), s.open) // bozuk ve yinelenen kimlikler elenir
        assertEquals(mapOf(2L to PaneWeight(2f, 3f)), s.weights) // geçersiz/sıfır/açık olmayan ağırlıklar düşer
        assertNull(s.pinned)
        assertEquals(LayoutPreset.EQUAL, s.preset)
        assertTrue(s.growOnFocus)
    }

    @Test fun pinOutsideOpenListIsDropped() {
        assertNull(WorkspaceCodec.decode("v1;pin=5;open=1,2").pinned)
    }

    @Test fun nonFiniteWeightsRejected() {
        val s = WorkspaceCodec.decode("v1;open=1;w=1:NaN:1")
        assertTrue(s.weights.isEmpty())
    }

    @Test fun retainingDropsDeletedAccounts() {
        val kept = sample.retaining(setOf(3L, 9L))
        assertEquals(listOf(3L, 9L), kept.open)
        assertEquals(setOf(3L), kept.weights.keys)
        assertNull(kept.pinned) // sabit pencerenin hesabı silinmiş
        assertEquals(LayoutPreset.SIDEBAR, kept.preset)
        assertFalse(kept.growOnFocus)
    }

    @Test fun finishedAccountsDetectsReplyCompletion() {
        val prev = mapOf(
            1L to WebPhase.GENERATING,
            2L to WebPhase.READY,
            3L to WebPhase.SENDING,
            5L to WebPhase.GENERATING,
        )
        val now = mapOf(
            1L to WebPhase.REPLIED,
            2L to WebPhase.REPLIED, // hiç üretmemişti: işaret yok
            3L to WebPhase.PROBLEM, // sorunla bitti: yine bildirilir
            4L to WebPhase.REPLIED, // önceki aşaması yok
            5L to WebPhase.GENERATING, // sürüyor
        )
        assertEquals(setOf(1L, 3L), finishedAccounts(prev, now))
        assertTrue(finishedAccounts(now, now).isEmpty())
    }

    @Test fun badgesPointAtPagesNotCurrentlyVisible() {
        val open = (1L..6L).toList()
        // Sabit yok: 5. AI ikinci sayfada.
        assertEquals(setOf(1), badgedPages(open, null, setOf(5L), 4))
        // Sabit 1: kalan [2..6] üçerli → 5 ikinci sayfada.
        assertEquals(setOf(1), badgedPages(open, 1L, setOf(5L), 4))
        // Sabit pencere her sayfada göründüğü için rozet çıkmaz.
        assertTrue(badgedPages(open, 1L, setOf(1L), 4).isEmpty())
        // Açık olmayan AI'nın rozeti yok.
        assertTrue(badgedPages(open, null, setOf(99L), 4).isEmpty())
        assertEquals(setOf(0, 1), badgedPages(open, 1L, setOf(2L, 6L), 4))
    }
}
