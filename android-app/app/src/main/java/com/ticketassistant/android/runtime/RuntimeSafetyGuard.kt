package com.ticketassistant.android.runtime

data class RuntimeLimits(
    val maxSubmitAttempts: Int,
    val maxRuntimeMillis: Long,
    val maxConsecutiveActionFailures: Int = 3,
) {
    init {
        require(maxSubmitAttempts >= 1)
        require(maxRuntimeMillis >= 1)
        require(maxConsecutiveActionFailures >= 1)
    }
}

sealed interface SafetyLimitDecision {
    data object Allowed : SafetyLimitDecision
    data class Stop(val reason: RuntimeStopReason) : SafetyLimitDecision
}

class RuntimeSafetyGuard(
    private val startedAtMillis: Long,
    private val limits: RuntimeLimits,
) {
    var submitAttemptCount: Int = 0
        private set
    var consecutiveActionFailureCount: Int = 0
        private set

    fun beforeAction(
        nowMillis: Long,
        countsAsSubmitAttempt: Boolean,
    ): SafetyLimitDecision {
        if (nowMillis - startedAtMillis >= limits.maxRuntimeMillis) {
            return SafetyLimitDecision.Stop(RuntimeStopReason.MAX_RUNTIME_REACHED)
        }
        if (countsAsSubmitAttempt && submitAttemptCount >= limits.maxSubmitAttempts) {
            return SafetyLimitDecision.Stop(
                RuntimeStopReason.MAX_SUBMIT_ATTEMPTS_REACHED,
            )
        }
        return SafetyLimitDecision.Allowed
    }

    fun recordCompletion(
        countsAsSubmitAttempt: Boolean,
        result: ActionExecutionResult,
    ): SafetyLimitDecision {
        if (countsAsSubmitAttempt && result == ActionExecutionResult.PERFORMED) {
            submitAttemptCount += 1
        }

        consecutiveActionFailureCount = if (result == ActionExecutionResult.FAILED) {
            consecutiveActionFailureCount + 1
        } else {
            0
        }
        if (consecutiveActionFailureCount >= limits.maxConsecutiveActionFailures) {
            return SafetyLimitDecision.Stop(
                RuntimeStopReason.ACTION_FAILURE_LIMIT_REACHED,
            )
        }
        if (submitAttemptCount >= limits.maxSubmitAttempts) {
            return SafetyLimitDecision.Stop(
                RuntimeStopReason.MAX_SUBMIT_ATTEMPTS_REACHED,
            )
        }
        return SafetyLimitDecision.Allowed
    }
}
