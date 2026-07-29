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
    fun `final allowed submit stops the engine`() = runBlocking {
        var now = 0L
        val records = mutableListOf<Record>()
        val engine = engine(
            maxSubmitAttempts = 1,
            nowMillis = { now },
            records = records,
        )
        engine.initialize()
        engine.recognizeTargetPage(1)
        val scheduled = (
            engine.requestAction(
                candidate = ActionCandidate(
                    actionKey = "SUBMIT",
                    pageFingerprint = "confirm-page",
                    countsAsSubmitAttempt = true,
                ),
                snapshotSequence = 1,
            ) as EngineActionDecision.Scheduled
        ).action

        now = 10
        engine.completeAction(scheduled, ActionExecutionResult.PERFORMED)

        assertEquals(TaskState.STOPPED, engine.state.value.taskState)
        assertEquals(
            RuntimeStopReason.MAX_SUBMIT_ATTEMPTS_REACHED,
            engine.state.value.stopReason,
        )
        assertTrue(records.any { it.eventCode == "SUBMIT" && it.detail == "attempt=1" })
        assertEquals("TASK_STOPPED", records.last().eventCode)
    }

    @Test
    fun `pause invalidates action and resume requires fresh snapshot`() = runBlocking {
        var now = 0L
        val engine = engine(nowMillis = { now })
        engine.initialize()
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

    private fun engine(
        maxSubmitAttempts: Int = 3,
        nowMillis: () -> Long,
        records: MutableList<Record> = mutableListOf(),
    ) = AutomationEngine(
        runId = "run-1",
        task = task(maxSubmitAttempts),
        startedAtMillis = 0,
        nowMillis = nowMillis,
        recorder = RuntimeStateRecorder { state, eventCode, detail ->
            records += Record(state.taskState, eventCode, detail)
        },
    )

    private fun task(maxSubmitAttempts: Int) = TicketTask(
        id = 1,
        platform = TicketPlatform.DAMAI,
        runMode = RunMode.SALE_ONLY,
        eventKeyword = "测试演出",
        targetSession = "周六 19:30",
        targetTier = "看台 580",
        targetPriceFen = 58_000,
        ticketCount = 1,
        adapterConfig = "{}",
        maxSubmitAttempts = maxSubmitAttempts,
        maxRuntimeSeconds = 60,
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
