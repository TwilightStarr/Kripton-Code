package com.kriptoncod.app.data.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelScorerTest {
    private fun m(id: String, ctx: Long? = null) = RemoteModel(id, ctx)
    private fun score(id: String, ctx: Long? = null) = ModelScorer.score(m(id, ctx))!!

    @Test fun nonChatModelsAreExcluded() {
        listOf(
            "whisper-large-v3", "text-embedding-004", "gemini-2.5-flash-image", "playai-tts",
            "llama-guard-4-12b", "gemini-2.0-flash-live", "models/imagen-3",
        ).forEach { assertNull(it, ModelScorer.score(m(it))) }
    }

    @Test fun geminiProBeatsFlashBeatsFlashLite() {
        val pro = score("gemini-2.5-pro")
        val flash = score("gemini-2.5-flash")
        val lite = score("gemini-2.5-flash-lite")
        assertTrue(pro > flash)
        assertTrue(flash > lite)
    }

    @Test fun previewIsSlightlyPenalized() {
        assertTrue(score("gemini-2.5-pro") > score("gemini-2.5-pro-preview"))
    }

    @Test fun codeModelsAndLargerSizesScoreHigher() {
        assertTrue(score("qwen3-coder-480b") > score("llama-3.3-70b-versatile"))
        assertTrue(score("llama-3.3-70b-versatile") > score("gemma2-9b-it"))
    }

    @Test fun contextLengthAddsBonus() {
        assertTrue(score("x-model", 200_000) > score("x-model", 40_000))
        assertTrue(score("x-model", 40_000) > score("x-model", null))
    }

    @Test fun rankRecommendsCoderOverGeneralModels() {
        val r = ModelScorer.rank(listOf(m("llama-3.3-70b-versatile"), m("qwen3-coder-480b"), m("gemma2-9b-it")))
        assertEquals("qwen3-coder-480b", r.recommended!!.id)
        assertEquals(listOf("qwen3-coder-480b", "llama-3.3-70b-versatile", "gemma2-9b-it"), r.models.map { it.model.id })
    }

    @Test fun rankDropsUnsuitableModelsFromList() {
        val r = ModelScorer.rank(listOf(m("whisper-large-v3"), m("llama-3.3-70b-versatile")))
        assertEquals(listOf("llama-3.3-70b-versatile"), r.models.map { it.model.id })
    }

    @Test fun freeMarkerRestrictsRecommendationOnlyWhenFreeModelsExist() {
        val models = listOf(m("deepseek/deepseek-r1"), m("meta-llama/llama-3.3-70b-instruct:free"))
        assertEquals("deepseek/deepseek-r1", ModelScorer.rank(models).recommended!!.id)
        val free = ModelScorer.rank(models, ":free")
        assertEquals("meta-llama/llama-3.3-70b-instruct:free", free.recommended!!.id)
        assertEquals(2, free.models.size)
        assertEquals("deepseek/deepseek-r1", ModelScorer.rank(listOf(models[0]), ":free").recommended!!.id)
    }

    @Test fun emptyOrAllUnsuitableListHasNoRecommendation() {
        assertNull(ModelScorer.rank(emptyList()).recommended)
        assertNull(ModelScorer.rank(listOf(m("whisper-large-v3"))).recommended)
    }

    @Test fun tiesAreBrokenDeterministically() {
        val r = ModelScorer.rank(listOf(m("b-model"), m("a-model")))
        assertNotNull(r.recommended)
        assertEquals("a-model", r.recommended!!.id)
        assertEquals(listOf("a-model", "b-model"), r.models.map { it.model.id })
    }
}
