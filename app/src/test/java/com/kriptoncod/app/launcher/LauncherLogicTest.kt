package com.kriptoncod.app.launcher

import com.kriptoncod.app.launcher.home.Home
import com.kriptoncod.app.launcher.home.HomeCodec
import com.kriptoncod.app.launcher.home.HomeItem
import com.kriptoncod.app.launcher.home.HomeRow
import com.kriptoncod.app.launcher.home.HomeRows
import com.kriptoncod.app.launcher.home.PanelKind
import com.kriptoncod.app.launcher.notify.LNotification
import com.kriptoncod.app.launcher.notify.Notifs
import com.kriptoncod.app.launcher.search.Calc
import com.kriptoncod.app.launcher.search.SearchRank
import com.kriptoncod.app.launcher.search.TextFold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLogicTest {
    @Test fun calcBasics() {
        assertEquals(84.0, Calc.eval("12*(3+4)")!!, 1e-9)
        assertEquals(1024.0, Calc.eval("2^10")!!, 1e-9)
        assertEquals(-4.0, Calc.eval("-2^2")!!, 1e-9)
        assertEquals(4.0, Calc.eval("sqrt(16)")!!, 1e-9)
        assertEquals(0.5, Calc.eval("sin(30)")!!, 1e-9)
        assertEquals("0.3", Calc.format(Calc.eval("0,1+0,2")!!))
    }

    @Test fun calcRejectsNonExpressions() {
        assertNull(Calc.eval("42"))
        assertNull(Calc.eval("tarih 5"))
        assertNull(Calc.eval("1/0"))
        assertNull(Calc.eval("(1+2"))
    }

    @Test fun turkishFold() {
        assertEquals("sarj", TextFold.fold("Şarj"))
        assertEquals("isparta", TextFold.fold("ISPARTA"))
        assertEquals("ilk", TextFold.fold("İlk"))
    }

    @Test fun rankOrder() {
        assertTrue(SearchRank.score("kri", "Kripton") > SearchRank.score("kri", "Mikri"))
        assertTrue(SearchRank.score("code", "Kripton Code") in 50..60)
        assertEquals(20, SearchRank.score("krpt", "Kripton"))
        assertEquals(0, SearchRank.score("xyz", "Kripton"))
        assertEquals(listOf("Kripton", "Mikri"), SearchRank.rank("kri", listOf("Mikri", "Kripton")) { it })
    }

    @Test fun homeCodecRoundTrip() {
        var s = Home.initial(listOf("kripton", "files"))
        s = Home.addApp(s, "pkg:com.x", 0)
        s = Home.addItem(s, HomeItem.Widget(7, 3), 1)
        val back = HomeCodec.decode(HomeCodec.encode(s))
        assertEquals(s, back)
    }

    @Test fun homeCodecSkipsGarbageAndDuplicates() {
        val s = HomeCodec.decode("h1;p=a:x|a:x|zz|p:nope|w:abc/a:y")!!
        assertEquals(2, s.pageCount)
        assertEquals(listOf(HomeItem.App("x")), s.pages[0].items)
        assertNull(HomeCodec.decode("garbage"))
    }

    @Test fun homeOps() {
        var s = Home.initial(listOf("a", "b", "c"))
        assertTrue(s.hasPanel(PanelKind.CLOCK))
        s = Home.move(s, HomeItem.App("c").key, -1)
        val apps = s.pages[0].items.filterIsInstance<HomeItem.App>().map { it.app }
        assertEquals(listOf("a", "c", "b"), apps)
        s = Home.moveToPage(s, HomeItem.App("a").key, 1)
        assertTrue(s.pages[1].items.contains(HomeItem.App("a")))
        assertEquals(s, Home.addApp(s, "a", 0))
        s = Home.retainApps(s) { it != "b" }
        assertTrue(!s.hasApp("b"))
        assertEquals(1, Home.removePage(Home.addPage(HomeCodecHelper.single()), 1).pageCount)
    }

    @Test fun rowsChunkApps() {
        val rows = HomeRows.build(
            listOf(HomeItem.App("1"), HomeItem.App("2"), HomeItem.App("3"), HomeItem.Panel(PanelKind.CLOCK), HomeItem.App("4")),
            2,
        )
        assertEquals(4, rows.size)
        assertTrue(rows[2] is HomeRow.Single)
    }

    private fun n(key: String, pkg: String, t: Long, ongoing: Boolean = false, summary: Boolean = false, group: String? = null) =
        LNotification(key, pkg, pkg, "t$key", "x", "", t, ongoing, !ongoing, summary, groupKey = group)

    @Test fun notificationGroupingAndBadges() {
        val all = listOf(
            n("1", "a", 10, group = "g"), n("2", "a", 20, group = "g"), n("s", "a", 30, summary = true, group = "g"),
            n("3", "b", 40), n("4", "c", 5, ongoing = true),
        )
        val vis = Notifs.visible(all)
        assertTrue(vis.none { it.isSummary })
        val (events, ongoing) = Notifs.split(vis)
        assertEquals(1, ongoing.size)
        assertEquals(listOf("b", "a"), Notifs.group(events).map { it.pkg })
        assertEquals(mapOf("a" to 2, "b" to 1), Notifs.badgeCounts(all))
        assertNotNull(Notifs.ago(100_000, 0, "now"))
    }

    @Test fun prefsCodecs() {
        val m = mapOf("a.b" to 3, "c.d" to 1)
        assertEquals(m, LauncherPrefs.decodeCounts(LauncherPrefs.encodeCounts(m)))
        assertEquals(emptyMap<String, Int>(), LauncherPrefs.decodeCounts("bad,=,x=y"))
        assertEquals(7, LauncherPrefs.cleanColumns(99))
    }
}

private object HomeCodecHelper {
    fun single() = com.kriptoncod.app.launcher.home.HomeState()
}
