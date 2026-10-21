package com.kriptoncod.app.data.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergePlannerTest {

    private fun src(name: String, id: Long, vararg files: Pair<String, String>) = MergePlanner.Source(name, id, files.joinToString("\n\n") { (p, c) -> "```$p\n$c\n```" })

    private fun parse(text: String) = CodeBlockParser.parse(text).associate { it.path to it.content }

    @Test fun disjointFilesNeedNoAiAndNothingIsDropped() {
        val plan = MergePlanner.plan(
            listOf(
                src("A", 1, "app/A.kt" to "class A", "app/Util.kt" to "object Util"),
                src("B", 2, "app/B.kt" to "class B"),
            ),
        )
        assertFalse(plan.needsAi)
        assertEquals(0, plan.conflictCount)
        val out = parse(MergePlanner.assemble(plan, null).text)
        assertEquals(setOf("app/A.kt", "app/Util.kt", "app/B.kt"), out.keys)
        assertEquals("class B", out["app/B.kt"])
    }

    @Test fun identicalVersionsAreNotAConflict() {
        val plan = MergePlanner.plan(
            listOf(src("A", 1, "x.txt" to "aynı\r\n"), src("B", 2, "x.txt" to "aynı")),
        )
        assertFalse(plan.needsAi)
        assertEquals(1, plan.files.size)
    }

    @Test fun differingVersionsAreTheOnlyThingSentToAi() {
        val big = "    println(\"keep\")\n".repeat(50)
        val plan = MergePlanner.plan(
            listOf(
                src("A", 1, "app/Main.kt" to "fun a() {}", "app/Keep.kt" to "class Keep {\n$big}"),
                src("B", 2, "app/Main.kt" to "fun b() {}"),
            ),
        )
        assertTrue(plan.needsAi)
        assertEquals(listOf("app/Main.kt"), plan.aiConflicts.keys.toList())
        val prompt = MergePlanner.conflictPrompt(plan, "Çakışma var.")
        assertTrue(prompt.contains("fun a() {}") && prompt.contains("fun b() {}"))
        // Çakışmasız dosyanın gövdesi AI'a gitmez; yalnız bildirim özeti bağlam olarak gider.
        assertFalse(prompt.contains(big))
        assertTrue(prompt.contains("app/Keep.kt"))
    }

    @Test fun aiResolutionReplacesOnlyConflictsAndKeepsUntouchedFiles() {
        val plan = MergePlanner.plan(
            listOf(
                src("A", 1, "app/Main.kt" to "fun a() {}", "app/Keep.kt" to "class Keep"),
                src("B", 2, "app/Main.kt" to "fun b() {}"),
            ),
        )
        val ai = "```app/Main.kt\nfun a() {}\nfun b() {}\n```"
        val out = parse(MergePlanner.assemble(plan, ai).text)
        assertEquals("fun a() {}\nfun b() {}", out["app/Main.kt"])
        assertEquals("class Keep", out["app/Keep.kt"])
    }

    @Test fun aiCannotOverwriteNonConflictingFiles() {
        val plan = MergePlanner.plan(
            listOf(
                src("A", 1, "app/Main.kt" to "fun a() {}", "app/Keep.kt" to "class Keep"),
                src("B", 2, "app/Main.kt" to "fun b() {}"),
            ),
        )
        val ai = "```app/Main.kt\nfun a() {}\nfun b() {}\n```\n```app/Keep.kt\nclass Hacked\n```"
        val res = MergePlanner.assemble(plan, ai)
        assertEquals("class Keep", parse(res.text)["app/Keep.kt"])
        assertTrue(res.notes.any { it.contains("app/Keep.kt") })
    }

    @Test fun missingOrTruncatedAiAnswerFallsBackToLongestVersion() {
        val long = "line\n".repeat(100)
        val plan = MergePlanner.plan(
            listOf(src("A", 1, "f.kt" to long, "g.kt" to "aa"), src("B", 2, "f.kt" to "short", "g.kt" to "bbbb")),
        )
        // f.kt: AI çok kısa döndü; g.kt: AI hiç döndürmedi.
        val res = MergePlanner.assemble(plan, "```f.kt\ntiny\n```")
        val out = parse(res.text)
        assertEquals(long.trimEnd('\n'), out["f.kt"])
        assertEquals("bbbb", out["g.kt"])
        assertEquals(2, res.notes.size)
    }

    @Test fun conflictsOverBudgetSkipAiAndTakeLongest() {
        val plan = MergePlanner.plan(
            listOf(src("A", 1, "f.kt" to "x".repeat(100)), src("B", 2, "f.kt" to "y".repeat(300))),
            maxConflictChars = 50,
        )
        assertFalse(plan.needsAi)
        assertEquals(1, plan.codeConflicts.size)
        assertEquals("y".repeat(300), parse(MergePlanner.assemble(plan, null).text)["f.kt"])
    }

    @Test fun proseOnlySourceIsPreservedAsNote() {
        val plan = MergePlanner.plan(listOf(MergePlanner.Source("Spec", 7, "Sadece açıklama metni")))
        val out = parse(MergePlanner.assemble(plan, null).text)
        assertEquals(1, out.size)
        assertTrue(out.keys.single().startsWith("notes/Spec_7"))
        assertEquals("Sadece açıklama metni", out.values.single())
    }

    @Test fun markdownWithNestedFencesRoundTrips() {
        val md = "# Başlık\n\n```kotlin\nval a = 1\n```\n\nson"
        val plan = MergePlanner.plan(listOf(src("A", 1, "README.md" to md, "a.kt" to "val a = 1")))
        val out = parse(MergePlanner.assemble(plan, null).text)
        assertEquals(md, out["README.md"])
        assertEquals("val a = 1", out["a.kt"])
    }

    @Test fun unsafePathsAreDropped() {
        val plan = MergePlanner.plan(listOf(src("A", 1, "../evil.sh" to "rm -rf /", "ok.txt" to "tamam")))
        assertEquals(setOf("ok.txt"), plan.files.keys)
    }
}
