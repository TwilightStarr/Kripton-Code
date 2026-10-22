package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectiveParsingTest {
    @Test fun parsesVariablesRulesAndRetry() {
        val d = FlowDirectivesParser.parse(
            """
            [[değişken: uygulama = "Kervan Çağı"]]
            [[var: dil=Kotlin]]
            [[doğrula: min:20, içerir:"fun main, x", dosya, yer-tutucu-yok]]
            [[tekrar: 3]]
            [[tekrar-bekle: 2dk]]
            [[tekrar-hesap: aynı]]
            [[tekrar-ipucu: hayır]]
            [[hatada: devam]]
            [[hata-tekrar: 5]]
            [[kaynak: zorunlu]]
            Görev {{uygulama}}
            """.trimIndent(),
        )
        assertEquals(listOf(VariableDef("uygulama", "Kervan Çağı"), VariableDef("dil", "Kotlin")), d.variables)
        assertEquals(
            listOf(OutputRule.MinChars(20), OutputRule.Contains("fun main, x"), OutputRule.HasFile(null), OutputRule.NoPlaceholders),
            d.rules,
        )
        assertEquals(3, d.retry.maxRetries)
        assertEquals(120_000L, d.retry.delayMs)
        assertFalse(d.retry.switchAccount)
        assertFalse(d.retry.feedback)
        assertEquals(OnExhausted.CONTINUE, d.retry.onExhausted)
        assertEquals(5, d.retry.maxErrorAttempts)
        assertTrue(d.requireSources)
        assertEquals("Görev {{uygulama}}", d.body)
        assertTrue(d.issues.isEmpty())
    }

    @Test fun limitsAreCappedAndBadValuesReported() {
        val d = FlowDirectivesParser.parse("[[tekrar: 99]]\n[[tekrar-bekle: 5sa]]\n[[değişken: 1x = a]]\n[[doğrula: min:abc, regex:(]]\n[[hatada: belki]]\nx")
        assertEquals(FlowDirectivesParser.MAX_RETRIES, d.retry.maxRetries)
        assertEquals(FlowDirectivesParser.MAX_DELAY_MS, d.retry.delayMs)
        assertTrue(d.variables.isEmpty())
        assertTrue(d.rules.isEmpty())
        val codes = d.issues.map { it.code }
        assertTrue(IssueCode.BAD_VARIABLE in codes)
        assertTrue(IssueCode.BAD_RULE in codes)
        assertTrue(IssueCode.BAD_REGEX in codes)
        assertTrue(IssueCode.BAD_CHOICE in codes)
    }

    @Test fun definitionOnlyNodeIsNotShared() {
        val d = FlowDirectivesParser.parse("[[değişken: a = 1]]")
        assertTrue(d.isDefinitionOnly)
        assertFalse(d.share)
    }

    @Test fun dependenciesCombineInputsAndReferences() {
        val d = FlowDirectivesParser.parse("[[girdi: A]]\nBak {{çıktı:B}} ve {{özet:a}} ve {{dosya:C#x/y.kt}} {{ad}}")
        assertEquals(listOf("A", "B", "C"), d.dependencies)
    }
}

class PromptTemplateTest {
    private fun ctx(
        vars: Map<String, String> = emptyMap(),
        outs: Map<String, String?> = emptyMap(),
    ) = PromptTemplate.Context(
        vars = vars.mapKeys { PromptTemplate.normName(it.key) },
        builtins = PromptTemplate.builtins(7, "Başlık", "kat", 2, 0L, 1, java.time.ZoneOffset.UTC),
        output = { t -> outs.entries.firstOrNull { it.key.equals(t, true) }?.let { PromptTemplate.SourceOutput(t, it.value) } },
    )

    @Test fun substitutesVariablesDefaultsAndBuiltins() {
        val r = PromptTemplate.render("{{App}} {{dil|Go}} {{başlık}} #{{sıra}} {{tarih}} run{{run_id}}", ctx(mapOf("app" to "Kervan")))
        assertEquals("Kervan Go Başlık #2 1970-01-01 run7", r.text)
        assertTrue(r.missing.isEmpty())
    }

    @Test fun unknownTemplatesAndEscapesAreLeftAlone() {
        val r = PromptTemplate.render("""<p>{{ user.name }}</p> {{x y}} \{{app}} {{yok}}""", ctx(mapOf("app" to "A")))
        assertEquals("""<p>{{ user.name }}</p> {{x y}} {{app}} {{yok}}""", r.text)
    }

    @Test fun nestedVariablesAndCycles() {
        val r = PromptTemplate.render("{{a}}|{{c}}", ctx(mapOf("a" to "x-{{b}}", "b" to "{{app}}", "app" to "K", "c" to "{{c}}")))
        assertEquals("x-K|{{c}}", r.text)
    }

