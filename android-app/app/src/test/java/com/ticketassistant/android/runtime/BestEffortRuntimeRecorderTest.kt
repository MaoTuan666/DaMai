package com.ticketassistant.android.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BestEffortRuntimeRecorderTest {
    @Test
    fun `successful record is reported`() = runBlocking {
        assertTrue(BestEffortRuntimeRecorder.record { })
    }

    @Test
    fun `ordinary persistence failure is isolated`() = runBlocking {
        assertFalse(
            BestEffortRuntimeRecorder.record {
                error("database unavailable")
            },
        )
    }

    @Test(expected = CancellationException::class)
    fun `cancellation still stops the caller`(): Unit = runBlocking {
        BestEffortRuntimeRecorder.record {
            throw CancellationException("collector cancelled")
        }
    }
}
