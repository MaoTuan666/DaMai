package com.ticketassistant.android.runtime

import com.ticketassistant.android.platform.api.AdapterContractViolation
import com.ticketassistant.android.platform.api.PageResult

internal object AwaitingValidStartEventCodes {
    const val UNKNOWN = "TARGET_START_UNKNOWN"
    const val MISMATCH = "TARGET_START_MISMATCH"
    const val INVALID_PHASE = "TARGET_START_INVALID_PHASE"
    const val SNAPSHOT_UNAVAILABLE = "TARGET_START_SNAPSHOT_UNAVAILABLE"
    const val CONTRACT_REJECTED = "TARGET_START_CONTRACT_REJECTED"
}

internal data class AwaitingValidStartLogEvent(
    val eventCode: String,
    val sanitizedDetail: String,
) {
    val deduplicationKey: String = "$eventCode:$sanitizedDetail"

    companion object {
        fun from(
            pageResult: PageResult,
            phase: AutomationPhase,
        ): AwaitingValidStartLogEvent = when (pageResult) {
            is PageResult.Unknown -> AwaitingValidStartLogEvent(
                eventCode = AwaitingValidStartEventCodes.UNKNOWN,
                sanitizedDetail = "reason=${pageResult.reason.name}",
            )

            is PageResult.Mismatch -> AwaitingValidStartLogEvent(
                eventCode = AwaitingValidStartEventCodes.MISMATCH,
                sanitizedDetail = "field=${pageResult.field.name}",
            )

            is PageResult.Recognized -> AwaitingValidStartLogEvent(
                eventCode = AwaitingValidStartEventCodes.INVALID_PHASE,
                sanitizedDetail =
                    "phase=${phase.name};page_type=${pageResult.pageType.code}",
            )
        }

        fun snapshotUnavailable() = AwaitingValidStartLogEvent(
            eventCode = AwaitingValidStartEventCodes.SNAPSHOT_UNAVAILABLE,
            sanitizedDetail = "reason=NO_SUPPORTED_APP_WINDOW",
        )

        fun contractRejected(violation: AdapterContractViolation) =
            AwaitingValidStartLogEvent(
                eventCode = AwaitingValidStartEventCodes.CONTRACT_REJECTED,
                sanitizedDetail = "reason=${violation.name}",
            )
    }
}

internal class AwaitingValidStartLogLimiter(
    private val changedObservationIntervalMillis: Long = 2_000L,
    private val repeatedObservationIntervalMillis: Long = 15_000L,
) {
    private val recordedAtMillisByKey = mutableMapOf<String, Long>()
    private var lastRecordedAtMillis: Long? = null

    init {
        require(changedObservationIntervalMillis > 0)
        require(repeatedObservationIntervalMillis >= changedObservationIntervalMillis)
    }

    fun shouldRecord(
        event: AwaitingValidStartLogEvent,
        nowElapsedRealtimeMillis: Long,
    ): Boolean {
        recordedAtMillisByKey.entries.removeAll { (_, recordedAtMillis) ->
            val elapsedMillis = nowElapsedRealtimeMillis - recordedAtMillis
            elapsedMillis >= repeatedObservationIntervalMillis
        }

        recordedAtMillisByKey[event.deduplicationKey]?.let { recordedAtMillis ->
            val elapsedMillis = nowElapsedRealtimeMillis - recordedAtMillis
            if (elapsedMillis in 0 until repeatedObservationIntervalMillis) return false
        }
        lastRecordedAtMillis?.let { recordedAtMillis ->
            val elapsedMillis = nowElapsedRealtimeMillis - recordedAtMillis
            if (elapsedMillis in 0 until changedObservationIntervalMillis) return false
        }

        markRecorded(event, nowElapsedRealtimeMillis)
        return true
    }

    fun reset() {
        recordedAtMillisByKey.clear()
        lastRecordedAtMillis = null
    }

    private fun markRecorded(
        event: AwaitingValidStartLogEvent,
        nowElapsedRealtimeMillis: Long,
    ) {
        recordedAtMillisByKey[event.deduplicationKey] = nowElapsedRealtimeMillis
        lastRecordedAtMillis = nowElapsedRealtimeMillis
    }
}
