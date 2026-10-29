package com.kriptoncod.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLayoutTest {
    @Test fun pagingSplitsIntoGroupsOfFour() {
        assertEquals(1, pageCount(0))
        assertEquals(1, pageCount(4))
        assertEquals(2, pageCount(5))
        assertEquals(3, pageCount(9))
        val items = (1..9).toList()
        assertEquals(listOf(1, 2, 3, 4), pageItems(items, 0))
        assertEquals(listOf(5, 6, 7, 8), pageItems(items, 1))
        assertEquals(listOf(9), pageItems(items, 2))
        // Sınır dışı sayfa en yakın geçerli sayfaya çekilir.
        assertEquals(listOf(9), pageItems(items, 99))
        assertEquals(listOf(1, 2, 3, 4), pageItems(items, -3))
    }

    @Test fun clampPageAfterClosing() {
        assertEquals(1, clampPage(2, total = 5))
        assertEquals(0, clampPage(1, total = 4))
        assertEquals(0, clampPage(0, total = 0))
    }

    @Test fun singlePaneFillsEverything() {
        val r = layoutPane(1, 1000f, 600f)
        assertEquals(listOf(PaneBox(0f, 0f, 1000f, 600f)), r)
    }

    @Test fun twoPanesSideBySideInLandscapeAndStackedInPortrait() {
        val land = layoutPane(2, 1000f, 600f, gap = 0f)
        assertEquals(PaneBox(0f, 0f, 500f, 600f), land[0])
        assertEquals(PaneBox(500f, 0f, 500f, 600f), land[1])
        val port = layoutPane(2, 600f, 1000f, gap = 0f)
        assertEquals(PaneBox(0f, 0f, 600f, 500f), port[0])
        assertEquals(PaneBox(0f, 500f, 600f, 500f), port[1])
    }

    @Test fun threePanesBigLeftTwoRight() {
        val r = layoutPane(3, 1000f, 600f)
        assertEquals(PaneBox(0f, 0f, 500f, 600f), r[0])
        assertEquals(PaneBox(500f, 0f, 500f, 300f), r[1])
        assertEquals(PaneBox(500f, 300f, 500f, 300f), r[2])
    }

    @Test fun fourPanesGrid() {
        val r = layoutPane(4, 1000f, 600f)
        assertEquals(PaneBox(0f, 0f, 500f, 300f), r[0])
        assertEquals(PaneBox(0f, 300f, 500f, 300f), r[1])
        assertEquals(PaneBox(500f, 0f, 500f, 300f), r[2])
        assertEquals(PaneBox(500f, 300f, 500f, 300f), r[3])
    }

    @Test fun panesNeverOverlapNorLeaveTheArea() {
        for (n in 1..4) {
            for ((w, h) in listOf(1280f to 800f, 800f to 1280f)) {
                val gap = 8f
                val r = layoutPane(n, w, h, gap = gap, focus = n - 1, focusBoost = 1.5f)
                r.forEach {
                    assertTrue(it.x >= -0.01f && it.y >= -0.01f)
                    assertTrue(it.x + it.w <= w + 0.01f && it.y + it.h <= h + 0.01f)
                    assertTrue(it.w > 0f && it.h > 0f)
                }
                for (i in r.indices) for (j in i + 1 until r.size) {
                    val a = r[i]
                    val b = r[j]
                    val overlap = a.x < b.x + b.w - 0.01f && b.x < a.x + a.w - 0.01f && a.y < b.y + b.h - 0.01f && b.y < a.y + a.h - 0.01f
                    assertTrue("n=$n i=$i j=$j overlap", !overlap)
                }
            }
        }
    }

    @Test fun focusGrowsOnlyTheFocusedPane() {
        val plain = layoutPane(4, 1000f, 600f)
        val boosted = layoutPane(4, 1000f, 600f, focus = 0, focusBoost = 1.5f)
        assertTrue(boosted[0].w > plain[0].w)
        assertTrue(boosted[0].h > plain[0].h)
        assertTrue(boosted[2].w < plain[2].w) // karşı sütun daralır
        assertTrue(boosted[1].h < plain[1].h) // aynı sütundaki komşu kısalır
    }

    @Test fun resizeGrowsPaneAndShrinksNeighbour() {
        val w = List(4) { PaneWeight() }
        val out = resizePane(4, w, index = 0, dMain = 0.1f, dCross = 0.1f)
        val r = layoutPane(4, 1000f, 600f, out)
        assertEquals(600f, r[0].w, 0.5f)
        assertEquals(400f, r[2].w, 0.5f)
        assertEquals(360f, r[0].h, 0.5f)
        assertEquals(240f, r[1].h, 0.5f)
        // Toplam korunur.
        assertEquals(1000f, r[0].w + r[2].w, 0.5f)
        assertEquals(600f, r[0].h + r[1].h, 0.5f)
    }

    @Test fun resizeCannotCrushANeighbour() {
        val w = List(2) { PaneWeight() }
        val out = resizePane(2, w, index = 0, dMain = 5f, dCross = 0f)
        val r = layoutPane(2, 1000f, 600f, out)
        assertTrue(r[1].w >= 1000f * MIN_FRACTION - 1f)
        assertEquals(1000f, r[0].w + r[1].w, 0.5f)
    }

    @Test fun lastPaneShrinksItsLeftNeighbourWhenGrown() {
        val w = List(2) { PaneWeight() }
        val out = resizePane(2, w, index = 1, dMain = 0.2f, dCross = 0f)
        val r = layoutPane(2, 1000f, 600f, out)
        assertEquals(700f, r[1].w, 0.5f)
        assertEquals(300f, r[0].w, 0.5f)
    }

    @Test fun singlePaneResizeIsNoOp() {
        val w = listOf(PaneWeight())
        assertEquals(w, resizePane(1, w, 0, 0.3f, 0.3f))
    }

    // ---- Hazır düzenler: yan şerit ----

    @Test fun sidebarFourPanesBigLeftThreeStackedRight() {
        val r = layoutPane(4, 1000f, 600f, sidebar = true)
        val big = 1000f * SIDEBAR_RATIO / (SIDEBAR_RATIO + 1f)
        assertEquals(0f, r[0].x, 0.01f)
        assertEquals(big, r[0].w, 0.01f)
        assertEquals(600f, r[0].h, 0.01f)
        for (i in 1..3) {
            assertEquals(big, r[i].x, 0.01f)
            assertEquals(1000f - big, r[i].w, 0.01f)
            assertEquals(200f, r[i].h, 0.01f)
            assertEquals((i - 1) * 200f, r[i].y, 0.01f)
        }
    }

    @Test fun sidebarTwoPanesBigAndStrip() {
        val r = layoutPane(2, 1000f, 600f, sidebar = true)
        assertTrue(r[0].w > r[1].w * 2f)
        assertEquals(1000f, r[0].w + r[1].w, 0.01f)
    }

    @Test fun sidebarIsOffByDefaultAndKeepsGrid() {
        assertEquals(layoutPane(4, 1000f, 600f), layoutPane(4, 1000f, 600f, sidebar = false))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), slotGroups(4))
        assertEquals(listOf(listOf(0), listOf(1, 2, 3)), slotGroups(4, sidebar = true))
        assertEquals(slotGroups(3), slotGroups(3, sidebar = true))
    }

    @Test fun sidebarPortraitStacksBigOnTop() {
        val r = layoutPane(3, 600f, 1000f, sidebar = true)
        assertEquals(0f, r[0].y, 0.01f)
        assertEquals(600f, r[0].w, 0.01f)
        assertTrue(r[0].h > r[1].h)
        assertEquals(1000f, r[0].h + r[1].h, 0.01f)
    }

    @Test fun sidebarResizeKeepsNaturalRatioUntilDragged() {
        val w = List(4) { PaneWeight() }
        // Sürüklenmeden önceki oran korunur: sıfır hareket düzeni değiştirmez.
        val same = resizePane(4, w, 0, 0f, 0f, sidebar = true)
        val before = layoutPane(4, 1000f, 600f, w, sidebar = true)
        val after = layoutPane(4, 1000f, 600f, same, sidebar = true)
        assertEquals(before[0].w, after[0].w, 0.01f)
        // Büyük pencere %5 daha geniş olur, şerit aynı oranda daralır.
        val out = resizePane(4, w, 0, 0.05f, 0f, sidebar = true)
        val r = layoutPane(4, 1000f, 600f, out, sidebar = true)
        assertEquals(1000f * (SIDEBAR_RATIO / (SIDEBAR_RATIO + 1f) + 0.05f), r[0].w, 0.5f)
        assertEquals(1000f, r[0].w + r[1].w, 0.5f)
    }

    @Test fun sidebarResizeCannotCrushTheStrip() {
        val out = resizePane(4, List(4) { PaneWeight() }, 0, 5f, 0f, sidebar = true)
        val r = layoutPane(4, 1000f, 600f, out, sidebar = true)
        assertTrue(r[1].w >= 1000f * MIN_FRACTION - 1f)
    }

    @Test fun presetKeysRoundTrip() {
        LayoutPreset.entries.forEach { assertEquals(it, LayoutPreset.fromKey(it.key)) }
        assertEquals(LayoutPreset.EQUAL, LayoutPreset.fromKey("nonsense"))
        assertEquals(LayoutPreset.EQUAL, LayoutPreset.fromKey(null))
    }

    // ---- Sabitleme ----

    private val seven = (1L..7L).toList()

    @Test fun pinnedPaneShownOnEveryPageOthersPaged() {
        assertEquals(listOf(3L, 1L, 2L, 4L), pagedItems(seven, 3L, 0))
        assertEquals(listOf(3L, 5L, 6L, 7L), pagedItems(seven, 3L, 1))
        assertEquals(2, pagesWithPin(seven, 3L))
        // 9 AI + sabit: kalan 8 üçerli → 3 sayfa.
        assertEquals(3, pagesWithPin((1L..9L).toList(), 1L))
    }

    @Test fun pinnedPaneRepeatsOnTheLastPage() {
        val five = (1L..5L).toList()
        assertEquals(listOf(1L, 2L, 3L, 4L), pagedItems(five, 1L, 0))
        assertEquals(listOf(1L, 5L), pagedItems(five, 1L, 1))
        // Sınır dışı sayfa en yakın geçerli sayfaya çekilir.
        assertEquals(listOf(1L, 5L), pagedItems(five, 1L, 9))
    }

    @Test fun pinIgnoredWhenNotOpenOrOnePerPage() {
        assertEquals(pageItems(seven, 0), pagedItems(seven, 99L, 0))
        assertEquals(pageCount(7), pagesWithPin(seven, 99L))
        assertEquals(listOf(2L), pagedItems(seven, 1L, 1, perPage = 1))
        assertEquals(7, pagesWithPin(seven, 1L, perPage = 1))
    }

    @Test fun lonePinnedPaneStillShowsOnePage() {
        assertEquals(1, pagesWithPin(listOf(5L), 5L))
        assertEquals(listOf(5L), pagedItems(listOf(5L), 5L, 0))
    }

    @Test fun pageOfItemAccountsForPin() {
        assertEquals(1, pageOfItem(seven, 3L, 5L))
        assertEquals(0, pageOfItem(seven, 3L, 2L))
        assertEquals(null, pageOfItem(seven, 3L, 3L)) // sabit: her sayfada
        assertEquals(null, pageOfItem(seven, 3L, 42L)) // açık değil
        assertEquals(1, pageOfItem(seven, null, 5L)) // sabit yok: 5. AI ikinci sayfada
    }

    // ---- Yer değiştirme ----

    @Test fun swapItemsExchangesTwoEntries() {
        assertEquals(listOf(3L, 2L, 1L), swapItems(listOf(1L, 2L, 3L), 1L, 3L))
        assertEquals(listOf(1L, 2L, 3L), swapItems(listOf(1L, 2L, 3L), 1L, 1L))
        assertEquals(listOf(1L, 2L, 3L), swapItems(listOf(1L, 2L, 3L), 1L, 9L))
    }

    @Test fun paneAtFindsThePaneUnderThePoint() {
        val boxes = layoutPane(2, 1000f, 600f)
        assertEquals(0, paneAt(boxes, 100f, 100f))
        assertEquals(1, paneAt(boxes, 700f, 500f))
        assertEquals(1, paneAt(boxes, 500f, 0f))
        assertEquals(null, paneAt(boxes, -5f, 10f))
        assertEquals(null, paneAt(boxes, 1000f, 10f))
        assertEquals(null, paneAt(emptyList(), 1f, 1f))
    }
}
