package com.ticketassistant.android.runtime

import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.domain.TicketTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface RuntimeStateRecorder {
    suspend fun record(
        state: EngineRuntimeState,
        eventCode: String,
        sanitizedDetail: String,
    )
}

sealed interface EngineActionDecision {
    data class Scheduled(val action: ScheduledAction) : EngineActionDecision
    data class Waiting(val reason: ActionBlockReason, val retryAfterMillis: Long?) :
        EngineActionDecision

    data class Paused(val reason: SafetyPauseReason) : EngineActionDecision
    data class Stopped(val reason: RuntimeStopReason) : EngineActionDecision
    data object StateDoesNotAllowAction : EngineActionDecision
}

class AutomationEngine(
    val runId: String,
    private val task: TicketTask,
    startedAtMillis: Long,
    private val nowMillis: () -> Long,
    private val recorder: RuntimeStateRecorder,
    private val onStateChanged: (EngineRuntimeState) -> Unit = {},
    actionPolicy: ActionPolicy = DEFAULT_ACTION_POLICY,
) {
    private val mutex = Mutex()
    private val scheduler = ControlledActionScheduler(runId, actionPolicy)
    private val safetyGuard = RuntimeSafetyGuard(
        startedAtMillis = startedAtMillis,
        limits = RuntimeLimits(
            maxSubmitAttempts = task.maxSubmitAttempts,
            maxRuntimeMillis = task.maxRuntimeSeconds * 1_000L,
        ),
    )
    private val mutableState = MutableStateFlow(EngineRuntimeState.initial(task.runMode))
    val state: StateFlow<EngineRuntimeState> = mutableState.asStateFlow()

    suspend fun initialize() = mutex.withLock {
        recorder.record(
            state = mutableState.value,
            eventCode = "ENGINE_READY",
            sanitizedDetail = "运行引擎已就绪，等待识别目标起始页面",
        )
        onStateChanged(mutableState.value)
    }

    suspend fun observeSnapshot(
        snapshotRunId: String,
        sequence: Long,
    ) = mutex.withLock {
        if (snapshotRunId != runId) return@withLock
        applyWithoutRecording(RuntimeEvent.SnapshotObserved(sequence))
    }

    suspend fun recordAdapterEvent(
        eventCode: String,
        sanitizedDetail: String,
    ) = mutex.withLock {
        if (mutableState.value.taskState == TaskState.STOPPED) return@withLock
        recorder.record(
            state = mutableState.value,
            eventCode = eventCode,
            sanitizedDetail = sanitizedDetail,
        )
    }

    suspend fun recognizeTargetPage(sequence: Long): StateTransition =
        applyAndRecord(
            event = RuntimeEvent.TargetPageRecognized(sequence),
            eventCode = "TARGET_PAGE_RECOGNIZED",
            detail = "目标应用起始页面已识别",
        )

    suspend fun pause(): StateTransition = mutex.withLock {
        val transition = applyAndRecordLocked(
            event = RuntimeEvent.UserPause,
            eventCode = "TASK_USER_PAUSED",
            detail = "用户暂停任务，待执行动作已取消",
        )
        if (transition is StateTransition.Applied) {
            scheduler.cancelPending()
        }
        transition
    }

    suspend fun resume(): StateTransition = applyAndRecord(
        event = RuntimeEvent.UserResume,
        eventCode = "TASK_USER_RESUMED",
        detail = "用户恢复任务，等待新的页面快照",
    )

    suspend fun safetyPause(reason: SafetyPauseReason): StateTransition = mutex.withLock {
        val transition = applyAndRecordLocked(
            event = RuntimeEvent.SafetyPause(reason),
            eventCode = "TASK_SAFETY_PAUSED",
            detail = reason.name,
        )
        if (transition is StateTransition.Applied) {
            scheduler.stop()
        }
        transition
    }

    suspend fun switchPhase(phase: AutomationPhase): StateTransition =
        applyAndRecord(
            event = RuntimeEvent.SwitchPhase(phase),
            eventCode = "TASK_PHASE_SWITCHED",
            detail = phase.name,
        )

    suspend fun lockOrder(): StateTransition = mutex.withLock {
        val transition = applyAndRecordLocked(
            event = RuntimeEvent.OrderLocked,
            eventCode = "ORDER_LOCKED",
            detail = "已进入用户接管节点，自动动作已停止",
        )
        if (transition is StateTransition.Applied) {
            scheduler.stop()
        }
        transition
    }

    suspend fun stop(reason: RuntimeStopReason): StateTransition = mutex.withLock {
        val transition = applyAndRecordLocked(
            event = RuntimeEvent.Stop(reason),
            eventCode = "TASK_STOPPED",
            detail = reason.name,
        )
        if (transition is StateTransition.Applied) {
            scheduler.stop()
        }
        transition
    }

    suspend fun checkRuntimeLimit(): RuntimeStopReason? = mutex.withLock {
        val current = mutableState.value
        if (current.taskState == TaskState.STOPPED) return@withLock current.stopReason
        return@withLock when (
            val limit = safetyGuard.beforeAction(
                nowMillis = nowMillis(),
                countsAsSubmitAttempt = false,
            )
        ) {
            SafetyLimitDecision.Allowed -> null
            is SafetyLimitDecision.Stop -> {
                scheduler.stop()
                applyAndRecordLocked(
                    event = RuntimeEvent.Stop(limit.reason),
                    eventCode = "TASK_STOPPED",
                    detail = limit.reason.name,
                )
                limit.reason
            }
        }
    }

    suspend fun requestAction(
        candidate: ActionCandidate,
        snapshotSequence: Long,
    ): EngineActionDecision = mutex.withLock {
        val current = mutableState.value
        if (!current.canScheduleAction(snapshotSequence)) {
            return@withLock EngineActionDecision.StateDoesNotAllowAction
        }

        when (
            val limit = safetyGuard.beforeAction(
                nowMillis = nowMillis(),
                countsAsSubmitAttempt = candidate.countsAsSubmitAttempt,
            )
        ) {
            SafetyLimitDecision.Allowed -> Unit
            is SafetyLimitDecision.Stop -> {
                scheduler.stop()
                applyAndRecordLocked(
                    event = RuntimeEvent.Stop(limit.reason),
                    eventCode = "TASK_STOPPED",
                    detail = limit.reason.name,
                )
                return@withLock EngineActionDecision.Stopped(limit.reason)
            }
        }

        when (val decision = scheduler.request(candidate, nowMillis())) {
            is ActionScheduleDecision.Ready -> EngineActionDecision.Scheduled(decision.action)
            is ActionScheduleDecision.Wait -> EngineActionDecision.Waiting(
                reason = decision.reason,
                retryAfterMillis = decision.retryAfterMillis,
            )

            is ActionScheduleDecision.SafetyPause -> {
                scheduler.stop()
                applyAndRecordLocked(
                    event = RuntimeEvent.SafetyPause(decision.reason),
                    eventCode = "TASK_SAFETY_PAUSED",
                    detail = decision.reason.name,
                )
                EngineActionDecision.Paused(decision.reason)
            }
        }
    }

    suspend fun completeAction(
        action: ScheduledAction,
        result: ActionExecutionResult,
    ): ActionCompletion = mutex.withLock {
        val completion = scheduler.complete(
            action = action,
            result = result,
            completedAtMillis = nowMillis(),
        )
        if (completion is ActionCompletion.IgnoredStaleAction) return@withLock completion

        when (result) {
            ActionExecutionResult.PERFORMED -> recorder.record(
                state = mutableState.value,
                eventCode = action.candidate.actionKey,
                sanitizedDetail = "attempt=${action.attemptNumber}",
            )

            ActionExecutionResult.FAILED -> recorder.record(
                state = mutableState.value,
                eventCode = "ACTION_FAILED",
                sanitizedDetail = "attempt=${action.attemptNumber}",
            )

            ActionExecutionResult.TARGET_INVALID,
            ActionExecutionResult.CANCELLED,
            -> Unit
        }

        if (completion is ActionCompletion.SafetyPause) {
            scheduler.stop()
            applyAndRecordLocked(
                event = RuntimeEvent.SafetyPause(completion.reason),
                eventCode = "TASK_SAFETY_PAUSED",
                detail = completion.reason.name,
            )
            return@withLock completion
        }

        when (
            val limit = safetyGuard.recordCompletion(
                countsAsSubmitAttempt = action.candidate.countsAsSubmitAttempt,
                result = result,
            )
        ) {
            SafetyLimitDecision.Allowed -> Unit
            is SafetyLimitDecision.Stop -> {
                scheduler.stop()
                applyAndRecordLocked(
                    event = RuntimeEvent.Stop(limit.reason),
                    eventCode = "TASK_STOPPED",
                    detail = limit.reason.name,
                )
            }
        }
        completion
    }

    fun isActionCurrent(action: ScheduledAction): Boolean = scheduler.isCurrent(action)

    private suspend fun applyAndRecord(
        event: RuntimeEvent,
        eventCode: String,
        detail: String,
    ): StateTransition = mutex.withLock {
        applyAndRecordLocked(event, eventCode, detail)
    }

    private suspend fun applyAndRecordLocked(
        event: RuntimeEvent,
        eventCode: String,
        detail: String,
    ): StateTransition {
        val transition = RuntimeStateMachine.transition(mutableState.value, event)
        if (transition is StateTransition.Applied) {
            mutableState.value = transition.state
            onStateChanged(transition.state)
            recorder.record(transition.state, eventCode, detail)
        }
        return transition
    }

    private fun applyWithoutRecording(event: RuntimeEvent) {
        val transition = RuntimeStateMachine.transition(mutableState.value, event)
        if (transition is StateTransition.Applied) {
            mutableState.value = transition.state
            onStateChanged(transition.state)
        }
    }

    companion object {
        val DEFAULT_ACTION_POLICY = ActionPolicy(
            minimumActionIntervalMillis = 250L,
            unchangedPageCooldownMillis = 800L,
            maxAttemptsPerFingerprint = 3,
        )
    }
}
