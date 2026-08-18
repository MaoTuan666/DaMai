package com.ticketassistant.android.runtime

internal enum class SnapshotArrivalDisposition {
    PROCESS,
    IGNORE_AND_KEEP_PROBE,
}

internal object SnapshotProbePolicy {
    fun arrivalDisposition(
        captureSequence: Long,
        lastObservedSequence: Long,
    ): SnapshotArrivalDisposition = if (
        captureSequence > 0 && captureSequence > lastObservedSequence
    ) {
        SnapshotArrivalDisposition.PROCESS
    } else {
        SnapshotArrivalDisposition.IGNORE_AND_KEEP_PROBE
    }

    fun retryDelayMillis(
        requestedDelayMillis: Long?,
        defaultDelayMillis: Long,
    ): Long {
        require(defaultDelayMillis >= 0)
        require(requestedDelayMillis == null || requestedDelayMillis >= 0)
        return requestedDelayMillis ?: defaultDelayMillis
    }
}
