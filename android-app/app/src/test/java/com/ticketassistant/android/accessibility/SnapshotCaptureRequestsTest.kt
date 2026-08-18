package com.ticketassistant.android.accessibility

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotCaptureRequestsTest {
    @Test(timeout = 1_000)
    fun `request sent before collector starts is retained`() = runBlocking {
        val queue = SnapshotCaptureRequestQueue()

        assertTrue(queue.request("run-before-collector"))

        assertEquals("run-before-collector", queue.requests.first())
    }

    @Test(timeout = 1_000)
    fun `only latest unconsumed request is retained`() = runBlocking {
        val queue = SnapshotCaptureRequestQueue()

        assertTrue(queue.request("run-old"))
        assertTrue(queue.request("run-latest"))

        assertEquals("run-latest", queue.requests.first())
    }

    @Test
    fun `blank run id is rejected`() {
        val queue = SnapshotCaptureRequestQueue()

        assertFalse(queue.request("  "))
    }
}
