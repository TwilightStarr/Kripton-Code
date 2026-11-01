package com.kriptoncod.app.ui.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopTabsTest {
    private fun opened(vararg apps: String): DesktopState = apps.fold(DesktopState()) { s, a -> Desktop.open(s, a) }

    @Test fun openCreatesOneTabPerAppAndActivatesIt() {
        var s = opened(DesktopApps.FILES, DesktopApps.ai(7))
        assertEquals(2, s.tabs.size)
        assertEquals(DesktopApps.ai(7), s.active?.app)
        // Aynı uygulama yeniden açılınca yeni sekme olmaz; görünür olur.
        s = Desktop.open(s, DesktopApps.FILES)
        assertEquals(2, s.tabs.size)
        assertEquals(DesktopApps.FILES, s.active?.app)
    }

    @Test fun showDesktopKeepsTabsOpen() {
        var s = opened("a", "b")
        s = Desktop.showDesktop(s)
        assertNull(s.active)
        assertEquals(2, s.tabs.size)
        // Sekmeye dönmek mümkündür.
        s = Desktop.activate(s, s.byApp("a")!!.id)
        assertEquals("a", s.active?.app)
    }

    @Test fun closingActiveTabActivatesRightNeighbourThenLeft() {
        var s = opened("a", "b", "c")
        s = Desktop.activate(s, s.byApp("b")!!.id)
        s = Desktop.close(s, s.byApp("b")!!.id)
        assertEquals("c", s.active?.app)
        s = Desktop.close(s, s.byApp("c")!!.id)
        assertEquals("a", s.active?.app)
        s = Desktop.close(s, s.byApp("a")!!.id)
        assertNull(s.active)
        assertTrue(s.tabs.isEmpty())
    }

    @Test fun closingBackgroundTabKeepsActive() {
        var s = opened("a", "b", "c")
        val active = s.active
        s = Desktop.close(s, s.byApp("a")!!.id)
        assertEquals(active, s.active)
        // Olmayan sekmeyi kapatmak bir şey değiştirmez.
        assertEquals(s, Desktop.close(s, 999))
    }

    @Test fun closingWhileOnDesktopStaysOnDesktop() {
        var s = Desktop.showDesktop(opened("a", "b"))
        s = Desktop.close(s, s.byApp("b")!!.id)
        assertNull(s.active)
    }

    @Test fun externalAppsAreTrackedButNeverVisible() {
        var s = opened("a")
        val pkg = DesktopApps.pkg("com.example.maps")
        s = Desktop.open(s, pkg)
        assertEquals(2, s.tabs.size)
        // Harici uygulama görünen sekmeyi değiştirmez.
        assertEquals("a", s.active?.app)
        assertEquals(s, Desktop.activate(s, s.byApp(pkg)!!.id))
        // Yeniden açmak yeni sekme yapmaz.
        assertEquals(s, Desktop.open(s, pkg))
        // Görünen sekme kapanınca harici sekme görünür olmaz.
        s = Desktop.close(s, s.byApp("a")!!.id)
        assertNull(s.active)
        assertEquals(1, s.tabs.size)
    }

    @Test fun pinUnpinAndMove() {
        var s = DesktopState(pinned = listOf("a", "b", "c"))
        s = Desktop.pin(s, "a")
        assertEquals(listOf("a", "b", "c"), s.pinned)
        s = Desktop.pin(s, "d")
        assertEquals(listOf("a", "b", "c", "d"), s.pinned)
        s = Desktop.unpin(s, "b")
        assertEquals(listOf("a", "c", "d"), s.pinned)
        assertEquals(listOf("a", "c", "d", "b"), Desktop.togglePin(s, "b").pinned)
        assertEquals(listOf("c", "a", "d"), Desktop.movePinned(s, "a", 1).pinned)
        assertEquals(listOf("a", "c", "d"), Desktop.movePinned(s, "a", -1).pinned)
        assertEquals(listOf("a", "d", "c"), Desktop.movePinned(s, "c", 99).pinned)
        assertEquals(s, Desktop.movePinned(s, "yok", 1))
    }

    @Test fun dockListsPinnedFirstThenOtherOpenTabs() {
        var s = DesktopState(pinned = listOf(DesktopApps.KRIPTON, DesktopApps.FILES))
        s = Desktop.open(s, DesktopApps.ai(3))
        s = Desktop.open(s, DesktopApps.FILES)
        val dock = Desktop.dock(s)
        assertEquals(listOf(DesktopApps.KRIPTON, DesktopApps.FILES, DesktopApps.ai(3)), dock.map { it.app })
        // Sabit ama açık olmayan uygulamanın sekmesi yoktur.
        assertFalse(dock[0].open)
        assertTrue(dock[0].pinned)
        assertTrue(dock[1].open)
        // Sabit olmayan açık sekme, açık olduğu sürece çubukta kalır.
        assertFalse(dock[2].pinned)
        assertNotNull(dock[2].tabId)
        // Sekme kapanınca sabit olmayan öğe çubuktan kalkar, sabit olan kalır.
        s = Desktop.close(Desktop.close(s, s.byApp(DesktopApps.ai(3))!!.id), s.byApp(DesktopApps.FILES)!!.id)
        assertEquals(listOf(DesktopApps.KRIPTON, DesktopApps.FILES), Desktop.dock(s).map { it.app })
    }

    @Test fun retainDropsTabsAndPinsOfRemovedApps() {
        var s = DesktopState(pinned = listOf(DesktopApps.KRIPTON, DesktopApps.ai(5)))
        s = Desktop.open(s, DesktopApps.ai(5))
        s = Desktop.open(s, DesktopApps.ai(6))
        s = Desktop.open(s, DesktopApps.FILES)
        s = Desktop.activate(s, s.byApp(DesktopApps.ai(5))!!.id)
        val kept = Desktop.retain(s) { app -> DesktopApps.aiAccountId(app) != 5L }
        assertEquals(listOf(DesktopApps.ai(6), DesktopApps.FILES), kept.tabs.map { it.app })
        assertEquals(listOf(DesktopApps.KRIPTON), kept.pinned)
        // Silinen sekme görünendi: yerine komşusu gelir.
        assertEquals(DesktopApps.ai(6), kept.active?.app)
        // Hiçbir şey silinmiyorsa durum aynı kalır.
        assertEquals(s, Desktop.retain(s) { true })
    }

    @Test fun codecRoundTrip() {
        var s = DesktopState(pinned = DesktopApps.DEFAULT_PINNED + DesktopApps.pkg("com.example.maps"))
        s = Desktop.open(s, DesktopApps.KRIPTON)
        s = Desktop.open(s, DesktopApps.ai(12))
        s = Desktop.open(s, DesktopApps.FILES)
        s = Desktop.activate(s, s.byApp(DesktopApps.ai(12))!!.id)
        val back = DesktopCodec.decode(DesktopCodec.encode(s))
        assertEquals(s, back)
        assertEquals(12L, DesktopApps.aiAccountId(back!!.tabs[1].app))
    }

    @Test fun codecKeepsDesktopShownAndSkipsExternalTabs() {
        var s = Desktop.open(DesktopState(), DesktopApps.FILES)
        s = Desktop.open(s, DesktopApps.pkg("com.example.maps"))
        s = Desktop.showDesktop(s)
        val back = DesktopCodec.decode(DesktopCodec.encode(s))!!
        assertNull(back.active)
        // Harici uygulama sekmesi kaydedilmez (uygulama artık çalışmıyor olabilir).
        assertEquals(listOf(DesktopApps.FILES), back.tabs.map { it.app })
        assertTrue(back.nextId > s.nextId - 1)
    }

    @Test fun emptyButSavedStateIsNotTreatedAsFresh() {
        // Kullanıcı her şeyi kapatıp hiçbir şey sabitlememişse bu geçerli bir kayıttır (null değil).
        val back = DesktopCodec.decode(DesktopCodec.encode(DesktopState()))
        assertNotNull(back)
        assertTrue(back!!.tabs.isEmpty() && back.pinned.isEmpty())
    }

    @Test fun decodeToleratesGarbage() {
        assertNull(DesktopCodec.decode(null))
        assertNull(DesktopCodec.decode(""))
        assertNull(DesktopCodec.decode("x9;t=1,a"))
        val s = DesktopCodec.decode("v2;next=1;active=77;t=1,a|bozuk|2,a|3,b,c|0,z|4,pkg:x.y|5,d;p=a|a||b")!!
        // Yinelenen uygulama, bozuk parça, sıfır kimlik ve harici uygulama atlanır; geçersiz etkin sekme null olur.
        assertEquals(listOf("a", "d"), s.tabs.map { it.app })
        assertNull(s.active)
        assertEquals(listOf("a", "b"), s.pinned)
        assertTrue(s.nextId > 5)
    }

    @Test fun oldFloatingWindowLayoutMigratesToTabs() {
        val old = "v1;next=4;w=1,files,24,24,700,500,n,1|2,ai:7,0,0,1000,700,max,0|3,kripton,0,0,600,500,l,1"
        val s = DesktopCodec.decode(old)!!
        assertEquals(listOf("files", "ai:7", "kripton"), s.tabs.map { it.app })
        // Küçültülmemiş son pencere görünen sekme olur.
        assertEquals("ai:7", s.active?.app)
        assertEquals(DesktopApps.DEFAULT_PINNED, s.pinned)
        assertEquals(4, s.nextId)
    }

    @Test fun appKeyParsing() {
        assertEquals(7L, DesktopApps.aiAccountId("ai:7"))
        assertNull(DesktopApps.aiAccountId("files"))
        assertNull(DesktopApps.aiAccountId("ai:abc"))
        assertNotNull(DesktopApps.ai(3))
        assertEquals("com.example.maps", DesktopApps.packageOf(DesktopApps.pkg("com.example.maps")))
        assertNull(DesktopApps.packageOf("files"))
        assertNull(DesktopApps.packageOf("pkg:"))
        assertTrue(DesktopApps.isExternal(DesktopApps.pkg("a.b")))
        assertFalse(DesktopApps.isExternal(DesktopApps.ai(1)))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Pencere gruplama (aynı uygulamanın birden çok penceresi)
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun onlyMultiWindowAppsGetNewWindows() {
        var s = opened(DesktopApps.FILES, DesktopApps.ai(1))
        s = Desktop.openNew(s, DesktopApps.FILES)
        s = Desktop.openNew(s, DesktopApps.FILES)
        assertEquals(3, s.tabsOf(DesktopApps.FILES).size)
        // Yeni pencere görünür olur.
        assertEquals(s.tabsOf(DesktopApps.FILES).last().id, s.activeId)
        // AI ve Kripton tek kalır: openNew open gibi davranır.
        val before = s.tabs.size
        s = Desktop.openNew(s, DesktopApps.ai(1))
        s = Desktop.openNew(s, DesktopApps.KRIPTON)
        assertEquals(before + 1, s.tabs.size) // yalnız Kripton ilk kez açıldı
        assertEquals(1, s.tabsOf(DesktopApps.ai(1)).size)
        assertEquals(1, s.tabsOf(DesktopApps.KRIPTON).size)
        assertTrue(DesktopApps.allowsMultiple(DesktopApps.FILES))
        assertFalse(DesktopApps.allowsMultiple(DesktopApps.ai(1)))
        assertFalse(DesktopApps.allowsMultiple(DesktopApps.pkg("a.b")))
    }

    @Test fun openKeepsVisibleWindowOfAGroupElseFirst() {
        var s = Desktop.openNew(Desktop.openNew(DesktopState(), DesktopApps.FILES), DesktopApps.FILES)
        val (first, second) = s.tabsOf(DesktopApps.FILES)
        assertEquals(second.id, s.activeId)
        // Görünen pencere zaten bu uygulamadansa open onu değiştirmez.
        assertEquals(second.id, Desktop.open(s, DesktopApps.FILES).activeId)
        // Masaüstündeyken (görünen yok) ilk pencere öne gelir; yeni pencere oluşmaz.
        s = Desktop.open(Desktop.showDesktop(s), DesktopApps.FILES)
        assertEquals(first.id, s.activeId)
        assertEquals(2, s.tabs.size)
    }

    @Test fun dockGroupsWindowsOfOneAppIntoOneEntry() {
        var s = DesktopState(pinned = listOf(DesktopApps.FILES))
        s = Desktop.openNew(s, DesktopApps.FILES)
        s = Desktop.open(s, DesktopApps.ai(2))
        s = Desktop.openNew(s, DesktopApps.FILES)
        val dock = Desktop.dock(s)
        assertEquals(listOf(DesktopApps.FILES, DesktopApps.ai(2)), dock.map { it.app })
        val files = dock[0]
        assertEquals(2, files.count)
        assertTrue(files.grouped)
        assertEquals(s.tabsOf(DesktopApps.FILES).map { it.id }, files.tabIds)
        // Görünen pencere grupta olduğundan temsilci odur.
        assertEquals(s.activeId, files.tabId)
        assertFalse(dock[1].grouped)
        // Görünen başka uygulamadaysa temsilci ilk penceredir.
        val onAi = Desktop.activate(s, s.byApp(DesktopApps.ai(2))!!.id)
        assertEquals(onAi.tabsOf(DesktopApps.FILES).first().id, Desktop.dock(onAi)[0].tabId)
    }

    @Test fun ordinalNumbersWindowsInsideTheirApp() {
        var s = Desktop.open(DesktopState(), DesktopApps.ai(1))
        s = Desktop.openNew(Desktop.openNew(s, DesktopApps.FILES), DesktopApps.FILES)
        val (a, b) = s.tabsOf(DesktopApps.FILES)
        assertEquals(1, s.ordinalOf(a.id))
        assertEquals(2, s.ordinalOf(b.id))
        assertEquals(1, s.ordinalOf(s.byApp(DesktopApps.ai(1))!!.id))
        assertEquals(0, s.ordinalOf(999))
    }

    @Test fun cycleInGroupWalksWindowsAndWraps() {
        var s = Desktop.openNew(Desktop.openNew(Desktop.openNew(DesktopState(), DesktopApps.FILES), DesktopApps.FILES), DesktopApps.FILES)
        val ids = s.tabsOf(DesktopApps.FILES).map { it.id }
        assertEquals(ids[2], s.activeId)
        s = Desktop.cycleInGroup(s, DesktopApps.FILES)
        assertEquals(ids[0], s.activeId)
        s = Desktop.cycleInGroup(s, DesktopApps.FILES)
        assertEquals(ids[1], s.activeId)
        // Tek pencerede ya da bilinmeyen uygulamada bir şey olmaz.
        val single = opened("a")
        assertEquals(single, Desktop.cycleInGroup(single, "a"))
        assertEquals(s, Desktop.cycleInGroup(s, "yok"))
        // Görünen başka uygulamadaysa ilk pencere öne gelir.
        val other = Desktop.open(s, "x")
        assertEquals(ids[0], Desktop.cycleInGroup(other, DesktopApps.FILES).activeId)
    }

    @Test fun closeAppAndCloseAll() {
        var s = Desktop.openNew(Desktop.openNew(DesktopState(), DesktopApps.FILES), DesktopApps.FILES)
        s = Desktop.open(s, "a")
        s = Desktop.activate(s, s.tabsOf(DesktopApps.FILES)[0].id)
        s = Desktop.closeApp(s, DesktopApps.FILES)
        assertEquals(listOf("a"), s.tabs.map { it.app })
        assertEquals("a", s.active?.app)
        // Olmayan uygulamayı kapatmak bir şey değiştirmez.
        assertEquals(s, Desktop.closeApp(s, "yok"))
        val nextId = s.nextId
        val none = Desktop.closeAll(Desktop.open(s, "b"))
        assertTrue(none.tabs.isEmpty())
        assertNull(none.active)
        assertEquals(nextId + 1, none.nextId) // kimlik sayacı geri sarılmaz
        assertEquals(none, Desktop.closeAll(none))
        // Sabitler kalır.
        val pinned = Desktop.closeAll(Desktop.open(DesktopState(pinned = listOf("p")), "p"))
        assertEquals(listOf("p"), pinned.pinned)
    }

    @Test fun codecKeepsAllWindowsOfMultiWindowApps() {
        var s = DesktopState(pinned = DesktopApps.DEFAULT_PINNED)
        s = Desktop.openNew(Desktop.openNew(s, DesktopApps.FILES), DesktopApps.FILES)
        s = Desktop.open(s, DesktopApps.ai(4))
        s = Desktop.activate(s, s.tabsOf(DesktopApps.FILES)[1].id)
        assertEquals(s, DesktopCodec.decode(DesktopCodec.encode(s)))
        // Tek pencereli uygulamada yinelenen kayıt yine atlanır; çok pencereli uygulamada kimlik yinelenirse atlanır.
        val dup = DesktopCodec.decode("v2;next=9;active=;t=1,ai:3|2,ai:3|3,files|4,files|3,files;p=")!!
        assertEquals(listOf("ai:3", "files", "files"), dup.tabs.map { it.app })
        assertEquals(listOf(1, 3, 4), dup.tabs.map { it.id })
    }

    @Test fun clockFormat() {
        assertEquals("00:00", formatClock(0))
        assertEquals("09:05", formatClock(9 * 60 + 5))
        assertEquals("23:59", formatClock(1439))
        assertEquals("23:59", formatClock(-1))
    }
}
