package com.ticketassistant.android.accessibility

import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.platform.api.ActionDecision
import com.ticketassistant.android.platform.api.ClickTarget
import com.ticketassistant.android.platform.api.EvidenceKind
import com.ticketassistant.android.platform.api.NodeReference
import com.ticketassistant.android.platform.api.PageEvidence
import com.ticketassistant.android.platform.api.PageFingerprint
import com.ticketassistant.android.platform.api.PageResult
import com.ticketassistant.android.platform.api.PlatformPageType
import com.ticketassistant.android.runtime.AccessibilityActionCommand
import com.ticketassistant.android.runtime.ActionCandidate
import com.ticketassistant.android.runtime.ActionExecutionResult
import com.ticketassistant.android.runtime.ActiveRunSession
import com.ticketassistant.android.runtime.AutomationPhase
import com.ticketassistant.android.runtime.EngineRuntimeState
import com.ticketassistant.android.runtime.ExecutableActionDecision
import com.ticketassistant.android.runtime.ScheduledAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledActionPreflightTest {
    @Test
    fun `current running command passes pure preflight`() {
        assertNull(
            ControlledActionPreflight.validate(
                command = command(),
                session = session(TaskState.RUNNING),
                latestSnapshot = snapshot(),
                nowElapsedRealtimeMillis = 200,
                maximumCommandAgeMillis = 1_500,
            ),
        )
    }

    @Test
    fun `paused run cancels queued command`() {
        assertEquals(
            ActionExecutionResult.CANCELLED,
            ControlledActionPreflight.validate(
                command = command(),
                session = session(TaskState.USER_PAUSED),
                latestSnapshot = snapshot(),
                nowElapsedRealtimeMillis = 200,
                maximumCommandAgeMillis = 1_500,
            ),
        )
    }

    @Test
    fun `newer snapshot cancels old command`() {
        assertEquals(
            ActionExecutionResult.CANCELLED,
            ControlledActionPreflight.validate(
                command = command(),
                session = session(TaskState.RUNNING).copy(
                    runtimeState = runtime(TaskState.RUNNING, sequence = 6),
                ),
                latestSnapshot = snapshot(sequence = 6),
                nowElapsedRealtimeMillis = 200,
                maximumCommandAgeMillis = 1_500,
            ),
        )
    }

    @Test
    fun `expired command is cancelled`() {
        assertEquals(
            ActionExecutionResult.CANCELLED,
            ControlledActionPreflight.validate(
                command = command(),
                session = session(TaskState.RUNNING),
                latestSnapshot = snapshot(),
                nowElapsedRealtimeMillis = 2_000,
                maximumCommandAgeMillis = 1_500,
            ),
        )
    }

    @Test
    fun `overlay intersection uses strict screen bounds`() {
        val target = UiBounds(10, 10, 40, 40)

        assertTrue(
            ControlledActionPreflight.intersects(
                target,
                listOf(UiBounds(30, 30, 60, 60)),
            ),
        )
        assertFalse(
            ControlledActionPreflight.intersects(
                target,
                listOf(UiBounds(40, 10, 60, 40)),
            ),
        )
    }

    private fun command(): AccessibilityActionCommand {
        val page = page()
        val click = ActionDecision.Click(
            target = ClickTarget(
                node = NodeReference(1, listOf(0)),
                expectedPackageName = PACKAGE_NAME,
                expectedPageFingerprint = page.fingerprint,
            ),
            eventCode = "TEST_CLICK",
        )
        return AccessibilityActionCommand(
            runId = RUN_ID,
            snapshotSequence = 5,
            expectedPackageName = PACKAGE_NAME,
            scheduledAction = ScheduledAction(
                runId = RUN_ID,
                actionId = 1,
                candidate = ActionCandidate(
                    actionKey = "TEST_CLICK",
                    pageFingerprint = FINGERPRINT,
                ),
                attemptNumber = 1,
                scheduledAtMillis = 100,
            ),
            page = page,
            decision = ExecutableActionDecision.Click(click),
        )
    }

    private fun page() = PageResult.Recognized(
        pageType = PlatformPageType("TEST_PAGE"),
        fingerprint = PageFingerprint(FINGERPRINT),
        windowId = 1,
        evidence = listOf(
            PageEvidence(
                node = NodeReference(1, listOf(0)),
                kind = EvidenceKind.TEXT_AND_ROLE,
                evidenceCode = "TEST_EVIDENCE",
            ),
        ),
    )

    private fun session(taskState: TaskState) = ActiveRunSession(
        runId = RUN_ID,
        taskId = 1,
        startedAtEpochMillis = 0,
        startedAtElapsedRealtimeMillis = 0,
        runtimeState = runtime(taskState),
    )

    private fun runtime(
        taskState: TaskState,
        sequence: Long = 5,
    ) = EngineRuntimeState(
        taskState = taskState,
        phase = AutomationPhase.SALE,
        lastSnapshotSequence = sequence,
    )

    private fun snapshot(sequence: Long = 5) = UiSnapshot(
        runId = RUN_ID,
        captureSequence = sequence,
        packageName = PACKAGE_NAME,
        capturedAtEpochMillis = 0,
        windows = emptyList(),
    )

    companion object {
        private const val RUN_ID = "run-1"
        private const val PACKAGE_NAME = "cn.damai"
        private const val FINGERPRINT = "fingerprint-1"
    }
}
