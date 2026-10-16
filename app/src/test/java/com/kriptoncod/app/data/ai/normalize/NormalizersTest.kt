package com.kriptoncod.app.data.ai.normalize

import com.kriptoncod.app.data.providers.UpstreamProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizersTest {
    private fun read(choice: String) = ChoiceReader.read(Json.parseToJsonElement(choice).jsonObject)

    private fun run(modelId: String, choice: String, hint: UpstreamProvider? = null): NormalizedOutput =
        Normalizers.forModel(modelId, hint).normalize(read(choice))

    /** Düz metin içerikli bir `choice` nesnesi (JSON kaçışlı). */
    private fun text(content: String, finish: String = "stop"): String {
        val q = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"message":{"role":"assistant","content":"$q"},"finish_reason":"$finish"}"""
    }

    // ---- seçim

    @Test fun normalizerIsChosenByModelIdThenHint() {
        assertSame(ClaudeNormalizer, Normalizers.forModel("claude-opus-5-5"))
        assertSame(GeminiNormalizer, Normalizers.forModel("gemini-3.8-flash"))
        assertSame(OpenAiNormalizer, Normalizers.forModel("gpt-6-luna"))
        assertSame(DeepSeekNormalizer, Normalizers.forModel("deepseek-v4"))
        assertSame(KimiNormalizer, Normalizers.forModel("kimi-k3"))
        assertSame(GenericNormalizer, Normalizers.forModel("grok-4.7"))
        assertSame(KimiNormalizer, Normalizers.forModel("grok-4.7", UpstreamProvider.KIMI)) // kimlik tanınmıyor → ipucu
        assertSame(GeminiNormalizer, Normalizers.forModel("gemini-x", UpstreamProvider.CLAUDE)) // model, ipucundan üstün
    }

    // ---- ChoiceReader

    @Test fun readerSplitsClaudeStylePartsIntoVisibleReasoningAndTools() {
        val raw = read(
            """{"message":{"content":[
                {"type":"thinking","thinking":"önce düşün"},
                {"type":"text","text":"Merhaba "},
                {"type":"text","text":"dünya"},
                {"type":"tool_use","name":"x"},
                {"type":"image","x":1}
            ]},"finish_reason":"stop"}""",
        )
        assertEquals("Merhaba dünya", raw.visibleText)
        assertEquals("önce düşün", raw.reasoningText)
        assertTrue(raw.hasToolCalls)
    }

    @Test fun readerTreatsGeminiThoughtFlaggedPartsAsReasoning() {
        val raw = read("""{"message":{"content":[{"type":"text","text":"plan","thought":true},{"type":"text","text":"Kod"}]}}""")
        assertEquals("Kod", raw.visibleText)
        assertEquals("plan", raw.reasoningText)
    }

    @Test fun readerReadsReasoningFieldsAndLegacyText() {
        val a = read("""{"message":{"content":"x","reasoning_content":"r1"}}""")
        assertEquals("x", a.visibleText)
        assertEquals("r1", a.reasoningText)
        assertEquals("legacy", read("""{"text":"legacy","finish_reason":"stop"}""").visibleText)
        assertNull(read("""{"message":{"content":null}}""").visibleText)
    }

    @Test fun readerFallsBackToStopReason() {
        assertEquals("max_tokens", read("""{"message":{"content":"x"},"stop_reason":"max_tokens"}""").finishReason)
    }

    // ---- Claude

    @Test fun claudeThinkingPartsNeverReachTheOutput() {
        val out = run(
            "claude-sonnet-5-5",
            """{"message":{"content":[{"type":"thinking","thinking":"gizli"},{"type":"text","text":"Sonuç"}]},"finish_reason":"stop"}""",
        )
        assertEquals("Sonuç", out.text)
        assertEquals(Finish.COMPLETE, out.finish)
    }

    @Test fun claudeStopReasonsAreClassified() {
        assertEquals(Finish.TRUNCATED, run("claude-opus-5-5", text("yarım", "max_tokens")).finish)
        assertEquals(Finish.TRUNCATED, run("claude-opus-5-5", text("yarım", "pause_turn")).finish)
        assertEquals(Finish.FILTERED, run("claude-opus-5-5", text("x", "refusal")).finish)
        assertEquals(Finish.COMPLETE, run("claude-opus-5-5", text("tam", "end_turn")).finish)
    }

    // ---- Gemini

    @Test fun geminiUppercaseFinishReasonsAreClassified() {
        assertEquals(Finish.TRUNCATED, run("gemini-3.8-flash", text("yarım", "MAX_TOKENS")).finish)
        assertEquals(Finish.FILTERED, run("gemini-3.8-flash", text("x", "SAFETY")).finish)
        assertEquals(Finish.FILTERED, run("gemini-3.8-flash", text("x", "RECITATION")).finish)
        assertEquals(Finish.COMPLETE, run("gemini-3.8-flash", text("tam", "STOP")).finish)
    }

    @Test fun geminiThoughtTagsAreStripped() {
        assertEquals("Kod", run("gemini-4-argon", text("<thought>plan yap</thought>\n\nKod")).text)
    }

    // ---- OpenAI

    @Test fun openAiRefusalWithoutTextIsFiltered() {
        val out = run("gpt-6-astra", """{"message":{"content":null,"refusal":"Yardımcı olamam"},"finish_reason":"stop"}""")
        assertEquals("", out.text)
        assertEquals(Finish.FILTERED, out.finish)
    }

    @Test fun openAiReasoningContentIsIgnoredAndLengthIsTruncated() {
        val out = run("o3-mini", """{"message":{"content":"Cevap","reasoning_content":"zincir"},"finish_reason":"length"}""")
        assertEquals("Cevap", out.text)
        assertEquals(Finish.TRUNCATED, out.finish)
        assertEquals(Finish.FILTERED, run("gpt-6-luna", text("x", "content_filter")).finish)
    }

    // ---- DeepSeek

    @Test fun deepSeekOrphanThinkCloseKeepsOnlyTheAnswer() {
        assertEquals("Sonuç", run("deepseek-v4", text("uzun düşünce\n</think>\n\nSonuç")).text)
    }

    @Test fun deepSeekSpecialTokensAreRemovedAndResourceStopIsTruncated() {
        assertEquals("Kod", run("deepseek-chat", text("Kod<｜end▁of▁sentence｜>")).text)
        assertEquals(Finish.TRUNCATED, run("deepseek-chat", text("yarım", "insufficient_system_resource")).finish)
    }

    // ---- Kimi

    @Test fun kimiThinkBlockSpecialTokensAndToolSectionsAreRemoved() {
        assertEquals("Sonuç", run("kimi-k3", text("<think>x</think>Sonuç<|im_end|>")).text)
        val withTools = "Metin<|tool_calls_section_begin|><|tool_call_begin|>fn<|tool_call_end|><|tool_calls_section_end|>"
        assertEquals("Metin", run("kimi-k3", text(withTools)).text)
    }

    // ---- ortak davranış

    @Test fun genericKeepsOrphanCloseAndSpecialTokens() {
        assertEquals("a</think>b", run("grok-4.7", text("a</think>b")).text)
        assertEquals("x<|im_end|>", run("grok-4.7", text("x<|im_end|>")).text)
    }

    @Test fun nonLeadingThinkBlockIsKept() {
        val t = "Önce\n<think>x</think>\nSonra"
        assertEquals(t, run("grok-4.7", text(t)).text)
    }

    @Test fun unclosedLeadingThinkMeansEmptyOutput() {
        listOf("claude-opus-5-5", "gemini-x", "gpt-6-luna", "deepseek-v4", "kimi-k3", "grok-4.7").forEach {
            assertEquals(it, "", run(it, text("<think>bitmeyen düşünce")).text)
        }
    }

    @Test fun lineEndingsAndInvisibleCharsAreNormalized() {
        assertEquals("a\nb", run("grok-4.7", """{"message":{"content":"\uFEFFa\r\nb\u200B"}}""").text)
    }

    @Test fun outerMarkdownWrapperIsRemovedOnlyWhenItHidesPathBlocks() {
        val wrapped = "```markdown\n```app/Main.kt\nfun a()\n```\n```"
        assertEquals("```app/Main.kt\nfun a()\n```", run("grok-4.7", text(wrapped)).text)

        val doc = "```markdown\n# Başlık\n```"
        assertEquals(doc, run("grok-4.7", text(doc)).text)
    }

    @Test fun emptyContentGivesEmptyTextAndCompleteFinish() {
        val out = run("grok-4.7", """{"message":{"content":null},"finish_reason":"stop"}""")
        assertEquals("", out.text)
        assertFalse(out.finish == Finish.TRUNCATED)
    }
}
