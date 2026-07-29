package com.ticketassistant.android.accessibility

class SnapshotDebouncePolicy(
    private val stabilityWindowMillis: Long,
    private val maximumWaitMillis: Long,
) {
    init {
        require(stabilityWindowMillis > 0)
        require(maximumWaitMillis >= stabilityWindowMillis)
    }

    fun delayMillis(
        burstStartedAtMillis: Long,
        eventAtMillis: Long,
    ): Long {
        require(eventAtMillis >= burstStartedAtMillis)
        val remainingMaximumWait = maximumWaitMillis - (eventAtMillis - burstStartedAtMillis)
        return minOf(stabilityWindowMillis, remainingMaximumWait).coerceAtLeast(0)
    }
}
