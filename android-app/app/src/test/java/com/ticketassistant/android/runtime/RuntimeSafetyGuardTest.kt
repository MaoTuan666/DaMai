package com.ticketassistant.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeSafetyGuardTest {
    @Test
    fun `consecutive failures stop at configured limit`() {
        val guard = guard(maxFailures = 2)

        guard.recordCompletion(ActionExecutionResult.FAILED)
        val decision = guard.recordCompletion(ActionExecutionResult.FAILED)

        assertEquals(
            RuntimeStopReason.ACTION_FAILURE_LIMIT_REACHED,
            (decision as SafetyLimitDecision.Stop).reason,
        )
    }

    private fun guard(
        maxFailures: Int = 3,
    ) = RuntimeSafetyGuard(
        limits = RuntimeLimits(
            maxConsecutiveActionFailures = maxFailures,
        ),
    )
}
