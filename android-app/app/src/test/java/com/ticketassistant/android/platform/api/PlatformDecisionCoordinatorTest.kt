package com.ticketassistant.android.platform.api

import com.ticketassistant.android.accessibility.UiBounds
import com.ticketassistant.android.accessibility.UiCheckedState
import com.ticketassistant.android.accessibility.UiNodeSnapshot
import com.ticketassistant.android.accessibility.UiSnapshot
import com.ticketassistant.android.accessibility.UiWindowSnapshot
import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.domain.TicketPlatform
import com.ticketassistant.android.domain.TicketTask
import com.ticketassistant.android.runtime.AutomationPhase
import com.ticketassistant.android.runtime.EngineRuntimeState
import com.ticketassistant.android.runtime.SafetyPauseReason
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformDecisionCoordinatorTest {
    @Test
    fun `valid top-window click proposal is accepted`() {
        val adapter = FakeAdapter(
            pageResult = recognizedPage(),
            action = clickDecision(),
        )

        val evaluation = coordinator(adapter).evaluate(
            expectedRunId = RUN_ID,
            snapshot = snapshot(),
            task = task(),
            runtime = runtime(TaskState.RUNNING),
        )

        assertTrue(evaluation is PlatformEvaluation.Decision)
        assertEquals(1, adapter.detectCalls)
        assertEquals(1, adapter.decideCalls)
    }

    @Test
    fun `stale run is ignored before adapter invocation`() {
        val adapter = FakeAdapter(recognizedPage(), clickDecision())

        val evaluation = coordinator(adapter).evaluate(
            expectedRunId = "new-run",
            snapshot = snapshot(),
            task = task(),
            runtime = runtime(TaskState.RUNNING),
        )

        assertEquals(
            PlatformEvaluation.Ignored(EvaluationIgnoreReason.RUN_ID_MISMATCH),
            evaluation,
        )
        assertEquals(0, adapter.detectCalls)
        assertEquals(0, adapter.decideCalls)
    }

    @Test
    fun `verification page pauses a running task without action decision`() {
        val unknown = PageResult.Unknown(PageUnknownReason.VERIFICATION_REQUIRED)
        val adapter = FakeAdapter(unknown, clickDecision())

        val evaluation = coordinator(adapter).evaluate(
            RUN_ID,
            snapshot(),
            task(),
            runtime(TaskState.RUNNING),
        )

        assertEquals(
            PlatformEvaluation.SafetyPause(
                SafetyPauseReason.VERIFICATION_REQUIRED,
                unknown,
            ),
            evaluation,
        )
        assertEquals(0, adapter.decideCalls)
    }

    @Test
    fun `unknown page only waits while looking for a valid start`() {
        val unknown = PageResult.Unknown(PageUnknownReason.INSUFFICIENT_EVIDENCE)
        val adapter = FakeAdapter(unknown, clickDecision())

        val evaluation = coordinator(adapter).evaluate(
            RUN_ID,
            snapshot(),
            task(),
            runtime(TaskState.WAIT_TARGET_APP),
        )

        assertEquals(PlatformEvaluation.AwaitingValidStart(unknown), evaluation)
        assertEquals(0, adapter.decideCalls)
    }

    @Test
    fun `target mismatch pauses a running task`() {
        val mismatch = PageResult.Mismatch(
            field = TargetField.PRICE,
            expected = "580",
            actual = "680",
        )
        val adapter = FakeAdapter(mismatch, clickDecision())

        val evaluation = coordinator(adapter).evaluate(
            RUN_ID,
            snapshot(),
            task(),
            runtime(TaskState.RUNNING),
        )

        assertEquals(
            PlatformEvaluation.SafetyPause(SafetyPauseReason.TARGET_MISMATCH, mismatch),
            evaluation,
        )
    }

    @Test
    fun `recognized lower window cannot propose click under top popup`() {
        val adapter = FakeAdapter(recognizedPage(windowId = 1), clickDecision(windowId = 1))
        val snapshot = snapshot(
            windows = listOf(
                window(id = 2, layer = 20),
                window(id = 1, layer = 10),
            ),
        )

        val evaluation = coordinator(adapter).evaluate(
            RUN_ID,
            snapshot,
            task(),
            runtime(TaskState.RUNNING),
        )

        assertEquals(
            PlatformEvaluation.ContractRejected(
                AdapterContractViolation.PAGE_WINDOW_NOT_TOPMOST,
                SafetyPauseReason.ACTION_TARGET_INVALID,
            ),
            evaluation,
        )
        assertEquals(0, adapter.decideCalls)
    }

    @Test
    fun `non-clickable exact target is rejected`() {
        val adapter = FakeAdapter(
            recognizedPage(),
            clickDecision(targetPath = listOf(0)),
        )

        val evaluation = coordinator(adapter).evaluate(
            RUN_ID,
            snapshot(),
            task(),
            runtime(TaskState.RUNNING),
        )

        assertEquals(
            PlatformEvaluation.ContractRejected(
                AdapterContractViolation.TARGET_NODE_NOT_ACTIONABLE,
                SafetyPauseReason.ACTION_TARGET_INVALID,
            ),
            evaluation,
        )
    }

    @Test
    fun `nearest clickable ancestor is accepted within declared bound`() {
        val textChild = node(
            path = listOf(0, 0),
            clickable = false,
        )
        val clickableParent = node(
            path = listOf(0),
            clickable = true,
            children = listOf(textChild),
        )
        val adapter = FakeAdapter(
            pageResult = recognizedPage(evidencePath = listOf(0, 0)),
            action = clickDecision(
                targetPath = listOf(0, 0),
                resolution = ClickResolution.NEAREST_CLICKABLE_ANCESTOR,
                maximumAncestorDepth = 1,
            ),
        )

        val evaluation = coordinator(adapter).evaluate(
            RUN_ID,
            snapshot(windows = listOf(window(children = listOf(clickableParent)))),
            task(),
            runtime(TaskState.RUNNING),
        )

        assertTrue(evaluation is PlatformEvaluation.Decision)
    }

    @Test
    fun `recognized page must declare current phase as a valid start`() {
        val page = recognizedPage(validStartPhases = setOf(AutomationPhase.RETURN_MONITOR))
        val adapter = FakeAdapter(page, clickDecision())

        val evaluation = coordinator(adapter).evaluate(
            RUN_ID,
            snapshot(),
            task(),
            runtime(TaskState.WAIT_TARGET_APP),
        )

        assertEquals(PlatformEvaluation.AwaitingValidStart(page), evaluation)
        assertEquals(0, adapter.decideCalls)
    }

    private fun coordinator(adapter: PlatformAdapter) =
        PlatformDecisionCoordinator(PlatformAdapterRegistry(listOf(adapter)))

    private fun recognizedPage(
        windowId: Int = 1,
        evidencePath: List<Int> = listOf(0),
        validStartPhases: Set<AutomationPhase> = setOf(AutomationPhase.SALE),
    ) = PageResult.Recognized(
        pageType = PlatformPageType("TEST_PAGE"),
        fingerprint = PageFingerprint(FINGERPRINT),
        windowId = windowId,
        evidence = listOf(
            PageEvidence(
                node = NodeReference(windowId, evidencePath),
                kind = EvidenceKind.TEXT_AND_ROLE,
                evidenceCode = "TEST_EVIDENCE",
            ),
        ),
        validStartPhases = validStartPhases,
    )

    private fun clickDecision(
        windowId: Int = 1,
        targetPath: List<Int> = listOf(1),
        resolution: ClickResolution = ClickResolution.EXACT_NODE,
        maximumAncestorDepth: Int = 0,
    ) = ActionDecision.Click(
        target = ClickTarget(
            node = NodeReference(windowId, targetPath),
            expectedPackageName = PACKAGE_NAME,
            expectedPageFingerprint = PageFingerprint(FINGERPRINT),
            resolution = resolution,
            maximumAncestorDepth = maximumAncestorDepth,
        ),
        eventCode = "TEST_CLICK",
    )

    private fun snapshot(
        windows: List<UiWindowSnapshot> = listOf(window()),
    ) = UiSnapshot(
        runId = RUN_ID,
        captureSequence = 1,
        packageName = PACKAGE_NAME,
        capturedAtEpochMillis = 1,
        windows = windows,
    )

    private fun window(
        id: Int = 1,
        layer: Int = 10,
        children: List<UiNodeSnapshot> = listOf(
            node(path = listOf(0), clickable = false),
            node(path = listOf(1), clickable = true),
        ),
    ) = UiWindowSnapshot(
        id = id,
        type = 1,
        layer = layer,
        title = null,
        boundsInScreen = UiBounds(0, 0, 1080, 1920),
        isActive = true,
        isFocused = true,
        isAccessibilityFocused = false,
        isInPictureInPictureMode = false,
        root = node(path = emptyList(), clickable = false, children = children),
    )

    private fun node(
        path: List<Int>,
        clickable: Boolean,
        children: List<UiNodeSnapshot> = emptyList(),
    ) = UiNodeSnapshot(
        path = path,
        packageName = PACKAGE_NAME,
        viewIdResourceName = null,
        text = null,
        contentDescription = null,
        className = if (clickable) "android.widget.Button" else "android.widget.TextView",
        boundsInScreen = UiBounds(10, 10, 200, 80),
        isVisibleToUser = true,
        isEnabled = true,
        isClickable = clickable,
        isSelected = false,
        isCheckable = false,
        checkedState = UiCheckedState.NOT_CHECKABLE,
        isFocusable = clickable,
        isScrollable = false,
        actionIds = emptySet(),
        children = children,
    )

    private fun runtime(taskState: TaskState) = EngineRuntimeState(
        taskState = taskState,
        phase = AutomationPhase.SALE,
        lastSnapshotSequence = 1,
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

    private class FakeAdapter(
        private val pageResult: PageResult,
        private val action: ActionDecision,
    ) : PlatformAdapter {
        override val platform = TicketPlatform.DAMAI
        override val supportedPackages = setOf(PACKAGE_NAME)
        override val supportedModes = RunMode.entries.toSet()
        var detectCalls = 0
        var decideCalls = 0

        override fun detectPage(
            snapshot: UiSnapshot,
            task: TicketTask,
        ): PageResult {
            detectCalls += 1
            return pageResult
        }

        override fun decideAction(
            page: PageResult.Recognized,
            task: TicketTask,
            runtime: EngineRuntimeState,
        ): ActionDecision {
            decideCalls += 1
            return action
        }
    }

    companion object {
        private const val RUN_ID = "run-1"
        private const val PACKAGE_NAME = "cn.damai"
        private const val FINGERPRINT = "fingerprint-1"
    }
}
