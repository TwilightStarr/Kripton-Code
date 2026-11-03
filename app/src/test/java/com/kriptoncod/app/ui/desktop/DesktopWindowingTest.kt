package com.kriptoncod.app.ui.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopWindowingTest {
    private val area = WinArea(1000f, 700f)

    private fun open(s: DesktopState, app: String) = Desktop.open(s, app, area)

    private fun opened(vararg apps: String): DesktopState = apps.fold(DesktopState()) { s, a -> open(s, a) }

    private fun DesktopState.id(app: String): Int = byApp(app)!!.id

    private fun DesktopState.win(app: String): WinState = wins[id(app)]!!

    // ---------------------------------------------------------------------------------------------------------------
    // Yeni pencere: boyut, cascade, dar alan
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun defaultSizesFollowTheApp() {
        val k = Windowing.defaultSize(DesktopApps.KRIPTON, area)
        assertEquals(700f, k.w, 0.01f)
        assertEquals(490f, k.h, 0.01f)
        val f = Windowing.defaultSize(DesktopApps.FILES, area)
        assertEquals(550f, f.w, 0.01f)
        assertEquals(385f, f.h, 0.01f)
        val ai = Windowing.defaultSize(DesktopApps.ai(1), area)
        assertEquals(650f, ai.w, 0.01f)
        assertEquals(455f, ai.h, 0.01f)
        // Küçük alanda asgari boyut korunur.
        val tiny = Windowing.defaultSize(DesktopApps.FILES, WinArea(400f, 300f))
        assertEquals(Windowing.MIN_W, tiny.w, 0.01f)
        assertEquals(Windowing.MIN_H, tiny.h, 0.01f)
    }

    @Test fun newWindowsCascadeWithoutExactOverlap() {
        var s = opened("a")
        assertEquals(24f, s.win("a").geom.x, 0.01f)
        assertEquals(24f, s.win("a").geom.y, 0.01f)
        s = open(s, "b")
        assertEquals(56f, s.win("b").geom.x, 0.01f)
        assertEquals(56f, s.win("b").geom.y, 0.01f)
        s = open(s, "c")
        assertEquals(88f, s.win("c").geom.x, 0.01f)
        // Hepsi alanın içinde ve normal modda.
        s.wins.values.forEach {
            assertEquals(WinMode.NORMAL, it.mode)
            assertTrue(it.geom.right <= area.w && it.geom.bottom <= area.h)
        }
        // Küçültülen pencerenin yuvası yeniden kullanılır.
        val freed = Windowing.minimize(s, s.id("a"))
        assertEquals(24f, Windowing.cascadeFor(freed, "d", area).x, 0.01f)
    }

    @Test fun cascadeWrapsAroundWhenSlotsRunOut() {
        var s = DesktopState()
        repeat(12) { s = Desktop.open(s, "app$it", area) }
        s.wins.values.forEach { assertTrue(it.geom.isSane) }
        assertEquals(12, s.wins.size)
    }

    @Test fun narrowAreaOpensMaximized() {
        val narrow = WinArea(500f, 800f)
        val s = Desktop.open(DesktopState(), DesktopApps.KRIPTON, narrow)
        val w = s.win(DesktopApps.KRIPTON)
        assertEquals(WinMode.MAXIMIZED, w.mode)
        assertNotNull(w.restoreGeom)
        // Geri alınca normal pencere olur.
        val back = Windowing.restore(s, s.id(DesktopApps.KRIPTON))
        assertEquals(WinMode.NORMAL, back.win(DesktopApps.KRIPTON).mode)
        // Eşik: 600dp ve üstü normal açılır.
        assertEquals(WinMode.NORMAL, Desktop.open(DesktopState(), "a", WinArea(600f, 800f)).win("a").mode)
        assertEquals(WinMode.MAXIMIZED, Desktop.open(DesktopState(), "a", WinArea(599f, 800f)).win("a").mode)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Odak, z-sırası, küçült / büyüt
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun focusRaisesAndActiveIsTopVisible() {
        var s = opened("a", "b", "c")
        assertEquals("c", s.active?.app)
        s = Windowing.focus(s, s.id("a"))
        assertEquals("a", s.active?.app)
        assertEquals(s.id("a"), s.z.last())
        // Zaten en üstteyse durum aynı örnektir.
        assertSame(s, Windowing.focus(s, s.id("a")))
        // Olmayan pencere bir şey değiştirmez.
        assertSame(s, Windowing.focus(s, 999))
        s = Windowing.minimize(s, s.id("a"))
        assertEquals("c", s.active?.app)
    }

    @Test fun minimizeAndRestoreKeepMode() {
        var s = opened("a", "b")
        val b = s.id("b")
        s = Windowing.maximize(s, b)
        assertEquals(WinMode.MAXIMIZED, s.win("b").mode)
        s = Windowing.minimize(s, b)
        assertEquals("a", s.active?.app)
        assertEquals(WinMode.MINIMIZED, s.win("b").mode)
        s = Windowing.restoreFromMin(s, b)
        assertEquals(WinMode.MAXIMIZED, s.win("b").mode)
        assertEquals("b", s.active?.app)
    }

    @Test fun maximizeAndRestoreRoundTrip() {
        var s = opened("a")
        val id = s.id("a")
        val before = s.win("a").geom
        s = Windowing.maximize(s, id)
        assertEquals(WinMode.MAXIMIZED, s.win("a").mode)
        assertEquals(before, s.win("a").restoreGeom)
        // Gösterilen dikdörtgen bütün alandır.
        assertEquals(area.rect, Windowing.rectOf(s.win("a"), area))
        s = Windowing.toggleMaximize(s, id)
        assertEquals(WinMode.NORMAL, s.win("a").mode)
        assertEquals(before, s.win("a").geom)
        assertNull(s.win("a").restoreGeom)
        s = Windowing.toggleMaximize(s, id)
        assertEquals(WinMode.MAXIMIZED, s.win("a").mode)
    }

    @Test fun closeMovesFocusDownTheZOrder() {
        var s = opened("a", "b", "c")
        s = Windowing.focus(s, s.id("a")) // z: b c a
        s = Desktop.close(s, s.id("a"))
        assertEquals("c", s.active?.app)
        // Geri kalanlar küçültülmüşse masaüstü görünür.
        s = Windowing.minimize(Windowing.minimize(s, s.id("b")), s.id("c"))
        assertNull(s.activeId)
        val closed = Desktop.close(s, s.id("c"))
        assertNull(closed.activeId)
        assertEquals(listOf(closed.id("b")), closed.z)
        assertEquals(setOf(closed.id("b")), closed.wins.keys)
    }

    @Test fun reopeningRestoresMinimizedWindow() {
        var s = opened("a", "b")
        s = Windowing.minimize(s, s.id("a"))
        s = Desktop.open(s, "a", area)
        assertEquals("a", s.active?.app)
        assertEquals(2, s.tabs.size)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Taşı / boyutlandır / sığdır
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun moveKeepsWindowInsideArea() {
        var s = opened("a")
        val id = s.id("a")
        val g = s.win("a").geom
        s = Windowing.move(s, id, -50f, 9999f, area)
        assertEquals(0f, s.win("a").geom.x, 0.01f)
        assertEquals(area.h - g.h, s.win("a").geom.y, 0.01f)
        assertEquals(g.w, s.win("a").geom.w, 0.01f)
        s = Windowing.move(s, id, 120f, 80f, area)
        assertEquals(120f, s.win("a").geom.x, 0.01f)
        assertEquals(80f, s.win("a").geom.y, 0.01f)
    }

    @Test fun resizeEnforcesMinimumSize() {
        var s = opened("a")
        val id = s.id("a")
        s = Windowing.resize(s, id, WinGeom(10f, 10f, 50f, 50f), area)
        val g = s.win("a").geom
        assertEquals(Windowing.MIN_W, g.w, 0.01f)
        assertEquals(Windowing.MIN_H, g.h, 0.01f)
        assertEquals(10f, g.x, 0.01f)
        // Bozuk geometri reddedilir.
        val same = Windowing.resize(s, id, WinGeom(Float.NaN, 0f, 400f, 400f), area)
        assertEquals(g, same.win("a").geom)
    }

    @Test fun resizeByAnchorsTheOppositeEdge() {
        val g = WinGeom(100f, 100f, 400f, 300f)
        // Doğu kenarı asgariden fazla daralmaz.
        val e = Windowing.resizeBy(g, ResizeHandle.E, -300f, 0f, area)
        assertEquals(100f, e.x, 0.01f)
        assertEquals(Windowing.MIN_W, e.w, 0.01f)
        // Batı kenarı sağ kenarı sabit tutar.
        val w = Windowing.resizeBy(g, ResizeHandle.W, 500f, 0f, area)
        assertEquals(500f, w.right, 0.01f)
        assertEquals(Windowing.MIN_W, w.w, 0.01f)
        // Kuzey-batı köşesi alanın dışına çıkmaz.
        val nw = Windowing.resizeBy(g, ResizeHandle.NW, -200f, -200f, area)
        assertEquals(0f, nw.x, 0.01f)
        assertEquals(0f, nw.y, 0.01f)
        assertEquals(500f, nw.right, 0.01f)
        assertEquals(400f, nw.bottom, 0.01f)
        // Güney-doğu köşesi alanın sağ-alt köşesinde durur.
        val se = Windowing.resizeBy(g, ResizeHandle.SE, 900f, 900f, area)
        assertEquals(area.w, se.right, 0.01f)
        assertEquals(area.h, se.bottom, 0.01f)
        // Kuzey/güney yalnız dikeyi değiştirir.
        val n = Windowing.resizeBy(g, ResizeHandle.N, 50f, -40f, area)
        assertEquals(100f, n.x, 0.01f)
        assertEquals(60f, n.y, 0.01f)
        assertEquals(340f, n.h, 0.01f)
    }

    @Test fun clampToAreaPullsWindowsBack() {
        var s = Windowing.move(opened("a"), 1, 300f, 200f, area)
        // Alan küçülünce pencere hem küçülür hem içeri girer.
        val small = WinArea(500f, 300f)
        val c = Windowing.clampToArea(s, small)
        val g = c.win("a").geom
        assertEquals(0f, g.x, 0.01f)
        assertEquals(0f, g.y, 0.01f)
        assertEquals(500f, g.w, 0.01f)
        assertEquals(300f, g.h, 0.01f)
        // Asgari boyutun altına inmez (alan daha küçük olsa da).
        val tiny = Windowing.clampToArea(s, WinArea(200f, 150f))
        assertEquals(Windowing.MIN_W, tiny.win("a").geom.w, 0.01f)
        // Değişen bir şey yoksa aynı örnek döner; geçersiz alan hiçbir şey yapmaz.
        s = c
        assertSame(s, Windowing.clampToArea(s, small))
        assertSame(s, Windowing.clampToArea(s, WinArea(0f, 0f)))
    }

    @Test fun dragFromMaximizedRestoresSizeUnderPointer() {
        var s = opened("a")
        val id = s.id("a")
        val before = s.win("a").geom
        s = Windowing.maximize(s, id)
        val start = Windowing.dragStartGeom(s.win("a"), 500f, area)
        assertEquals(before.w, start.w, 0.01f)
        assertEquals(before.h, start.h, 0.01f)
        // İşaretçi oransal nokta (yarı) aynı kalır: pencere ortası işaretçide.
        assertEquals(500f, start.x + start.w / 2f, 0.01f)
        assertEquals(0f, start.y, 0.01f)
        // Bırakınca normale döner, dönülecek dikdörtgen silinir.
        val placed = Windowing.placeFromDrag(s, id, start, area)
        assertEquals(WinMode.NORMAL, placed.win("a").mode)
        assertNull(placed.win("a").restoreGeom)
        assertEquals(start, placed.win("a").geom)
        // Normal pencerede başlangıç kendi dikdörtgenidir.
        val normal = opened("n")
        assertEquals(normal.win("n").geom, Windowing.dragStartGeom(normal.win("n"), 100f, area))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Yaslama (snap)
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun snapZonesFromPointer() {
        assertEquals(SnapZone.LEFT, Windowing.snapZoneAt(10f, 300f, area))
        assertEquals(SnapZone.RIGHT, Windowing.snapZoneAt(990f, 300f, area))
        assertEquals(SnapZone.TOP, Windowing.snapZoneAt(500f, 10f, area))
        assertEquals(SnapZone.TOP_LEFT, Windowing.snapZoneAt(10f, 10f, area))
        assertEquals(SnapZone.TOP_RIGHT, Windowing.snapZoneAt(995f, 5f, area))
        assertEquals(SnapZone.BOTTOM_LEFT, Windowing.snapZoneAt(5f, 690f, area))
        assertEquals(SnapZone.BOTTOM_RIGHT, Windowing.snapZoneAt(990f, 690f, area))
        assertNull(Windowing.snapZoneAt(500f, 300f, area))
        // Alt kenarın ortası bölge değildir.
        assertNull(Windowing.snapZoneAt(500f, 695f, area))
        assertNull(Windowing.snapZoneAt(10f, 10f, WinArea(0f, 0f)))
    }

    @Test fun snapRects() {
        assertEquals(WinGeom(0f, 0f, 500f, 700f), Windowing.snapRect(SnapZone.LEFT, area))
        assertEquals(WinGeom(500f, 0f, 500f, 700f), Windowing.snapRect(SnapZone.RIGHT, area))
        assertEquals(area.rect, Windowing.snapRect(SnapZone.TOP, area))
        assertEquals(WinGeom(0f, 0f, 500f, 350f), Windowing.snapRect(SnapZone.TOP_LEFT, area))
        assertEquals(WinGeom(500f, 350f, 500f, 350f), Windowing.snapRect(SnapZone.BOTTOM_RIGHT, area))
        // Çeyrek asgariden küçük kalırsa büyütülür ve kenara dayanır.
        val r = Windowing.snapRect(SnapZone.BOTTOM_RIGHT, WinArea(500f, 400f))
        assertEquals(WinGeom(180f, 160f, 320f, 240f), r)
    }

    @Test fun snapKeepsOldGeometryForRestore() {
        var s = opened("a")
        val id = s.id("a")
        val before = s.win("a").geom
        s = Windowing.snap(s, id, SnapZone.LEFT, area)
        assertEquals(WinGeom(0f, 0f, 500f, 700f), s.win("a").geom)
        assertEquals(before, s.win("a").restoreGeom)
        assertTrue(s.win("a").snapped)
        // İkinci yaslama eski konumu ezmez.
        s = Windowing.snap(s, id, SnapZone.RIGHT, area)
        assertEquals(before, s.win("a").restoreGeom)
        // Geri al: yaslamadan önceki dikdörtgen.
        s = Windowing.restore(s, id)
        assertEquals(before, s.win("a").geom)
        assertFalse(s.win("a").snapped)
        // Üst bölge büyütür.
        s = Windowing.snap(s, id, SnapZone.TOP, area)
        assertEquals(WinMode.MAXIMIZED, s.win("a").mode)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Masaüstünü göster, çubuk davranışı
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun showDesktopRemembersVisibleWindows() {
        var s = opened("a", "b", "c")
        s = Windowing.minimize(s, s.id("c"))
        s = Windowing.showDesktop(s)
        assertNull(s.activeId)
        assertEquals(listOf(s.id("a"), s.id("b")), s.shownDesktop)
        s = Windowing.toggleDesktop(s)
        assertEquals("b", s.active?.app)
        assertEquals(WinMode.NORMAL, s.win("a").mode)
        // Önceden elle küçültülen pencere geri gelmez.
        assertEquals(WinMode.MINIMIZED, s.win("c").mode)
        assertTrue(s.shownDesktop.isEmpty())
        // Görünen pencere varken geçiş hepsini küçültür.
        assertNull(Windowing.toggleDesktop(s).activeId)
    }

    @Test fun showDesktopWithNothingRememberedRestoresOnlyTheLatest() {
        var s = opened("a", "b")
        s = Windowing.minimize(Windowing.minimize(s, s.id("a")), s.id("b"))
        s = Windowing.toggleDesktop(s)
        assertEquals("b", s.active?.app)
        assertEquals(WinMode.MINIMIZED, s.win("a").mode)
        // Hiç pencere yokken bir şey olmaz.
        val none = DesktopState()
        assertSame(none, Windowing.showDesktop(none))
        assertSame(none, Windowing.toggleDesktop(none))
    }

    @Test fun dockTapFollowsDesktopRules() {
        var s = opened("a", "b")
        val a = s.id("a")
        val b = s.id("b")
        // Arkadaki görünür pencere öne gelir.
        s = Windowing.dockTap(s, a)
        assertEquals(a, s.activeId)
        assertEquals(WinMode.NORMAL, s.win("a").mode)
        // Odaklıya dokunmak küçültür.
        s = Windowing.dockTap(s, a)
        assertEquals(WinMode.MINIMIZED, s.win("a").mode)
        assertEquals(b, s.activeId)
        // Küçültülmüşe dokunmak geri getirir ve öne alır.
        s = Windowing.dockTap(s, a)
        assertEquals(a, s.activeId)
        assertEquals(WinMode.NORMAL, s.win("a").mode)
        // Pencere olmayan kimlikte bir şey olmaz.
        assertSame(s, Windowing.dockTap(s, 999))
    }

    @Test fun dockRepPrefersTopVisibleThenLatestMinimized() {
        var s = Desktop.openNew(Desktop.openNew(DesktopState(), DesktopApps.FILES, area), DesktopApps.FILES, area)
        val (f1, f2) = s.tabsOf(DesktopApps.FILES).map { it.id }
        assertEquals(f2, Desktop.dock(s)[0].tabId)
        s = Windowing.focus(s, f1)
        assertEquals(f1, Desktop.dock(s)[0].tabId)
        s = Windowing.minimize(Windowing.minimize(s, f1), f2)
        // Hepsi küçültülmüş: en son kullanılan (z-sırasında en üstteki) pencere.
        assertEquals(f1, Desktop.dock(s)[0].tabId)
        assertTrue(Desktop.dock(s)[0].grouped)
    }

    @Test fun externalAppsNeverGetWindows() {
        val pkg = DesktopApps.pkg("com.example.maps")
        var s = opened("a")
        s = Desktop.open(s, pkg, area)
        assertEquals(2, s.tabs.size)
        assertEquals(1, s.wins.size)
        assertEquals(1, s.z.size)
        assertEquals("a", s.active?.app)
        // Çubukta izlenir (açık sekme), ama pencere kaydı yoktur.
        val entry = Desktop.dock(s).first { it.app == pkg }
        assertNotNull(entry.tabId)
        assertFalse(s.wins.containsKey(entry.tabId))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Codec v3
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun codecV3RoundTripKeepsGeometryModesAndZOrder() {
        var s = DesktopState(pinned = DesktopApps.DEFAULT_PINNED)
        s = Desktop.open(s, DesktopApps.KRIPTON, area)
        s = Desktop.openNew(s, DesktopApps.FILES, area)
        s = Desktop.openNew(s, DesktopApps.FILES, area)
        s = Desktop.open(s, DesktopApps.ai(4), area)
        val ids = s.tabs.map { it.id }
        s = Windowing.move(s, ids[1], 133.33333f, 41.5f, area)
        s = Windowing.maximize(s, ids[0])
        s = Windowing.snap(s, ids[2], SnapZone.RIGHT, area)
        s = Windowing.minimize(s, ids[3])
        s = Windowing.focus(s, ids[1])
        val back = DesktopCodec.decode(DesktopCodec.encode(s), area)
        assertEquals(s, back)
        assertEquals(s.z, back!!.z)
        assertEquals(WinMode.MAXIMIZED, back.wins[ids[0]]!!.mode)
        assertEquals(WinMode.MINIMIZED, back.wins[ids[3]]!!.mode)
        assertTrue(back.wins[ids[2]]!!.snapped)
        assertEquals(ids[1], back.activeId)
    }

    @Test fun codecV3KeepsShowDesktopMemory() {
        var s = Windowing.showDesktop(opened("a", "b"))
        val back = DesktopCodec.decode(DesktopCodec.encode(s))!!
        assertEquals(s, back)
        assertNull(back.activeId)
        s = Windowing.toggleDesktop(back)
        assertEquals("b", s.active?.app)
    }

    @Test fun codecV3SurvivesGarbageGeometry() {
        val text = "v3;next=1;" +
            "t=1,files,NaN,-5,100,100,n,n,,,,|" + // bozuk konum -> varsayılan cascade
            "2,ai:3,10,10,0,0,x,n,,,,|" + // sıfır boyut -> varsayılan cascade
            "3,kripton,5,5,10,10,m,x,,,,|" + // asgariden küçük -> asgariye çıkar
            "4,x,1,1,500,500,n,n,,,|" + // alan sayısı yanlış -> atlanır
            "5,y,Infinity,1,500,500,n,n,,,,|" + // sonsuz -> varsayılan
            "6,z,1,1,500,500,q,q,-1,-1,-5,100;" + // geçersiz mod ve bozuk dönüş dikdörtgeni
            "z=9|3|3;sd=77|1;p=files"
        val s = DesktopCodec.decode(text)!!
        assertEquals(listOf(1, 2, 3, 5, 6), s.tabs.map { it.id })
        s.wins.values.forEach {
            assertTrue(it.geom.isSane)
            assertTrue(it.geom.w >= Windowing.MIN_W && it.geom.h >= Windowing.MIN_H)
        }
        assertEquals(WinMode.MAXIMIZED, s.wins[2]!!.mode)
        val three = s.wins[3]!!
        assertEquals(WinMode.MINIMIZED, three.mode)
        assertEquals(WinMode.MAXIMIZED, three.beforeMin)
        assertEquals(Windowing.MIN_W, three.geom.w, 0.01f)
        assertEquals(WinMode.NORMAL, s.wins[6]!!.mode)
        assertNull(s.wins[6]!!.restoreGeom)
        // z: bilinmeyen kimlik atılır, yinelenen tekilleşir, eksikler sona eklenir.
        assertEquals(listOf(3, 1, 2, 5, 6), s.z)
        assertEquals(listOf(1), s.shownDesktop)
        assertTrue(s.nextId > 6)
        assertEquals(listOf("files"), s.pinned)
        // Tamamen boş ama geçerli kayıt.
        val empty = DesktopCodec.decode("v3;t=;z=;sd=;p=")!!
        assertTrue(empty.tabs.isEmpty() && empty.wins.isEmpty() && empty.z.isEmpty())
    }

    @Test fun codecV2MigratesToCascadeWindows() {
        val old = "v2;next=4;active=2;t=1,files|2,ai:7|3,kripton;p=files|kripton"
        val s = DesktopCodec.decode(old, area)!!
        assertEquals(listOf("files", "ai:7", "kripton"), s.tabs.map { it.app })
        // Yalnız eski görünen sekme açık pencere olur ve en üste gelir; diğerleri küçültülmüş.
        assertEquals(2, s.activeId)
        assertEquals(2, s.z.last())
        assertEquals(WinMode.NORMAL, s.wins[2]!!.mode)
        assertEquals(WinMode.MINIMIZED, s.wins[1]!!.mode)
        assertEquals(WinMode.MINIMIZED, s.wins[3]!!.mode)
        assertEquals(listOf("files", "kripton"), s.pinned)
        assertEquals(4, s.nextId)
        // Cascade: pencereler birbirinden farklı konumda.
        assertEquals(3, s.wins.values.map { it.geom.x }.distinct().size)
        // Eski masaüstü gösterilmişti (etkin yok): hepsi küçültülmüş.
        val shown = DesktopCodec.decode("v2;next=3;active=;t=1,files|2,ai:7;p=", area)!!
        assertNull(shown.activeId)
    }

    @Test fun codecV2OnNarrowAreaMigratesToMaximized() {
        val old = "v2;next=3;active=1;t=1,files|2,ai:7;p="
        val s = DesktopCodec.decode(old, WinArea(500f, 800f))!!
        assertEquals(WinMode.MAXIMIZED, s.wins[1]!!.mode)
        assertEquals(WinMode.MINIMIZED, s.wins[2]!!.mode)
        assertEquals(WinMode.MAXIMIZED, s.wins[2]!!.beforeMin)
        // Küçültülmüş pencere geri gelince büyütülmüş açılır.
        assertEquals(WinMode.MAXIMIZED, Windowing.focus(s, 2).wins[2]!!.mode)
    }

    @Test fun codecV1MigratesWithZOrderAndMinimizedFlags() {
        val old = "v1;next=4;w=1,files,24,24,700,500,n,1|2,ai:7,0,0,1000,700,max,0|3,kripton,0,0,600,500,l,0"
        val s = DesktopCodec.decode(old, area)!!
        assertEquals(WinMode.MINIMIZED, s.wins[1]!!.mode)
        // Küçültülmemiş pencereler görünür; liste sırası z-sırasıdır: en üstteki küçültülmemiş = kripton.
        assertEquals(3, s.activeId)
        assertEquals(WinMode.NORMAL, s.wins[2]!!.mode)
        assertEquals(listOf(1, 2, 3), s.z)
    }

    @Test fun decodedStateCanBeClampedToAnotherArea() {
        val s = DesktopCodec.decode(DesktopCodec.encode(opened("a")), area)!!
        val small = Windowing.clampToArea(s, WinArea(400f, 300f))
        assertEquals(400f, small.win("a").geom.w, 0.01f)
        assertTrue(small.win("a").geom.right <= 400f)
    }
}
