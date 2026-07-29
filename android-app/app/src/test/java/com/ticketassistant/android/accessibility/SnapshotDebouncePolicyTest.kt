package com.ticketassistant.android.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotDebouncePolicyTest {
    private val policy = SnapshotDebouncePolicy(
        stabilityWindowMillis = 180,
        maximumWaitMillis = 800,
    )

    @Test
    fun `isolated event waits for full stability window`() {
        assertEquals(180L, policy.delayMillis(1_000, 1_000))
    }

    @Test
    fun `continuous events cannot postpone capture beyond maximum wait`() {
        assertEquals(50L, policy.delayMillis(1_000, 1_750))
        assertEquals(0L, policy.delayMillis(1_000, 1_800))
        assertEquals(0L, policy.delayMillis(1_000, 1_900))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `event timestamp before burst start is rejected`() {
        policy.delayMillis(1_000, 999)
    }
}
