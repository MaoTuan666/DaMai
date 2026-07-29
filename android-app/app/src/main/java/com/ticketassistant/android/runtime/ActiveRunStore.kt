package com.ticketassistant.android.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local gate for accessibility snapshots and, later, automation actions.
 *
 * A persisted task never arms the service after a process restart. The user must explicitly
 * start a new run, which prevents stale task state from observing or acting on another session.
 */
object ActiveRunStore {
    private val mutableSession = MutableStateFlow<ActiveRunSession?>(null)
    val session: StateFlow<ActiveRunSession?> = mutableSession.asStateFlow()

    fun arm(
        runId: String,
        taskId: Long,
        startedAtEpochMillis: Long = System.currentTimeMillis(),
        startedAtElapsedRealtimeMillis: Long = startedAtEpochMillis,
    ) {
        require(runId.isNotBlank())
        require(taskId > 0)
        mutableSession.value = ActiveRunSession(
            runId = runId,
            taskId = taskId,
            startedAtEpochMillis = startedAtEpochMillis,
            startedAtElapsedRealtimeMillis = startedAtElapsedRealtimeMillis,
            runtimeState = null,
        )
    }

    fun isActive(runId: String): Boolean = mutableSession.value?.runId == runId

    fun updateRuntimeState(
        runId: String,
        state: EngineRuntimeState,
    ) {
        val current = mutableSession.value ?: return
        if (current.runId == runId) {
            mutableSession.value = current.copy(runtimeState = state)
        }
    }

    fun disarm(expectedRunId: String? = null) {
        val current = mutableSession.value ?: return
        if (expectedRunId == null || current.runId == expectedRunId) {
            mutableSession.value = null
        }
    }
}

data class ActiveRunSession(
    val runId: String,
    val taskId: Long,
    val startedAtEpochMillis: Long,
    val startedAtElapsedRealtimeMillis: Long,
    val runtimeState: EngineRuntimeState?,
)
