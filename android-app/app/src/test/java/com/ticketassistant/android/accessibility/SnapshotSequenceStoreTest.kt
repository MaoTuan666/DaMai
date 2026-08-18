package com.ticketassistant.android.accessibility

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotSequenceStoreTest {
    private val runIdsToClear = mutableSetOf<String>()

    @After
    fun tearDown() {
        runIdsToClear.forEach(SnapshotSequenceStore::clear)
    }

    @Test
    fun `sequence remains monotonic across service instances`() {
        val runId = trackedRunId("run-rebound")
        val firstService = SnapshotProducer(runId)
        val replacementService = SnapshotProducer(runId)

        assertEquals(1L, firstService.nextSequence())
        assertEquals(2L, replacementService.nextSequence())
        assertEquals(3L, firstService.nextSequence())
    }

    @Test
    fun `runs have independent sequences and completed run can be cleared`() {
        val firstRunId = trackedRunId("run-first")
        val secondRunId = trackedRunId("run-second")

        assertEquals(1L, SnapshotSequenceStore.next(firstRunId))
        assertEquals(2L, SnapshotSequenceStore.next(firstRunId))
        assertEquals(1L, SnapshotSequenceStore.next(secondRunId))

        SnapshotSequenceStore.clear(firstRunId)

        assertEquals(1L, SnapshotSequenceStore.next(firstRunId))
        assertEquals(2L, SnapshotSequenceStore.next(secondRunId))
    }

    private fun trackedRunId(runId: String): String = runId.also(runIdsToClear::add)

    private class SnapshotProducer(
        private val runId: String,
    ) {
        fun nextSequence(): Long = SnapshotSequenceStore.next(runId)
    }
}
