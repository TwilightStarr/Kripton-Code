package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.run.VerifyScripts.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyScriptsTest {

    private fun files(vararg p: Pair<String, String>) = p.associate { it.first to it.second.toByteArray() }

    @Test fun detectsProjectTypeFromRootMarkers() {
        assertEquals(ProjectType.NODE, VerifyScripts.detect(listOf("package.json", "src/a.js")))
        assertEquals(ProjectType.GRADLE, VerifyScripts.detect(listOf("build.gradle.kts", "app/src/Main.kt")))
        assertEquals(ProjectType.MAVEN, VerifyScripts.detect(listOf("pom.xml")))
        assertEquals(ProjectType.GO, VerifyScripts.detect(listOf("go.mod", "main.go")))
        assertEquals(ProjectType.CARGO, VerifyScripts.detect(listOf("Cargo.toml", "src/main.rs")))
        assertEquals(ProjectType.PYTHON, VerifyScripts.detect(listOf("requirements.txt", "app.py")))
        assertEquals(ProjectType.PYTHON, VerifyScripts.detect(listOf("main.py")))
        assertEquals(ProjectType.UNKNOWN, VerifyScripts.detect(listOf("README.md", "notes.txt")))
        // iç dizindeki işaret dosyası kök sayılmaz
        assertEquals(ProjectType.UNKNOWN, VerifyScripts.detect(listOf("sub/package.json")))
    }

    @Test fun everyKnownTypeHasAScriptAndUnknownHasNone() {
        for (t in ProjectType.entries) {
            val s = VerifyScripts.scriptFor(t)
            if (t == ProjectType.UNKNOWN) {
                assertNull(s)
            } else {
                assertNotNull(t.name, s)
                assertTrue(s!!.startsWith("#!/usr/bin/env bash"))
                assertFalse("§ yer tutucusu kalmamalı", s.contains('§'))
                assertTrue("test yoksa başarısız olmalı", s.contains("no_tests"))
                assertTrue(s.contains("set -euo pipefail"))
            }
        }
        assertTrue(VerifyScripts.scriptFor(ProjectType.NODE)!!.contains("npm test"))
        assertTrue(VerifyScripts.scriptFor(ProjectType.PYTHON)!!.contains("pytest"))
        assertTrue(VerifyScripts.scriptFor(ProjectType.GRADLE)!!.contains("\"\$G\""))
        assertTrue(VerifyScripts.scriptFor(ProjectType.GO)!!.contains("go test"))
    }

    @Test fun hasTestsPerType() {
        assertFalse(VerifyScripts.hasTests(ProjectType.NODE, files("package.json" to "{}", "index.js" to "x")))
        assertTrue(VerifyScripts.hasTests(ProjectType.NODE, files("package.json" to "{}", "tests/a.test.js" to "x")))
        assertTrue(VerifyScripts.hasTests(ProjectType.NODE, files("package.json" to "{\"scripts\":{\"test\":\"jest\"}}")))
        assertFalse(
            VerifyScripts.hasTests(
                ProjectType.NODE,
                files("package.json" to "{\"scripts\":{\"test\":\"echo \\\"Error: no test specified\\\" && exit 1\"}}"),
            ),
        )
        assertFalse(VerifyScripts.hasTests(ProjectType.PYTHON, files("app.py" to "x")))
        assertTrue(VerifyScripts.hasTests(ProjectType.PYTHON, files("tests/test_app.py" to "x")))
        assertTrue(VerifyScripts.hasTests(ProjectType.PYTHON, files("app_test.py" to "x")))
        assertFalse(VerifyScripts.hasTests(ProjectType.GRADLE, files("src/main/kotlin/A.kt" to "x")))
        assertTrue(VerifyScripts.hasTests(ProjectType.GRADLE, files("app/src/test/kotlin/ATest.kt" to "x")))
        assertTrue(VerifyScripts.hasTests(ProjectType.GO, files("a_test.go" to "x")))
        assertTrue(VerifyScripts.hasTests(ProjectType.CARGO, files("src/lib.rs" to "#[test] fn t() {}")))
        assertFalse(VerifyScripts.hasTests(ProjectType.CARGO, files("src/lib.rs" to "pub fn a() {}")))
        assertFalse(VerifyScripts.hasTests(ProjectType.UNKNOWN, files("a_test.go" to "x")))
    }

    @Test fun aiMayOnlyWriteTestPaths() {
        val n = ProjectType.NODE
        assertTrue(VerifyScripts.isAllowedTestPath(n, "tests/a.test.js"))
        assertTrue(VerifyScripts.isAllowedTestPath(n, "src/util.spec.ts"))
        assertFalse(VerifyScripts.isAllowedTestPath(n, "src/index.js"))
        assertFalse(VerifyScripts.isAllowedTestPath(n, "package.json"))
        assertFalse(VerifyScripts.isAllowedTestPath(n, ".github/workflows/tests/x.js"))
        assertFalse(VerifyScripts.isAllowedTestPath(n, VerifyScripts.PATH))
        assertFalse(VerifyScripts.isAllowedTestPath(n, "_kripton/tests/a.test.js"))
        assertTrue(VerifyScripts.isAllowedTestPath(ProjectType.PYTHON, "tests/test_x.py"))
        assertFalse(VerifyScripts.isAllowedTestPath(ProjectType.PYTHON, "x.py"))
        assertTrue(VerifyScripts.isAllowedTestPath(ProjectType.GRADLE, "app/src/test/kotlin/XTest.kt"))
        assertFalse(VerifyScripts.isAllowedTestPath(ProjectType.GRADLE, "app/src/main/kotlin/X.kt"))
        assertTrue(VerifyScripts.isAllowedTestPath(ProjectType.GO, "pkg/x_test.go"))
        assertFalse(VerifyScripts.isAllowedTestPath(ProjectType.GO, "pkg/x.go"))
    }
}
