package com.kriptoncod.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test fun parsesTextFormat() {
        val text = """
            ön yazı yok sayılır
            ## KATALOG: Arayüz
            ### PROMPT: Ana sayfa
            Ana sayfayı yaz.
            İkinci satır.
            ### PROMPT: Menü
            Menüyü yaz.
            ## KATALOG: Sunucu
            ### PROMPT: API
            Uç noktaları yaz.
            ## KATALOG: Boş
        """.trimIndent()
        val c = CatalogParser.parseText(text)
        assertEquals(listOf("Arayüz", "Sunucu"), c.map { it.name })
        assertEquals(listOf("Ana sayfa", "Menü"), c[0].prompts.map { it.title })
        assertEquals("Ana sayfayı yaz.\nİkinci satır.", c[0].prompts[0].body)
    }

    @Test fun noHeadersMeansEmpty() {
        assertTrue(CatalogParser.parseText("sadece metin").isEmpty())
    }

    @Test fun parsesZipFoldersAsCatalogs() {
        fun e(p: String, t: String) = p to t.toByteArray()
        val c = CatalogParser.parseZip(
            listOf(
                e("ui/anasayfa.md", "A"), e("ui/menu.txt", "B"), e("api/auth.md", "C"),
                e("giris.md", "D"), e("__MACOSX/x.md", "yok"), e("ui/resim.png", "yok"),
            ),
        )
        assertEquals(listOf("api", "ui", "genel").sorted(), c.map { it.name }.sorted())
        assertEquals(listOf("anasayfa", "menu"), c.first { it.name == "ui" }.prompts.map { it.title })
    }

    @Test fun stripsSingleWrapperFolder() {
        fun e(p: String, t: String) = p to t.toByteArray()
        val c = CatalogParser.parseZip(listOf(e("proje/ui/a.md", "A"), e("proje/api/b.md", "B")))
        assertEquals(setOf("ui", "api"), c.map { it.name }.toSet())
    }

    @Test fun zipRootFileInCatalogFormatIsParsed() {
        val md = "## KATALOG: X\n### PROMPT: p\ngövde"
        val c = CatalogParser.parseZip(listOf("katalog.md" to md.toByteArray()))
        assertEquals("X", c.single().name)
        assertEquals("gövde", c.single().prompts.single().body)
    }

    @Test fun plannerPromptRoundTripsAsFallback() {
        val p = CatalogParser.plannerPrompt(listOf(CatalogPrompt("a", "bir"), CatalogPrompt("b", "iki")))
        val c = CatalogParser.parseText(p).single()
        assertEquals(CatalogParser.DEFAULT_CATALOG, c.name)
        assertEquals(listOf("a", "b"), c.prompts.map { it.title })
    }

    @Test fun strongerAccountGetsMoreWork() {
        val out = WorkAssigner.assign(List(9) { 100 }, listOf(WorkAssigner.Candidate(1, 9), WorkAssigner.Candidate(2, 1)))
        assertTrue(out.count { it == 1L } > out.count { it == 2L })
        assertEquals(1L, out[0])
        assertTrue(WorkAssigner.assign(listOf(5), emptyList()).single() == null)
        assertNull(WorkAssigner.assign(listOf(5), emptyList())[0])
    }

    @Test fun layoutSizesByWeightAndLinksLayers() {
        fun n(id: Long, k: JobKind, cat: String?, w: Float) = GraphNodeUi(id, k, cat, JobState.PENDING, w, "$id")
        val r = GraphLayout.layout(
            listOf(
                n(1, JobKind.PLAN, null, 1f), n(2, JobKind.WORK, "a", 0f), n(3, JobKind.WORK, "a", 1f),
                n(4, JobKind.CATALOG_MERGE, "a", 0.5f), n(5, JobKind.FINAL_MERGE, null, 1f),
            ),
        )
        val size = r.nodes.associate { it.node.id to it.size }
        assertTrue(size.getValue(3) > size.getValue(2))
        assertEquals(5, r.edges.size) // plan->2, plan->3, 2->4, 3->4, 4->5 : 5
        val y = r.nodes.associate { it.node.id to it.y }
        assertTrue(y.getValue(1) < y.getValue(2) && y.getValue(2) < y.getValue(4) && y.getValue(4) < y.getValue(5))
    }
}
