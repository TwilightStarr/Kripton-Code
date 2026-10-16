package com.kriptoncod.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class GraphLayoutTest {
    private fun n(id: Long, kind: JobKind, cat: String? = null, w: Float = 0.5f) =
        GraphNodeUi(id, kind, cat, JobState.PENDING, w, "$id")

    private fun manyWork(count: Int): List<GraphNodeUi> =
        listOf(n(1, JobKind.PLAN, w = 1f)) +
            (0 until count).map { n(10L + it, JobKind.WORK, "a") } +
            listOf(n(900, JobKind.CATALOG_MERGE, "a"), n(901, JobKind.FINAL_MERGE, w = 1f))

    @Test fun emptyGraphHasNoSize() {
        val r = GraphLayout.layout(emptyList())
        assertTrue(r.nodes.isEmpty())
        assertEquals(0f, r.width, 0f)
        assertEquals(0f, r.height, 0f)
    }

    @Test fun rowWidthFollowsMaxPerRow() {
        val nodes = manyWork(20)
        val narrow = GraphLayout.layout(nodes, maxPerRow = 3)
        val wide = GraphLayout.layout(nodes, maxPerRow = 8)
        assertTrue(narrow.width < wide.width)
        assertTrue(narrow.height > wide.height) // dar ekranda satırlar çoğalır, genişlik taşmaz
        val perY = narrow.nodes.filter { it.node.kind == JobKind.WORK }.groupBy { it.y }
        assertTrue(perY.values.all { it.size <= 3 })
    }

    @Test fun nodesStayInsideBoundsAndNeverOverlap() {
        val r = GraphLayout.layout(manyWork(17), maxPerRow = 4, minSize = 48f, maxSize = 84f)
        r.nodes.forEach { p ->
            assertTrue(p.x - p.size / 2f >= 0f && p.x + p.size / 2f <= r.width)
            assertTrue(p.y - p.size / 2f >= 0f && p.y + p.size / 2f <= r.height)
        }
        for (i in r.nodes.indices) for (j in i + 1 until r.nodes.size) {
            val a = r.nodes[i]
            val b = r.nodes[j]
            val dist = hypot(a.x - b.x, a.y - b.y)
            assertTrue("düğümler çakışıyor", dist >= (a.size + b.size) / 2f)
        }
    }

    @Test fun minimumNodeSizeIsAComfortableTouchTarget() {
        val r = GraphLayout.layout(manyWork(3))
        assertTrue(r.nodes.all { it.size >= 48f })
    }
}
