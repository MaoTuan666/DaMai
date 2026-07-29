package com.ticketassistant.android.accessibility

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SnapshotCaptureRequests {
    private val mutableRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val requests: SharedFlow<String> = mutableRequests.asSharedFlow()

    fun request(runId: String): Boolean {
        if (runId.isBlank()) return false
        return mutableRequests.tryEmit(runId)
    }
}
