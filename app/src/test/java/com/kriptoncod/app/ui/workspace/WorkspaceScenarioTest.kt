package com.kriptoncod.app.ui.workspace

import com.kriptoncod.app.data.web.WebPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Düzen, sabitleme, yer değiştirme ve kayıt mantığının birlikte çalışmasını (özellik testleri) doğrular. */
class WorkspaceScenarioTest {
    private fun overlap(a: PaneBox, b: PaneBox) =
        a.x < b.x + b.w - 0.01f && b.x < a.x + a.w - 0.01f && a.y < b.y + b.h - 0.01f && b.y < a.y + a.h - 0.01f

    @Test fun everyPresetKeepsPanesInsideAndApart() {
        for (sidebar in listOf(false, true)) for (n in 1..4) for ((w, h) in listOf(1280f to 800f, 800f to 1280f, 600f to 600f)) {
            for (focus in listOf(null) + (0 until n).toList()) {
                val r = layoutPane(n, w, h, gap = 8f, focus = focus, focusBoost = if (focus == null) 1f else 2.4f, sidebar = sidebar)
                assertEquals(n, r.size)
                r.forEach {
                    assertTrue(it.x >= -0.01f && it.y >= -0.01f && it.w > 0f && it.h > 0f)
                    assertTrue(it.x + it.w <= w + 0.01f && it.y + it.h <= h + 0.01f)
                }
                for (i in r.indices) for (j in i + 1 until r.size) assertFalse("sidebar=$sidebar n=$n $i/$j", overlap(r[i], r[j]))
            }
        }
    }

    @Test fun sidebarBigPaneIsAlwaysTheFirstAndLargest() {
        for (n in 2..4) {
            val r = layoutPane(n, 1280f, 800f, gap = 8f, sidebar = true)
            val area = r.map { it.w * it.h }
            assertEquals("n=$n", area.max(), area[0], 0.5f)
            assertTrue(area[0] > area.drop(1).max())
        }
    }

    @Test fun sidebarStripPanesShareTheirColumnEqually() {
        val r = layoutPane(4, 1000f, 900f, gap = 0f, sidebar = true)
        assertEquals(r[1].h, r[2].h, 0.01f)
        assertEquals(r[2].h, r[3].h, 0.01f)
        assertEquals(900f, r[1].h + r[2].h + r[3].h, 0.01f)
    }

    @Test fun randomResizesNeverBreakInvariants() {
        val rnd = Random(42)
        for (sidebar in listOf(false, true)) for (n in 2..4) {
            var weights = List(n) { PaneWeight() }
            repeat(300) {
                val idx = rnd.nextInt(n)
                weights = resizePane(n, weights, idx, rnd.nextFloat() * 1.6f - 0.8f, rnd.nextFloat() * 1.6f - 0.8f, sidebar)
                val r = layoutPane(n, 1000f, 700f, weights, sidebar = sidebar)
                // Hiçbir pencere dayanılmaz küçülmez; ana eksen toplamı korunur.
                r.forEach {
                    assertTrue("sidebar=$sidebar n=$n w=${it.w}", it.w >= 1000f * MIN_FRACTION * 0.99f)
                    assertTrue("sidebar=$sidebar n=$n h=${it.h}", it.h >= 700f * MIN_FRACTION * 0.99f)
                }
                val groups = slotGroups(n, sidebar)
                assertEquals(1000f, groups.sumOf { g -> r[g.first()].w.toDouble() }.toFloat(), 0.5f)
            }
        }
    }

    @Test fun everyOpenPaneIsReachableAndPagesAreBounded() {
        for (size in 1..12) {
            val open = (1L..size).toList()
            for (pin in listOf<Long?>(null) + open) {
                val pages = pagesWithPin(open, pin, PANES_PER_PAGE)
                val seen = mutableMapOf<Long, Int>()
                for (pg in 0 until pages) {
                    val items = pagedItems(open, pin, pg)
                    assertTrue("size=$size pin=$pin page=$pg", items.size in 1..PANES_PER_PAGE)
                    assertEquals(items.distinct(), items)
                    if (pin != null) assertEquals(pin, items.first())
                    items.forEach { seen[it] = (seen[it] ?: 0) + 1 }
                }
                assertEquals("size=$size pin=$pin", open.toSet(), seen.keys)
                // Sabit olmayan her AI tam bir sayfada; sabit olan hepsinde.
                open.forEach { id ->
                    val expected = if (id == pin) pages else 1
                    assertEquals("id=$id size=$size pin=$pin", expected, seen[id])
                }
            }
        }
    }

