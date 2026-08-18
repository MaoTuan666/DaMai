package com.ticketassistant.android.accessibility

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

object SnapshotCaptureRequests {
    private val queue = SnapshotCaptureRequestQueue()

    val requests: Flow<String> = queue.requests

    fun request(runId: String): Boolean = queue.request(runId)
}

internal class SnapshotCaptureRequestQueue {
    private val channel = Channel<String>(Channel.CONFLATED)

    val requests: Flow<String> = channel.receiveAsFlow()

    fun request(runId: String): Boolean {
        if (runId.isBlank()) return false
        return channel.trySend(runId).isSuccess
    }
}
