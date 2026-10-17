package com.kriptoncod.app.data.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** XML denetimi android.util.Xml kullandığından Robolectric ile çalışır. */
@RunWith(RobolectricTestRunner::class)
class SyntaxCheckTest {

    private fun ok(path: String, code: String) = assertEquals("$path temiz olmalı", emptyList<SyntaxCheck.Issue>(), SyntaxCheck.check(path, code))
    private fun bad(path: String, code: String) = assertTrue("$path uyarı vermeli", SyntaxCheck.check(path, code).isNotEmpty())

    @Test fun balancedKotlinIsClean() = ok("A.kt", """
        class A {
            fun f(x: Int): String {
                val s = "bir ${'$'}{x + 1} { parantez" // yorumda ) var
                /* blok ( */
                return s
            }
        }
    """.trimIndent())

    @Test fun truncatedKotlinIsFlagged() {
        val issues = SyntaxCheck.check("A.kt", "class A {\n    fun f() {\n        println(1)\n")
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("kapanmamış"))
    }

    @Test fun mismatchedBracketsAreFlagged() = bad("A.java", "class A { void f() { int[] a = new int[2); } }")

    @Test fun extraClosingIsFlagged() = bad("a.js", "function f() { return 1; }}")

    @Test fun kotlinNestedTemplateQuotesAreHandled() =
        ok("A.kt", "val x = \"a ${'$'}{map[\"k\"]} b\"\nval y = '\"'\n")

    @Test fun dartSingleQuoteTemplateWithNestedQuotes() =
        ok("a.dart", "final s = '\${m['a']} yok';\nvoid f() {}\n")

    @Test fun kotlinRawStringWithBracesIsSkipped() =
        ok("A.kt", "val s = \"\"\"\n{ ( [ \n\"\"\"\nfun f() {}\n")

    @Test fun unterminatedBlockCommentIsFlagged() = bad("A.kt", "fun f() {}\n/* hiç kapanmıyor\n")

    @Test fun unterminatedStringIsFlagged() = bad("A.kt", "val s = \"kesik\nfun f() {}\n")

    @Test fun jsTemplateLiteralWithBraces() =
        ok("a.ts", "const s = `a \${f({x: 1})} b`;\nexport {};\n")

    @Test fun pythonTripleQuotesAndHashComments() = ok("a.py", "def f():\n    \"\"\"doc ( \"\"\"\n    return [1, 2]  # ) yorum\n")

    @Test fun validJsonIsClean() = ok("a.json", """{"a": [1, 2, {"b": null}]}""")

    @Test fun truncatedJsonIsFlagged() = bad("a.json", """{"a": [1, 2""")

    @Test fun tsconfigAllowsComments() = ok("tsconfig.json", "{ // yorum\n \"a\": 1 }")

    @Test fun validXmlIsClean() = ok("AndroidManifest.xml", """<manifest xmlns:android="x"><application android:name="a"/></manifest>""")

    @Test fun unclosedXmlTagIsFlagged() = bad("a.xml", "<a><b></a>")

    @Test fun yamlTabIndentIsFlagged() = bad("w.yml", "jobs:\n\tbuild: 1\n")

    @Test fun yamlSpacesAreClean() = ok("w.yml", "jobs:\n  build: 1\n")

    @Test fun unknownExtensionIsIgnored() = ok("README.md", "( [ {")

    @Test fun checkOutputParsesFencedBlocks() {
        val raw = "```lib/a.kt\nfun f() {\n```\n\n```lib/b.kt\nfun g() {}\n```\n"
        val issues = SyntaxCheck.checkOutput(raw)
        assertEquals(listOf("lib/a.kt"), issues.map { it.path })
    }
}
