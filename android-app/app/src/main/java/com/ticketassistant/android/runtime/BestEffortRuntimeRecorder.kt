package com.ticketassistant.android.runtime

import kotlinx.coroutines.CancellationException

internal object BestEffortRuntimeRecorder {
    suspend fun record(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
