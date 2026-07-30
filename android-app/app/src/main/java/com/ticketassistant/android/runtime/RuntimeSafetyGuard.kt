package com.ticketassistant.android.runtime

data class RuntimeLimits(
    val maxConsecutiveActionFailures: Int = 3,
) {
    init {
        require(maxConsecutiveActionFailures >= 1)
    }
}

sealed interface SafetyLimitDecision {
    data object Allowed : SafetyLimitDecision
    data class Stop(val reason: RuntimeStopReason) : SafetyLimitDecision
}

class RuntimeSafetyGuard(
    private val limits: RuntimeLimits,
) {
    var consecutiveActionFailureCount: Int = 0
        private set

    fun reset() {
        consecutiveActionFailureCount = 0
    }

    fun recordCompletion(
        result: ActionExecutionResult,
    ): SafetyLimitDecision {
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
        return SafetyLimitDecision.Allowed
    }
}
