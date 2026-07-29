package com.ticketassistant.android.overlay

import com.ticketassistant.android.runtime.EngineRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeOverlayLogState(
    val runId: String,
    val entries: List<OverlayLogEntry>,
)

object RuntimeOverlayStore {
    private const val MAX_VISIBLE_ENTRIES = 4

    private val mutableLogs = MutableStateFlow<RuntimeOverlayLogState?>(null)
    val logs: StateFlow<RuntimeOverlayLogState?> = mutableLogs.asStateFlow()

    @Synchronized
    fun begin(runId: String) {
        require(runId.isNotBlank())
        mutableLogs.value = RuntimeOverlayLogState(runId, emptyList())
    }

    @Synchronized
    fun record(
        runId: String,
        state: EngineRuntimeState,
        eventCode: String,
        occurredAtEpochMillis: Long = System.currentTimeMillis(),
        sanitizedDetail: String = "",
    ) {
        val current = mutableLogs.value
        if (current?.runId != runId) return

        val entry = OverlayLogEntry(
            runId = runId,
            occurredAtEpochMillis = occurredAtEpochMillis,
            phase = state.phase,
            eventCode = eventCode,
            message = OverlayEventFormatter.message(eventCode, state),
            attemptNumber = parseAttemptNumber(sanitizedDetail),
        )
        mutableLogs.value = current.copy(
            entries = (current.entries + entry).takeLast(MAX_VISIBLE_ENTRIES),
        )
    }

    @Synchronized
    fun end(expectedRunId: String? = null) {
        val current = mutableLogs.value ?: return
        if (expectedRunId == null || current.runId == expectedRunId) {
            mutableLogs.value = null
        }
    }

    private fun parseAttemptNumber(sanitizedDetail: String): Int? {
        val match = ACTION_ATTEMPT_PATTERN.matchEntire(sanitizedDetail) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
    }

    private val ACTION_ATTEMPT_PATTERN = Regex("""attempt=(\d+)""")
}
