package com.ticketassistant.android.overlay

import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.runtime.AutomationPhase
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

        assertEquals("等待", presentation.primaryButtonText)
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

    private fun state(taskState: TaskState) = EngineRuntimeState(
        taskState = taskState,
        phase = AutomationPhase.SALE,
    )
}
