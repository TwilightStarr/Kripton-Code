package com.kriptoncod.app.data.kimi

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KimiCodeRepositoryTest {

    private fun repo() = KimiCodeRepository(Files.createTempDirectory("kimi-test").toFile())

    @Test
    fun `collect parses and stores safe blocks`() {
        val r = repo()
        val n = r.collect(1L, "Özet metin\n```src/main/A.kt\nclass A\n```\n")
        assertEquals(1, n)
        val blocks = r.listBlocks(1L)
        assertEquals(1, blocks.size)
        assertEquals("src/main/A.kt", blocks[0].path)
        assertTrue(blocks[0].content.contains("class A"))
    }

    @Test
    fun `unsafe paths are rejected`() {
        val r = repo()
        val n = r.collect(1L, "```../evil.txt\nx\n```")
        assertEquals(0, n)
        assertFalse(r.hasOutputs(1L))
    }

    @Test
    fun `same path later wins`() {
        val r = repo()
        r.collect(1L, "```a.kt\nv1\n```")
        r.collect(1L, "```a.kt\nv2\n```")
        assertEquals(1, r.listBlocks(1L).size)
        assertEquals("v2", r.listBlocks(1L)[0].content.trim())
    }

    @Test
    fun `clear removes run data`() {
        val r = repo()
        r.collect(1L, "```a.kt\nv1\n```")
        assertTrue(r.hasOutputs(1L))
        r.clear(1L)
        assertFalse(r.hasOutputs(1L))
        assertEquals(0, r.listBlocks(1L).size)
    }
}
