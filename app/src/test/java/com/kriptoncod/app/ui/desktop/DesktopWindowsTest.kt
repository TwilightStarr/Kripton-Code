package com.kriptoncod.app.ui.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopWindowsTest {
    private val area = DSize(1200f, 700f)

    private fun opened(vararg apps: String): DesktopState = apps.fold(DesktopState()) { s, a -> Desktop.open(s, a, area) }

    @Test fun openCreatesOneWindowPerApp() {
        var s = opened(DesktopApps.FILES, DesktopApps.ai(7))
        assertEquals(2, s.windows.size)
        // Aynı uygulama yeniden açılınca yeni pencere olmaz; öne gelir.
        s = Desktop.open(s, DesktopApps.FILES, area)
        assertEquals(2, s.windows.size)
        assertEquals(DesktopApps.FILES, s.focused?.app)
    }

    @Test fun newWindowsCascadeAndStayInsideArea() {
        val s = opened("a", "b", "c")
        val boxes = s.windows.map { it.box }
        assertTrue(boxes[1].x > boxes[0].x && boxes[2].y > boxes[1].y)
        boxes.forEach {
            assertTrue(it.x >= 0f && it.y >= 0f && it.right <= area.w && it.bottom <= area.h)
        }
    }

    @Test fun minimizeMovesFocusToNextAndRaiseRestores() {
        var s = opened("a", "b")
        val b = s.byApp("b")!!
        s = Desktop.minimize(s, b.id)
        assertEquals("a", s.focused?.app)
        s = Desktop.toggleFromTaskbar(s, b.id)
        assertEquals("b", s.focused?.app)
        assertFalse(s.byApp("b")!!.minimized)
        // Odaktaki pencerenin görev çubuğu düğmesi onu küçültür.
        s = Desktop.toggleFromTaskbar(s, b.id)
        assertTrue(s.byApp("b")!!.minimized)
        assertEquals("a", s.focused?.app)
    }

    @Test fun focusReordersOnlyWhenNeeded() {
        val s = opened("a", "b", "c")
        val a = s.byApp("a")!!
        val top = s.byApp("c")!!
        assertEquals(s, Desktop.focus(s, top.id))
        assertEquals("a", Desktop.focus(s, a.id).windows.last().app)
    }

    @Test fun maximizeToggles() {
        var s = opened("a")
        val id = s.windows.first().id
        val original = s.windows.first().box
        s = Desktop.toggleMaximize(s, id)
        assertEquals(DBox(0f, 0f, area.w, area.h), Desktop.boxOf(s.byId(id)!!, area))
        s = Desktop.toggleMaximize(s, id)
        assertEquals(original, Desktop.boxOf(s.byId(id)!!, area))
    }

    @Test fun snapModesFillHalvesAndQuarters() {
        assertEquals(DBox(0f, 0f, 600f, 700f), Desktop.modeBox(WinMode.SNAP_LEFT, area))
        assertEquals(DBox(600f, 0f, 600f, 700f), Desktop.modeBox(WinMode.SNAP_RIGHT, area))
        assertEquals(DBox(600f, 350f, 600f, 350f), Desktop.modeBox(WinMode.SNAP_BOTTOM_RIGHT, area))
        assertNull(Desktop.modeBox(WinMode.NORMAL, area))
    }

    @Test fun snapZones() {
        assertEquals(WinMode.MAXIMIZED, Desktop.snapZone(600f, 2f, area))
        assertEquals(WinMode.SNAP_LEFT, Desktop.snapZone(3f, 350f, area))
        assertEquals(WinMode.SNAP_RIGHT, Desktop.snapZone(1198f, 350f, area))
        assertEquals(WinMode.SNAP_TOP_LEFT, Desktop.snapZone(5f, 5f, area))
        assertEquals(WinMode.SNAP_TOP_RIGHT, Desktop.snapZone(1195f, 30f, area))
        assertEquals(WinMode.SNAP_BOTTOM_LEFT, Desktop.snapZone(4f, 690f, area))
        assertEquals(WinMode.SNAP_BOTTOM_RIGHT, Desktop.snapZone(1190f, 698f, area))
        assertNull(Desktop.snapZone(600f, 350f, area))
    }

    @Test fun moveKeepsTitleBarReachable() {
        var s = opened("a")
        val id = s.windows.first().id
        s = Desktop.moveBy(s, id, -99999f, -99999f, area)
        var b = s.byId(id)!!.box
        assertTrue(b.right >= Desktop.GRAB_W - 0.01f)
        assertEquals(0f, b.y, 0.01f)
        s = Desktop.moveBy(s, id, 99999f, 99999f, area)
        b = s.byId(id)!!.box
        assertTrue(b.x <= area.w - Desktop.GRAB_W + 0.01f)
        assertTrue(b.y <= area.h - Desktop.GRAB_H + 0.01f)
    }

    @Test fun dragFromMaximizedRestoresUnderPointer() {
        var s = opened("a")
        val id = s.windows.first().id
        val normalW = s.windows.first().box.w
        s = Desktop.toggleMaximize(s, id)
        s = Desktop.beginMove(s, id, area, grabFracX = 0.5f)
        val w = s.byId(id)!!
        assertEquals(WinMode.NORMAL, w.mode)
        assertEquals(normalW, w.box.w, 0.01f)
        // İmleç, eski büyük pencerenin ortasındaydı (600): yeni pencerenin de ortasında kalır.
        assertEquals(600f, w.box.cx, 1f)
    }

    @Test fun resizeRespectsMinimumAndArea() {
        var s = opened("a")
        val id = s.windows.first().id
        s = Desktop.resizeBy(s, id, Edge.RIGHT or Edge.BOTTOM, -99999f, -99999f, area)
        var b = s.byId(id)!!.box
        assertEquals(Desktop.MIN_SIZE.w, b.w, 0.01f)
        assertEquals(Desktop.MIN_SIZE.h, b.h, 0.01f)
        s = Desktop.resizeBy(s, id, Edge.LEFT or Edge.TOP, -99999f, -99999f, area)
        b = s.byId(id)!!.box
        assertEquals(0f, b.x, 0.01f)
        assertEquals(0f, b.y, 0.01f)
        s = Desktop.resizeBy(s, id, Edge.RIGHT or Edge.BOTTOM, 99999f, 99999f, area)
        b = s.byId(id)!!.box
        assertEquals(area.w, b.right, 0.01f)
        assertEquals(area.h, b.bottom, 0.01f)
    }

    @Test fun leftEdgeResizeKeepsRightEdgeFixed() {
        var s = opened("a")
        val id = s.windows.first().id
        val before = s.byId(id)!!.box
        s = Desktop.resizeBy(s, id, Edge.LEFT, 50f, 0f, area)
        val after = s.byId(id)!!.box
        assertEquals(before.right, after.right, 0.01f)
        assertEquals(before.x + 50f, after.x, 0.01f)
    }

    @Test fun resizingASnappedWindowMakesItFloating() {
        var s = opened("a")
        val id = s.windows.first().id
        s = Desktop.setMode(s, id, WinMode.SNAP_LEFT)
        s = Desktop.resizeBy(s, id, Edge.RIGHT, 100f, 0f, area)
        val w = s.byId(id)!!
        assertEquals(WinMode.NORMAL, w.mode)
        assertEquals(700f, w.box.w, 0.01f)
    }

    @Test fun fitShrinksAndPullsInsideSmallerArea() {
        // Klavye açılınca alan küçülür: kayan pencere sığdırılır, kayıtlı kutu değişmez.
        val s = opened("a")
        val win = s.windows.first()
        val small = DSize(1200f, 300f)
        val fit = Desktop.boxOf(win, small)
        assertTrue(fit.bottom <= small.h + 0.01f && fit.h <= small.h)
        assertEquals(win.box, s.windows.first().box)
    }

    @Test fun tileLayouts() {
        for (n in 1..6) {
            var s = DesktopState()
            repeat(n) { s = Desktop.open(s, "app$it", area) }
            s = Desktop.tile(s, area)
            val boxes = s.windows.map { it.box }
            assertEquals(n, boxes.size)
            boxes.forEach { assertTrue(it.x >= -0.01f && it.y >= -0.01f && it.right <= area.w + 0.01f && it.bottom <= area.h + 0.01f) }
            // Döşenen pencereler üst üste binmez.
            for (i in boxes.indices) {
                for (j in i + 1 until boxes.size) {
                    val a = boxes[i]
                    val b = boxes[j]
                    val overlap = a.x < b.right - 0.01f && b.x < a.right - 0.01f && a.y < b.bottom - 0.01f && b.y < a.bottom - 0.01f
                    assertFalse("n=$n $a $b", overlap)
                }
            }
        }
    }

    @Test fun tileSkipsMinimized() {
        var s = opened("a", "b", "c")
        s = Desktop.minimize(s, s.byApp("c")!!.id)
        s = Desktop.tile(s, area)
        assertEquals(area.h, s.byApp("a")!!.box.h, 0.01f)
        assertTrue(s.byApp("c")!!.minimized)
    }

    @Test fun codecRoundTrip() {
        var s = opened(DesktopApps.KRIPTON, DesktopApps.ai(12), DesktopApps.FILES)
        s = Desktop.setMode(s, s.byApp(DesktopApps.FILES)!!.id, WinMode.SNAP_RIGHT)
        s = Desktop.minimize(s, s.byApp(DesktopApps.ai(12))!!.id)
        val back = DesktopCodec.decode(DesktopCodec.encode(s))
        assertEquals(s, back)
        assertEquals(12L, DesktopApps.aiAccountId(back.windows[1].app))
    }

    @Test fun decodeToleratesGarbage() {
        assertEquals(DesktopState(), DesktopCodec.decode(null))
        assertEquals(DesktopState(), DesktopCodec.decode(""))
        assertEquals(DesktopState(), DesktopCodec.decode("x1;w=1,a,1,2,3,4,n,0"))
        val s = DesktopCodec.decode("v1;next=1;w=1,a,0,0,400,300,n,0|bozuk|2,a,0,0,400,300,n,0|3,b,0,0,0,300,n,0|4,c,NaN,0,400,300,n,0|5,d,10,10,400,300,zz,1")
        // Yinelenen uygulama, sıfır genişlik ve sayı olmayan alanlar atlanır; bilinmeyen mod NORMAL olur.
        assertEquals(listOf("a", "d"), s.windows.map { it.app })
        assertEquals(WinMode.NORMAL, s.byApp("d")!!.mode)
        assertTrue(s.byApp("d")!!.minimized)
        assertTrue(s.nextId > 5)
    }

    @Test fun aiAccountIdParsing() {
        assertEquals(7L, DesktopApps.aiAccountId("ai:7"))
        assertNull(DesktopApps.aiAccountId("files"))
        assertNull(DesktopApps.aiAccountId("ai:abc"))
        assertNotNull(DesktopApps.ai(3))
    }

    @Test fun clockFormat() {
        assertEquals("00:00", formatClock(0))
        assertEquals("09:05", formatClock(9 * 60 + 5))
        assertEquals("23:59", formatClock(1439))
        assertEquals("23:59", formatClock(-1))
    }
}
