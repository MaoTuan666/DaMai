package com.ticketassistant.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketTaskValidatorTest {
    @Test
    fun `valid draft produces immutable target snapshot values`() {
        val result = TicketTaskValidator.validate(validDraft())

        assertTrue(result is ValidationResult.Valid)
        val task = (result as ValidationResult.Valid).task
        assertEquals("周六 19:30", task.targetSession)
        assertEquals("看台 580", task.targetTier)
        assertEquals(58_000L, task.targetPriceFen)
        assertEquals(2, task.ticketCount)
        assertEquals(1_800L, task.maxRuntimeSeconds)
    }

    @Test
    fun `price parser converts exact yuan amount to fen`() {
        assertEquals(58_001L, TicketTaskValidator.parsePriceFen("580.01"))
        assertEquals(58_000L, TicketTaskValidator.parsePriceFen("580"))
    }

    @Test
    fun `price parser rejects fractions smaller than one fen`() {
        assertEquals(null, TicketTaskValidator.parsePriceFen("580.001"))
    }

    @Test
    fun `missing target and attendee confirmation are rejected`() {
        val result = TicketTaskValidator.validate(
            validDraft().copy(
                targetTier = "",
                attendeesConfigured = false,
            ),
        )

        assertTrue(result is ValidationResult.Invalid)
        val fields = (result as ValidationResult.Invalid).issues.map { it.field }
        assertTrue(ConfigField.TARGET_TIER in fields)
        assertTrue(ConfigField.ATTENDEES_CONFIGURED in fields)
    }

    @Test
    fun `safety limits reject unbounded values`() {
        val result = TicketTaskValidator.validate(
            validDraft().copy(
                maxSubmitAttempts = "101",
                maxRuntimeMinutes = "361",
            ),
        )

        assertTrue(result is ValidationResult.Invalid)
        val fields = (result as ValidationResult.Invalid).issues.map { it.field }
        assertTrue(ConfigField.MAX_SUBMIT_ATTEMPTS in fields)
        assertTrue(ConfigField.MAX_RUNTIME in fields)
    }

    private fun validDraft() = TicketTaskDraft(
        platform = TicketPlatform.DAMAI,
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
