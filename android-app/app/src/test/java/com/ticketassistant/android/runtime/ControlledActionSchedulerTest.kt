package com.ticketassistant.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledActionSchedulerTest {
    private val candidate = ActionCandidate(
        actionKey = "CLICK_BOOK_NOW",
        pageFingerprint = "page-a",
    )

    @Test
    fun `same action and fingerprint obey cooldown and retry limit`() {
        val scheduler = scheduler(maxAttempts = 2)
        val first = ready(scheduler.request(candidate, 0))
        scheduler.complete(first, ActionExecutionResult.PERFORMED, 10)

        val coolingDown = scheduler.request(candidate, 500)
        assertEquals(
            ActionBlockReason.UNCHANGED_PAGE_COOLDOWN,
            (coolingDown as ActionScheduleDecision.Wait).reason,
        )

        val retry = ready(scheduler.request(candidate, 810))
        assertEquals(2, retry.attemptNumber)
        scheduler.complete(retry, ActionExecutionResult.PERFORMED, 820)

        val exhausted = scheduler.request(candidate, 1_620)
        assertEquals(
            SafetyPauseReason.ACTION_RETRY_LIMIT,
            (exhausted as ActionScheduleDecision.SafetyPause).reason,
        )
    }

    @Test
    fun `page change allows a new action with monotonic id`() {
        val scheduler = scheduler()
        val first = ready(scheduler.request(candidate, 0))
        scheduler.complete(first, ActionExecutionResult.PERFORMED, 10)

        val second = ready(
            scheduler.request(
                candidate.copy(
                    actionKey = "CLICK_SEAT_ENTRY",
                    pageFingerprint = "page-b",
                ),
                300,
            ),
        )

        assertEquals(first.actionId + 1, second.actionId)
        assertEquals(1, second.attemptNumber)
    }

    @Test
    fun `different action on unchanged page waits for page change`() {
        val scheduler = scheduler()
        val first = ready(scheduler.request(candidate, 0))
        scheduler.complete(first, ActionExecutionResult.PERFORMED, 10)

        val decision = scheduler.request(
            candidate.copy(actionKey = "OTHER_ACTION"),
            1_000,
        )

        assertEquals(
            ActionBlockReason.PAGE_CHANGE_REQUIRED,
            (decision as ActionScheduleDecision.Wait).reason,
        )
    }

    @Test
    fun `pause invalidates delayed completion`() {
        val scheduler = scheduler()
        val action = ready(scheduler.request(candidate, 0))
        assertTrue(scheduler.isCurrent(action))

        scheduler.cancelPending()

        assertFalse(scheduler.isCurrent(action))
        assertEquals(
            ActionCompletion.IgnoredStaleAction,
            scheduler.complete(action, ActionExecutionResult.PERFORMED, 10),
        )
    }

    @Test
    fun `target invalid requests safety pause`() {
        val scheduler = scheduler()
        val action = ready(scheduler.request(candidate, 0))

        val completion = scheduler.complete(
            action,
            ActionExecutionResult.TARGET_INVALID,
            10,
        )

        assertEquals(
            SafetyPauseReason.ACTION_TARGET_INVALID,
            (completion as ActionCompletion.SafetyPause).reason,
        )
    }

    @Test
    fun `cancelled submission releases in flight action for retry`() {
        val scheduler = scheduler()
        val first = ready(scheduler.request(candidate, 0))

        assertEquals(
            ActionCompletion.Recorded,
            scheduler.complete(first, ActionExecutionResult.CANCELLED, 10),
        )

        val retry = ready(scheduler.request(candidate, 10))
        assertEquals(first.actionId + 1, retry.actionId)
        assertEquals(1, retry.attemptNumber)
    }

    private fun scheduler(maxAttempts: Int = 3) = ControlledActionScheduler(
        runId = "run-1",
        policy = ActionPolicy(
            minimumActionIntervalMillis = 250,
            unchangedPageCooldownMillis = 800,
            maxAttemptsPerFingerprint = maxAttempts,
        ),
    )

    private fun ready(decision: ActionScheduleDecision): ScheduledAction =
        (decision as ActionScheduleDecision.Ready).action
}
