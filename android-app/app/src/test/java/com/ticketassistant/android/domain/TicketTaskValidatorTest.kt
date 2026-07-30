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
        assertEquals("2026-08-01", task.targetDate)
        assertEquals(58_000L, task.targetPriceFen)
    }

    @Test
    fun `price parser converts whole yuan amount to fen`() {
        assertEquals(58_000L, TicketTaskValidator.parsePriceFen("580"))
    }

    @Test
    fun `price parser rejects decimal and non-numeric input`() {
        assertEquals(null, TicketTaskValidator.parsePriceFen("580.01"))
        assertEquals(null, TicketTaskValidator.parsePriceFen("580.001"))
        assertEquals(null, TicketTaskValidator.parsePriceFen("580元"))
        assertEquals(null, TicketTaskValidator.parsePriceFen("￥580"))
        assertEquals(null, TicketTaskValidator.parsePriceFen(" 580 "))
    }

    @Test
    fun `missing date is rejected`() {
        val result = TicketTaskValidator.validate(
            validDraft().copy(
                targetDate = "",
            ),
        )

        assertTrue(result is ValidationResult.Invalid)
        val fields = (result as ValidationResult.Invalid).issues.map { it.field }
        assertTrue(ConfigField.TARGET_DATE in fields)
    }

    @Test
    fun `invalid calendar date is rejected`() {
        val result = TicketTaskValidator.validate(
            validDraft().copy(targetDate = "2026-02-30"),
        )

        assertTrue(result is ValidationResult.Invalid)
        val fields = (result as ValidationResult.Invalid).issues.map { it.field }
        assertTrue(ConfigField.TARGET_DATE in fields)
    }

    private fun validDraft() = TicketTaskDraft(
        platform = TicketPlatform.DAMAI,
        runMode = RunMode.SALE_THEN_RETURN,
        targetDate = "2026-08-01",
        targetPriceYuan = "580",
    )
}
