package com.kriptoncod.app.domain

/** Grafikte çizilecek bir düğümün girdisi. [weight] 0..1: hesabın göreli gücü (büyük = güçlü). */
data class GraphNodeUi(
    val id: Long,
    val kind: JobKind,
    val catalog: String?,
    val state: JobState,
    val weight: Float,
    val label: String,
)

data class PlacedNode(val node: GraphNodeUi, val x: Float, val y: Float, val size: Float)

data class GraphEdge(val from: Long, val to: Long)

data class GraphLayoutResult(
    val nodes: List<PlacedNode>,
    val edges: List<GraphEdge>,
    val width: Float,
    val height: Float,
)

/**
 * Yukarıdan aşağıya katmanlı yerleşim: PLAN, WORK, CATALOG_MERGE, FINAL_MERGE.
 * Düğüm çapı güce göre [minSize]..[maxSize] arasındadır. Kalabalık katmanlar satırlara sarılır.
 * Birimler dp gibi düşünülür; x/y düğüm merkezidir.
 */
object GraphLayout {
    private val LAYERS = listOf(JobKind.PLAN, JobKind.WORK, JobKind.CATALOG_MERGE, JobKind.FINAL_MERGE)

    fun layout(
        nodes: List<GraphNodeUi>,
        maxPerRow: Int = 6,
        minSize: Float = 48f,
        maxSize: Float = 84f,
        gap: Float = 20f,
        padding: Float = 16f,
    ): GraphLayoutResult {
        if (nodes.isEmpty()) return GraphLayoutResult(emptyList(), emptyList(), 0f, 0f)
        val cell = maxSize + gap
        val perRow = maxPerRow.coerceAtLeast(1)

        // Katalog sırası: ilk görülme sırasına göre; WORK düğümleri katalog bazında kümelenir.
        val catalogOrder = nodes.mapNotNull { it.catalog }.distinct()
        val byLayer = LAYERS.associateWith { kind ->
            val inKind = nodes.filter { it.kind == kind }
            if (kind == JobKind.WORK) inKind.sortedBy { catalogOrder.indexOf(it.catalog) } else inKind
        }

        val widest = byLayer.values.maxOf { minOf(it.size, perRow) }.coerceAtLeast(1)
        val width = padding * 2 + widest * cell - gap
        val placed = mutableListOf<PlacedNode>()
        var y = padding
        for (kind in LAYERS) {
            val layer = byLayer.getValue(kind)
            if (layer.isEmpty()) continue
            layer.chunked(perRow).forEach { row ->
                val rowWidth = row.size * cell - gap
                var x = (width - rowWidth) / 2f
                row.forEach { n ->
                    val size = minSize + n.weight.coerceIn(0f, 1f) * (maxSize - minSize)
                    placed += PlacedNode(n, x + maxSize / 2f, y + maxSize / 2f, size)
                    x += cell
                }
                y += cell
            }
            y += gap // katmanlar arası ek boşluk
        }

        val edges = mutableListOf<GraphEdge>()
        val plan = nodes.firstOrNull { it.kind == JobKind.PLAN }
        val final = nodes.firstOrNull { it.kind == JobKind.FINAL_MERGE }
        val merges = nodes.filter { it.kind == JobKind.CATALOG_MERGE }
        nodes.filter { it.kind == JobKind.WORK }.forEach { w ->
            plan?.let { edges += GraphEdge(it.id, w.id) }
            merges.firstOrNull { it.catalog == w.catalog }?.let { edges += GraphEdge(w.id, it.id) }
        }
        if (final != null) merges.forEach { edges += GraphEdge(it.id, final.id) }
        return GraphLayoutResult(placed, edges, width, y - gap + padding)
    }
}
