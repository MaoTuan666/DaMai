package com.ticketassistant.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotProbePolicyTest {
    @Test
    fun `stale and invalid snapshots keep the existing probe`() {
        listOf(0L, 7L, 6L).forEach { captureSequence ->
            assertEquals(
                SnapshotArrivalDisposition.IGNORE_AND_KEEP_PROBE,
                SnapshotProbePolicy.arrivalDisposition(
                    captureSequence = captureSequence,
                    lastObservedSequence = 7L,
                ),
            )
        }
    }

    @Test
    fun `newer snapshot can replace the existing probe`() {
        assertEquals(
            SnapshotArrivalDisposition.PROCESS,
            SnapshotProbePolicy.arrivalDisposition(
                captureSequence = 8L,
                lastObservedSequence = 7L,
            ),
        )
    }

    @Test
    fun `missing retry delay falls back to page poll interval`() {
        assertEquals(
            300L,
            SnapshotProbePolicy.retryDelayMillis(
                requestedDelayMillis = null,
                defaultDelayMillis = 300L,
            ),
        )
    }

    @Test
    fun `explicit retry delay is preserved`() {
        assertEquals(
            850L,
            SnapshotProbePolicy.retryDelayMillis(
                requestedDelayMillis = 850L,
                defaultDelayMillis = 300L,
            ),
        )
    }
}
