package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPlannerTest {

    private fun job(
        id: Long,
        kind: JobKind,
        state: JobState,
        catalog: String? = null,
        title: String? = null,
        body: String? = null,
    ) = Job(id = id, runId = 1, promptId = 0, state = state, updatedAt = 0, kind = kind, catalog = catalog, title = title, body = body)

    private val plan = job(1, JobKind.PLAN, JobState.DONE)
    private val a = job(2, JobKind.WORK, JobState.DONE, "K1", "Ana sayfa", "x")
    private val b = job(3, JobKind.WORK, JobState.FAILED, "K1", "Menü", "x")
    private val c = job(4, JobKind.WORK, JobState.DONE, "K2", "Ayarlar", "x")
    private val m1 = job(5, JobKind.CATALOG_MERGE, JobState.DONE, "K1")
    private val m2 = job(6, JobKind.CATALOG_MERGE, JobState.DONE, "K2")
    private val fin = job(7, JobKind.FINAL_MERGE, JobState.DONE)

    @Test fun failedWorkResetsItsCatalogMergeAndFinalOnly() {
        val ids = RetryPlanner.plan(b, listOf(plan, a, b, c, m1, m2, fin))
        assertEquals(listOf(3L, 5L, 7L), ids) // a, c ve K2 birleştirmesi korunur
    }

    @Test fun nonFailedJobYieldsNothing() {
        assertEquals(emptyList<Long>(), RetryPlanner.plan(a, listOf(plan, a, b, c, m1, m2, fin)))
    }

    @Test fun inputChainIsFollowedTransitively() {
        val dep = job(8, JobKind.WORK, JobState.DONE, "K2", "Ayarlar 2", "[[girdi: Menü]]\ngörev")
        val dep2 = job(9, JobKind.WORK, JobState.DONE, "K2", "Ayarlar 3", "[[girdi: Ayarlar 2]]\ngörev")
        val ids = RetryPlanner.plan(b, listOf(plan, a, b, c, m1, m2, fin, dep, dep2))
        assertEquals(setOf(3L, 8L, 9L, 5L, 6L, 7L), ids.toSet())
        assertEquals(3L, ids.first())
    }

    @Test fun pendingDownstreamIsLeftAlone() {
        val finPending = fin.copy(state = JobState.PENDING)
        val ids = RetryPlanner.plan(b, listOf(plan, a, b, c, m1, m2, finPending))
        assertEquals(listOf(3L, 5L), ids)
    }

    @Test fun failedCatalogMergeResetsItselfAndFinal() {
        val m1Failed = m1.copy(state = JobState.FAILED)
        assertEquals(listOf(5L, 7L), RetryPlanner.plan(m1Failed, listOf(plan, a, b.copy(state = JobState.DONE), c, m1Failed, m2, fin)))
    }

    @Test fun failedPlanResetsOnlyItself() {
        val p = plan.copy(state = JobState.FAILED)
        assertEquals(listOf(1L), RetryPlanner.plan(p, listOf(p)))
    }
}