    @Test fun pageOfItemAgreesWithPagedItems() {
        for (size in 1..12) {
            val open = (1L..size).toList()
            for (pin in listOf<Long?>(null) + open) {
                open.filter { it != pin }.forEach { id ->
                    val pg = pageOfItem(open, pin, id)!!
                    assertTrue("id=$id size=$size pin=$pin", id in pagedItems(open, pin, pg))
                }
            }
        }
    }

    @Test fun swapIsItsOwnInverseAndKeepsMembers() {
        val rnd = Random(7)
        repeat(100) {
            val list = (1L..rnd.nextInt(2, 9)).toList().shuffled(rnd)
            val a = list.random(rnd)
            val b = list.random(rnd)
            val once = swapItems(list, a, b)
            assertEquals(list.toSet(), once.toSet())
            assertEquals(list.size, once.size)
            assertEquals(list, swapItems(once, a, b))
        }
    }

    @Test fun dropOnAnotherPaneSwapsExactlyThosePositions() {
        val open = listOf(10L, 20L, 30L, 40L)
        val boxes = layoutPane(4, 1000f, 600f)
        val from = 0
        val target = paneAt(boxes, 900f, 500f)!!
        val swapped = swapItems(open, open[from], open[target])
        assertEquals(listOf(40L, 20L, 30L, 10L), swapped)
    }

    @Test fun codecRoundTripsRandomSnapshots() {
        val rnd = Random(99)
        repeat(200) {
            val open = (1..rnd.nextInt(0, 9)).map { rnd.nextLong(1, 1_000_000) }.distinct()
            val weights = open.filter { rnd.nextBoolean() }.associateWith { PaneWeight(rnd.nextFloat() + 0.05f, rnd.nextFloat() * 3f + 0.05f) }
            val snap = WorkspaceSnapshot(
                open = open,
                weights = weights,
                pinned = open.randomOrNull(rnd)?.takeIf { rnd.nextBoolean() },
                preset = LayoutPreset.entries.random(rnd),
                growOnFocus = rnd.nextBoolean(),
            )
            assertEquals(snap, WorkspaceCodec.decode(WorkspaceCodec.encode(snap)))
        }
    }

    @Test fun codecNeverThrowsOnGarbage() {
        val rnd = Random(5)
        val alphabet = "v1;=,:.-eE0123456789abcxyzNaN Infinity\n"
        repeat(500) {
            val text = (if (rnd.nextBoolean()) "v1;" else "") + String(CharArray(rnd.nextInt(0, 60)) { alphabet.random(rnd) })
            val s = WorkspaceCodec.decode(text)
            // Dönen düzen her zaman kendi içinde tutarlıdır.
            assertTrue(s.weights.keys.all { it in s.open })
            assertTrue(s.pinned == null || s.pinned in s.open)
            assertEquals(s.open.distinct(), s.open)
            assertTrue(s.weights.values.all { it.main > 0f && it.cross > 0f && it.main.isFinite() && it.cross.isFinite() })
        }
    }

    @Test fun restoredLayoutDropsDeletedAccountsAndKeepsOrder() {
        val saved = WorkspaceSnapshot(open = listOf(5L, 1L, 9L, 3L), pinned = 9L, weights = mapOf(1L to PaneWeight(2f, 1f), 9L to PaneWeight(1f, 3f)))
        val restored = WorkspaceCodec.decode(WorkspaceCodec.encode(saved)).retaining(setOf(1L, 3L, 5L))
        assertEquals(listOf(5L, 1L, 3L), restored.open)
        assertEquals(mapOf(1L to PaneWeight(2f, 1f)), restored.weights)
        assertEquals(null, restored.pinned)
    }

    @Test fun retainingEverythingIsIdentity() {
        val s = WorkspaceSnapshot(open = listOf(1L, 2L), weights = mapOf(2L to PaneWeight(1.5f, 0.5f)), pinned = 2L, preset = LayoutPreset.SIDEBAR)
        assertEquals(s, s.retaining(setOf(1L, 2L, 3L)))
    }

    @Test fun badgeLifecycleOverAReplySequence() {
        val open = (1L..6L).toList()
        // Sayfa 0 görünüyor (1-4); 5. AI (ikinci sayfada) yanıtı bitirir.
        val steps = listOf(WebPhase.READY, WebPhase.SENDING, WebPhase.GENERATING, WebPhase.REPLIED, WebPhase.REPLIED)
            .map { mapOf(5L to it) }
        val finished = steps.zipWithNext().map { (a, b) -> finishedAccounts(a, b) }
        assertEquals(listOf(emptySet<Long>(), emptySet(), setOf(5L), emptySet()), finished)
        assertEquals(setOf(1), badgedPages(open, null, setOf(5L), 4))
        // Sayfa 1'e geçilince AI görünür olur (rozet temizlenir).
        assertTrue(5L in pagedItems(open, null, 1))
        assertFalse(5L in pagedItems(open, null, 0))
    }
}
