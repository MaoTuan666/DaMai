package com.ticketassistant.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSafetyGuardTest {
    @Test
    fun `runtime limit stops without an action`() {
        val guard = guard(maxRuntimeMillis = 1_000)

        val decision = guard.beforeAction(
            nowMillis = 1_000,
            countsAsSubmitAttempt = false,
        )

        assertEquals(
            RuntimeStopReason.MAX_RUNTIME_REACHED,
            (decision as SafetyLimitDecision.Stop).reason,
        )
    }

    @Test
    fun `reaching submit limit stops immediately after final allowed submit`() {
        val guard = guard(maxSubmitAttempts = 2)

        assertTrue(guard.beforeAction(0, true) is SafetyLimitDecision.Allowed)
        guard.recordCompletion(true, ActionExecutionResult.PERFORMED)
        assertTrue(guard.beforeAction(10, true) is SafetyLimitDecision.Allowed)
        val finalCompletion = guard.recordCompletion(
            true,
            ActionExecutionResult.PERFORMED,
        )

        assertEquals(2, guard.submitAttemptCount)
        assertEquals(
            RuntimeStopReason.MAX_SUBMIT_ATTEMPTS_REACHED,
            (finalCompletion as SafetyLimitDecision.Stop).reason,
        )
    }

    @Test
    fun `consecutive failures stop at configured limit`() {
        val guard = guard(maxFailures = 2)

        assertTrue(
            guard.recordCompletion(false, ActionExecutionResult.FAILED) is
                SafetyLimitDecision.Allowed,
        )
        val decision = guard.recordCompletion(false, ActionExecutionResult.FAILED)

        assertEquals(
            RuntimeStopReason.ACTION_FAILURE_LIMIT_REACHED,
            (decision as SafetyLimitDecision.Stop).reason,
        )
    }

    private fun guard(
        maxSubmitAttempts: Int = 3,
        maxRuntimeMillis: Long = 10_000,
        maxFailures: Int = 3,
    ) = RuntimeSafetyGuard(
        startedAtMillis = 0,
        limits = RuntimeLimits(
            maxSubmitAttempts = maxSubmitAttempts,
            maxRuntimeMillis = maxRuntimeMillis,
            maxConsecutiveActionFailures = maxFailures,
        ),
    )
}
