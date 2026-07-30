package com.ticketassistant.android.runtime

data class ActionPolicy(
    val minimumActionIntervalMillis: Long,
    val unchangedPageCooldownMillis: Long,
    val maxAttemptsPerFingerprint: Int,
) {
    init {
        require(minimumActionIntervalMillis >= 0)
        require(unchangedPageCooldownMillis >= minimumActionIntervalMillis)
        require(maxAttemptsPerFingerprint >= 1)
    }
}

data class ActionCandidate(
    val actionKey: String,
    val pageFingerprint: String,
) {
    init {
        require(actionKey.isNotBlank())
        require(pageFingerprint.isNotBlank())
    }
}

data class ScheduledAction(
    val runId: String,
    val actionId: Long,
    val candidate: ActionCandidate,
    val attemptNumber: Int,
    val scheduledAtMillis: Long,
)

enum class ActionBlockReason {
    STOPPED,
    ACTION_IN_FLIGHT,
    MINIMUM_INTERVAL,
    UNCHANGED_PAGE_COOLDOWN,
    PAGE_CHANGE_REQUIRED,
}

sealed interface ActionScheduleDecision {
    data class Ready(val action: ScheduledAction) : ActionScheduleDecision
    data class Wait(val reason: ActionBlockReason, val retryAfterMillis: Long? = null) :
        ActionScheduleDecision

    data class SafetyPause(val reason: SafetyPauseReason) : ActionScheduleDecision
}

enum class ActionExecutionResult {
    PERFORMED,
    FAILED,
    TARGET_INVALID,
    CANCELLED,
}

sealed interface ActionCompletion {
    data object Recorded : ActionCompletion
    data object IgnoredStaleAction : ActionCompletion
    data class SafetyPause(val reason: SafetyPauseReason) : ActionCompletion
}

class ControlledActionScheduler(
    private val runId: String,
    private val policy: ActionPolicy,
) {
    private var nextActionId = 1L
    private var stopped = false
    private var inFlight: ScheduledAction? = null
    private var awaitingPageChange: CompletedAction? = null
    private var lastActionAtMillis: Long? = null

    @Synchronized
    fun request(
        candidate: ActionCandidate,
        nowMillis: Long,
    ): ActionScheduleDecision {
        if (stopped) return ActionScheduleDecision.Wait(ActionBlockReason.STOPPED)
        if (inFlight != null) {
            return ActionScheduleDecision.Wait(ActionBlockReason.ACTION_IN_FLIGHT)
        }

        var attemptNumber = 1
        val awaiting = awaitingPageChange
        if (awaiting != null) {
            if (candidate.pageFingerprint != awaiting.pageFingerprint) {
                awaitingPageChange = null
            } else {
                if (candidate.actionKey != awaiting.actionKey) {
                    return ActionScheduleDecision.Wait(ActionBlockReason.PAGE_CHANGE_REQUIRED)
                }
                val elapsed = nowMillis - awaiting.completedAtMillis
                if (elapsed < policy.unchangedPageCooldownMillis) {
                    return ActionScheduleDecision.Wait(
                        reason = ActionBlockReason.UNCHANGED_PAGE_COOLDOWN,
                        retryAfterMillis = policy.unchangedPageCooldownMillis - elapsed,
                    )
                }
                if (awaiting.attemptNumber >= policy.maxAttemptsPerFingerprint) {
                    return ActionScheduleDecision.SafetyPause(
                        SafetyPauseReason.ACTION_RETRY_LIMIT,
                    )
                }
                attemptNumber = awaiting.attemptNumber + 1
            }
        }

        val lastActionAt = lastActionAtMillis
        if (lastActionAt != null) {
            val elapsed = nowMillis - lastActionAt
            if (elapsed < policy.minimumActionIntervalMillis) {
                return ActionScheduleDecision.Wait(
                    reason = ActionBlockReason.MINIMUM_INTERVAL,
                    retryAfterMillis = policy.minimumActionIntervalMillis - elapsed,
                )
            }
        }

        return ActionScheduleDecision.Ready(
            ScheduledAction(
                runId = runId,
                actionId = nextActionId++,
                candidate = candidate,
                attemptNumber = attemptNumber,
                scheduledAtMillis = nowMillis,
            ).also { inFlight = it },
        )
    }

    @Synchronized
    fun complete(
        action: ScheduledAction,
        result: ActionExecutionResult,
        completedAtMillis: Long,
    ): ActionCompletion {
        val current = inFlight
        if (
            stopped ||
            current == null ||
            current.runId != action.runId ||
            current.actionId != action.actionId
        ) {
            return ActionCompletion.IgnoredStaleAction
        }
        inFlight = null

        return when (result) {
            ActionExecutionResult.PERFORMED,
            ActionExecutionResult.FAILED,
            -> {
                lastActionAtMillis = completedAtMillis
                awaitingPageChange = CompletedAction(
                    actionKey = action.candidate.actionKey,
                    pageFingerprint = action.candidate.pageFingerprint,
                    attemptNumber = action.attemptNumber,
                    completedAtMillis = completedAtMillis,
                )
                if (
                    result == ActionExecutionResult.FAILED &&
                    action.attemptNumber >= policy.maxAttemptsPerFingerprint
                ) {
                    ActionCompletion.SafetyPause(SafetyPauseReason.ACTION_RETRY_LIMIT)
                } else {
                    ActionCompletion.Recorded
                }
            }

            ActionExecutionResult.TARGET_INVALID -> {
                ActionCompletion.SafetyPause(SafetyPauseReason.ACTION_TARGET_INVALID)
            }

            ActionExecutionResult.CANCELLED -> ActionCompletion.Recorded
        }
    }

    @Synchronized
    fun cancelPending() {
        inFlight = null
    }

    @Synchronized
    fun stop() {
        stopped = true
        inFlight = null
        awaitingPageChange = null
    }

    @Synchronized
    fun isCurrent(action: ScheduledAction): Boolean =
        !stopped &&
            inFlight?.runId == action.runId &&
            inFlight?.actionId == action.actionId

    private data class CompletedAction(
        val actionKey: String,
        val pageFingerprint: String,
        val attemptNumber: Int,
        val completedAtMillis: Long,
    )
}
