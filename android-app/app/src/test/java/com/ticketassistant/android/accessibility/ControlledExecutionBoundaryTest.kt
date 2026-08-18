package com.ticketassistant.android.accessibility

import com.ticketassistant.android.runtime.ActionExecutionResult
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlledExecutionBoundaryTest {
    @Test
    fun `ordinary live node failure becomes target invalid`() {
        assertEquals(
            ActionExecutionResult.TARGET_INVALID,
            ControlledExecutionBoundary.run {
                error("live accessibility tree changed")
            },
        )
    }

    @Test(expected = CancellationException::class)
    fun `cancellation still stops action execution`() {
        ControlledExecutionBoundary.run {
            throw CancellationException("service disconnected")
        }
    }
}
