package com.ticketassistant.android.runtime

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveRunStoreTest {
    @After
    fun tearDown() {
        ActiveRunStore.disarm()
    }

    @Test
    fun `arm stores the task identity and run token`() {
        ActiveRunStore.arm(
            runId = "run-new",
            taskId = 42,
            startedAtEpochMillis = 123,
        )

        val session = requireNotNull(ActiveRunStore.session.value)
        assertEquals("run-new", session.runId)
        assertEquals(42L, session.taskId)
        assertEquals(123L, session.startedAtEpochMillis)
        assertTrue(ActiveRunStore.isActive("run-new"))
    }

    @Test
    fun `old run cannot disarm the current run`() {
        ActiveRunStore.arm("run-current", 7, 123)

        ActiveRunStore.disarm("run-old")

        assertTrue(ActiveRunStore.isActive("run-current"))
        assertFalse(ActiveRunStore.isActive("run-old"))
    }
}
