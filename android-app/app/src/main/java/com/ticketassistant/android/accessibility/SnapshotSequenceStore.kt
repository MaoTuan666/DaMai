package com.ticketassistant.android.accessibility

object SnapshotSequenceStore {
    private val lastSequenceByRunId = mutableMapOf<String, Long>()

    fun next(runId: String): Long {
        require(runId.isNotBlank())
        return synchronized(lastSequenceByRunId) {
            nextLocked(runId)
        }
    }

    fun clear(runId: String) {
        require(runId.isNotBlank())
        synchronized(lastSequenceByRunId) {
            lastSequenceByRunId.remove(runId)
        }
    }

    private fun nextLocked(runId: String): Long {
        val lastSequence = lastSequenceByRunId[runId] ?: 0L
        check(lastSequence < Long.MAX_VALUE) {
            "Snapshot sequence exhausted for the active run"
        }
        return (lastSequence + 1).also { nextSequence ->
            lastSequenceByRunId[runId] = nextSequence
        }
    }
}