    @Test fun dollarAndBackslashInValuesAreLiteral() {
        val r = PromptTemplate.render("{{p}}", ctx(mapOf("p" to "cost \$1 \\n")))
        assertEquals("cost \$1 \\n", r.text)
    }

    @Test fun outputReferences() {
        val out = "```src/a.kt\nfun a() = 1\n```\n```src/b/c.kt\nfun c() = 2\n```"
        val c = ctx(outs = mapOf("A" to out, "Boş" to null))
        assertEquals(out, PromptTemplate.render("{{çıktı:A}}", c).text)
        assertEquals("src/a.kt\nsrc/b/c.kt", PromptTemplate.render("{{yollar:A}}", c).text)
        assertEquals("fun c() = 2", PromptTemplate.render("{{dosya:a#c.kt}}", c).text)
        assertEquals("fun a() = 1", PromptTemplate.render("{{blok:A}}", c).text)
        assertTrue(PromptTemplate.render("{{çıktı:A|10}}", c).text.contains("kısaltıldı"))
    }

    @Test fun missingSourcesAreReported() {
        val c = ctx(outs = mapOf("Boş" to null))
        val r = PromptTemplate.render("{{çıktı:Boş}} {{çıktı:Yok}} {{dosya:Boş#a.kt}}", c)
        assertEquals(listOf("Boş", "Yok"), r.missing)
        assertTrue(r.text.contains("[kaynak çıktı yok: Boş]"))
    }

    @Test fun outputReferenceInsideVariableIsNotResolved() {
        val r = PromptTemplate.render("{{v}}", ctx(mapOf("v" to "{{çıktı:A}}"), mapOf("A" to "x")))
        assertEquals("{{çıktı:A}}", r.text)
    }

    @Test fun lintFindsUnknownAndForwardSources() {
        val issues = PromptTemplate.lint("[[girdi: Yok]]\n{{çıktı:Sonraki}} {{çıktı:Önceki}}", earlier = listOf("Önceki"), others = listOf("Sonraki", "Önceki"))
        assertEquals(setOf(IssueCode.UNKNOWN_SOURCE, IssueCode.FORWARD_SOURCE), issues.map { it.code }.toSet())
        assertEquals(2, issues.size)
    }
}

class OutputRulesTest {
    private fun check(text: String, vararg rules: OutputRule) = OutputRules.check(text, rules.toList())

    @Test fun basicRules() {
        assertTrue(check("yeterince uzun bir metin", OutputRule.MinChars(5), OutputRule.Contains("UZUN")).isEmpty())
        assertEquals(1, check("kısa", OutputRule.MinChars(50)).size)
        assertEquals(1, check("abc", OutputRule.NotContains("b")).size)
        assertEquals(1, check("abc", OutputRule.MaxChars(2)).size)
        assertTrue(check("v1.2", OutputRule.Matches("""v\d+\.\d+""")).isEmpty())
    }

    @Test fun fileAndCompletenessRules() {
        val ok = "```src/A.kt\nclass A\n```"
        assertTrue(check(ok, OutputRule.HasFile(), OutputRule.HasFile("A.kt"), OutputRule.Complete).isEmpty())
        assertEquals(1, check("düz metin", OutputRule.HasFile()).size)
        assertEquals(1, check(ok, OutputRule.HasFile("B.kt")).size)
        assertEquals(1, check("```src/A.kt\nclass A", OutputRule.Complete).size)
    }

    @Test fun placeholderRule() {
        assertEquals(1, check("```A.kt\nfun a() {\n    // ...\n}\n```", OutputRule.NoPlaceholders).size)
        assertTrue(check("```A.kt\nfun a() = 1\n```", OutputRule.NoPlaceholders).isEmpty())
    }

    @Test fun feedbackListsReasons() {
        val f = OutputRules.feedback(listOf("neden 1"), 1, 2)
        assertTrue(f.contains("- neden 1"))
        assertTrue(f.contains("1/3"))
    }
}

class RetryPlannerReferenceTest {
    private fun job(id: Long, title: String, body: String, state: JobState = JobState.DONE, kind: JobKind = JobKind.WORK) =
        Job(id = id, runId = 1, promptId = id, state = state, updatedAt = 0, kind = kind, catalog = "k", title = title, body = body)

    @Test fun outputReferenceChainsAreResetLikeInputs() {
        val all = listOf(
            job(1, "A", "a", JobState.FAILED),
            job(2, "B", "x {{çıktı:A}}"),
            job(3, "C", "y {{özet:B}}"),
            job(4, "D", "bağımsız"),
        )
        val ids = RetryPlanner.plan(all[0], all)
        assertEquals(listOf(1L, 2L, 3L), ids.filter { it <= 3 })
        assertFalse(4L in ids)
        assertNull(ids.firstOrNull { it == 4L })
    }
}
