package com.ticketassistant.android.overlay

import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.runtime.AutomationPhase
import com.ticketassistant.android.runtime.EngineRuntimeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeOverlayStoreTest {
    @After
    fun tearDown() {
        RuntimeOverlayStore.end()
    }

    @Test
    fun `store only retains four most recent entries`() {
        RuntimeOverlayStore.begin("run-current")

        repeat(6) { index ->
            RuntimeOverlayStore.record(
                runId = "run-current",
                state = runningState,
                eventCode = if (index == 5) "TASK_USER_PAUSED" else "ENGINE_READY",
                occurredAtEpochMillis = index.toLong(),
                sanitizedDetail = if (index == 5) "attempt=3" else "",
            )
        }

        val entries = requireNotNull(RuntimeOverlayStore.logs.value).entries
        assertEquals(4, entries.size)
        assertEquals(listOf(2L, 3L, 4L, 5L), entries.map { it.occurredAtEpochMillis })
        assertEquals("用户已暂停，待执行动作已取消", entries.last().message)
        assertEquals(3, entries.last().attemptNumber)
    }

    @Test
    fun `stale run cannot append or clear current logs`() {
        RuntimeOverlayStore.begin("run-current")

        RuntimeOverlayStore.record(
            runId = "run-stale",
            state = runningState,
            eventCode = "ENGINE_READY",
        )
        RuntimeOverlayStore.end("run-stale")

        val state = requireNotNull(RuntimeOverlayStore.logs.value)
        assertEquals("run-current", state.runId)
        assertEquals(emptyList<OverlayLogEntry>(), state.entries)
    }

    @Test
    fun `matching run can clear logs`() {
        RuntimeOverlayStore.begin("run-current")

        RuntimeOverlayStore.end("run-current")

        assertNull(RuntimeOverlayStore.logs.value)
    }

    private val runningState = EngineRuntimeState(
        taskState = TaskState.RUNNING,
        phase = AutomationPhase.SALE,
    )
}
