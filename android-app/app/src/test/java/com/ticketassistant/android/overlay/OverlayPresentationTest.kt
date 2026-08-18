package com.ticketassistant.android.overlay

import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.runtime.AutomationPhase
import com.ticketassistant.android.runtime.AwaitingValidStartEventCodes
import com.ticketassistant.android.runtime.EngineRuntimeState
import com.ticketassistant.android.runtime.SafetyPauseReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPresentationTest {
    @Test
    fun `running state exposes pause control`() {
        val presentation = OverlayPresentation.controls(state(TaskState.RUNNING))

        assertEquals("暂停", presentation.primaryButtonText)
        assertEquals(OverlayPrimaryAction.PAUSE, presentation.primaryAction)
        assertTrue(presentation.primaryButtonEnabled)
    }

    @Test
    fun `user paused state exposes resume control`() {
        val presentation = OverlayPresentation.controls(state(TaskState.USER_PAUSED))

        assertEquals("开始", presentation.primaryButtonText)
        assertEquals(OverlayPrimaryAction.RESUME, presentation.primaryAction)
        assertTrue(presentation.primaryButtonEnabled)
    }

    @Test
    fun `armed state exposes explicit start control`() {
        val presentation = OverlayPresentation.controls(state(TaskState.ARMED))

        assertEquals("开始", presentation.primaryButtonText)
        assertEquals(OverlayPrimaryAction.START, presentation.primaryAction)
        assertTrue(presentation.primaryButtonEnabled)
    }

    @Test
    fun `safety paused state cannot be resumed from overlay`() {
        val presentation = OverlayPresentation.controls(
            state(TaskState.SAFETY_PAUSED).copy(
                safetyPauseReason = SafetyPauseReason.UNKNOWN_PAGE,
            ),
        )

        assertEquals("已暂停", presentation.primaryButtonText)
        assertNull(presentation.primaryAction)
        assertFalse(presentation.primaryButtonEnabled)
    }

    @Test
    fun `waiting state keeps primary control disabled`() {
        val presentation = OverlayPresentation.controls(state(TaskState.WAIT_TARGET_APP))

        assertEquals("识别中", presentation.primaryButtonText)
        assertNull(presentation.primaryAction)
        assertFalse(presentation.primaryButtonEnabled)
    }

    @Test
    fun `formatter maps safety reason without accepting page content`() {
        val message = OverlayEventFormatter.message(
            eventCode = "TASK_SAFETY_PAUSED",
            state = state(TaskState.SAFETY_PAUSED).copy(
                safetyPauseReason = SafetyPauseReason.TARGET_MISMATCH,
            ),
        )

        assertEquals("购票目标不一致，已安全暂停", message)
    }

    @Test
    fun `formatter explains sanitized unknown start reason`() {
        val message = OverlayEventFormatter.message(
            eventCode = AwaitingValidStartEventCodes.UNKNOWN,
            state = state(TaskState.WAIT_TARGET_APP),
            sanitizedDetail = "reason=LOGIN_REQUIRED",
        )

        assertEquals("起始页未识别：需要登录", message)
    }

    @Test
    fun `formatter explains sanitized mismatch field`() {
        val message = OverlayEventFormatter.message(
            eventCode = AwaitingValidStartEventCodes.MISMATCH,
            state = state(TaskState.WAIT_TARGET_APP),
            sanitizedDetail = "field=DATE",
        )

        assertEquals("目标不一致：日期", message)
    }

    @Test
    fun `formatter reports recognized page invalid for current phase`() {
        val message = OverlayEventFormatter.message(
            eventCode = AwaitingValidStartEventCodes.INVALID_PHASE,
            state = state(TaskState.WAIT_TARGET_APP).copy(
                phase = AutomationPhase.RETURN_MONITOR,
            ),
            sanitizedDetail = "phase=RETURN_MONITOR;page_type=DM_ORDER_CONFIRM",
        )

        assertEquals("已识别页面，但不是回流阶段的起始页", message)
    }

    @Test
    fun `formatter never reflects unexpected detail into overlay`() {
        val privatePageText = "reason=LOGIN_REQUIRED;account=13800138000"
        val message = OverlayEventFormatter.message(
            eventCode = AwaitingValidStartEventCodes.UNKNOWN,
            state = state(TaskState.WAIT_TARGET_APP),
            sanitizedDetail = privatePageText,
        )

        assertEquals("尚未识别目标起始页面", message)
        assertFalse(message.contains("13800138000"))
    }

    @Test
    fun `formatter reports missing damai snapshot after probe timeout`() {
        val message = OverlayEventFormatter.message(
            eventCode = AwaitingValidStartEventCodes.SNAPSHOT_UNAVAILABLE,
            state = state(TaskState.WAIT_TARGET_APP),
            sanitizedDetail = "reason=NO_SUPPORTED_APP_WINDOW",
        )

        assertEquals("未读取到大麦页面，请确认大麦在前台", message)
    }

    @Test
    fun `formatter keeps contract failure detail out of overlay`() {
        val message = OverlayEventFormatter.message(
            eventCode = AwaitingValidStartEventCodes.CONTRACT_REJECTED,
            state = state(TaskState.WAIT_TARGET_APP),
            sanitizedDetail = "reason=PAGE_EVIDENCE_MISSING;account=13800138000",
        )

        assertEquals("起始页校验未通过，正在重新识别", message)
        assertFalse(message.contains("13800138000"))
    }

    private fun state(taskState: TaskState) = EngineRuntimeState(
        taskState = taskState,
        phase = AutomationPhase.SALE,
    )
}
