package com.ticketassistant.android.ui.config

import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TicketTaskDraft
import com.ticketassistant.android.runtime.NotificationPermissionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigUiStateTest {
    @Test
    fun `start requires valid config and both user-facing permissions`() {
        val base = ConfigUiState(draft = validDraft())

        assertFalse(base.canStart)
        assertFalse(base.copy(accessibilityEnabled = true).canStart)
        assertTrue(
            base.copy(
                accessibilityEnabled = true,
                notificationPermissionState = NotificationPermissionState.GRANTED,
            ).canStart,
        )
    }

    @Test
    fun `active run blocks a second task`() {
        val state = ConfigUiState(
            draft = validDraft(),
            accessibilityEnabled = true,
            notificationPermissionState = NotificationPermissionState.GRANTED,
            runActive = true,
        )

        assertFalse(state.canStart)
    }

    private fun validDraft() = TicketTaskDraft(
        runMode = RunMode.SALE_THEN_RETURN,
        eventKeyword = "测试演出",
        targetSession = "周六 19:30",
        targetTier = "看台 580",
        targetPriceYuan = "580",
        ticketCount = "2",
        attendeesConfigured = true,
        maxSubmitAttempts = "20",
        maxRuntimeMinutes = "30",
    )
}
