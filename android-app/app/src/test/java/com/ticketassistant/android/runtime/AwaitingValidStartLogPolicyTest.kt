package com.ticketassistant.android.runtime

import com.ticketassistant.android.platform.api.NodeReference
import com.ticketassistant.android.platform.api.AdapterContractViolation
import com.ticketassistant.android.platform.api.PageEvidence
import com.ticketassistant.android.platform.api.PageFingerprint
import com.ticketassistant.android.platform.api.PageResult
import com.ticketassistant.android.platform.api.PageUnknownReason
import com.ticketassistant.android.platform.api.PlatformPageType
import com.ticketassistant.android.platform.api.EvidenceKind
import com.ticketassistant.android.platform.api.TargetField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwaitingValidStartLogPolicyTest {
    @Test
    fun `unknown event records only the enum reason`() {
        val event = AwaitingValidStartLogEvent.from(
            pageResult = PageResult.Unknown(
                reason = PageUnknownReason.LOGIN_REQUIRED,
                eventCode = "DM_PAGE_UNKNOWN",
            ),
            phase = AutomationPhase.SALE,
        )

        assertEquals(AwaitingValidStartEventCodes.UNKNOWN, event.eventCode)
        assertEquals("reason=LOGIN_REQUIRED", event.sanitizedDetail)
        assertFalse(event.sanitizedDetail.contains("DM_PAGE_UNKNOWN"))
    }

    @Test
    fun `mismatch event discards expected and actual page values`() {
        val privatePageValue = "账号 13800138000 的页面原文"
        val event = AwaitingValidStartLogEvent.from(
            pageResult = PageResult.Mismatch(
                field = TargetField.DATE,
                expected = "2026-08-18",
                actual = privatePageValue,
            ),
            phase = AutomationPhase.SALE,
        )

        assertEquals(AwaitingValidStartEventCodes.MISMATCH, event.eventCode)
        assertEquals("field=DATE", event.sanitizedDetail)
        assertFalse(event.sanitizedDetail.contains("2026-08-18"))
        assertFalse(event.sanitizedDetail.contains(privatePageValue))
    }

    @Test
    fun `recognized event identifies invalid phase without fingerprint or node content`() {
        val privateFingerprint = "account-13800138000"
        val event = AwaitingValidStartLogEvent.from(
            pageResult = PageResult.Recognized(
                pageType = PlatformPageType("DM_ORDER_CONFIRM"),
                fingerprint = PageFingerprint(privateFingerprint),
                windowId = 7,
                evidence = listOf(
                    PageEvidence(
                        node = NodeReference(windowId = 7, path = listOf(0, 1)),
                        kind = EvidenceKind.TEXT_AND_ROLE,
                        evidenceCode = "DM_CONFIRM_MARKER",
                    ),
                ),
                validStartPhases = emptySet(),
            ),
            phase = AutomationPhase.RETURN_MONITOR,
        )

        assertEquals(AwaitingValidStartEventCodes.INVALID_PHASE, event.eventCode)
        assertEquals(
            "phase=RETURN_MONITOR;page_type=DM_ORDER_CONFIRM",
            event.sanitizedDetail,
        )
        assertFalse(event.sanitizedDetail.contains(privateFingerprint))
        assertFalse(event.sanitizedDetail.contains("DM_CONFIRM_MARKER"))
    }

    @Test
    fun `limiter deduplicates repeats and rate limits changing observations`() {
        val limiter = AwaitingValidStartLogLimiter(
            changedObservationIntervalMillis = 2_000L,
            repeatedObservationIntervalMillis = 15_000L,
        )
        val unknown = event(AwaitingValidStartEventCodes.UNKNOWN, "reason=INSUFFICIENT_EVIDENCE")
        val mismatch = event(AwaitingValidStartEventCodes.MISMATCH, "field=DATE")

        assertTrue(limiter.shouldRecord(unknown, nowElapsedRealtimeMillis = 1_000L))
        assertFalse(limiter.shouldRecord(unknown, nowElapsedRealtimeMillis = 15_999L))
        assertTrue(limiter.shouldRecord(unknown, nowElapsedRealtimeMillis = 16_000L))
        assertFalse(limiter.shouldRecord(mismatch, nowElapsedRealtimeMillis = 17_999L))
        assertTrue(limiter.shouldRecord(mismatch, nowElapsedRealtimeMillis = 18_000L))
    }

    @Test
    fun `limiter keeps per reason cooldown across a b a observations`() {
        val limiter = AwaitingValidStartLogLimiter(
            changedObservationIntervalMillis = 2_000L,
            repeatedObservationIntervalMillis = 15_000L,
        )
        val unknown = event(AwaitingValidStartEventCodes.UNKNOWN, "reason=INSUFFICIENT_EVIDENCE")
        val mismatch = event(AwaitingValidStartEventCodes.MISMATCH, "field=DATE")

        assertTrue(limiter.shouldRecord(unknown, nowElapsedRealtimeMillis = 1_000L))
        assertTrue(limiter.shouldRecord(mismatch, nowElapsedRealtimeMillis = 3_000L))
        assertFalse(limiter.shouldRecord(unknown, nowElapsedRealtimeMillis = 5_000L))
        assertTrue(limiter.shouldRecord(unknown, nowElapsedRealtimeMillis = 16_000L))
    }

    @Test
    fun `limiter reset allows the next run to record immediately`() {
        val limiter = AwaitingValidStartLogLimiter()
        val unknown = event(AwaitingValidStartEventCodes.UNKNOWN, "reason=UNKNOWN_TOP_WINDOW")
        assertTrue(limiter.shouldRecord(unknown, nowElapsedRealtimeMillis = 10_000L))
        assertFalse(limiter.shouldRecord(unknown, nowElapsedRealtimeMillis = 10_001L))

        limiter.reset()

        assertTrue(limiter.shouldRecord(unknown, nowElapsedRealtimeMillis = 10_002L))
    }

    @Test
    fun `snapshot timeout and contract failure contain enum-only details`() {
        assertEquals(
            AwaitingValidStartLogEvent(
                eventCode = AwaitingValidStartEventCodes.SNAPSHOT_UNAVAILABLE,
                sanitizedDetail = "reason=NO_SUPPORTED_APP_WINDOW",
            ),
            AwaitingValidStartLogEvent.snapshotUnavailable(),
        )
        assertEquals(
            AwaitingValidStartLogEvent(
                eventCode = AwaitingValidStartEventCodes.CONTRACT_REJECTED,
                sanitizedDetail = "reason=PAGE_EVIDENCE_MISSING",
            ),
            AwaitingValidStartLogEvent.contractRejected(
                AdapterContractViolation.PAGE_EVIDENCE_MISSING,
            ),
        )
    }

    private fun event(eventCode: String, detail: String) = AwaitingValidStartLogEvent(
        eventCode = eventCode,
        sanitizedDetail = detail,
    )
}
