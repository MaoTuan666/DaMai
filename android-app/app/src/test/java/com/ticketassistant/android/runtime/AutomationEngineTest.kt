package com.ticketassistant.android.runtime

import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.domain.TicketPlatform
import com.ticketassistant.android.domain.TicketTask
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEngineTest {
    @Test
    fun `repeated submissions remain running without elapsed time limit`() = runBlocking {
        var now = 0L
        val records = mutableListOf<Record>()
        val engine = engine(
            nowMillis = { now },
            records = records,
        )
        engine.initialize()
        engine.start()
        engine.recognizeTargetPage(1)
        now = 86_400_000L

        repeat(25) { index ->
            val sequence = index.toLong() + 1
            if (index > 0) {
                now += 300
                engine.observeSnapshot("run-1", sequence)
            }
            val scheduled = (
                engine.requestAction(
                    candidate = ActionCandidate(
                        actionKey = "SUBMIT",
                        pageFingerprint = "confirm-page-$index",
                    ),
                    snapshotSequence = sequence,
                ) as EngineActionDecision.Scheduled
            ).action
            now += 10
            engine.completeAction(scheduled, ActionExecutionResult.PERFORMED)
        }

        assertEquals(TaskState.RUNNING, engine.state.value.taskState)
        assertEquals(25, records.count { it.eventCode == "SUBMIT" })
        assertFalse(records.any { it.eventCode == "TASK_STOPPED" })
    }

    @Test
    fun `pause invalidates action and resume requires fresh snapshot`() = runBlocking {
        var now = 0L
        val engine = engine(nowMillis = { now })
        engine.initialize()
        engine.start()
        engine.recognizeTargetPage(5)
        val scheduled = (
            engine.requestAction(
                ActionCandidate("BOOK", "detail-page"),
                snapshotSequence = 5,
            ) as EngineActionDecision.Scheduled
        ).action
        assertTrue(engine.isActionCurrent(scheduled))

        engine.pause()
        assertFalse(engine.isActionCurrent(scheduled))
        engine.resume()
        assertEquals(
            EngineActionDecision.StateDoesNotAllowAction,
            engine.requestAction(
                ActionCandidate("BOOK", "detail-page"),
                snapshotSequence = 5,
            ),
        )

        engine.observeSnapshot("run-1", 6)
        now = 300
        assertTrue(
            engine.requestAction(
                ActionCandidate("BOOK", "detail-page"),
                snapshotSequence = 6,
            ) is EngineActionDecision.Scheduled,
        )
    }

    @Test
    fun `adapter diagnostic failure does not escape into runtime collector`() = runBlocking {
        val engine = AutomationEngine(
            runId = "run-1",
            task = task(),
            nowMillis = { 0L },
            recorder = RuntimeStateRecorder { _, eventCode, _ ->
                if (eventCode == "DIAGNOSTIC") error("database unavailable")
            },
        )
        engine.initialize()

        assertFalse(engine.recordAdapterEvent("DIAGNOSTIC", "reason=TEST"))
        assertTrue(engine.recordAdapterEvent("DIAGNOSTIC_RECOVERED", "reason=TEST"))
    }

    @Test
    fun `core state persistence failure does not stop runtime progress`() = runBlocking {
        val engine = AutomationEngine(
            runId = "run-1",
            task = task(),
            nowMillis = { 0L },
            recorder = RuntimeStateRecorder { _, _, _ ->
                error("database unavailable")
            },
        )

        engine.initialize()
        assertTrue(engine.start() is StateTransition.Applied)
        engine.observeSnapshot("run-1", 1)
        assertTrue(engine.recognizeTargetPage(1) is StateTransition.Applied)
        assertEquals(TaskState.RUNNING, engine.state.value.taskState)
    }

    private fun engine(
        nowMillis: () -> Long,
        records: MutableList<Record> = mutableListOf(),
    ) = AutomationEngine(
        runId = "run-1",
        task = task(),
        nowMillis = nowMillis,
        recorder = RuntimeStateRecorder { state, eventCode, detail ->
            records += Record(state.taskState, eventCode, detail)
        },
    )

    private fun task() = TicketTask(
        id = 1,
        platform = TicketPlatform.DAMAI,
        runMode = RunMode.SALE_ONLY,
        targetDate = "2026-08-01",
        targetPriceFen = 58_000,
        adapterConfig = "{}",
        state = TaskState.WAIT_TARGET_APP,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private data class Record(
        val state: TaskState,
        val eventCode: String,
        val detail: String,
    )
}
