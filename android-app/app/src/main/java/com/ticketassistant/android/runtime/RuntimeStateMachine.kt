package com.ticketassistant.android.runtime

import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TaskState

enum class AutomationPhase {
    SALE,
    RETURN_MONITOR,
}

enum class SafetyPauseReason {
    UNKNOWN_PAGE,
    TARGET_MISMATCH,
    VERIFICATION_REQUIRED,
    TARGET_APP_LEFT,
    UNKNOWN_POPUP,
    ACTION_TARGET_INVALID,
    ACTION_RETRY_LIMIT,
}

enum class RuntimeStopReason {
    USER_REQUESTED,
    ACTION_FAILURE_LIMIT_REACHED,
    ORDER_LOCKED,
    PLATFORM_REQUESTED,
}

data class EngineRuntimeState(
    val taskState: TaskState,
    val phase: AutomationPhase,
    val lastSnapshotSequence: Long = 0,
    val requiredFreshSnapshotAfter: Long? = null,
    val safetyPauseReason: SafetyPauseReason? = null,
    val stopReason: RuntimeStopReason? = null,
) {
    fun canScheduleAction(snapshotSequence: Long): Boolean =
        taskState == TaskState.RUNNING &&
            snapshotSequence > 0 &&
            snapshotSequence == lastSnapshotSequence &&
            requiredFreshSnapshotAfter?.let { snapshotSequence > it } != false

    companion object {
        fun initial(runMode: RunMode): EngineRuntimeState = EngineRuntimeState(
            taskState = TaskState.ARMED,
            phase = if (runMode == RunMode.RETURN_ONLY) {
                AutomationPhase.RETURN_MONITOR
            } else {
                AutomationPhase.SALE
            },
        )
    }
}

sealed interface RuntimeEvent {
    data class SnapshotObserved(val sequence: Long) : RuntimeEvent
    data object UserStart : RuntimeEvent
    data class TargetPageRecognized(val sequence: Long) : RuntimeEvent
    data object UserPause : RuntimeEvent
    data object UserResume : RuntimeEvent
    data class SafetyPause(val reason: SafetyPauseReason) : RuntimeEvent
    data object OrderLocked : RuntimeEvent
    data class SwitchPhase(val phase: AutomationPhase) : RuntimeEvent
    data class Stop(val reason: RuntimeStopReason) : RuntimeEvent
}

sealed interface StateTransition {
    data class Applied(val state: EngineRuntimeState) : StateTransition
    data class Rejected(val currentState: TaskState, val event: RuntimeEvent) : StateTransition
}

object RuntimeStateMachine {
    fun transition(
        current: EngineRuntimeState,
        event: RuntimeEvent,
    ): StateTransition {
        val next = when (event) {
            is RuntimeEvent.SnapshotObserved -> {
                if (
                    event.sequence <= 0 ||
                    current.taskState == TaskState.STOPPED ||
                    current.taskState == TaskState.ORDER_LOCKED
                ) {
                    return StateTransition.Rejected(current.taskState, event)
                }
                observeSnapshot(current, event.sequence)
            }
            RuntimeEvent.UserStart -> {
                if (current.taskState != TaskState.ARMED) {
                    return StateTransition.Rejected(current.taskState, event)
                }
                current.copy(
                    taskState = TaskState.WAIT_TARGET_APP,
                    requiredFreshSnapshotAfter = current.lastSnapshotSequence,
                )
            }
            is RuntimeEvent.TargetPageRecognized -> {
                if (
                    current.taskState != TaskState.WAIT_TARGET_APP ||
                    event.sequence <= 0 ||
                    event.sequence < current.lastSnapshotSequence ||
                    current.requiredFreshSnapshotAfter?.let {
                        event.sequence <= it
                    } == true
                ) {
                    return StateTransition.Rejected(current.taskState, event)
                }
                current.copy(
                    taskState = TaskState.RUNNING,
                    lastSnapshotSequence = event.sequence,
                    requiredFreshSnapshotAfter = null,
                )
            }

            RuntimeEvent.UserPause -> {
                if (current.taskState != TaskState.RUNNING) {
                    return StateTransition.Rejected(current.taskState, event)
                }
                current.copy(taskState = TaskState.USER_PAUSED)
            }

            RuntimeEvent.UserResume -> {
                if (current.taskState != TaskState.USER_PAUSED) {
                    return StateTransition.Rejected(current.taskState, event)
                }
                current.copy(
                    taskState = TaskState.RUNNING,
                    requiredFreshSnapshotAfter = current.lastSnapshotSequence,
                )
            }

            is RuntimeEvent.SafetyPause -> {
                if (current.taskState != TaskState.RUNNING) {
                    return StateTransition.Rejected(current.taskState, event)
                }
                current.copy(
                    taskState = TaskState.SAFETY_PAUSED,
                    safetyPauseReason = event.reason,
                )
            }

            RuntimeEvent.OrderLocked -> {
                if (current.taskState != TaskState.RUNNING) {
                    return StateTransition.Rejected(current.taskState, event)
                }
                current.copy(
                    taskState = TaskState.ORDER_LOCKED,
                    stopReason = RuntimeStopReason.ORDER_LOCKED,
                )
            }

            is RuntimeEvent.SwitchPhase -> {
                if (current.taskState != TaskState.RUNNING || event.phase == current.phase) {
                    return StateTransition.Rejected(current.taskState, event)
                }
                current.copy(phase = event.phase)
            }

            is RuntimeEvent.Stop -> {
                if (current.taskState == TaskState.STOPPED) {
                    return StateTransition.Rejected(current.taskState, event)
                }
                current.copy(
                    taskState = TaskState.STOPPED,
                    stopReason = event.reason,
                )
            }
        }
        return StateTransition.Applied(next)
    }

    private fun observeSnapshot(
        current: EngineRuntimeState,
        sequence: Long,
    ): EngineRuntimeState {
        if (sequence <= current.lastSnapshotSequence) return current
        val freshRequirement = current.requiredFreshSnapshotAfter
        return current.copy(
            lastSnapshotSequence = sequence,
            requiredFreshSnapshotAfter = if (
                freshRequirement != null && sequence > freshRequirement
            ) {
                null
            } else {
                freshRequirement
            },
        )
    }
}
