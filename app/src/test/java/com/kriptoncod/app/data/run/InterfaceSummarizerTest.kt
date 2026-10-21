package com.kriptoncod.app.data.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceSummarizerTest {

    @Test fun kotlinKeepsDeclarationsAndDropsBodies() {
        val code = """
            package demo

            import foo.Bar

            /** Belge. */
            class Repo(private val db: Db) {
                private val cache = mutableMapOf<String, Int>()

                suspend fun load(id: Long): Item {
                    val local = db.get(id)
                    return local
                }
            }

            interface Store { fun put(x: Int) }
            const val LIMIT = 5
        """.trimIndent()
        val s = InterfaceSummarizer.summarizeFile("demo/Repo.kt", code)
        assertTrue(s.contains("package demo"))
        assertTrue(s.contains("class Repo(private val db: Db) {"))
        assertTrue(s.contains("suspend fun load(id: Long): Item {"))
        assertTrue(s.contains("interface Store"))
        assertTrue(s.contains("const val LIMIT = 5"))
        assertFalse("gövde satırı özette olmamalı", s.contains("val local = db.get(id)"))
        assertFalse(s.contains("import foo.Bar"))
    }

    @Test fun pythonAndTypeScript() {
        val py = "import os\n\nclass A:\n    def run(self, x):\n        y = x + 1\n        return y\n\nasync def go():\n    pass\n"
        val sp = InterfaceSummarizer.summarizeFile("a.py", py)
        assertTrue(sp.contains("class A:") && sp.contains("def run(self, x):") && sp.contains("async def go():"))
        assertFalse(sp.contains("y = x + 1"))

        val ts = "export function add(a: number, b: number): number {\n  return a + b\n}\nexport default class X {}\nconst hidden = 1\n"
        val st = InterfaceSummarizer.summarizeFile("a.ts", ts)
        assertTrue(st.contains("export function add"))
        assertFalse(st.contains("return a + b"))
    }

    @Test fun smallConfigKeptWholeLargeConfigTruncated() {
        val small = "{\"a\": 1}"
        assertTrue(InterfaceSummarizer.summarizeFile("p.json", small).contains(small))
        val large = (1..200).joinToString("\n") { "key$it=value$it" }
        val s = InterfaceSummarizer.summarizeFile("big.properties", large)
        assertTrue(s.contains("key1=value1"))
        assertFalse(s.contains("key100=value100"))
        assertTrue(s.contains("satır daha"))
    }

    @Test fun outputIsMuchSmallerThanFullAndHasNoCodeFences() {
        val body = (1..400).joinToString("\n") { "        val x$it = compute($it)" }
        val full = "```app/Big.kt\nclass Big {\n    fun a() {\n$body\n    }\n}\n```"
        val s = InterfaceSummarizer.summarizeOutput(full)
        assertTrue(s.length < full.length / 10)
        assertFalse(s.contains("```"))
        assertTrue(s.contains("## dosya: app/Big.kt"))
    }

    @Test fun budgetListsRemainingPathsOnly() {
        val files = (1..30).map { "src/F$it.kt" to "class F$it { fun m$it() {} }" }
        val s = InterfaceSummarizer.summarizeFiles(files, budget = 400)
        assertTrue(s.contains("özet bütçesi doldu"))
        assertTrue(s.contains("src/F30.kt"))
        assertTrue(s.length < 1100)
        // Binlerce dosyada yol listesi de sınırlı kalır.
        val many = (1..5000).map { "src/pkg/F$it.kt" to "class F$it" }
        val capped = InterfaceSummarizer.summarizeFiles(many, budget = 400)
        assertTrue(capped.length < 1500)
        assertTrue(capped.contains("dosya daha)"))
    }

    @Test fun proseOutputIsTruncatedNotDropped() {
        val prose = "Tasarım notu. ".repeat(2000)
        val s = InterfaceSummarizer.summarizeOutput(prose, budget = 500)
        assertTrue(s.startsWith("Tasarım notu."))
        assertTrue(s.length < 700)
        assertEquals("kısa", InterfaceSummarizer.summarizeOutput("kısa"))
    }
}
