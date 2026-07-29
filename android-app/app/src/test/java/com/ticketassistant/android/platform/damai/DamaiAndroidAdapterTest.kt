package com.ticketassistant.android.platform.damai

import com.ticketassistant.android.accessibility.UiBounds
import com.ticketassistant.android.accessibility.UiCheckedState
import com.ticketassistant.android.accessibility.UiNodeSnapshot
import com.ticketassistant.android.accessibility.UiSnapshot
import com.ticketassistant.android.accessibility.UiWindowSnapshot
import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.domain.TicketPlatform
import com.ticketassistant.android.domain.TicketTask
import com.ticketassistant.android.platform.api.ActionDecision
import com.ticketassistant.android.platform.api.AdapterStopReason
import com.ticketassistant.android.platform.api.PageResult
import com.ticketassistant.android.platform.api.PageUnknownReason
import com.ticketassistant.android.platform.api.PlatformAdapterRegistry
import com.ticketassistant.android.platform.api.PlatformDecisionCoordinator
import com.ticketassistant.android.platform.api.PlatformEvaluation
import com.ticketassistant.android.platform.api.TargetField
import com.ticketassistant.android.platform.api.WaitReason
import com.ticketassistant.android.runtime.AutomationPhase
import com.ticketassistant.android.runtime.EngineRuntimeState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DamaiAndroidAdapterTest {
    private val adapter = DamaiAndroidAdapter()

    @Test
    fun `reserved project page waits without click`() {
        val page = recognized(
            adapter.detectPage(
                snapshot(
                    node(EVENT),
                    node("抢票攻略"),
                    node("已预约"),
                ),
                task(),
            ),
        )

        assertEquals(DamaiPageTypes.WAIT_SALE_DETAIL, page.pageType)
        assertEquals(
            ActionDecision.Wait(WaitReason.SALE_NOT_OPEN, "DM_WAIT_RESERVED"),
            adapter.decideAction(page, task(), runtime()),
        )
    }

    @Test
    fun `bookable project page proposes book now once`() {
        val sourceSnapshot = snapshot(
            node(EVENT),
            node("抢票攻略"),
            node("立即预订", clickable = true),
        )
        val page = recognized(adapter.detectPage(sourceSnapshot, task()))

        val action = adapter.decideAction(page, task(), runtime())

        assertEquals(DamaiPageTypes.BOOKABLE_PROJECT_DETAIL, page.pageType)
        assertTrue(action is ActionDecision.Click)
        assertEquals("DM_CLICK_BOOK_NOW", (action as ActionDecision.Click).eventCode)
        assertFalse(action.countsAsSubmitAttempt)

        val evaluation = PlatformDecisionCoordinator(
            PlatformAdapterRegistry(listOf(adapter)),
        ).evaluate(
            expectedRunId = "run-1",
            snapshot = sourceSnapshot,
            task = task(),
            runtime = runtime(),
        )
        assertTrue(evaluation is PlatformEvaluation.Decision)
    }

    @Test
    fun `seat entry requires fixed session and tier`() {
        val page = recognized(
            adapter.detectPage(
                snapshot(
                    node(SESSION),
                    node(TIER),
                    node("去选座", clickable = true),
                ),
                task(),
            ),
        )

        val action = adapter.decideAction(page, task(), runtime())

        assertEquals(DamaiPageTypes.SEAT_ENTRY, page.pageType)
        assertEquals("DM_CLICK_SEAT_ENTRY", (action as ActionDecision.Click).eventCode)
    }

    @Test
    fun `seat entry reports session mismatch before any action`() {
        val result = adapter.detectPage(
            snapshot(
                node("其他场次"),
                node(TIER),
                node("去选座", clickable = true),
            ),
            task(),
        )

        assertTrue(result is PageResult.Mismatch)
        assertEquals(TargetField.SESSION, (result as PageResult.Mismatch).field)
    }

    @Test
    fun `fully validated confirmation page proposes submit and counts attempt`() {
        val page = recognized(adapter.detectPage(orderConfirmationSnapshot(), task()))

        val action = adapter.decideAction(page, task(), runtime())

        assertEquals(DamaiPageTypes.ORDER_CONFIRM, page.pageType)
        assertTrue(action is ActionDecision.Click)
        action as ActionDecision.Click
        assertEquals("DM_CLICK_SUBMIT", action.eventCode)
        assertTrue(action.countsAsSubmitAttempt)
    }

    @Test
    fun `confirmation page rejects mismatched quantity`() {
        val result = adapter.detectPage(
            snapshot(
                node("确认购买"),
                node(EVENT),
                node(SESSION),
                node(TIER),
                node("￥580"),
                node("数量：2"),
                node("实名观演人"),
                node("已选1人"),
                node("立即提交", clickable = true),
            ),
            task(),
        )

        assertTrue(result is PageResult.Mismatch)
        assertEquals(TargetField.QUANTITY, (result as PageResult.Mismatch).field)
    }

    @Test
    fun `confirmation page rejects mismatched price`() {
        val result = adapter.detectPage(
            snapshot(
                node("确认购买"),
                node(EVENT),
                node(SESSION),
                node(TIER),
                node("￥680"),
                node("数量：1"),
                node("实名观演人"),
                node("已选1人"),
                node("立即提交", clickable = true),
            ),
            task(),
        )

        assertTrue(result is PageResult.Mismatch)
        assertEquals(TargetField.PRICE, (result as PageResult.Mismatch).field)
        assertEquals(DamaiEventCodes.SAFE_PAUSE, result.eventCode)
    }

    @Test
    fun `confirmation page rejects attendee count mismatch`() {
        val result = adapter.detectPage(
            snapshot(
                node("确认购买"),
                node(EVENT),
                node(SESSION),
                node(TIER),
                node("￥580"),
                node("数量：1"),
                node("实名观演人"),
                node("已选2人"),
                node("立即提交", clickable = true),
            ),
            task(),
        )

        assertTrue(result is PageResult.Mismatch)
        assertEquals(TargetField.ATTENDEE_COUNT, (result as PageResult.Mismatch).field)
    }

    @Test
    fun `continue always wins when both popup actions exist`() {
        val page = recognized(
            adapter.detectPage(
                snapshot(
                    node("继续尝试", clickable = true),
                    node("返回重新选购", clickable = true),
                ),
                task(),
            ),
        )

        val action = adapter.decideAction(page, task(), runtime())

        assertEquals(DamaiPageTypes.SUBMIT_RESULT_DIALOG, page.pageType)
        assertTrue(action is ActionDecision.Click)
        action as ActionDecision.Click
        assertEquals("DM_POPUP_CONTINUE", action.eventCode)
        assertTrue(action.countsAsSubmitAttempt)
    }

    @Test
    fun `disabled continue does not outrank actionable return`() {
        val page = recognized(
            adapter.detectPage(
                snapshot(
                    node("继续尝试", clickable = true, enabled = false),
                    node("返回重新选购", clickable = true),
                ),
                task(runMode = RunMode.SALE_THEN_RETURN),
            ),
        )

        val action = adapter.decideAction(
            page,
            task(runMode = RunMode.SALE_THEN_RETURN),
            runtime(),
        )

        assertEquals("DM_POPUP_RETURN", (action as ActionDecision.Click).eventCode)
    }

    @Test
    fun `sale-only return popup stops without clicking return`() {
        val page = recognized(
            adapter.detectPage(
                snapshot(node("返回重新选购", clickable = true)),
                task(),
            ),
        )

        val action = adapter.decideAction(page, task(), runtime())

        assertEquals(
            ActionDecision.Stop(
                AdapterStopReason.SALE_FLOW_EXHAUSTED,
                "DM_POPUP_RETURN",
            ),
            action,
        )
    }

    @Test
    fun `sale-then-return popup proposes return click`() {
        val configuredTask = task(runMode = RunMode.SALE_THEN_RETURN)
        val page = recognized(
            adapter.detectPage(
                snapshot(node("返回重新选购", clickable = true)),
                configuredTask,
            ),
        )

        val action = adapter.decideAction(page, configuredTask, runtime())

        assertEquals("DM_POPUP_RETURN", (action as ActionDecision.Click).eventCode)
    }

    @Test
    fun `sold-out target uses accessible back before global action`() {
        val page = recognized(
            adapter.detectPage(
                returnSelectionSnapshot(
                    target = node(
                        TIER,
                        children = listOf(node("缺货登记")),
                    ),
                    confirm = node("确定", clickable = false),
                    extra = listOf(node("返回", clickable = true)),
                ),
                task(runMode = RunMode.RETURN_ONLY),
            ),
        )

        val action = adapter.decideAction(
            page,
            task(runMode = RunMode.RETURN_ONLY),
            runtime(AutomationPhase.RETURN_MONITOR),
        )

        assertEquals(DamaiPageTypes.RETURN_SOLD_OUT, page.pageType)
        assertTrue(action is ActionDecision.Click)
        assertEquals("DM_RETURN_BACK_TO_DETAIL", (action as ActionDecision.Click).eventCode)
    }

    @Test
    fun `sold-out target falls back to fingerprint-bound global back`() {
        val page = recognized(
            adapter.detectPage(
                returnSelectionSnapshot(
                    target = node(
                        TIER,
                        children = listOf(node("缺货登记")),
                    ),
                    confirm = node("确定", clickable = false),
                ),
                task(runMode = RunMode.RETURN_ONLY),
            ),
        )

        val action = adapter.decideAction(
            page,
            task(runMode = RunMode.RETURN_ONLY),
            runtime(AutomationPhase.RETURN_MONITOR),
        )

        assertTrue(action is ActionDecision.GlobalBack)
        assertEquals(page.fingerprint, (action as ActionDecision.GlobalBack).expectedPageFingerprint)
    }

    @Test
    fun `available return page selects fixed target tier not another tier`() {
        val target = node(TIER, clickable = true)
        val other = node("其他票档 680", clickable = true)
        val snapshot = returnSelectionSnapshot(
            target = target,
            confirm = node("确定", clickable = false),
            extra = listOf(other),
        )
        val page = recognized(
            adapter.detectPage(snapshot, task(runMode = RunMode.RETURN_ONLY)),
        )

        val action = adapter.decideAction(
            page,
            task(runMode = RunMode.RETURN_ONLY),
            runtime(AutomationPhase.RETURN_MONITOR),
        ) as ActionDecision.Click

        assertEquals(DamaiPageTypes.RETURN_TIER_SELECTION, page.pageType)
        assertEquals(DamaiEventCodes.TARGET_TIER_AVAILABLE, page.observationEventCode)
        assertEquals("DM_SELECT_TARGET_TIER", action.eventCode)
        assertEquals(page.evidence.first { it.evidenceCode == "DM_TARGET_TIER" }.node, action.target.node)
    }

    @Test
    fun `selected fixed tier and enabled confirm proposes confirm`() {
        val page = recognized(
            adapter.detectPage(
                returnSelectionSnapshot(
                    target = node(TIER, clickable = true, selected = true),
                    confirm = node("确定", clickable = true),
                    extra = listOf(node("数量：1")),
                ),
                task(runMode = RunMode.RETURN_ONLY),
            ),
        )

        val action = adapter.decideAction(
            page,
            task(runMode = RunMode.RETURN_ONLY),
            runtime(AutomationPhase.RETURN_MONITOR),
        )

        assertEquals(DamaiPageTypes.RETURN_CONFIRM, page.pageType)
        assertEquals("DM_CONFIRM_TARGET_TIER", (action as ActionDecision.Click).eventCode)
    }

    @Test
    fun `sale-then-return switches phase before return-page action`() {
        val configuredTask = task(runMode = RunMode.SALE_THEN_RETURN)
        val page = recognized(
            adapter.detectPage(
                returnSelectionSnapshot(
                    target = node(TIER, clickable = true),
                    confirm = node("确定"),
                ),
                configuredTask,
            ),
        )

        val action = adapter.decideAction(page, configuredTask, runtime())

        assertEquals(
            ActionDecision.SwitchPhase(
                AutomationPhase.RETURN_MONITOR,
                "DM_SWITCH_TO_RETURN",
            ),
            action,
        )
    }

    @Test
    fun `return reentry detail without sale guide only acts in return phase`() {
        val configuredTask = task(runMode = RunMode.RETURN_ONLY)
        val page = recognized(
            adapter.detectPage(
                snapshot(
                    node(EVENT),
                    node("立即预订", clickable = true),
                ),
                configuredTask,
            ),
        )

        val action = adapter.decideAction(
            page,
            configuredTask,
            runtime(AutomationPhase.RETURN_MONITOR),
        )

        assertEquals(DamaiPageTypes.RETURN_REENTER_DETAIL, page.pageType)
        assertEquals("DM_RETURN_REENTER", (action as ActionDecision.Click).eventCode)
    }

    @Test
    fun `checkout stops for manual payment`() {
        val page = recognized(
            adapter.detectPage(
                snapshot(
                    node("订单金额 ￥580"),
                    node("支付方式"),
                    node("付款", clickable = true),
                ),
                task(),
            ),
        )

        val action = adapter.decideAction(page, task(), runtime())

        assertEquals(DamaiPageTypes.CHECKOUT, page.pageType)
        assertEquals(
            ActionDecision.Stop(AdapterStopReason.ORDER_LOCKED, "DM_ORDER_LOCKED"),
            action,
        )
    }

    @Test
    fun `verification page is never treated as ordinary page`() {
        assertEquals(
            PageResult.Unknown(
                PageUnknownReason.VERIFICATION_REQUIRED,
                DamaiEventCodes.SAFE_PAUSE,
            ),
            adapter.detectPage(snapshot(node("请完成安全验证")), task()),
        )
    }

    @Test
    fun `unknown top popup blocks actionable lower confirmation page`() {
        val lower = orderConfirmationWindow(id = 1, layer = 10)
        val unknownPopup = window(
            id = 2,
            layer = 20,
            children = listOf(node("知道了", clickable = true)),
        )

        val result = adapter.detectPage(
            snapshot(windows = listOf(unknownPopup, lower)),
            task(),
        )

        assertEquals(
            PageResult.Unknown(
                PageUnknownReason.UNKNOWN_TOP_WINDOW,
                DamaiEventCodes.SAFE_PAUSE,
            ),
            result,
        )
    }

    @Test
    fun `unrecognized page returns unknown`() {
        assertEquals(
            PageResult.Unknown(
                PageUnknownReason.INSUFFICIENT_EVIDENCE,
                DamaiEventCodes.SAFE_PAUSE,
            ),
            adapter.detectPage(snapshot(node("普通页面")), task()),
        )
    }

    private fun orderConfirmationSnapshot(): UiSnapshot =
        snapshot(*orderConfirmationNodes().toTypedArray())

    private fun orderConfirmationWindow(
        id: Int,
        layer: Int,
    ): UiWindowSnapshot = window(
        id = id,
        layer = layer,
        children = orderConfirmationNodes(),
    )

    private fun orderConfirmationNodes(): List<TestNode> = listOf(
        node("确认购买"),
        node(EVENT),
        node(SESSION),
        node(TIER),
        node("￥580"),
        node("数量：1"),
        node("实名观演人"),
        node("已选1人"),
        node("立即提交", clickable = true),
    )

    private fun returnSelectionSnapshot(
        target: TestNode,
        confirm: TestNode,
        extra: List<TestNode> = emptyList(),
    ): UiSnapshot = snapshot(
        node(EVENT),
        node("场次"),
        node(SESSION),
        node("票档"),
        target,
        node("￥580"),
        confirm,
        *extra.toTypedArray(),
    )

    private fun recognized(result: PageResult): PageResult.Recognized {
        assertTrue("Expected recognized page but was $result", result is PageResult.Recognized)
        return result as PageResult.Recognized
    }

    private fun snapshot(
        vararg nodes: TestNode,
        windows: List<UiWindowSnapshot>? = null,
    ): UiSnapshot = UiSnapshot(
        runId = "run-1",
        captureSequence = 1,
        packageName = DamaiAndroidAdapter.DAMAI_PACKAGE,
        capturedAtEpochMillis = 1,
        windows = windows ?: listOf(window(children = nodes.toList())),
    )

    private fun window(
        id: Int = 1,
        layer: Int = 10,
        children: List<TestNode>,
    ): UiWindowSnapshot {
        val root = testNodeToSnapshot(
            spec = node(null, children = children),
            path = emptyList(),
        )
        return UiWindowSnapshot(
            id = id,
            type = 1,
            layer = layer,
            title = null,
            boundsInScreen = UiBounds(0, 0, 1080, 1920),
            isActive = true,
            isFocused = true,
            isAccessibilityFocused = false,
            isInPictureInPictureMode = false,
            root = root,
        )
    }

    private fun testNodeToSnapshot(
        spec: TestNode,
        path: List<Int>,
    ): UiNodeSnapshot = UiNodeSnapshot(
        path = path,
        packageName = DamaiAndroidAdapter.DAMAI_PACKAGE,
        viewIdResourceName = null,
        text = spec.text,
        contentDescription = null,
        className = if (spec.clickable) "android.widget.Button" else "android.widget.TextView",
        boundsInScreen = UiBounds(10, 10, 300, 90),
        isVisibleToUser = true,
        isEnabled = spec.enabled,
        isClickable = spec.clickable,
        isSelected = spec.selected,
        isCheckable = spec.selected,
        checkedState = if (spec.selected) UiCheckedState.CHECKED else UiCheckedState.NOT_CHECKABLE,
        isFocusable = spec.clickable,
        isScrollable = false,
        actionIds = emptySet(),
        children = spec.children.mapIndexed { index, child ->
            testNodeToSnapshot(child, path + index)
        },
    )

    private fun node(
        text: String?,
        clickable: Boolean = false,
        enabled: Boolean = true,
        selected: Boolean = false,
        children: List<TestNode> = emptyList(),
    ) = TestNode(text, clickable, enabled, selected, children)

    private fun runtime(
        phase: AutomationPhase = AutomationPhase.SALE,
    ) = EngineRuntimeState(
        taskState = TaskState.RUNNING,
        phase = phase,
        lastSnapshotSequence = 1,
    )

    private fun task(
        runMode: RunMode = RunMode.SALE_ONLY,
    ) = TicketTask(
        id = 1,
        platform = TicketPlatform.DAMAI,
        runMode = runMode,
        eventKeyword = EVENT,
        targetSession = SESSION,
        targetTier = TIER,
        targetPriceFen = 58_000,
        ticketCount = 1,
        adapterConfig = "{}",
        maxSubmitAttempts = 20,
        maxRuntimeSeconds = 1_800,
        state = TaskState.WAIT_TARGET_APP,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private data class TestNode(
        val text: String?,
        val clickable: Boolean,
        val enabled: Boolean,
        val selected: Boolean,
        val children: List<TestNode>,
    )

    companion object {
        private const val EVENT = "测试演出"
        private const val SESSION = "周六 19:30"
        private const val TIER = "看台 580"
    }
}
